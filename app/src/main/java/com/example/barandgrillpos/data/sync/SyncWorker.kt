package com.example.barandgrillpos.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.barandgrillpos.BuildConfig
import com.example.barandgrillpos.data.local.AppDatabase
import com.example.barandgrillpos.data.local.SyncQueueEntry
import com.example.barandgrillpos.data.remote.SupabaseManager
import com.example.barandgrillpos.data.remote.dto.SaleDto
import com.example.barandgrillpos.data.remote.dto.SaleItemDto
import com.example.barandgrillpos.models.BranchRef
import com.example.barandgrillpos.models.OrderItem
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.*

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    @Serializable
    private data class InsertedSale(val id: String)

    override suspend fun doWork(): Result {
        if (runAttemptCount >= 3) {
            // Stop retrying after 3 failed attempts to prevent infinite loops
            println("SYNC: SyncWorker failed after $runAttemptCount attempts. Stopping retries.")
            return Result.failure()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val saleDao = database.saleDao()
        val queue = saleDao.getSyncQueue()
        if (queue.isEmpty()) return Result.success()

        // Resolve the branch UUID for this app flavor at sync time.
        // This is more reliable than relying on UI state (appBranchId) which may be null if
        // the sale was recorded before the branch fetch completed.
        val resolvedBranchId: String? = try {
            SupabaseManager.client.postgrest["branches"]
                .select { filter { eq("name", BuildConfig.STORE_NAME) } }
                .decodeAs<List<BranchRef>>()
                .firstOrNull()?.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

        // 1) Sync Expenses
        val expenseDao = database.expenseDao()
        val unsyncedExpenses = expenseDao.getUnsyncedExpenses()
        for (expense in unsyncedExpenses) {
            try {
                val expenseDto = buildJsonObject {
                    put("amount", expense.amount)
                    put("category", expense.category)
                    put("description", expense.description)
                    put("branch_id", expense.branchId ?: resolvedBranchId)
                    put("timestamp", sdf.format(Date(expense.timestamp)))
                }
                SupabaseManager.client.postgrest["expenses"].insert(expenseDto)
                expenseDao.markAsSynced(expense.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2) Sync Sales
        for (entry in queue) {
            try {
                if (entry.action != "INSERT_SALE") {
                    // Unknown action: drop it so it doesn't block queue forever
                    saleDao.removeFromSyncQueue(entry)
                    continue
                }

                val saleEntity = Json.decodeFromString<com.example.barandgrillpos.data.local.SaleEntity>(entry.payloadJson)

                // Idempotency: if sale already exists remotely (same order_id), just mark local as synced and drop queue entry
                val existingSaleId: String? = try {
                    SupabaseManager.client.postgrest["sales"]
                        .select { filter { eq("order_id", saleEntity.id) } }
                        .decodeAs<List<InsertedSale>>()
                        .firstOrNull()?.id
                } catch (_: Exception) {
                    null
                }

                val saleUuid = existingSaleId ?: run {
                    val saleDto = SaleDto(
                        orderId = saleEntity.id,
                        totalAmount = saleEntity.totalAmount,
                        paymentMethod = saleEntity.paymentMethod,
                        soldBy = saleEntity.soldBy,
                        timestamp = sdf.format(Date(saleEntity.timestamp)),
                        branchId = saleEntity.branchId ?: resolvedBranchId
                    )
                    SupabaseManager.client.postgrest["sales"]
                        .insert(saleDto) { select() }
                        .decodeSingle<InsertedSale>()
                        .id
                }

                val items: List<OrderItem> = Json.decodeFromString(saleEntity.itemsJson)
                val itemDtos = items.map { item ->
                    SaleItemDto(
                        saleId = saleUuid,
                        name = item.item.name,
                        price = item.item.price,
                        quantity = item.quantity,
                        category = item.item.category
                    )
                }

                if (itemDtos.isNotEmpty()) {
                    SupabaseManager.client.postgrest["sale_items"].insert(itemDtos)

                    for (idx in items.indices) {
                        val orderItem = items[idx]
                        val itemDto = itemDtos[idx]
                        val ingredients = orderItem.item.ingredients
                        
                        if (!ingredients.isNullOrEmpty()) {
                            for (ing in ingredients) {
                                try {
                                    SupabaseManager.client.postgrest.rpc(
                                        function = "deduct_inventory",
                                        parameters = buildJsonObject {
                                            put("item_name", ing.inventory_name)
                                            put("qty", (orderItem.quantity * ing.quantity)) 
                                            put("b_id", saleEntity.branchId ?: resolvedBranchId)
                                        }
                                    )
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        } else {
                            try {
                                SupabaseManager.client.postgrest.rpc(
                                    function = "deduct_inventory",
                                    parameters = buildJsonObject {
                                        put("item_name", itemDto.name)
                                        put("qty", itemDto.quantity)
                                        put("b_id", saleEntity.branchId ?: resolvedBranchId)
                                    }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                saleDao.markAsSynced(saleEntity.id)
                saleDao.removeFromSyncQueue(entry)
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't return Result.retry() here, continue with other sales/expenses
            }
        }

        // 3) Download Menu & Inventory for current branch
        if (resolvedBranchId != null) {
            try {
                // Fetch Menu Items
                val remoteMenu = SupabaseManager.client.postgrest["menu_items"]
                    .select { filter { eq("branch_id", resolvedBranchId) } }
                    .decodeAs<List<com.example.barandgrillpos.data.remote.dto.MenuItemDto>>()
                
                val menuEntities = remoteMenu.map { dto ->
                    com.example.barandgrillpos.data.local.MenuItemEntity(
                        id = dto.id,
                        name = dto.name,
                        price = dto.price,
                        category = dto.category,
                        subcategory = dto.subcategory,
                        branchId = dto.branchId,
                        isActive = dto.isActive,
                        ingredientsJson = dto.ingredients?.let { Json.encodeToString(it) }
                    )
                }
                database.cacheDao().upsertMenu(menuEntities)

                // Fetch Inventory
                val remoteInv = SupabaseManager.client.postgrest["inventory"]
                    .select { filter { eq("branch_id", resolvedBranchId) } }
                    .decodeAs<List<com.example.barandgrillpos.data.remote.dto.InventoryItemDto>>()
                
                val invEntities = remoteInv.map { dto ->
                    com.example.barandgrillpos.data.local.InventoryItemEntity(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        name = dto.name,
                        stock_quantity = dto.stock_quantity,
                        min_threshold = dto.min_threshold,
                        status = dto.status,
                        branchId = dto.branchId
                    )
                }
                database.cacheDao().upsertInventory(invEntities)

                // Fetch Categories (Optional but good)
                val remoteCats = SupabaseManager.client.postgrest["categories"]
                    .select()
                    .decodeAs<List<com.example.barandgrillpos.data.remote.dto.CategoryDto>>()
                
                val catEntities = remoteCats.map { dto ->
                    com.example.barandgrillpos.data.local.CategoryEntity(
                        name = dto.name,
                        parentName = dto.parentName
                    )
                }
                database.cacheDao().upsertCategories(catEntities)

                // 4) Fetch Customers (Membership Data)
                val remoteCustomers = SupabaseManager.client.postgrest["customers"]
                    .select { filter { eq("branch_id", resolvedBranchId) } }
                    .decodeAs<List<com.example.barandgrillpos.data.remote.dto.CustomerDto>>()
                
                val customerEntities = remoteCustomers.map { dto ->
                    com.example.barandgrillpos.data.local.CustomerEntity(
                        id = dto.id,
                        name = dto.name,
                        phone = dto.phone,
                        email = dto.email,
                        address = dto.address,
                        idType = dto.idType,
                        idNumber = dto.idNumber,
                        profileImageUrl = dto.profileImageUrl,
                        membershipStatus = dto.membershipStatus,
                        membershipExpiry = dto.membershipExpiry,
                        notes = dto.notes,
                        branchId = dto.branchId,
                        createdAt = dto.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                database.cacheDao().upsertCustomers(customerEntities)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5) Check for Low Stock and notify (Local check)
        checkLowStock(applicationContext)

        return Result.success()
    }

    private suspend fun checkLowStock(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val inventory = db.cacheDao().observeInventory().first()
        val lowStockItems = inventory.filter { it.stock_quantity <= it.min_threshold && it.stock_quantity > 0 }
        
        if (lowStockItems.isNotEmpty()) {
            val title = "Low Stock Alert!"
            val message = lowStockItems.take(3).joinToString(", ") { it.name } + 
                          if (lowStockItems.size > 3) " and ${lowStockItems.size - 3} more" else ""
            showNotification(context, title, message)
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "low_stock_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Low Stock Alerts", android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
