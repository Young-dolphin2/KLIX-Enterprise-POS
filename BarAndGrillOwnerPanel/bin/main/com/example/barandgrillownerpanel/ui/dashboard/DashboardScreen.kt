package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.ui.text.*
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.data.CacheManager
import com.example.barandgrillownerpanel.data.DashboardDataCache
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.*
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

// Dashboard Models and Tabs
import com.example.barandgrillownerpanel.ui.dashboard.OverviewTab
import com.example.barandgrillownerpanel.ui.dashboard.InventoryTab
import com.example.barandgrillownerpanel.ui.dashboard.MenuControlTab
import com.example.barandgrillownerpanel.ui.dashboard.SalesStatsTab
import com.example.barandgrillownerpanel.ui.dashboard.CreditsTab
import com.example.barandgrillownerpanel.ui.dashboard.ExpensesTab
import com.example.barandgrillownerpanel.ui.dashboard.ReportsTab
import com.example.barandgrillownerpanel.ui.dashboard.SettingsTab
import com.example.barandgrillownerpanel.ui.dashboard.InventoryItem
import com.example.barandgrillownerpanel.ui.dashboard.DesktopMenuItem
import com.example.barandgrillownerpanel.ui.dashboard.InventoryCreditKind
import com.example.barandgrillownerpanel.ui.dashboard.CreditInventorySubmission
import com.example.barandgrillownerpanel.ui.dashboard.CreditFormSubmission
import com.example.barandgrillownerpanel.ui.dashboard.syncBar1ToBar2
import com.example.barandgrillownerpanel.ui.dashboard.patchInventorySellingPriceFromMenu
import com.example.barandgrillownerpanel.ui.dashboard.fetchInventoryDtoById
import com.example.barandgrillownerpanel.ui.dashboard.insertInventoryWithCloneBranchesAndMenu

import com.example.barandgrillownerpanel.utils.Logger
import com.example.barandgrillownerpanel.utils.NetworkUtils.withRetry

// Subscription imports
import com.example.barandgrillownerpanel.subscription.SubscriptionManager
import com.example.barandgrillownerpanel.subscription.SubscriptionStatus
import com.example.barandgrillownerpanel.subscription.SubscriptionInfo
import com.example.barandgrillownerpanel.subscription.CheckoutDialog
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

enum class DashboardTab {
    OVERVIEW, INVENTORY, CUSTOMERS, MENU_CONTROL, SALES_STATS, CREDITS, EXPENSES, REPORTS, SETTINGS
}

