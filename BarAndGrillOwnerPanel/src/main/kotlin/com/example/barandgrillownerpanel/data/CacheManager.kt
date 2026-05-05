package com.example.barandgrillownerpanel.data

import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.utils.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * CacheManager — Hybrid cache layer.
 * 
 * PRIMARY: SQLite local database (fast, queryable, offline)
 * FALLBACK: Legacy encrypted JSON file (preserved for migration)
 * 
 * On first launch with SQLite, if the old JSON cache exists,
 * it's migrated to SQLite. The JSON file is no longer written to
 * but is kept as a safety net.
 */
@Serializable
data class DashboardDataCache(
    val branches: List<BranchDto> = emptyList(),
    val menuItems: List<MenuItemDto> = emptyList(),
    val inventoryItems: List<InventoryItemDto> = emptyList(),
    val credits: List<CreditDto> = emptyList(),
    val sales: List<SaleDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList()
)

object CacheManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheFile = File("dashboard_cache.json")
    private var sqliteInitialized = false
    private var legacyCacheMigrated = false
    
    // Machine-locked key generation (kept for legacy fallback)
    private val secretKey: SecretKeySpec by lazy {
        val machineId = System.getenv("COMPUTERNAME") ?: System.getProperty("user.name") ?: "KLIX_FALLBACK"
        val hash = MessageDigest.getInstance("SHA-256").digest(machineId.toByteArray())
        SecretKeySpec(hash.copyOf(16), "AES")
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))
    }

    private fun decrypt(encrypted: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return String(cipher.doFinal(Base64.getDecoder().decode(encrypted)))
    }

    /**
     * Initialize the cache system.
     * Must be called once on app startup before any data access.
     */
    fun initialize(dbDirectory: String = ".") {
        if (sqliteInitialized) return
        
        LocalDatabase.initialize(dbDirectory)
        sqliteInitialized = true
        
        // Migrate legacy JSON cache to SQLite (one-time)
        migrateLegacyCache()
        
        Logger.info("CACHE", "Cache system initialized (SQLite primary, JSON fallback)")
    }

    /**
     * One-time migration from legacy JSON cache to SQLite.
     * The JSON file is preserved but no longer updated.
     */
    private fun migrateLegacyCache() {
        if (legacyCacheMigrated) return
        if (!cacheFile.exists()) {
            legacyCacheMigrated = true
            return
        }

        try {
            val encrypted = cacheFile.readText()
            val decrypted = if (encrypted.trim().startsWith("{")) {
                encrypted
            } else {
                decrypt(encrypted)
            }
            val legacyData = json.decodeFromString(DashboardDataCache.serializer(), decrypted)
            
            // Migrate branches
            legacyData.branches.forEach { LocalDatabase.upsertBranch(it) }
            
            Logger.info("CACHE", "Migrated legacy cache to SQLite (${legacyData.branches.size} branches, ${legacyData.sales.size} sales, ${legacyData.inventoryItems.size} inventory items)")
            legacyCacheMigrated = true
        } catch (e: Exception) {
            Logger.error("CACHE", "Failed to migrate legacy cache", e)
        }
    }

    // ================================================================
    // MODERN DATA ACCESS — via SQLite
    // ================================================================

    /**
     * Get recent sales (last N days) from local SQLite.
     * Fast, no network needed.
     */
    fun getRecentSales(days: Int = 2): List<SaleDto> {
        return LocalDatabase.getRecentSales(days)
    }

    /**
     * Get ALL sales from local SQLite.
     * Used for reports that need full history.
     */
    fun getAllSales(): List<SaleDto> {
        return LocalDatabase.getAllSales()
    }

    fun getBranches(): List<BranchDto> {
        return LocalDatabase.getBranches()
    }

    fun getInventory(): List<InventoryItemDto> {
        return LocalDatabase.getInventory()
    }

    fun getRecentExpenses(days: Int = 2): List<ExpenseDto> {
        return LocalDatabase.getRecentExpenses(days)
    }

    fun getAllExpenses(): List<ExpenseDto> {
        return LocalDatabase.getAllExpenses()
    }

    fun getRecentCredits(days: Int = 2): List<CreditDto> {
        return LocalDatabase.getRecentCredits(days)
    }

    fun getAllCredits(): List<CreditDto> {
        return LocalDatabase.getAllCredits()
    }

    fun getCategories(): List<CategoryDto> {
        return LocalDatabase.getCategories()
    }

    fun getMenuItems(): List<MenuItemDto> {
        return LocalDatabase.getMenuItems()
    }

    fun getCustomers(): List<CustomerDto> {
        return LocalDatabase.getCustomers()
    }

    // ================================================================
    // LEGACY METHODS — Kept for backward compatibility
    // These still work but delegate to SQLite internally.
    // DashboardDataCache can be constructed from SQLite for old code.
    // ================================================================

    /**
     * Save cache — now writes to SQLite instead of JSON.
     * Legacy JSON file is preserved but no longer updated.
     */
    fun saveCache(data: DashboardDataCache) {
        try {
            // Save branches to SQLite
            data.branches.forEach { LocalDatabase.upsertBranch(it) }
            
            Logger.info("CACHE", "Cache saved to SQLite (${data.branches.size} branches, ${data.sales.size} sales)")
        } catch (e: Exception) {
            Logger.error("CACHE", "Failed to save cache to SQLite", e)
            // Fallback: write to legacy JSON
            try {
                val jsonStr = json.encodeToString(DashboardDataCache.serializer(), data)
                val encrypted = encrypt(jsonStr)
                cacheFile.writeText(encrypted)
                Logger.warn("CACHE", "Fell back to legacy JSON cache")
            } catch (e2: Exception) {
                Logger.error("CACHE", "Legacy fallback also failed", e2)
            }
        }
    }

    /**
     * Load cache — now reads from SQLite, falls back to JSON.
     */
    fun loadCache(): DashboardDataCache? {
        // Try SQLite first
        try {
            val branches = LocalDatabase.getBranches()
            val menuItems = LocalDatabase.getMenuItems()
            val inventory = LocalDatabase.getInventory()
            val categories = LocalDatabase.getCategories()
            
            // For the initial load, get recent data (last 2 days)
            val sales = LocalDatabase.getRecentSales(2)
            val credits = LocalDatabase.getRecentCredits(2)
            
            if (branches.isNotEmpty() || sales.isNotEmpty()) {
                return DashboardDataCache(
                    branches = branches,
                    menuItems = menuItems,
                    inventoryItems = inventory,
                    credits = credits,
                    sales = sales,
                    categories = categories
                )
            }
        } catch (e: Exception) {
            Logger.error("CACHE", "SQLite load failed, falling back to JSON", e)
        }

        // Fallback: legacy JSON
        return loadLegacyCache()
    }

    /**
     * Load from legacy JSON cache (fallback).
     */
    private fun loadLegacyCache(): DashboardDataCache? {
        return try {
            if (cacheFile.exists()) {
                val encrypted = cacheFile.readText()
                val decrypted = if (encrypted.trim().startsWith("{")) {
                    encrypted
                } else {
                    decrypt(encrypted)
                }
                json.decodeFromString(DashboardDataCache.serializer(), decrypted)
            } else null
        } catch (e: Exception) {
            Logger.error("CACHE", "Failed to load legacy cache", e)
            null
        }
    }

    /**
     * Check if the cache has any data.
     */
    fun hasData(): Boolean {
        return try {
            LocalDatabase.getBranches().isNotEmpty() || cacheFile.exists()
        } catch (e: Exception) {
            cacheFile.exists()
        }
    }
}
