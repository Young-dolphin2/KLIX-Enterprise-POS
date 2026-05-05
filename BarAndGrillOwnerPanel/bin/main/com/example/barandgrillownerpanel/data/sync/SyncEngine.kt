package com.example.barandgrillownerpanel.data.sync

import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.data.local.SyncQueueItem
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.utils.Logger
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.time.Instant

/**
 * Background sync engine that keeps the local SQLite database
 * in sync with Supabase.
 * 
 * Runs on app launch and periodically thereafter.
 * Never blocks the UI — all operations are on Dispatchers.IO.
 */
object SyncEngine {
    private const val TAG = "SYNC"
    private const val PUSH_BATCH_SIZE = 50
    private const val PULL_LIMIT = 500L
    
    private var syncJob: Job? = null
    private var isSyncing = false

    private val _syncStatus = kotlinx.coroutines.flow.MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: kotlinx.coroutines.flow.StateFlow<SyncStatus> = _syncStatus

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        object Error : SyncStatus()
        object Success : SyncStatus()
    }

    /**
     * Start the background sync cycle.
     * 1. Push local changes to Supabase
     * 2. Pull remote changes from Supabase
     * 3. Schedule next sync
     */
    fun startSyncCycle(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch(IO) {
            while (isActive) {
                try {
                    if (!isSyncing) {
                        isSyncing = true
                        _syncStatus.value = SyncStatus.Syncing
                        pushLocalChanges()
                        pullRemoteChanges()
                        _syncStatus.value = SyncStatus.Success
                        isSyncing = false
                    }
                } catch (e: Exception) {
                    isSyncing = false
                    _syncStatus.value = SyncStatus.Error
                    Logger.warn(TAG, "Sync cycle failed: ${e.message}")
                }
                // Sync every 30 seconds while app is open
                delay(30_000)
            }
        }
        Logger.info(TAG, "Sync cycle started")
    }

    fun stopSyncCycle() {
        syncJob?.cancel()
        syncJob = null
        Logger.info(TAG, "Sync cycle stopped")
    }

    // ================================================================
    // PUSH: Local → Supabase
    // ================================================================

    private suspend fun pushLocalChanges() {
        val pendingSyncs = LocalDatabase.getPendingSyncs()
        if (pendingSyncs.isEmpty()) return

        Logger.info(TAG, "Pushing ${pendingSyncs.size} local changes to Supabase")

        for (sync in pendingSyncs.take(PUSH_BATCH_SIZE)) {
            // Exponential backoff check: retry_count * 5 minutes
            val waitTime = sync.retryCount * 300_000L 
            val createdAt = Instant.parse(sync.createdAt).toEpochMilli()
            if (System.currentTimeMillis() - createdAt < waitTime) continue

            try {
                if (sync.operation == "DELETE") {
                    SupabaseManager.client.postgrest[sync.tableName].delete {
                        filter { eq("id", sync.recordId) }
                    }
                } else {
                    when (sync.tableName) {
                        "sales" -> pushSale(sync)
                        "expenses" -> pushExpense(sync)
                        "credits" -> pushCredit(sync)
                        "inventory" -> pushInventory(sync)
                        "menu_items" -> pushMenuItem(sync)
                        "branches" -> pushBranch(sync)
                        "customers" -> pushCustomer(sync)
                    }
                }
                LocalDatabase.markSyncPushed(sync.id)
            } catch (e: Exception) {
                LocalDatabase.incrementSyncRetry(sync.id)
                Logger.warn(TAG, "Failed to push ${sync.tableName}/${sync.recordId}: ${e.message}")
            }
        }
    }

    private suspend fun pushSale(sync: SyncQueueItem) {
        val sale = LocalDatabase.getSale(sync.recordId) ?: return
        val items = LocalDatabase.getSaleItems(sync.recordId)
        
        // Push sale first
        SupabaseManager.client.postgrest["sales"].upsert(sale)
        
        // Push items
        if (items.isNotEmpty()) {
            SupabaseManager.client.postgrest["sale_items"].upsert(items)
        }
    }

    private suspend fun pushExpense(sync: SyncQueueItem) {
        val expense = LocalDatabase.getExpense(sync.recordId) ?: return
        SupabaseManager.client.postgrest["expenses"].upsert(expense)
    }

    private suspend fun pushCredit(sync: SyncQueueItem) {
        val credit = LocalDatabase.getCredit(sync.recordId) ?: return
        SupabaseManager.client.postgrest["credits"].upsert(credit)
    }

    private suspend fun pushInventory(sync: SyncQueueItem) {
        val item = LocalDatabase.getInventoryItem(sync.recordId) ?: return
        SupabaseManager.client.postgrest["inventory"].upsert(item)
    }

    private suspend fun pushMenuItem(sync: SyncQueueItem) {
        val item = LocalDatabase.getMenuItem(sync.recordId) ?: return
        SupabaseManager.client.postgrest["menu_items"].upsert(item)
    }

    private suspend fun pushBranch(sync: SyncQueueItem) {
        // Branches are usually read-only from the POS, but if we need to push:
        // SupabaseManager.client.postgrest["branches"].upsert(...)
    }

    private suspend fun pushCustomer(sync: SyncQueueItem) {
        val customer = LocalDatabase.getCustomer(sync.recordId) ?: return
        SupabaseManager.client.postgrest["customers"].upsert(customer)
    }

    // ================================================================
    // PULL: Supabase → Local
    // ================================================================

    private suspend fun pullRemoteChanges() {
        try {
            val lastSync = LocalDatabase.getLastSyncTimestamp()
            val now = Instant.now().toString()

            Logger.info(TAG, "Pulling remote changes since $lastSync")

            // Pull branches
            pullBranches(lastSync)
            // Pull menu items
            pullMenuItems(lastSync)
            // Pull inventory
            pullInventory(lastSync)
            // Pull sales (only recent — full history on demand)
            pullSales(lastSync)
            // Pull expenses
            pullExpenses(lastSync)
            // Pull credits
            pullCredits(lastSync)
            // Pull categories
            pullCategories(lastSync)
            // Pull customers
            pullCustomers(lastSync)

            LocalDatabase.setLastSyncTimestamp(now)
            Logger.info(TAG, "Pull complete")
        } catch (e: Exception) {
            Logger.warn(TAG, "Pull failed: ${e.message}")
        }
    }

    private suspend fun pullBranches(lastSync: String) {
        try {
            val branches = SupabaseManager.client.postgrest["branches"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<BranchDto>>()
            
            branches.forEach { LocalDatabase.upsertBranch(it, fromSync = true) }
            if (branches.isNotEmpty()) Logger.info(TAG, "Pulled ${branches.size} branches")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull branches: ${e.message}")
        }
    }

    private suspend fun pullMenuItems(lastSync: String) {
        try {
            val items = SupabaseManager.client.postgrest["menu_items"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<MenuItemDto>>()
            
            items.forEach { LocalDatabase.saveMenuItem(it, fromSync = true) }
            if (items.isNotEmpty()) Logger.info(TAG, "Pulled ${items.size} menu items")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull menu items: ${e.message}")
        }
    }

    private suspend fun pullInventory(lastSync: String) {
        try {
            val items = SupabaseManager.client.postgrest["inventory"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<InventoryItemDto>>()
            
            items.forEach { LocalDatabase.saveInventoryItem(it, fromSync = true) }
            if (items.isNotEmpty()) Logger.info(TAG, "Pulled ${items.size} inventory items")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull inventory: ${e.message}")
        }
    }

    private suspend fun pullSales(lastSync: String) {
        try {
            val sales = SupabaseManager.client.postgrest["sales"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<SaleDto>>()
            
            sales.forEach { LocalDatabase.saveSale(it, fromSync = true) }
            if (sales.isNotEmpty()) Logger.info(TAG, "Pulled ${sales.size} sales")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull sales: ${e.message}")
        }
    }

    private suspend fun pullExpenses(lastSync: String) {
        try {
            val expenses = SupabaseManager.client.postgrest["expenses"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<ExpenseDto>>()
            
            expenses.forEach { LocalDatabase.saveExpense(it, fromSync = true) }
            if (expenses.isNotEmpty()) Logger.info(TAG, "Pulled ${expenses.size} expenses")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull expenses: ${e.message}")
        }
    }

    private suspend fun pullCredits(lastSync: String) {
        try {
            val credits = SupabaseManager.client.postgrest["credits"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<CreditDto>>()
            
            credits.forEach { LocalDatabase.saveCredit(it, fromSync = true) }
            if (credits.isNotEmpty()) Logger.info(TAG, "Pulled ${credits.size} credits")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull credits: ${e.message}")
        }
    }

    private suspend fun pullCategories(lastSync: String) {
        try {
            val categories = SupabaseManager.client.postgrest["categories"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<CategoryDto>>()
            
            categories.forEach { LocalDatabase.saveCategory(it, fromSync = true) }
            if (categories.isNotEmpty()) Logger.info(TAG, "Pulled ${categories.size} categories")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull categories: ${e.message}")
        }
    }

    private suspend fun pullCustomers(lastSync: String) {
        try {
            val customers = SupabaseManager.client.postgrest["customers"]
                .select { 
                    filter { gte("updated_at", lastSync) }
                    limit(PULL_LIMIT)
                }
                .decodeAs<List<CustomerDto>>()
            
            customers.forEach { LocalDatabase.saveCustomer(it, fromSync = true) }
            if (customers.isNotEmpty()) Logger.info(TAG, "Pulled ${customers.size} customers")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to pull customers: ${e.message}")
        }
    }

    /**
     * Perform a one-time full sync (first launch or manual refresh).
     */
    suspend fun fullSync() {
        Logger.info(TAG, "Starting full sync...")
        try {
            pushLocalChanges()
            
            // Reset last sync to epoch to pull everything
            LocalDatabase.setLastSyncTimestamp("1970-01-01T00:00:00Z")
            pullRemoteChanges()
            
            Logger.info(TAG, "Full sync complete")
        } catch (e: Exception) {
            Logger.error(TAG, "Full sync failed", e)
        }
    }

    // Helper to fetch full objects before pushing
    private fun fetchLocal(tableName: String, recordId: String): Any? {
        return when (tableName) {
            "sales" -> LocalDatabase.getSale(recordId)
            "expenses" -> LocalDatabase.getExpense(recordId)
            "credits" -> LocalDatabase.getCredit(recordId)
            "inventory" -> LocalDatabase.getInventoryItem(recordId)
            "menu_items" -> LocalDatabase.getMenuItem(recordId)
            "customers" -> LocalDatabase.getCustomer(recordId)
            "categories" -> LocalDatabase.getCategory(recordId)
            else -> null
        }
    }
}