@Composable
fun DashboardScreen(
    initialSettings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onLogout: () -> Unit = {},
    onLock: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    val scope = rememberCoroutineScope()

    // Hoist menu state — populated from Supabase
    val menuItems = remember { mutableStateListOf<DesktopMenuItem>() }

    // Hoist app settings
    var appSettings by remember(initialSettings) { mutableStateOf(initialSettings) }

    // Hoist inventory and sales state
    val inventoryItems = remember { mutableStateListOf<InventoryItem>() }
    val saleHistory = remember { mutableStateListOf<SaleRecord>() }

    // Branch state
    val branches = remember { mutableStateListOf<BranchDto>() }
    var selectedBranch by remember { mutableStateOf<BranchDto?>(null) } // null = GENERAL

    // Credits state
    val credits = remember { mutableStateListOf<CreditDto>() }
    val expenses = remember { mutableStateListOf<ExpenseDto>() }

    // Dynamic category + subcategory state (user-manageable)
    val customCategories = remember { mutableStateListOf<String>() }
    val customSubcategories = remember { mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<String>>() }

    var isLoadingData by remember { mutableStateOf(true) }
    var errorLoading by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableStateOf(0) }
    var isOffline by remember { mutableStateOf(false) }
    val syncStatus by com.example.barandgrillownerpanel.data.sync.SyncEngine.syncStatus.collectAsState()

    // ── Subscription State ────────────────────────────────────────
    var subscriptionInfo by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }
    var userBusinessName by remember { mutableStateOf("") }

    // Initialize local cache system (SQLite primary, JSON fallback)
    LaunchedEffect(Unit) {
        try {
            com.example.barandgrillownerpanel.data.CacheManager.initialize(".")
        } catch (e: Exception) {
            Logger.error("DASHBOARD", "Cache initialization failed", e)
        }
        com.example.barandgrillownerpanel.data.sync.SyncEngine.startSyncCycle(scope)
    }

    // Check subscription on launch
    LaunchedEffect(Unit) {
        val session = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.auth.sessionStatus
        if (session is SessionStatus.Authenticated) {
            val userId = session.session?.user?.id ?: return@LaunchedEffect
            userEmail = session.session?.user?.email ?: ""
            userPhone = session.session?.user?.phone ?: ""
            userBusinessName = appSettings.businessName
            
            subscriptionInfo = SubscriptionManager.checkStatus(userId)
            
            // Poll for subscription updates every 10 seconds
            scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(10_000)
                    subscriptionInfo = SubscriptionManager.checkStatus(userId)
                }
            }
        }
    }


    suspend fun refreshInventory() {
        val items = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
            .postgrest["inventory"].select().decodeAs<List<InventoryItemDto>>()

        val mappedItems = items.map { dto ->
            InventoryItem(
                id = dto.id ?: dto.name,
                name = dto.name,
                category = dto.category.uppercase(),
                subcategory = dto.subcategory,
                currentStock = dto.stock_quantity,
                capacity = (dto.min_threshold * 5).coerceAtLeast(10.0),
                lowStockThreshold = dto.min_threshold,
                unit = dto.unit,
                unitCost = dto.cost_price,
                retailPrice = dto.sellingPrice,
                isPortionTracked = dto.isPortionTracked,
                portionsPerUnit = dto.portionsPerUnit,
                linkedMenuItemName = dto.linkedMenuItemName,
                soldByShot = dto.soldByShot,
                bottleVolumeMl = dto.bottleVolumeMl,
                shotSizeMl = dto.shotSizeMl,
                status = dto.status,
                branchId = dto.branchId
            )
        }
        inventoryItems.clear()
        inventoryItems.addAll(mappedItems)
    }

    suspend fun refreshCategories() {
        val cats = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
            .postgrest["categories"].select().decodeAs<List<com.example.barandgrillownerpanel.models.CategoryDto>>()
        
        customCategories.clear()
        customSubcategories.clear()
        
        val topLevel = cats.filter { it.parentName == null }.map { it.name }
        customCategories.addAll(topLevel)
        
        for (cat in topLevel) {
            val subs = cats.filter { it.parentName == cat }.map { it.name }
            customSubcategories[cat] = androidx.compose.runtime.mutableStateListOf(*subs.toTypedArray())
        }
        
        // Ensure standard fallback if empty
        if (customCategories.isEmpty()) {
            customCategories.addAll(listOf("FOOD", "DRINKS", "SIDES", "DESSERTS", "SHISHA", "KITCHEN"))
        }
    }

    suspend fun refreshSalesHistory() {
        val salesList = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
            .postgrest["sales"].select().decodeAs<List<SaleDto>>()

        val saleIds = salesList.map { it.id }
        val saleItems = if (saleIds.isNotEmpty()) {
            com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                .postgrest["sale_items"].select {
                    filter { isIn("sale_id", saleIds) }
                }.decodeAs<List<SaleItemDto>>()
        } else emptyList()

        val itemsBySale = saleItems.groupBy { it.saleId }

        val mappedSales = salesList.map { dto ->
            val itemsForSale = itemsBySale[dto.id] ?: emptyList()
            SaleRecord(
                id = dto.orderId,
                branchId = dto.branchId,
                items = itemsForSale.map { itemDto ->
                    SaleItem(
                        item = MenuItem("0", itemDto.name, itemDto.price, itemDto.category, ""),
                        quantity = itemDto.quantity
                    )
                },
                totalAmount = dto.totalAmount,
                paymentMethod = dto.paymentMethod,
                soldBy = dto.soldBy,
                timestamp = try {
                    java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli()
                } catch (e: Exception) { System.currentTimeMillis() }
            )
        }
        saleHistory.addAll(mappedSales)
    }

    suspend fun refreshCredits() {
        try {
            val fetched = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["credits"]
                .select().decodeAs<List<CreditDto>>()
            credits.clear()
            credits.addAll(fetched.sortedByDescending { it.created_at })
        } catch (e: Exception) {
            Logger.error("DASHBOARD", "Failed to refresh credits", e)
        }
    }

    suspend fun refreshExpenses() {
        try {
            val fetched = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["expenses"]
                .select().decodeAs<List<ExpenseDto>>()
            expenses.clear()
            expenses.addAll(fetched.sortedByDescending { it.created_at })
        } catch (e: Exception) {
            Logger.error("DASHBOARD", "Failed to refresh expenses", e)
        }
    }

    LaunchedEffect(retryCount) {
        isLoadingData = true
        errorLoading = null
        
        // Try to load from cache first
        val cache = CacheManager.loadCache()
        if (cache != null) {
            // Populate UI from cache immediately
            branches.clear(); branches.addAll(cache.branches)
            appSettings = appSettings.copy(branches = cache.branches)
            
            menuItems.clear()
            menuItems.addAll(cache.menuItems.mapIndexed { index, dto ->
                DesktopMenuItem(
                    id = dto.id ?: (index + 1).toString(),
                    name = dto.name,
                    price = dto.price,
                    category = dto.category,
                    subcategory = dto.subcategory,
                    branchId = dto.branchId
                )
            })

            // Map inventory from cache
            val mappedInventory = cache.inventoryItems.map { dto ->
                InventoryItem(
                    id = dto.id ?: dto.name,
                    name = dto.name,
                    category = dto.category.uppercase(),
                    subcategory = dto.subcategory,
                    currentStock = dto.stock_quantity,
                    capacity = (dto.min_threshold * 5).coerceAtLeast(10.0),
                    lowStockThreshold = dto.min_threshold,
                    unit = dto.unit,
                    unitCost = dto.cost_price,
                    retailPrice = dto.sellingPrice,
                    isPortionTracked = dto.isPortionTracked,
                    portionsPerUnit = dto.portionsPerUnit,
                    linkedMenuItemName = dto.linkedMenuItemName,
                    soldByShot = dto.soldByShot,
                    bottleVolumeMl = dto.bottleVolumeMl,
                    shotSizeMl = dto.shotSizeMl,
                    status = dto.status,
                    branchId = dto.branchId
                )
            }
            inventoryItems.clear(); inventoryItems.addAll(mappedInventory)

            // Map sales from cache (simplified mapping for cache display)
            val mappedSales = cache.sales.map { dto ->
                SaleRecord(
                    id = dto.orderId,
                    branchId = dto.branchId,
                    items = dto.items.map { itemDto ->
                        SaleItem(
                            item = MenuItem("0", itemDto.name, itemDto.price, itemDto.category, ""),
                            quantity = itemDto.quantity
                        )
                    },
                    totalAmount = dto.totalAmount,
                    paymentMethod = dto.paymentMethod,
                    soldBy = dto.soldBy,
                    timestamp = try {
                        java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli()
                    } catch (e: Exception) { System.currentTimeMillis() }
                )
            }
            saleHistory.clear(); saleHistory.addAll(mappedSales)

            credits.clear()
            credits.addAll(cache.credits.sortedByDescending { it.created_at })
            
            // Map categories from cache
            if (cache.categories.isNotEmpty()) {
                customCategories.clear()
                customSubcategories.clear()
                val topLevel = cache.categories.filter { it.parentName == null }.map { it.name }
                customCategories.addAll(topLevel)
                for (cat in topLevel) {
                    val subs = cache.categories.filter { it.parentName == cat }.map { it.name }
                    customSubcategories[cat] = androidx.compose.runtime.mutableStateListOf(*subs.toTypedArray())
                }
            }
            
            refreshCredits()
            refreshExpenses()
            isLoadingData = false // Show cache while loading fresh data
            isOffline = true // Assume offline until network succeeds
        }

        try {
            // Use SyncEngine to pull latest data from Supabase
            com.example.barandgrillownerpanel.data.sync.SyncEngine.fullSync()
            
            // Reload all data from SQLite
            val localDb = com.example.barandgrillownerpanel.data.local.LocalDatabase
            val freshBranches = localDb.getBranches()
            if (freshBranches.isNotEmpty()) {
                branches.clear()
                branches.addAll(freshBranches)
                appSettings = appSettings.copy(branches = freshBranches)
            }
            
            val freshMenu = localDb.getMenuItems()
            if (freshMenu.isNotEmpty()) {
                menuItems.clear()
                menuItems.addAll(freshMenu.mapIndexed { index, dto ->
                    com.example.barandgrillownerpanel.ui.dashboard.DesktopMenuItem(
                        id = dto.id ?: (index + 1).toString(),
                        name = dto.name,
                        price = dto.price,
                        category = dto.category,
                        subcategory = dto.subcategory,
                        branchId = dto.branchId
                    )
                })
            }
            
            val freshInventory = localDb.getInventory()
            if (freshInventory.isNotEmpty()) {
                val mapped = freshInventory.map { dto ->
                    com.example.barandgrillownerpanel.ui.dashboard.InventoryItem(
                        id = dto.id ?: dto.name,
                        name = dto.name,
                        category = dto.category.uppercase(),
                        subcategory = dto.subcategory,
                        currentStock = dto.stock_quantity,
                        capacity = (dto.min_threshold * 5).coerceAtLeast(10.0),
                        lowStockThreshold = dto.min_threshold,
                        unit = dto.unit,
                        unitCost = dto.cost_price,
                        retailPrice = dto.sellingPrice,
                        isPortionTracked = dto.isPortionTracked,
                        portionsPerUnit = dto.portionsPerUnit,
                        linkedMenuItemName = dto.linkedMenuItemName,
                        soldByShot = dto.soldByShot,
                        bottleVolumeMl = dto.bottleVolumeMl,
                        shotSizeMl = dto.shotSizeMl,
                        status = dto.status,
                        branchId = dto.branchId
                    )
                }
                inventoryItems.clear(); inventoryItems.addAll(mapped)
            }
            
            val freshCategories = localDb.getCategories()
            if (freshCategories.isNotEmpty()) {
                customCategories.clear()
                customSubcategories.clear()
                val topLevel = freshCategories.filter { it.parentName == null }.map { it.name }
                customCategories.addAll(topLevel)
                for (cat in topLevel) {
                    val subs = freshCategories.filter { it.parentName == cat }.map { it.name }
                    customSubcategories[cat] = androidx.compose.runtime.mutableStateListOf(*subs.toTypedArray())
                }
            }
            
            val freshCredits = localDb.getRecentCredits(90)
            credits.clear()
            credits.addAll(freshCredits.sortedByDescending { it.created_at })
            
            val freshSales = localDb.getRecentSales(90)
            val mappedSales = freshSales.map { dto ->
                com.example.barandgrillownerpanel.models.SaleRecord(
                    id = dto.orderId,
                    branchId = dto.branchId,
                    items = emptyList(),
                    totalAmount = dto.totalAmount,
                    paymentMethod = dto.paymentMethod,
                    soldBy = dto.soldBy,
                    timestamp = try { java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                )
            }
            saleHistory.clear()
            saleHistory.addAll(mappedSales)
            
            // Refresh detailed data
            refreshSalesHistory()
            refreshExpenses()
            
            isOffline = false
            errorLoading = null

        } catch (e: Exception) {
            com.example.barandgrillownerpanel.utils.Logger.error("DASHBOARD", "Failed to refresh from SyncEngine", e)
            
            val errorMessage = e.message ?: e.toString()
            val friendlyMessage = if (errorMessage.contains("supabase.co") && !errorMessage.contains(" ")) {
                "Unable to reach database ($errorMessage). Your Supabase project might be paused. Please check your Supabase dashboard to restore it."
            } else {
                errorMessage
            }

            if (inventoryItems.isEmpty() && branches.isEmpty()) {
                errorLoading = friendlyMessage
            } else {
                isOffline = true
            }
        } finally {
            isLoadingData = false
        }
    }

    // Keep dashboard sales live while owner panel is open.
    LaunchedEffect(Unit) {
        try {
            val client = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
            val salesChannel = client.realtime.channel("dashboard_sales_changes")
            val salesFlow = salesChannel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
                table = "sales"
            }
            salesChannel.subscribe(blockUntilSubscribed = true)

            val saleItemsChannel = client.realtime.channel("dashboard_sale_items_changes")
            val saleItemsFlow = saleItemsChannel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
                table = "sale_items"
            }
            saleItemsChannel.subscribe(blockUntilSubscribed = true)
            val inventoryChannel = client.realtime.channel("dashboard_inventory_changes")
            val inventoryFlow = inventoryChannel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
                table = "inventory"
            }
            inventoryChannel.subscribe(blockUntilSubscribed = true)

            launch {
                salesFlow.collect {
                    delay(800) // allow sale_items insert to complete before reloading combined records
                    try {
                        refreshSalesHistory()
                    } catch (e: Exception) {
                        Logger.error("DASHBOARD", "Realtime sales refresh failed", e)
                    }
                }
            }
            launch {
                saleItemsFlow.collect {
                    try {
                        refreshSalesHistory()
                    } catch (e: Exception) {
                        Logger.error("DASHBOARD", "Realtime sale_items refresh failed", e)
                    }
                }
            }
            launch {
                inventoryFlow.collect {
                    try {
                        refreshInventory()
                    } catch (e: Exception) {
                        Logger.error("DASHBOARD", "Realtime inventory refresh failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("DASHBOARD", "Realtime subscription setup failed", e)
        }
    }

    // Daily fallback refresh — re-fetches sales, inventory and categories every 24 h
    // This guarantees data stays fresh even if Realtime drops a change notification.
    LaunchedEffect(Unit) {
        while (true) {
            delay(24 * 60 * 60 * 1000L) // 24 hours
            try {
                refreshSalesHistory()
                refreshInventory()
                refreshCategories()
            } catch (_: Exception) {}
        }
    }

    fun aggregateInventory(items: List<InventoryItem>): List<InventoryItem> {
        return items
            .groupBy { 
                val nameKey = it.name.trim().lowercase()
                val catKey = it.category.trim().uppercase()
                val subKey = it.subcategory.trim().lowercase()
                val unitKey = it.unit.trim().lowercase()
                "$nameKey|$catKey|$subKey|$unitKey"
            }
            .map { (key, groupedItems) ->
                val base = groupedItems.first()
                InventoryItem(
                    id = "agg:$key",
                    name = base.name,
                    category = base.category,
                    subcategory = base.subcategory,
                    currentStock = groupedItems.sumOf { it.currentStock },
                    capacity = groupedItems.sumOf { it.capacity },
                    lowStockThreshold = groupedItems.sumOf { it.lowStockThreshold },
                    unit = base.unit,
                    unitCost = base.unitCost,
                    retailPrice = base.retailPrice,
                    isPortionTracked = base.isPortionTracked,
                    portionsPerUnit = base.portionsPerUnit,
                    linkedMenuItemName = base.linkedMenuItemName,
                    soldByShot = base.soldByShot,
                    bottleVolumeMl = base.bottleVolumeMl,
                    shotSizeMl = base.shotSizeMl,
                    branchId = null
                )
            }
            .sortedBy { it.name }
    }

    // Filter by branch when one is selected.
    // GENERAL (null) shows a combined view where matching items are summed across bars.
    // If a branch is selected, we include items for that branch AND any items whose branch_id belongs to a child branch.
    val filteredInventory by remember { derivedStateOf {
        val b = selectedBranch
        if (b == null) aggregateInventory(inventoryItems)
        else {
            val childIds = branches.filter { it.parentId == b.id }.mapNotNull { it.id }
            val relevantIds = setOfNotNull(b.id) + childIds
            val filtered = inventoryItems.filter { item -> 
                val isKitchen = item.category.equals("KITCHEN", ignoreCase = true) || 
                               item.category.equals("FOOD", ignoreCase = true) || 
                               item.isPortionTracked
                
                // If it's a kitchen item AND we are looking at a leaf branch (has parent), hide it.
                if (isKitchen && b.parentId != null) return@filter false
                
                // Show if it belongs to this branch, its children, or is global (null) and we are at parent root.
                item.branchId in relevantIds || (item.branchId == null && b.parentId == null)
            }
            if (childIds.isNotEmpty()) aggregateInventory(filtered) else filtered
        }
    }}
    val filteredSales by remember { derivedStateOf {
        val b = selectedBranch
        if (b == null) saleHistory.toList()
        else {
            val childIds = branches.filter { it.parentId == b.id }.mapNotNull { it.id }
            val relevantIds = setOfNotNull(b.id) + childIds
            saleHistory.filter { sale -> sale.branchId in relevantIds }
        }
    }}
    val filteredCredits by remember { derivedStateOf {
        val b = selectedBranch
        if (b == null) credits.toList()
        else credits.filter { it.branchId == b.id }
    }}

    Row(
        modifier = Modifier.fillMaxSize().background(DarkBackground)
    ) {
        // Sidebar Navigation
        Sidebar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            settings = appSettings,
            isOffline = isOffline,
            syncStatus = syncStatus,
            subscriptionInfo = subscriptionInfo,
            onUpgrade = { showUpgradeDialog = true },
            onLock = onLock,
            onLogout = onLogout,
            modifier = Modifier.width(250.dp).fillMaxHeight()
        )

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(SurfaceColor)
                .padding(24.dp)
        ) {
            if (errorLoading != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Failed to load dashboard data", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(errorLoading!!, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { retryCount++ },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                    ) { Text("Retry Loading") }
                }
            } else if (isLoadingData && inventoryItems.isEmpty() && branches.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryOrange)
            } else {
                when (selectedTab) {
                    DashboardTab.OVERVIEW -> OverviewTab(
                        saleHistory = filteredSales,
                        settings = appSettings,
                        allSaleHistory = saleHistory,
                        branches = branches,
                        selectedBranch = selectedBranch,
                        onBranchChange = { it: BranchDto? -> selectedBranch = it }
                    )
                    DashboardTab.INVENTORY -> InventoryTab(
                        inventoryItems = filteredInventory,
                        allInventoryItems = inventoryItems,
                        menuItems = menuItems,
                        saleHistory = filteredSales,
                        settings = appSettings,
                        branches = branches,
                        selectedBranch = selectedBranch,
                        onBranchChange = { selectedBranch = it },
                        customCategories = customCategories,
                        customSubcategories = customSubcategories
                    )
                    DashboardTab.MENU_CONTROL -> {
                        val scope = rememberCoroutineScope()
                        MenuControlTab(
                            menuItems = menuItems,
                            inventoryItems = inventoryItems,
                            branches = branches,
                            selectedBranch = selectedBranch,
                            onBranchChange = { selectedBranch = it },
                            customCategories = customCategories,
                            customSubcategories = customSubcategories,
                            onRefresh = {
                                scope.launch {
                                    try {
                                        val fetchedMenu = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["menu_items"]
                                            .select { filter { eq("is_active", true) } }
                                            .decodeAs<List<com.example.barandgrillownerpanel.models.MenuItemDto>>()
                                        menuItems.clear()
                                        menuItems.addAll(fetchedMenu.mapIndexed { index, dto ->
                                            DesktopMenuItem(
                                                id = dto.id ?: (index + 1).toString(),
                                                name = dto.name,
                                                price = dto.price,
                                                category = dto.category,
                                                subcategory = dto.subcategory,
                                                branchId = dto.branchId
                                            )
                                        })
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onSaveItem = { dto: com.example.barandgrillownerpanel.models.MenuItemDto ->
                                scope.launch {
                                    try {
                                        val inserted = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["menu_items"]
                                            .insert(dto) { select() }
                                            .decodeSingle<com.example.barandgrillownerpanel.models.MenuItemDto>()
                                        
                                        // If there are ingredients, save them to ingredient_menu_portions
                                        dto.ingredients?.let { ingredients ->
                                            if (ingredients.isNotEmpty()) {
                                                // We need branch-specific inventory IDs. 
                                                // For now, the POS logic uses item name matching if no ID is provided, 
                                                // but ingredient_menu_portions requires an inventory_id (UUID).
                                                // Let's find IDs matching these names for this branch.
                                                val names = ingredients.map { it.inventory_name }
                                                val matchingInventory = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                                    .postgrest["inventory"].select {
                                                        filter {
                                                            isIn("name", names)
                                                            if (dto.branchId != null) eq("branch_id", dto.branchId)
                                                        }
                                                    }.decodeAs<List<com.example.barandgrillownerpanel.models.InventoryItemDto>>()
                                                
                                                val portionRows = ingredients.mapNotNull { ing ->
                                                    val inv = matchingInventory.find { it.name == ing.inventory_name }
                                                    if (inv?.id != null) {
                                                        IngredientMenuPortionRow(
                                                            inventory_id = inv.id,
                                                            menu_item_name = dto.name,
                                                            portions_per_sale = ing.quantity,
                                                            branch_id = dto.branchId
                                                        )
                                                    } else null
                                                }
                                                
                                                if (portionRows.isNotEmpty()) {
                                                    com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                                        .postgrest["ingredient_menu_portions"]
                                                        .insert(portionRows)
                                                }
                                            }
                                        }

                                        val requestedId = dto.id
                                        if (requestedId != null) {
                                            val idx = menuItems.indexOfFirst { it.id == requestedId }
                                            if (idx >= 0) {
                                                val actualId = inserted.id ?: requestedId
                                                if (actualId != requestedId) {
                                                    menuItems[idx] = menuItems[idx].copy(id = actualId)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onUpdateItem = { updated: DesktopMenuItem, previousName: String ->
                                scope.launch {
                                    try {
                                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["menu_items"]
                                            .update({
                                                set("price", updated.price)
                                                set("name", updated.name)
                                            }) {
                                                filter { eq("id", updated.id) }
                                            }
                                        patchInventorySellingPriceFromMenu(
                                            previousItemName = previousName,
                                            menuBranchId = updated.branchId,
                                            newPrice = updated.price,
                                            newItemName = updated.name.takeIf { it != previousName },
                                            inventoryItems = inventoryItems
                                        )
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onAdjustMenuPrice = { item: DesktopMenuItem, newPrice: Double ->
                                scope.launch {
                                    try {
                                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["menu_items"]
                                            .update({ set("price", newPrice) }) {
                                                filter { eq("id", item.id) }
                                            }
                                        patchInventorySellingPriceFromMenu(
                                            previousItemName = item.name,
                                            menuBranchId = item.branchId,
                                            newPrice = newPrice,
                                            newItemName = null,
                                            inventoryItems = inventoryItems
                                        )
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onDeleteItem = { item: DesktopMenuItem ->
                                scope.launch {
                                    try {
                                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["menu_items"]
                                            .update({ set("is_active", false) }) {
                                                filter { eq("id", item.id) }
                                            }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        )
                    }
                    DashboardTab.SALES_STATS -> SalesStatsTab(
                        branches = branches,
                        selectedBranch = selectedBranch,
                        onBranchChange = { it: BranchDto? -> selectedBranch = it }
                    )
                    DashboardTab.CREDITS -> {
                        val scope = rememberCoroutineScope()
                        CreditsTab(
                            credits = filteredCredits,
                            branches = branches,
                            selectedBranch = selectedBranch,
                            onBranchChange = { selectedBranch = it },
                            allInventoryItems = inventoryItems,
                            onSaveCredit = { dto: CreditDto ->
                                scope.launch {
                                    try {
                                        val newRecord = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["credits"]
                                            .insert(dto)
                                            .decodeSingle<CreditDto>()
                                        val isCurrentBranch = selectedBranch == null || newRecord.branchId == selectedBranch?.id
                                        if (isCurrentBranch) credits.add(0, newRecord)
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onSaveInventoryCredit = { submission: CreditInventorySubmission ->
                                scope.launch {
                                    try {
                                        val client = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                        when (submission.kind) {
                                            InventoryCreditKind.NEW_INBOUND -> {
                                                insertInventoryWithCloneBranchesAndMenu(
                                                    insertDto = submission.inventoryItem,
                                                    cloneBranches = submission.cloneBranches,
                                                    menuItems = menuItems,
                                                    allInventoryItems = inventoryItems,
                                                    syncMenu = true,
                                                    allBranches = branches
                                                )
                                                val creditInserted = client.postgrest["credits"]
                                                    .insert(submission.credit) { select() }
                                                    .decodeSingle<CreditDto>()
                                                val isCurrentBranch = selectedBranch == null || creditInserted.branchId == selectedBranch?.id
                                                if (isCurrentBranch) credits.add(0, creditInserted)
                                            }
                                            InventoryCreditKind.EXISTING_INBOUND -> {
                                                val rowId = submission.existingInventoryId ?: return@launch
                                                val remote = fetchInventoryDtoById(rowId) ?: return@launch
                                                val newQty = remote.stock_quantity + submission.quantityDelta
                                                client.postgrest["inventory"].update({
                                                    set("stock_quantity", newQty)
                                                    set("cost_price", submission.inventoryItem.cost_price)
                                                }) {
                                                    filter { eq("id", rowId) }
                                                }
                                                val idx = inventoryItems.indexOfFirst { it.id == rowId }
                                                if (idx >= 0) {
                                                    val cur = inventoryItems[idx]
                                                    inventoryItems[idx] = cur.copy(
                                                        currentStock = newQty,
                                                        unitCost = submission.inventoryItem.cost_price
                                                    )
                                                } else {
                                                    inventoryItems.add(
                                                        InventoryItem(
                                                            id = rowId,
                                                            name = remote.name,
                                                            category = remote.category.uppercase(),
                                                            subcategory = remote.subcategory,
                                                            currentStock = newQty,
                                                            capacity = (remote.min_threshold * 5).coerceAtLeast(10.0),
                                                            lowStockThreshold = remote.min_threshold,
                                                            unit = remote.unit,
                                                            unitCost = submission.inventoryItem.cost_price,
                                                            retailPrice = remote.sellingPrice,
                                                            isPortionTracked = remote.isPortionTracked,
                                                            portionsPerUnit = remote.portionsPerUnit,
                                                            linkedMenuItemName = remote.linkedMenuItemName,
                                                            soldByShot = remote.soldByShot,
                                                            bottleVolumeMl = remote.bottleVolumeMl,
                                                            shotSizeMl = remote.shotSizeMl,
                                                            branchId = remote.branchId
                                                        )
                                                    )
                                                }
                                                val creditInserted = client.postgrest["credits"]
                                                    .insert(submission.credit) { select() }
                                                    .decodeSingle<CreditDto>()
                                                val isCurrentBranch = selectedBranch == null || creditInserted.branchId == selectedBranch?.id
                                                if (isCurrentBranch) credits.add(0, creditInserted)
                                            }
                                            InventoryCreditKind.EXISTING_OUTBOUND -> {
                                                val rowId = submission.existingInventoryId ?: return@launch
                                                val remote = fetchInventoryDtoById(rowId) ?: return@launch
                                                val newQty = (remote.stock_quantity - submission.quantityDelta).coerceAtLeast(0.0)
                                                client.postgrest["inventory"].update({
                                                    set("stock_quantity", newQty)
                                                }) {
                                                    filter { eq("id", rowId) }
                                                }
                                                val idx = inventoryItems.indexOfFirst { it.id == rowId }
                                                if (idx >= 0) {
                                                    inventoryItems[idx] = inventoryItems[idx].copy(currentStock = newQty)
                                                } else {
                                                    inventoryItems.add(
                                                        InventoryItem(
                                                            id = rowId,
                                                            name = remote.name,
                                                            category = remote.category.uppercase(),
                                                            subcategory = remote.subcategory,
                                                            currentStock = newQty,
                                                            capacity = (remote.min_threshold * 5).coerceAtLeast(10.0),
                                                            lowStockThreshold = remote.min_threshold,
                                                            unit = remote.unit,
                                                            unitCost = remote.cost_price,
                                                            retailPrice = remote.sellingPrice,
                                                            isPortionTracked = remote.isPortionTracked,
                                                            portionsPerUnit = remote.portionsPerUnit,
                                                            linkedMenuItemName = remote.linkedMenuItemName,
                                                            soldByShot = remote.soldByShot,
                                                            bottleVolumeMl = remote.bottleVolumeMl,
                                                            shotSizeMl = remote.shotSizeMl,
                                                            branchId = remote.branchId
                                                        )
                                                    )
                                                }
                                                val creditInserted = client.postgrest["credits"]
                                                    .insert(submission.credit) { select() }
                                                    .decodeSingle<CreditDto>()
                                                val isCurrentBranch = selectedBranch == null || creditInserted.branchId == selectedBranch?.id
                                                if (isCurrentBranch) credits.add(0, creditInserted)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            onSettleCredit = { dto: CreditDto ->
                                scope.launch {
                                    try {
                                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["credits"]
                                            .update({ 
                                                set("is_settled", true)
                                                set("settled_at", java.time.OffsetDateTime.now().toString())
                                            }) {
                                                filter { eq("id", dto.id!!) }
                                            }
                                        val idx = credits.indexOfFirst { it.id == dto.id }
                                        if (idx >= 0) {
                                            credits[idx] = credits[idx].copy(
                                                isSettled = true,
                                                settledAt = java.time.OffsetDateTime.now().toString()
                                            )
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        )
                    }
                    DashboardTab.EXPENSES -> ExpensesTab(
                        branches = branches,
                        selectedBranch = selectedBranch,
                        onBranchChange = { selectedBranch = it },
                        saleHistory = filteredSales,
                        expenses = expenses,
                        onRefresh = { scope.launch { refreshExpenses() } }
                    )
                    DashboardTab.CUSTOMERS -> CustomersTab(
                        branchId = selectedBranch?.id
                    )
                    DashboardTab.REPORTS -> ReportsTab(
                        saleHistory = saleHistory,
                        inventoryItems = inventoryItems,
                        branches = branches,
                        settings = appSettings
                    )
                    DashboardTab.SETTINGS -> SettingsTab(
                        settings = appSettings,
                        onSettingsChange = { appSettings = it },
                        branches = branches,
                        onBranchesChange = { updated ->
                            branches.clear()
                            branches.addAll(updated)
                            appSettings = appSettings.copy(branches = updated)
                        },
                        inventoryItems = inventoryItems,
                        saleHistory = saleHistory,
                        credits = credits,
                        expenses = expenses
                    )
                }
            }
        }
    }
    // ── UPGRADE DIALOG ────────────────────────────────────────────
    if (showUpgradeDialog) {
        CheckoutDialog(
            tenantId = (com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.auth.sessionStatus as? SessionStatus.Authenticated)?.session?.user?.id ?: "",
            email = userEmail,
            phone = userPhone,
            businessName = userBusinessName,
            onDismiss = { showUpgradeDialog = false },
            onCheckoutComplete = {
                showUpgradeDialog = false
            }
        )
    }

}
@Composable
fun Sidebar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    settings: AppSettings,
    isOffline: Boolean,
    syncStatus: com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus,
    subscriptionInfo: SubscriptionInfo?,
    onUpgrade: () -> Unit,
    onLock: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val textMeasurer = rememberTextMeasurer()
    
    // Static random elements for the background texture
    val stars = remember { List(40) { Offset(Random.nextFloat(), Random.nextFloat()) } }
    val watermarks = remember { 
        List(10) { 
            val rot = Random.nextFloat() * 360f
            val pos = Offset(Random.nextFloat(), Random.nextFloat())
            val size = 10 + Random.nextInt(30)
            val alpha = 0.02f + Random.nextFloat() * 0.04f
            Triple(pos, rot, Pair(size, alpha))
        } 
    }
    val boldWatermarks = remember {
        List(5) {
            val rot = Random.nextFloat() * 360f
            val pos = Offset(Random.nextFloat(), Random.nextFloat())
            val size = 16 + Random.nextInt(10)
            val alpha = 0.05f + Random.nextFloat() * 0.05f
            Triple(pos, rot, Pair(size, alpha))
        }
    }
    val shootingStarData = remember { 
        List(3) { 
            val start = Offset(Random.nextFloat() * 0.8f, Random.nextFloat() * 0.5f)
            val angle = Random.nextFloat() * 45f + 15f
            Pair(start, angle)
        } 
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0A0C10)) // Deep charcoal base
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
    ) {
        // Textured Smears
        Canvas(modifier = Modifier.fillMaxSize()) {
            val silverSmear = Color(0xFFC0C0C0).copy(alpha = 0.03f)
            val darkSilver = Color(0xFF4A4E69).copy(alpha = 0.05f)
            
            // Large Top-Left Smear
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(silverSmear, Color.Transparent),
                    center = Offset(0f, 200f),
                    radius = size.width * 1.5f
                ),
                center = Offset(0f, 200f),
                radius = size.width * 1.5f
            )

            // Dynamic Bottom-Right Smear
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(darkSilver, Color.Transparent),
                    center = Offset(size.width, size.height * 0.8f),
                    radius = size.height * 0.5f
                ),
                center = Offset(size.width, size.height * 0.8f),
                radius = size.height * 0.5f
            )
            
            // Subtle middle accent
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.01f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )
            )

            // Random KLIX watermarks
            watermarks.forEach { (pos, rot, style) ->
                rotate(rot, pivot = Offset(pos.x * size.width, pos.y * size.height)) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "KLIX",
                        topLeft = Offset(pos.x * size.width, pos.y * size.height),
                        style = TextStyle(
                            color = Color(0xFFC0C0C0).copy(alpha = style.second),
                            fontSize = style.first.sp,
                            fontWeight = FontWeight.ExtraLight,
                            letterSpacing = 5.sp
                        )
                    )
                }
            }

            // Bold KLIX watermarks
            boldWatermarks.forEach { (pos, rot, style) ->
                rotate(rot, pivot = Offset(pos.x * size.width, pos.y * size.height)) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "KLIX",
                        topLeft = Offset(pos.x * size.width, pos.y * size.height),
                        style = TextStyle(
                            color = Color(0xFFC0C0C0).copy(alpha = style.second),
                            fontSize = style.first.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                }
            }

            // Shooting stars
            shootingStarData.forEachIndexed { index, (start, angle) ->
                val delay = index * 0.3f
                if (shootingStarProgress > delay && shootingStarProgress < delay + 0.2f) {
                    val p = (shootingStarProgress - delay) / 0.2f
                    val length = 150.dp.toPx()
                    val startX = start.x * size.width + (p * length * 2)
                    val startY = start.y * size.height + (p * length)
                    
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                            start = Offset(startX - length * 0.5f, startY - length * 0.25f),
                            end = Offset(startX, startY)
                        ),
                        start = Offset(startX - length * 0.5f, startY - length * 0.25f),
                        end = Offset(startX, startY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            // Twinkling stars
            stars.forEachIndexed { i, pos ->
                val alpha = if (i % 2 == 0) starAlpha else 1f - starAlpha
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.6f),
                    radius = if (i % 5 == 0) 1.5.dp.toPx() else 0.8.dp.toPx(),
                    center = Offset(pos.x * size.width, pos.y * size.height)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // Logo / Title area
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
        ) {
            Image(
                painter = painterResource("icon_klix.png"),
                contentDescription = "KLIX Logo",
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    settings.businessName.split(" ").firstOrNull() ?: "KLIX",
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontSize = 20.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DASHBOARD", fontWeight = FontWeight.Bold, color = PrimaryOrange, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val statusText = when {
                        isOffline -> "OFFLINE"
                        syncStatus is com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus.Error -> "SYNC ERROR"
                        syncStatus is com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus.Syncing -> "SYNCING..."
                        syncStatus is com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus.Success -> "SYNCED"
                        else -> "IDLE"
                    }
                    val statusColor = when {
                        isOffline || syncStatus is com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus.Error -> ErrorRed
                        syncStatus is com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus.Syncing -> PrimaryOrange
                        syncStatus is com.example.barandgrillownerpanel.data.sync.SyncEngine.SyncStatus.Success -> SuccessGreen
                        else -> TextSecondary
                    }

                    // Sync Status Indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = CharcoalGray, modifier = Modifier.padding(bottom = 16.dp))

        SidebarItem("Overview", Icons.Default.Dashboard, selectedTab == DashboardTab.OVERVIEW) { onTabSelected(DashboardTab.OVERVIEW) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Sales Stats", Icons.Default.BarChart, selectedTab == DashboardTab.SALES_STATS) { onTabSelected(DashboardTab.SALES_STATS) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Reports", Icons.Default.Assessment, selectedTab == DashboardTab.REPORTS) { onTabSelected(DashboardTab.REPORTS) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Inventory", Icons.Default.Inventory, selectedTab == DashboardTab.INVENTORY) { onTabSelected(DashboardTab.INVENTORY) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Menu & Pricing", Icons.AutoMirrored.Filled.MenuBook, selectedTab == DashboardTab.MENU_CONTROL) { onTabSelected(DashboardTab.MENU_CONTROL) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Credits & Debts", Icons.Default.CreditCard, selectedTab == DashboardTab.CREDITS) { onTabSelected(DashboardTab.CREDITS) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Expenses", Icons.Default.AccountBalance, selectedTab == DashboardTab.EXPENSES) { onTabSelected(DashboardTab.EXPENSES) }
        Spacer(modifier = Modifier.height(2.dp))
        SidebarItem("Customers", Icons.Default.People, selectedTab == DashboardTab.CUSTOMERS) { onTabSelected(DashboardTab.CUSTOMERS) }

        // Subscription status
        subscriptionInfo?.let { info ->
            if (info.status == SubscriptionStatus.EXPIRED || info.status == SubscriptionStatus.NONE) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = PrimaryOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (info.status == SubscriptionStatus.EXPIRED) "Expired" else "Free Plan",
                                color = PrimaryOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onUpgrade) {
                            Text("Upgrade", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else if (info.status == SubscriptionStatus.ACTIVE) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(info.plan?.displayName ?: "Pro", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${info.daysRemaining} days left", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Settings item
        HorizontalDivider(color = CharcoalGray.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        SidebarItem("Settings", Icons.Default.Settings, selectedTab == DashboardTab.SETTINGS) { onTabSelected(DashboardTab.SETTINGS) }
        Spacer(modifier = Modifier.height(4.dp))
        // Quick-action mini buttons under Settings
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Lock Screen shortcut
            Surface(
                onClick = { onLock() },
                color = CharcoalGray,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock Screen", tint = TextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("Lock", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
            // Employee Manager shortcut
            Surface(
                onClick = { onTabSelected(DashboardTab.SETTINGS) },
                color = CharcoalGray,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Badge, contentDescription = "Employees", tint = TextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("Staff", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
            // Logout shortcut
            Surface(
                onClick = { onLogout() },
                color = ErrorRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = ErrorRed.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("Logout", color = ErrorRed.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}
}

@Composable
fun SidebarItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) PrimaryOrange.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (isSelected) PrimaryOrange else TextSecondary

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = text, tint = contentColor)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, color = contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
