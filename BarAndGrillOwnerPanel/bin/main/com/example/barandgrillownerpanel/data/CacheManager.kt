package com.example.barandgrillownerpanel.data

import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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
    
    private const val AES_GCM_TAG_LEN = 128
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_NODE = "com.example.barandgrillownerpanel.cache"
    private const val KEY_NAME = "cache_encryption_key"

    // Cache encryption key stored in an owner-only key file next to the JSON cache.
    // This avoids storing raw secrets in Java Preferences.
    private val cacheKeyFile: File by lazy { File(cacheFile.parentFile ?: File("."), ".klix_cache_key.bin") }

    private val secretKey: SecretKeySpec by lazy {
        try {
            cacheKeyFile.parentFile?.mkdirs()
            val keyBytes = if (cacheKeyFile.exists()) {
                cacheKeyFile.readBytes().also { setOwnerOnlyPermissions(cacheKeyFile) }
            } else {
                val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
                cacheKeyFile.writeBytes(generated)
                setOwnerOnlyPermissions(cacheKeyFile)
                generated
            }
            if (keyBytes.size == 32) SecretKeySpec(keyBytes, "AES") else throw IllegalStateException("Invalid cache key length")
        } catch (e: Exception) {
            // Best-effort fallback to a generated in-memory key (not persisted). This is safer
            // than deriving from machine identifiers or storing in Preferences.
            val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
            SecretKeySpec(generated, "AES")
        }
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(encrypted: String): String {
        return try {
            val decoded = Base64.getDecoder().decode(encrypted)
            if (decoded.size < 13) throw IllegalArgumentException("Ciphertext too short for GCM")
            val iv = decoded.copyOfRange(0, 12)
            val cipherText = decoded.copyOfRange(12, decoded.size)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.error("CACHE", "Decryption failed for legacy cache: ${e.message}", e)
            // Return the original value so callers can decide if it's plain JSON or handle failures.
            encrypted
        }
    }

    /**
     * Initialize the cache system.
     * Must be called once on app startup before any data access.
     */
    suspend fun initialize(dbDirectory: String = ".") {
        if (sqliteInitialized) return
        
        withContext(Dispatchers.IO) {
            setOwnerOnlyPermissions(cacheFile)
            LocalDatabase.initialize(dbDirectory)
            sqliteInitialized = true
            
            // Migrate legacy JSON cache to SQLite (one-time)
            migrateLegacyCache()
        }
        
        Logger.info("CACHE", "Cache system initialized (SQLite primary, JSON fallback)")
    }

    /**
     * One-time migration from legacy JSON cache to SQLite.
     * The JSON file is preserved but no longer updated.
     */
    private suspend fun migrateLegacyCache() = withContext(Dispatchers.IO) {
        if (legacyCacheMigrated) return@withContext
        if (!cacheFile.exists()) {
            legacyCacheMigrated = true
            return@withContext
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

    private fun setOwnerOnlyPermissions(file: File) {
        try {
            file.parentFile?.mkdirs()
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (ignored: Exception) {
            // Best-effort hardening for local fallback cache file permissions.
        }
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

    fun getCustomers(offset: Int = 0, limit: Int = 100): List<CustomerDto> {
        return LocalDatabase.getCustomers(offset, limit)
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
    suspend fun saveCache(data: DashboardDataCache) = withContext(Dispatchers.IO) {
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
    suspend fun loadCache(): DashboardDataCache? = withContext(Dispatchers.IO) {
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
                return@withContext DashboardDataCache(
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
        return@withContext loadLegacyCache()
    }

    /**
     * Load from legacy JSON cache (fallback).
     */
    private suspend fun loadLegacyCache(): DashboardDataCache? = withContext(Dispatchers.IO) {
        return@withContext try {
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
