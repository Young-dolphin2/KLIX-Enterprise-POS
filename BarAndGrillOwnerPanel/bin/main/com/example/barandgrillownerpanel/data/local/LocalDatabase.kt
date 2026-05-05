package com.example.barandgrillownerpanel.data.local

import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.utils.Logger
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Local SQLite database for offline-first operations.
 * Replaces the JSON cache file with a proper relational DB.
 * 
 * Architecture:
 * - All reads go here first (instant, no network)
 * - All writes go here first (instant, no network)
 * - SyncEngine pushes/pulls changes to/from Supabase in background
 * - Supabase remains the source of truth
 */
object LocalDatabase {
    private const val TAG = "LOCAL_DB"
    private const val DB_VERSION = 1
    private const val DB_NAME = "klix_local.db"
    
    private var connection: Connection? = null
    private var dbFile: File? = null

    /**
     * Initialize the database. Creates tables if they don't exist.
     * Call once on app startup.
     */
    fun initialize(dbDirectory: String = ".") {
        try {
            Class.forName("org.sqlite.JDBC")
            dbFile = File(dbDirectory, DB_NAME)
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile?.absolutePath}")
            connection?.apply {
                // Enable WAL mode for better concurrent read/write performance
                createStatement().execute("PRAGMA journal_mode=WAL")
                // Enable foreign keys
                createStatement().execute("PRAGMA foreign_keys=ON")
            }
            createTables()
            migrateIfNeeded()
            Logger.info(TAG, "Database initialized at ${dbFile?.absolutePath}")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to initialize database", e)
            // Fail gracefully — app will fetch from Supabase directly
        }
    }

    // ================================================================
    // TABLE CREATION
    // ================================================================

    private fun createTables() {
        val conn = connection ?: return
        conn.createStatement().apply {
            execute("""
                CREATE TABLE IF NOT EXISTS branches (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT DEFAULT 'BAR',
                    address TEXT,
                    is_active INTEGER DEFAULT 1,
                    parent_id TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS menu_items (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    price REAL NOT NULL,
                    category TEXT NOT NULL,
                    subcategory TEXT DEFAULT '',
                    branch_id TEXT,
                    is_active INTEGER DEFAULT 1,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS inventory (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    subcategory TEXT DEFAULT '',
                    stock_quantity REAL NOT NULL DEFAULT 0,
                    min_threshold REAL NOT NULL DEFAULT 10,
                    cost_price REAL NOT NULL DEFAULT 0,
                    selling_price REAL NOT NULL DEFAULT 0,
                    unit TEXT DEFAULT 'Units',
                    is_portion_tracked INTEGER DEFAULT 0,
                    portions_per_unit REAL,
                    linked_menu_item_name TEXT,
                    sold_by_shot INTEGER DEFAULT 0,
                    bottle_volume_ml REAL,
                    shot_size_ml REAL,
                    status TEXT DEFAULT 'AVAILABLE',
                    branch_id TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS sales (
                    id TEXT PRIMARY KEY,
                    order_id TEXT NOT NULL,
                    total_amount REAL NOT NULL,
                    payment_method TEXT DEFAULT 'Cash',
                    sold_by TEXT DEFAULT '',
                    timestamp TEXT NOT NULL,
                    branch_id TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS sale_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sale_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    price REAL NOT NULL,
                    quantity INTEGER NOT NULL,
                    category TEXT DEFAULT '',
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT,
                    FOREIGN KEY (sale_id) REFERENCES sales(id)
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id TEXT PRIMARY KEY,
                    branch_id TEXT,
                    category TEXT NOT NULL,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL,
                    expense_date TEXT,
                    created_at TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS credits (
                    id TEXT PRIMARY KEY,
                    branch_id TEXT,
                    contact_name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL,
                    credit_type TEXT NOT NULL,
                    is_settled INTEGER DEFAULT 0,
                    settled_at TEXT,
                    notes TEXT,
                    created_at TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    parent_name TEXT,
                    created_at TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    plan TEXT NOT NULL DEFAULT 'free',
                    status TEXT NOT NULL DEFAULT 'pending',
                    current_period_start TEXT NOT NULL,
                    current_period_end TEXT NOT NULL,
                    trial_end TEXT,
                    cancelled_at TEXT,
                    metadata TEXT DEFAULT '{}',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS customers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    email TEXT,
                    address TEXT,
                    id_type TEXT,
                    id_number TEXT,
                    profile_image_url TEXT,
                    membership_status TEXT DEFAULT 'ACTIVE',
                    membership_expiry TEXT,
                    notes TEXT,
                    branch_id TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)
            // Sync tracking
            execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_name TEXT NOT NULL,
                    record_id TEXT NOT NULL,
                    action TEXT NOT NULL CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    pushed INTEGER DEFAULT 0,
                    pushed_at TEXT,
                    retry_count INTEGER DEFAULT 0
                )
            """)
            execute("""
                CREATE TABLE IF NOT EXISTS sync_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)
            // Indexes for performance
            execute("CREATE INDEX IF NOT EXISTS idx_sales_timestamp ON sales(timestamp)")
            execute("CREATE INDEX IF NOT EXISTS idx_sales_branch ON sales(branch_id)")
            execute("CREATE INDEX IF NOT EXISTS idx_expenses_created ON expenses(created_at)")
            execute("CREATE INDEX IF NOT EXISTS idx_credits_created ON credits(created_at)")
            execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_pushed ON sync_queue(pushed)")
            execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_table ON sync_queue(table_name)")
        }
    }

    private fun migrateIfNeeded() {
        // Future migrations will go here
        // For now, just store the version
        setMeta("db_version", DB_VERSION.toString())
    }

    // ================================================================
    // SYNC METADATA
    // ================================================================

    fun getMeta(key: String, default: String = ""): String {
        val conn = connection ?: return default
        try {
            val stmt = conn.prepareStatement("SELECT value FROM sync_metadata WHERE key = ?")
            stmt.setString(1, key)
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getString("value") ?: default else default
        } catch (e: Exception) { return default }
    }

    fun setMeta(key: String, value: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO sync_metadata (key, value) VALUES (?, ?)"
            )
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to set meta $key", e)
        }
    }

    /**
     * Get the timestamp of the last successful sync.
     * Used to pull only new/changed records from Supabase.
     */
    fun getLastSyncTimestamp(): String = getMeta("last_sync", "1970-01-01T00:00:00Z")

    fun setLastSyncTimestamp(timestamp: String) = setMeta("last_sync", timestamp)

    // ================================================================
    // SYNC QUEUE
    // ================================================================

    /**
     * Queue a record change to be pushed to Supabase.
     */
    fun queueSync(tableName: String, recordId: String, action: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement(
                "INSERT INTO sync_queue (table_name, record_id, action) VALUES (?, ?, ?)"
            )
            stmt.setString(1, tableName)
            stmt.setString(2, recordId)
            stmt.setString(3, action)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to queue sync", e)
        }
    }

    /**
     * Get all pending sync operations.
     */
    fun getPendingSyncs(): List<SyncQueueItem> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<SyncQueueItem>()
        try {
            val stmt = conn.prepareStatement(
                "SELECT id, table_name, record_id, action, created_at, retry_count FROM sync_queue WHERE pushed = 0 ORDER BY id ASC LIMIT 100"
            )
            val rs = stmt.executeQuery()
            while (rs.next()) {
                items.add(SyncQueueItem(
                    id = rs.getLong("id"),
                    tableName = rs.getString("table_name") ?: "",
                    recordId = rs.getString("record_id") ?: "",
                    action = rs.getString("action") ?: "INSERT",
                    created_at = rs.getString("created_at") ?: "",
                    retryCount = rs.getInt("retry_count")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get pending syncs", e)
        }
        return items
    }

    /**
     * Mark a sync queue item as pushed.
     */
    fun markSyncPushed(id: Long) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement(
                "UPDATE sync_queue SET pushed = 1, pushed_at = datetime('now') WHERE id = ?"
            )
            stmt.setLong(1, id)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to mark sync pushed", e)
        }
    }

    /**
     * Increment retry count for a failed sync.
     */
    fun incrementSyncRetry(id: Long) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement(
                "UPDATE sync_queue SET retry_count = retry_count + 1 WHERE id = ?"
            )
            stmt.setLong(1, id)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to increment sync retry", e)
        }
    }

    // ================================================================
    private fun createTables() {
        val sql = """
            CREATE TABLE IF NOT EXISTS sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                record_id TEXT NOT NULL,
                operation TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                is_pushed INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS app_settings (
                id TEXT PRIMARY KEY,
                business_name TEXT NOT NULL,
                country TEXT NOT NULL,
                currency_symbol TEXT NOT NULL,
                currency_code TEXT NOT NULL,
                primary_color_hex TEXT,
                payment_options_json TEXT,
                is_onboarded INTEGER DEFAULT 0,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS branches (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT DEFAULT 'BAR',
                address TEXT,
                is_active INTEGER DEFAULT 1,
                parent_id TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS menu_items (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT,
                subcategory TEXT,
                price REAL NOT NULL,
                is_active INTEGER DEFAULT 1,
                branch_id TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS inventory (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT,
                subcategory TEXT,
                stock_quantity REAL DEFAULT 0,
                min_threshold REAL DEFAULT 0,
                cost_price REAL DEFAULT 0,
                selling_price REAL DEFAULT 0,
                unit TEXT DEFAULT 'Units',
                is_portion_tracked INTEGER DEFAULT 0,
                portions_per_unit REAL,
                linked_menu_item_name TEXT,
                sold_by_shot INTEGER DEFAULT 0,
                bottle_volume_ml REAL,
                shot_size_ml REAL,
                status TEXT DEFAULT 'AVAILABLE',
                branch_id TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS sales (
                id TEXT PRIMARY KEY,
                total_amount REAL NOT NULL,
                payment_method TEXT,
                branch_id TEXT,
                created_at TEXT,
                is_synced INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS expenses (
                id TEXT PRIMARY KEY,
                amount REAL NOT NULL,
                category TEXT,
                description TEXT,
                branch_id TEXT,
                expense_date TEXT,
                created_at TEXT
            );

            CREATE TABLE IF NOT EXISTS credits (
                id TEXT PRIMARY KEY,
                contact_name TEXT,
                description TEXT,
                amount REAL,
                credit_type TEXT,
                is_settled INTEGER DEFAULT 0,
                branch_id TEXT,
                created_at TEXT,
                updated_at TEXT
            );
        """.trimIndent()
        
        connection?.createStatement()?.use { it.executeUpdate(sql) }
    }

    // --- Branches ---
    fun saveBranch(branch: BranchDto) {
        val sql = "INSERT OR REPLACE INTO branches (id, name, type, address, is_active, parent_id, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        try {
            connection?.prepareStatement(sql)?.use { pstmt ->
                pstmt.setString(1, branch.id ?: UUID.randomUUID().toString())
                pstmt.setString(2, branch.name)
                pstmt.setString(3, branch.type)
                pstmt.setString(4, branch.address)
                pstmt.setInt(5, if (branch.is_active) 1 else 0)
                pstmt.setString(6, branch.parentId)
                pstmt.setString(7, LocalDateTime.now().toString())
                pstmt.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getBranches(): List<BranchDto> {
        val branches = mutableListOf<BranchDto>()
        try {
            val rs = connection?.createStatement()?.executeQuery("SELECT * FROM branches WHERE is_active = 1")
            while (rs?.next() == true) {
                branches.add(BranchDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    type = rs.getString("type") ?: "BAR",
                    address = rs.getString("address"),
                    is_active = rs.getInt("is_active") == 1,
                    parentId = rs.getString("parent_id")
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return branches
    }

    // ================================================================
    // DATA ACCESS — SALES (Limited to recent N days)
    // ================================================================

    /**
     * Get sales from the last N days.
     * This keeps memory usage fixed regardless of total history.
     */
    fun getRecentSales(days: Int = 2): List<SaleDto> {
        val conn = connection ?: return emptyList()
        val sales = mutableListOf<SaleDto>()
        try {
            val cutoff = Instant.now().minusSeconds(days * 86400L).toString()
            val stmt = conn.prepareStatement(
                "SELECT * FROM sales WHERE timestamp >= ? ORDER BY timestamp DESC"
            )
            stmt.setString(1, cutoff)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                sales.add(SaleDto(
                    id = rs.getString("id") ?: "",
                    orderId = rs.getString("order_id") ?: "",
                    totalAmount = rs.getDouble("total_amount"),
                    paymentMethod = rs.getString("payment_method") ?: "Cash",
                    soldBy = rs.getString("sold_by") ?: "",
                    timestamp = rs.getString("timestamp") ?: "",
                    branchId = rs.getString("branch_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get recent sales", e)
        }
        return sales
    }

    /**
     * Get ALL sales (for reports that need full history).
     * This loads from SQLite only — no network needed.
     */
    fun getAllSales(): List<SaleDto> {
        val conn = connection ?: return emptyList()
        val sales = mutableListOf<SaleDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM sales ORDER BY timestamp DESC")
            while (rs.next()) {
                sales.add(SaleDto(
                    id = rs.getString("id") ?: "",
                    orderId = rs.getString("order_id") ?: "",
                    totalAmount = rs.getDouble("total_amount"),
                    paymentMethod = rs.getString("payment_method") ?: "Cash",
                    soldBy = rs.getString("sold_by") ?: "",
                    timestamp = rs.getString("timestamp") ?: "",
                    branchId = rs.getString("branch_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get all sales", e)
        }
        return sales
    }

    // ================================================================
    // DATA ACCESS — INVENTORY
    // ================================================================

    fun getInventory(): List<InventoryItemDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<InventoryItemDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM inventory")
            while (rs.next()) {
                items.add(InventoryItemDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    category = rs.getString("category") ?: "",
                    subcategory = rs.getString("subcategory") ?: "",
                    stock_quantity = rs.getDouble("stock_quantity"),
                    min_threshold = rs.getDouble("min_threshold"),
                    cost_price = rs.getDouble("cost_price"),
                    selling_price = rs.getDouble("selling_price"),
                    unit = rs.getString("unit") ?: "Units",
                    is_portion_tracked = rs.getInt("is_portion_tracked") == 1,
                    portionsPerUnit = rs.getDouble("portions_per_unit").takeIf { !rs.wasNull() },
                    linkedMenuItemName = rs.getString("linked_menu_item_name"),
                    sold_by_shot = rs.getInt("sold_by_shot") == 1,
                    bottleVolumeMl = rs.getDouble("bottle_volume_ml").takeIf { !rs.wasNull() },
                    shotSizeMl = rs.getDouble("shot_size_ml").takeIf { !rs.wasNull() },
                    status = rs.getString("status") ?: "AVAILABLE",
                    branchId = rs.getString("branch_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get inventory", e)
        }
        return items
    }

    // ================================================================
    // DATA ACCESS — EXPENSES
    // ================================================================

    fun getRecentExpenses(days: Int = 2): List<ExpenseDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<ExpenseDto>()
        try {
            val cutoff = Instant.now().minusSeconds(days * 86400L).toString()
            val stmt = conn.prepareStatement(
                "SELECT * FROM expenses WHERE created_at >= ? OR created_at IS NULL ORDER BY created_at DESC"
            )
            stmt.setString(1, cutoff)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                items.add(ExpenseDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    category = rs.getString("category") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    expenseDate = rs.getString("expense_date"),
                    created_at = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get expenses", e)
        }
        return items
    }

    fun getAllExpenses(): List<ExpenseDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<ExpenseDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM expenses ORDER BY created_at DESC")
            while (rs.next()) {
                items.add(ExpenseDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    category = rs.getString("category") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    expenseDate = rs.getString("expense_date"),
                    created_at = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get all expenses", e)
        }
        return items
    }

    // ================================================================
    // DATA ACCESS — CREDITS
    // ================================================================

    fun getRecentCredits(days: Int = 2): List<CreditDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<CreditDto>()
        try {
            val cutoff = Instant.now().minusSeconds(days * 86400L).toString()
            val stmt = conn.prepareStatement(
                "SELECT * FROM credits WHERE created_at >= ? OR created_at IS NULL ORDER BY created_at DESC"
            )
            stmt.setString(1, cutoff)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                items.add(CreditDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    contactName = rs.getString("contact_name") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    creditType = rs.getString("credit_type") ?: "GIVEN",
                    is_settled = rs.getInt("is_settled") == 1,
                    settledAt = rs.getString("settled_at"),
                    notes = rs.getString("notes"),
                    created_at = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get credits", e)
        }
        return items
    }

    fun getAllCredits(): List<CreditDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<CreditDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM credits ORDER BY created_at DESC")
            while (rs.next()) {
                items.add(CreditDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    contactName = rs.getString("contact_name") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    creditType = rs.getString("credit_type") ?: "GIVEN",
                    is_settled = rs.getInt("is_settled") == 1,
                    settledAt = rs.getString("settled_at"),
                    notes = rs.getString("notes"),
                    created_at = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get all credits", e)
        }
        return items
    }

    // ================================================================
    // DATA ACCESS — CATEGORIES
    // ================================================================

    fun getCategories(): List<CategoryDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<CategoryDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM categories")
            while (rs.next()) {
                items.add(CategoryDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    parentName = rs.getString("parent_name"),
                    created_at = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get categories", e)
        }
        return items
    }

    // ================================================================
    // DATA ACCESS — MENU ITEMS
    // ================================================================

    fun getMenuItems(): List<MenuItemDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<MenuItemDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM menu_items WHERE is_active = 1")
            while (rs.next()) {
                items.add(MenuItemDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    price = rs.getDouble("price"),
                    category = rs.getString("category") ?: "",
                    subcategory = rs.getString("subcategory") ?: "",
                    branchId = rs.getString("branch_id"),
                    is_active = rs.getInt("is_active") == 1
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get menu items", e)
        }
        return items
    }

    // ================================================================
    // DATA ACCESS — CUSTOMERS
    // ================================================================

    fun getCustomers(): List<CustomerDto> {
        val conn = connection ?: return emptyList()
        val items = mutableListOf<CustomerDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM customers")
            while (rs.next()) {
                items.add(CustomerDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    phone = rs.getString("phone") ?: "",
                    email = rs.getString("email"),
                    address = rs.getString("address"),
                    idType = rs.getString("id_type"),
                    idNumber = rs.getString("id_number"),
                    profileImageUrl = rs.getString("profile_image_url"),
                    membershipStatus = rs.getString("membership_status") ?: "ACTIVE",
                    membershipExpiry = rs.getString("membership_expiry"),
                    notes = rs.getString("notes"),
                    branchId = rs.getString("branch_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get customers", e)
        }
        return items
    }

    // ================================================================
    // UPSERT (Insert or Update) — Used by sync engine
    // ================================================================

    fun upsertBranch(branch: BranchDto) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO branches (id, name, type, address, is_active, parent_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            stmt.setString(1, branch.id ?: "")
            stmt.setString(2, branch.name)
            stmt.setString(3, branch.type)
            stmt.setString(4, branch.address)
            stmt.setInt(5, if (branch.isActive) 1 else 0)
            stmt.setString(6, branch.parentId)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to upsert branch", e)
        }
    }

    // ================================================================
    // CLEANUP
    // ================================================================

    fun close() {
        try {
            connection?.close()
            connection = null
            Logger.info(TAG, "Database closed")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to close database", e)
        }
    }
}

/**
 * Represents a pending sync operation.
 */
data class SyncQueueItem(
    val id: Long,
    val tableName: String,
    val recordId: String,
    val action: String,
    val createdAt: String,
    val retryCount: Int
)
