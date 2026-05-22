package com.example.barandgrillownerpanel.data.local

import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.utils.Logger
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local SQLite database for offline-first operations.
 * 
 * Architecture:
 * - All reads go here first (instant, no network)
 * - All writes go here first (instant, no network)
 * - SyncEngine pushes/pulls changes to/from Supabase in background
 * - Supabase remains the source of truth
 */
object LocalDatabase {
    private const val TAG = "LOCAL_DB"
    private const val DB_VERSION = 2
    private const val DB_NAME = "klix_local_v2.db"
    private const val ENCRYPTION_KEY_FILE = "klix_local_key.bin"
    private const val AES_GCM_TAG_LEN = 128
    private const val AES_KEY_BYTES = 32
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    private var connection: Connection? = null
    private var dbFile: File? = null
    private var encryptionKey: SecretKeySpec? = null

    suspend fun initialize(dbDirectory: String = ".") = withContext(Dispatchers.IO) {
        try {
            if (connection != null && !connection!!.isClosed) {
                if (encryptionKey == null) loadOrCreateEncryptionKey()
                return@withContext
            }
            Class.forName("org.sqlite.JDBC")
            dbFile = File(dbDirectory, DB_NAME)
            setOwnerOnlyPermissions(dbFile)
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile?.absolutePath}")
            connection?.apply {
                createStatement().execute("PRAGMA journal_mode=WAL")
                createStatement().execute("PRAGMA foreign_keys=ON")
                createStatement().execute("PRAGMA secure_delete=ON")
                createStatement().execute("PRAGMA temp_store=MEMORY")
            }
            createTables()
            migrateIfNeeded()
            loadOrCreateEncryptionKey()
            Logger.info(TAG, "Database initialized at ${dbFile?.absolutePath}")
        } catch (e: Exception) {
            Logger.error(TAG, "Database init failed: ${e.message}")
            Logger.warn(TAG, "Attempting recovery: deleting corrupted db and re-creating...")
            try {
                connection?.close()
                connection = null
                dbFile?.delete()
                setOwnerOnlyPermissions(dbFile)
                connection = DriverManager.getConnection("jdbc:sqlite:${dbFile?.absolutePath}")
                connection?.apply {
                    createStatement().execute("PRAGMA journal_mode=WAL")
                    createStatement().execute("PRAGMA foreign_keys=ON")
                    createStatement().execute("PRAGMA secure_delete=ON")
                    createStatement().execute("PRAGMA temp_store=MEMORY")
                }
                createTables()
                migrateIfNeeded()
                loadOrCreateEncryptionKey()
                setMeta("needs_full_sync", "true")
                setMeta("last_sync", "1970-01-01T00:00:00Z")
                Logger.warn(TAG, "Database recovered. Full re-sync required.")
            } catch (recoveryError: Exception) {
                Logger.fatal(TAG, "Database recovery failed: ${recoveryError.message}", recoveryError)
                connection = null
                dbFile = null
            }
        }
    }

    private fun loadOrCreateEncryptionKey() {
        try {
            if (encryptionKey != null) return
            val keyFile = dbFile?.parentFile?.let { File(it, ENCRYPTION_KEY_FILE) }
            if (keyFile == null) {
                Logger.warn(TAG, "Encryption key file location unavailable")
                return
            }
            keyFile.parentFile?.mkdirs()
            val keyBytes = if (keyFile.exists()) {
                Files.readAllBytes(keyFile.toPath()).also { setOwnerOnlyPermissions(keyFile) }
            } else {
                val generated = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
                Files.write(keyFile.toPath(), generated)
                setOwnerOnlyPermissions(keyFile)
                generated
            }
            if (keyBytes.size == AES_KEY_BYTES) {
                encryptionKey = SecretKeySpec(keyBytes, "AES")
            } else {
                Logger.warn(TAG, "Local encryption key had invalid length, regenerating")
                val generated = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
                Files.write(keyFile.toPath(), generated)
                setOwnerOnlyPermissions(keyFile)
                encryptionKey = SecretKeySpec(generated, "AES")
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Unable to initialize local encryption key: ${e.message}")
            encryptionKey = null
        }
    }

    private fun encryptColumn(value: String?): String? {
        if (value.isNullOrBlank() || encryptionKey == null) return value
        return try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(iv + encrypted)
        } catch (e: Exception) {
            Logger.warn(TAG, "Column encryption failed: ${e.message}")
            value
        }
    }

    private fun decryptColumn(value: String?): String? {
        if (value.isNullOrBlank() || encryptionKey == null) return value
        return try {
            val decoded = Base64.getDecoder().decode(value)
            if (decoded.size < 13) return value
            val iv = decoded.copyOfRange(0, 12)
            val cipherText = decoded.copyOfRange(12, decoded.size)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.warn(TAG, "Column decryption failed: ${e.message}")
            value
        }
    }

    private fun setOwnerOnlyPermissions(file: File?) {
        try {
            if (file == null) return
            file.parentFile?.mkdirs()
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (ignored: Exception) {
            // Best-effort file permission hardening.
        }
    }

    private fun createTables() {
        val conn = connection ?: return
        conn.createStatement().apply {
            // Business Settings
            execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    id TEXT PRIMARY KEY,
                    business_name TEXT NOT NULL,
                    country TEXT,
                    currency_symbol TEXT DEFAULT '$',
                    currency_code TEXT DEFAULT 'USD',
                    phone_number TEXT,
                    payment_methods_json TEXT,
                    primary_color_hex TEXT DEFAULT '#FF5722',
                    is_onboarded INTEGER DEFAULT 0,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """)

            // Branches
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

            // Menu & Inventory
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

            // Sales & Items
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

            // Financials
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

            // Metadata
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
                CREATE TABLE IF NOT EXISTS customers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    email TEXT,
                    address TEXT,
                    membership_status TEXT DEFAULT 'ACTIVE',
                    branch_id TEXT,
                    tenant_id TEXT,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    synced_at TEXT
                )
            """)

            // Sync Infrastructure
            execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_name TEXT NOT NULL,
                    record_id TEXT NOT NULL,
                    operation TEXT NOT NULL DEFAULT 'UPDATE',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
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

            // Performance indexes for high-read queries
            execute("CREATE INDEX IF NOT EXISTS idx_branches_is_active ON branches(is_active)")
            execute("CREATE INDEX IF NOT EXISTS idx_menu_items_branch_id ON menu_items(branch_id)")
            execute("CREATE INDEX IF NOT EXISTS idx_menu_items_is_active ON menu_items(is_active)")
            execute("CREATE INDEX IF NOT EXISTS idx_inventory_branch_id ON inventory(branch_id)")
            execute("CREATE INDEX IF NOT EXISTS idx_inventory_status ON inventory(status)")
            execute("CREATE INDEX IF NOT EXISTS idx_sales_timestamp ON sales(timestamp)")
            execute("CREATE INDEX IF NOT EXISTS idx_sales_branch_id ON sales(branch_id)")
            execute("CREATE INDEX IF NOT EXISTS idx_expenses_expense_date ON expenses(expense_date)")
            execute("CREATE INDEX IF NOT EXISTS idx_customers_branch_id ON customers(branch_id)")
            execute("CREATE INDEX IF NOT EXISTS idx_customers_updated_at ON customers(updated_at)")
            execute("CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name)")
            execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_pushed ON sync_queue(pushed)")
        }
    }

    private fun migrateIfNeeded() {
        val currentVersion = getMeta("db_version")?.toIntOrNull() ?: 1
        if (currentVersion < DB_VERSION) {
            // Future migration logic
            setMeta("db_version", DB_VERSION.toString())
        }
    }

    // ================================================================
    // SYNC METADATA
    // ================================================================

    fun setMeta(key: String, value: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("INSERT OR REPLACE INTO sync_metadata (key, value) VALUES (?, ?)")
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to set meta $key", e)
        }
    }

    fun getMeta(key: String): String? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT value FROM sync_metadata WHERE key = ?")
            stmt.setString(1, key)
            val rs = stmt.executeQuery()
            if (rs.next()) return rs.getString("value")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get meta $key", e)
        }
        return null
    }

    // ================================================================
    // APP SETTINGS
    // ================================================================

    fun saveAppSettings(
        settings: com.example.barandgrillownerpanel.ui.dashboard.AppSettings,
        phone: String,
        country: String,
        currencyCode: String,
        isOnboarded: Boolean
    ) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO app_settings (
                    id, business_name, country, currency_symbol, currency_code, 
                    phone_number, payment_methods_json, primary_color_hex, is_onboarded, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            stmt.setString(1, "singleton")
            stmt.setString(2, settings.businessName)
            stmt.setString(3, country)
            stmt.setString(4, settings.currencySymbol)
            stmt.setString(5, currencyCode)
            stmt.setString(6, encryptColumn(phone))
            stmt.setString(7, kotlinx.serialization.json.Json.encodeToString(settings.paymentMethods))
            stmt.setString(8, settings.primaryColorHex)
            stmt.setInt(9, if (isOnboarded) 1 else 0)
            stmt.execute()
            
            queueSync("app_settings", "singleton", "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save app settings", e)
        }
    }

    fun getAppSettings(): com.example.barandgrillownerpanel.ui.dashboard.AppSettings? {
        val conn = connection ?: return null
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM app_settings WHERE id = 'singleton'")
            if (rs.next()) {
                val methodsJson = rs.getString("payment_methods_json") ?: "[]"
                val methods = try {
                    kotlinx.serialization.json.Json.decodeFromString<List<com.example.barandgrillownerpanel.ui.dashboard.PaymentMethod>>(methodsJson)
                } catch (e: Exception) { emptyList() }

                return com.example.barandgrillownerpanel.ui.dashboard.AppSettings(
                    businessName = rs.getString("business_name") ?: "",
                    country = rs.getString("country") ?: "",
                    currencySymbol = rs.getString("currency_symbol") ?: "$",
                    currencyCode = rs.getString("currency_code") ?: "USD",
                    phoneNumber = decryptColumn(rs.getString("phone_number")) ?: "",
                    primaryColorHex = rs.getString("primary_color_hex") ?: "#FF5722",
                    paymentMethods = methods
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get app settings", e)
        }
        return null
    }

    // ================================================================
    // BRANCHES
    // ================================================================

    fun upsertBranch(branch: BranchDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO branches (id, name, type, address, is_active, parent_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            val id = branch.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, branch.name)
            stmt.setString(3, branch.type)
            stmt.setString(4, branch.address)
            stmt.setInt(5, if (branch.is_active) 1 else 0)
            stmt.setString(6, branch.parentId)
            stmt.execute()
            if (!fromSync) queueSync("branches", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to upsert branch", e)
        }
    }

    fun saveBranch(branch: BranchDto) {
        upsertBranch(branch, fromSync = false)
    }

    fun getBranches(): List<BranchDto> {
        val conn = connection ?: return emptyList()
        val branches = mutableListOf<BranchDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM branches WHERE is_active = 1")
            while (rs.next()) {
                branches.add(BranchDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    type = rs.getString("type") ?: "BAR",
                    address = rs.getString("address"),
                    is_active = rs.getInt("is_active") == 1,
                    parentId = rs.getString("parent_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get branches", e)
        }
        return branches
    }

    // ================================================================
    // SALES
    // ================================================================

    fun getRecentSales(days: Int = 2): List<SaleDto> {
        val conn = connection ?: return emptyList()
        val sales = mutableListOf<SaleDto>()
        try {
            val cutoff = Instant.now().minusSeconds(days * 86400L).toString()
            val stmt = conn.prepareStatement("SELECT * FROM sales WHERE timestamp >= ? ORDER BY timestamp DESC")
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

    fun getAllSales(): List<SaleDto> {
        return getRecentSales(3650) // roughly 10 years
    }

    fun saveSale(sale: SaleDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO sales (
                    id, order_id, total_amount, payment_method, sold_by, timestamp, branch_id, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            stmt.setString(1, sale.id)
            stmt.setString(2, sale.orderId)
            stmt.setDouble(3, sale.totalAmount)
            stmt.setString(4, sale.paymentMethod)
            stmt.setString(5, sale.soldBy)
            stmt.setString(6, sale.timestamp)
            stmt.setString(7, sale.branchId)
            stmt.execute()

            if (!fromSync) queueSync("sales", sale.id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save sale", e)
        }
    }

    fun deleteSale(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM sales WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("sales", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete sale", e)
        }
    }

    fun getSale(id: String): SaleDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM sales WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return SaleDto(
                    id = rs.getString("id") ?: "",
                    orderId = rs.getString("order_id") ?: "",
                    totalAmount = rs.getDouble("total_amount"),
                    paymentMethod = rs.getString("payment_method") ?: "Cash",
                    soldBy = rs.getString("sold_by") ?: "",
                    timestamp = rs.getString("timestamp") ?: "",
                    branchId = rs.getString("branch_id")
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get sale", e)
        }
        return null
    }

    fun saveSaleItem(item: SaleItemDto) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO sale_items (
                    sale_id, name, quantity, price, category, updated_at
                ) VALUES (?, ?, ?, ?, ?, datetime('now'))
            """)
            stmt.setString(1, item.saleId)
            stmt.setString(2, item.name)
            stmt.setInt(3, item.quantity)
            stmt.setDouble(4, item.price)
            stmt.setString(5, item.category)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save sale item", e)
        }
    }

    fun getSaleItems(saleId: String): List<SaleItemDto> {
        val conn = connection ?: return emptyList()
        val list = mutableListOf<SaleItemDto>()
        try {
            val stmt = conn.prepareStatement("SELECT * FROM sale_items WHERE sale_id = ?")
            stmt.setString(1, saleId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(SaleItemDto(
                    saleId = rs.getString("sale_id") ?: "",
                    name = rs.getString("name") ?: "",
                    price = rs.getDouble("price"),
                    quantity = rs.getInt("quantity"),
                    category = rs.getString("category") ?: ""
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get sale items", e)
        }
        return list
    }

    fun saveInventoryItem(item: InventoryItemDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO inventory (
                    id, name, category, subcategory, stock_quantity, min_threshold, 
                    cost_price, selling_price, unit, is_portion_tracked, portions_per_unit, 
                    linked_menu_item_name, sold_by_shot, bottle_volume_ml, shot_size_ml, 
                    status, branch_id, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            val id = item.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, item.name)
            stmt.setString(3, item.category)
            stmt.setString(4, item.subcategory)
            stmt.setDouble(5, item.stock_quantity)
            stmt.setDouble(6, item.min_threshold)
            stmt.setDouble(7, item.cost_price)
            stmt.setDouble(8, item.sellingPrice)
            stmt.setString(9, item.unit)
            stmt.setInt(10, if (item.isPortionTracked) 1 else 0)
            stmt.setDouble(11, item.portionsPerUnit ?: 0.0)
            stmt.setString(12, item.linkedMenuItemName)
            stmt.setInt(13, if (item.soldByShot) 1 else 0)
            stmt.setDouble(14, item.bottleVolumeMl ?: 0.0)
            stmt.setDouble(15, item.shotSizeMl ?: 0.0)
            stmt.setString(16, item.status)
            stmt.setString(17, item.branchId)
            stmt.execute()

            if (!fromSync) queueSync("inventory", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save inventory item", e)
        }
    }

    fun deleteInventoryItem(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM inventory WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("inventory", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete inventory item", e)
        }
    }

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
                    sellingPrice = rs.getDouble("selling_price"),
                    unit = rs.getString("unit") ?: "Units",
                    isPortionTracked = rs.getInt("is_portion_tracked") == 1,
                    portionsPerUnit = rs.getDouble("portions_per_unit"),
                    linkedMenuItemName = rs.getString("linked_menu_item_name"),
                    soldByShot = rs.getInt("sold_by_shot") == 1,
                    bottleVolumeMl = rs.getDouble("bottle_volume_ml"),
                    shotSizeMl = rs.getDouble("shot_size_ml"),
                    status = rs.getString("status") ?: "AVAILABLE",
                    branchId = rs.getString("branch_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get inventory", e)
        }
        return items
    }

    fun getInventoryItem(id: String): InventoryItemDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM inventory WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return InventoryItemDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    category = rs.getString("category") ?: "",
                    subcategory = rs.getString("subcategory") ?: "",
                    stock_quantity = rs.getDouble("stock_quantity"),
                    min_threshold = rs.getDouble("min_threshold"),
                    cost_price = rs.getDouble("cost_price"),
                    sellingPrice = rs.getDouble("selling_price"),
                    unit = rs.getString("unit") ?: "Units",
                    isPortionTracked = rs.getInt("is_portion_tracked") == 1,
                    portionsPerUnit = rs.getDouble("portions_per_unit"),
                    linkedMenuItemName = rs.getString("linked_menu_item_name"),
                    soldByShot = rs.getInt("sold_by_shot") == 1,
                    bottleVolumeMl = rs.getDouble("bottle_volume_ml"),
                    shotSizeMl = rs.getDouble("shot_size_ml"),
                    status = rs.getString("status") ?: "AVAILABLE",
                    branchId = rs.getString("branch_id")
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get inventory item", e)
        }
        return null
    }

    fun saveExpense(expense: ExpenseDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO expenses (
                    id, branch_id, category, description, amount, expense_date, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            val id = expense.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, expense.branchId)
            stmt.setString(3, expense.category)
            stmt.setString(4, expense.description)
            stmt.setDouble(5, expense.amount)
            stmt.setString(6, expense.expenseDate)
            stmt.execute()

            if (!fromSync) queueSync("expenses", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save expense", e)
        }
    }

    fun deleteExpense(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM expenses WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("expenses", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete expense", e)
        }
    }

    fun getRecentExpenses(days: Int = 2): List<ExpenseDto> {
        val conn = connection ?: return emptyList()
        val expenses = mutableListOf<ExpenseDto>()
        try {
            val cutoff = Instant.now().minusSeconds(days * 86400L).toString()
            val stmt = conn.prepareStatement("SELECT * FROM expenses WHERE expense_date >= ? OR updated_at >= ?")
            stmt.setString(1, cutoff)
            stmt.setString(2, cutoff)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                expenses.add(ExpenseDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    category = rs.getString("category") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    expenseDate = rs.getString("expense_date")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get recent expenses", e)
        }
        return expenses
    }

    fun getAllExpenses(): List<ExpenseDto> = getRecentExpenses(3650)

    fun getExpense(id: String): ExpenseDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM expenses WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return ExpenseDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    category = rs.getString("category") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    expenseDate = rs.getString("expense_date")
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get expense", e)
        }
        return null
    }

    fun saveCredit(credit: CreditDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO credits (
                    id, branch_id, contact_name, description, amount, credit_type, 
                    is_settled, settled_at, notes, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            val id = credit.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, credit.branchId)
            stmt.setString(3, credit.contactName)
            stmt.setString(4, credit.description)
            stmt.setDouble(5, credit.amount)
            stmt.setString(6, credit.creditType)
            stmt.setInt(7, if (credit.isSettled) 1 else 0)
            stmt.setString(8, credit.settledAt)
            stmt.setString(9, credit.notes)
            stmt.execute()

            if (!fromSync) queueSync("credits", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save credit", e)
        }
    }

    fun deleteCredit(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM credits WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("credits", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete credit", e)
        }
    }

    fun getRecentCredits(days: Int = 2): List<CreditDto> {
        val conn = connection ?: return emptyList()
        val credits = mutableListOf<CreditDto>()
        try {
            val cutoff = Instant.now().minusSeconds(days * 86400L).toString()
            val stmt = conn.prepareStatement("SELECT * FROM credits WHERE updated_at >= ?")
            stmt.setString(1, cutoff)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                credits.add(CreditDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    contactName = rs.getString("contact_name") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    creditType = rs.getString("credit_type") ?: "GIVEN",
                    isSettled = rs.getInt("is_settled") == 1,
                    settledAt = rs.getString("settled_at"),
                    notes = rs.getString("notes")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get credits", e)
        }
        return credits
    }

    fun getAllCredits(): List<CreditDto> = getRecentCredits(3650)

    fun getCredit(id: String): CreditDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM credits WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return CreditDto(
                    id = rs.getString("id"),
                    branchId = rs.getString("branch_id"),
                    contactName = rs.getString("contact_name") ?: "",
                    description = rs.getString("description") ?: "",
                    amount = rs.getDouble("amount"),
                    creditType = rs.getString("credit_type") ?: "GIVEN",
                    isSettled = rs.getInt("is_settled") == 1,
                    settledAt = rs.getString("settled_at"),
                    notes = rs.getString("notes")
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get credit", e)
        }
        return null
    }

    fun getCategories(): List<CategoryDto> {
        val conn = connection ?: return emptyList()
        val list = mutableListOf<CategoryDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM categories")
            while (rs.next()) {
                list.add(CategoryDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    parentName = rs.getString("parent_name"),
                    created_at = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get categories", e)
        }
        return list
    }

    fun getCategory(id: String): CategoryDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM categories WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return CategoryDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    parentName = rs.getString("parent_name"),
                    created_at = rs.getString("created_at")
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get category", e)
        }
        return null
    }

    fun saveCategory(category: CategoryDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO categories (id, name, parent_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, datetime('now'))
            """)
            val id = category.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, category.name)
            stmt.setString(3, category.parentName)
            stmt.setString(4, category.created_at ?: Instant.now().toString())
            stmt.execute()
            if (!fromSync) queueSync("categories", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save category", e)
        }
    }

    fun deleteCategory(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM categories WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("categories", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete category", e)
        }
    }

    fun saveMenuItem(item: MenuItemDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO menu_items (
                    id, name, price, category, subcategory, branch_id, is_active, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            val id = item.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, item.name)
            stmt.setDouble(3, item.price)
            stmt.setString(4, item.category)
            stmt.setString(5, item.subcategory)
            stmt.setString(6, item.branchId)
            stmt.setInt(7, if (item.isActive) 1 else 0)
            stmt.execute()

            if (!fromSync) queueSync("menu_items", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save menu item", e)
        }
    }

    fun deleteMenuItem(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM menu_items WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("menu_items", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete menu item", e)
        }
    }

    fun getMenuItems(): List<MenuItemDto> {
        val conn = connection ?: return emptyList()
        val list = mutableListOf<MenuItemDto>()
        try {
            val rs = conn.createStatement().executeQuery("SELECT * FROM menu_items")
            while (rs.next()) {
                list.add(MenuItemDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    price = rs.getDouble("price"),
                    category = rs.getString("category") ?: "",
                    subcategory = rs.getString("subcategory") ?: "",
                    branchId = rs.getString("branch_id"),
                    isActive = rs.getInt("is_active") == 1
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get menu items", e)
        }
        return list
    }

    fun getMenuItem(id: String): MenuItemDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM menu_items WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return MenuItemDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    price = rs.getDouble("price"),
                    category = rs.getString("category") ?: "",
                    subcategory = rs.getString("subcategory") ?: "",
                    branchId = rs.getString("branch_id"),
                    isActive = rs.getInt("is_active") == 1
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get menu item", e)
        }
        return null
    }

    fun saveCustomer(customer: CustomerDto, fromSync: Boolean = false) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO customers (
                    id, name, phone, email, address, membership_status, branch_id, tenant_id, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """)
            val id = customer.id ?: UUID.randomUUID().toString()
            stmt.setString(1, id)
            stmt.setString(2, customer.name)
            stmt.setString(3, encryptColumn(customer.phone))
            stmt.setString(4, encryptColumn(customer.email))
            stmt.setString(5, encryptColumn(customer.address))
            stmt.setString(6, customer.membershipStatus)
            stmt.setString(7, customer.branchId)
            stmt.setString(8, null)
            stmt.execute()

            if (!fromSync) queueSync("customers", id, "UPDATE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save customer", e)
        }
    }

    fun deleteCustomer(id: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("DELETE FROM customers WHERE id = ?")
            stmt.setString(1, id)
            stmt.execute()
            queueSync("customers", id, "DELETE")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete customer", e)
        }
    }

    fun getCustomers(offset: Int = 0, limit: Int = 100): List<CustomerDto> {
        val conn = connection ?: return emptyList()
        val list = mutableListOf<CustomerDto>()
        try {
            val stmt = conn.prepareStatement("SELECT * FROM customers ORDER BY updated_at DESC LIMIT ? OFFSET ?")
            stmt.setInt(1, if (limit > 0) limit else 100)
            stmt.setInt(2, if (offset >= 0) offset else 0)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(CustomerDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    phone = decryptColumn(rs.getString("phone")) ?: "",
                    email = decryptColumn(rs.getString("email")),
                    address = decryptColumn(rs.getString("address")),
                    membershipStatus = rs.getString("membership_status") ?: "ACTIVE",
                    branchId = rs.getString("branch_id")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get customers", e)
        }
        return list
    }

    fun getCustomer(id: String): CustomerDto? {
        val conn = connection ?: return null
        try {
            val stmt = conn.prepareStatement("SELECT * FROM customers WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return CustomerDto(
                    id = rs.getString("id"),
                    name = rs.getString("name") ?: "",
                    phone = decryptColumn(rs.getString("phone")) ?: "",
                    email = decryptColumn(rs.getString("email")),
                    address = decryptColumn(rs.getString("address")),
                    membershipStatus = rs.getString("membership_status") ?: "ACTIVE",
                    branchId = rs.getString("branch_id")
                )
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get customer", e)
        }
        return null
    }

    fun incrementSyncRetry(id: Long) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("UPDATE sync_queue SET retry_count = retry_count + 1 WHERE id = ?")
            stmt.setLong(1, id)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to increment sync retry", e)
        }
    }

    fun getLastSyncTimestamp(): String {
        val conn = connection ?: return "1970-01-01T00:00:00Z"
        try {
            val rs = conn.createStatement().executeQuery("SELECT value FROM sync_metadata WHERE key = 'last_sync'")
            if (rs.next()) return rs.getString("value") ?: "1970-01-01T00:00:00Z"
        } catch (e: Exception) { }
        return "1970-01-01T00:00:00Z"
    }

    fun setLastSyncTimestamp(timestamp: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("INSERT OR REPLACE INTO sync_metadata (key, value) VALUES ('last_sync', ?)")
            stmt.setString(1, timestamp)
            stmt.execute()
        } catch (e: Exception) { }
    }

    fun markSyncPushed(id: Long) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("UPDATE sync_queue SET pushed = 1, updated_at = datetime('now') WHERE id = ?")
            stmt.setLong(1, id)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to mark sync pushed", e)
        }
    }

    fun getPendingSyncs(): List<SyncQueueItem> {
        val conn = connection ?: return emptyList()
        val list = mutableListOf<SyncQueueItem>()
        try {
            val rs = conn.createStatement().executeQuery("""
                SELECT * FROM sync_queue 
                WHERE pushed = 0 AND retry_count < 5 
                ORDER BY created_at ASC
            """)
            while (rs.next()) {
                list.add(SyncQueueItem(
                    id = rs.getLong("id"),
                    tableName = rs.getString("table_name") ?: "",
                    recordId = rs.getString("record_id") ?: "",
                    operation = rs.getString("operation") ?: "UPDATE",
                    pushed = rs.getInt("pushed") == 1,
                    retryCount = rs.getInt("retry_count"),
                    createdAt = rs.getString("created_at")
                ))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to get pending syncs", e)
        }
        return list
    }

    fun queueSync(tableName: String, recordId: String, operation: String) {
        val conn = connection ?: return
        try {
            val stmt = conn.prepareStatement("""
                INSERT INTO sync_queue (table_name, record_id, operation, pushed, retry_count, created_at, updated_at)
                VALUES (?, ?, ?, 0, 0, datetime('now'), datetime('now'))
            """)
            stmt.setString(1, tableName)
            stmt.setString(2, recordId)
            stmt.setString(3, operation)
            stmt.execute()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to queue sync", e)
        }
    }

    // ================================================================
    // CLEANUP
    // ================================================================

    fun close() {
        try {
            connection?.close()
            connection = null
        } catch (e: Exception) { }
    }
}

data class SyncQueueItem(
    val id: Long,
    val tableName: String,
    val recordId: String,
    val operation: String,
    val pushed: Boolean,
    val retryCount: Int,
    val createdAt: String
)
