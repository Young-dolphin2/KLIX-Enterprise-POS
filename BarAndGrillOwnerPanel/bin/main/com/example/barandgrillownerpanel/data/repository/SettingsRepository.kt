package com.example.barandgrillownerpanel.data.repository

import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.utils.Logger
import com.example.barandgrillownerpanel.ui.dashboard.AppSettings
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for settings-related data operations.
 * Centralizes all Supabase calls for settings, branches, and employees.
 * UI layer calls here instead of hitting Supabase directly.
 */
object SettingsRepository {
    private const val TAG = "SETTINGS_REPO"

    // ── Branches ───────────────────────────────────────────────────

    suspend fun fetchBranches(): List<BranchDto> {
        return withContext(Dispatchers.IO) {
            try {
                val branches = SupabaseManager.client.postgrest["branches"]
                    .select()
                    .decodeAs<List<BranchDto>>()
                // Cache to local DB
                branches.forEach { LocalDatabase.upsertBranch(it) }
                Logger.info(TAG, "Fetched ${branches.size} branches from Supabase")
                branches
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to fetch branches from Supabase, using local cache")
                LocalDatabase.getBranches().ifEmpty { throw e }
            }
        }
    }

    suspend fun createBranch(name: String, type: String): BranchDto? {
        return withContext(Dispatchers.IO) {
            try {
                val newBranch = SupabaseManager.client.postgrest["branches"]
                    .insert(mapOf("name" to name, "type" to type)) {
                        select()
                    }
                    .decodeAs<BranchDto>()
                newBranch.also { LocalDatabase.upsertBranch(it) }
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to create branch on Supabase, saving locally")
                // Create locally with temp ID
                val localBranch = BranchDto(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    is_active = true
                )
                LocalDatabase.upsertBranch(localBranch)
                LocalDatabase.queueSync("branches", localBranch.id ?: "", "INSERT")
                localBranch
            }
        }
    }

    suspend fun updateBranch(id: String, updates: Map<String, Any>) {
        withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client.postgrest["branches"]
                    .update(updates) { filter { eq("id", id) } }
                Logger.info(TAG, "Updated branch $id")
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to update branch $id on Supabase")
                LocalDatabase.queueSync("branches", id, "UPDATE")
            }
        }
    }

    // ── Employees ─────────────────────────────────────────────────

    suspend fun fetchEmployees(): List<Map<String, Any?>> {
        return withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client.postgrest["employees"]
                    .select()
                    .decodeAs<List<Map<String, Any?>>>()
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to fetch employees: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun createEmployee(employee: Map<String, Any?>): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client.postgrest["employees"]
                    .insert(employee) { select() }
                    .decodeAs<Map<String, Any?>>()
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to create employee", e)
                null
            }
        }
    }

    suspend fun updateEmployee(id: String, updates: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client.postgrest["employees"]
                    .update(updates) { filter { eq("id", id) } }
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to update employee $id", e)
            }
        }
    }

    suspend fun deleteEmployee(id: String) {
        withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client.postgrest["employees"]
                    .delete { filter { eq("id", id) } }
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to delete employee $id", e)
            }
        }
    }

    // ── App Settings ──────────────────────────────────────────────

    /**
     * Load settings from local SQLite (fast, works offline).
     */
    fun getLocalSettings(): AppSettings? {
        return LocalDatabase.getAppSettings()
    }

    /**
     * Save settings to local SQLite immediately.
     */
    fun saveLocalSettings(settings: AppSettings) {
        LocalDatabase.saveAppSettings(
            settings = settings,
            phone = "",
            country = settings.country,
            currencyCode = settings.currencySymbol,
            isOnboarded = true
        )
        Logger.info(TAG, "Settings saved to local database")
    }
}
