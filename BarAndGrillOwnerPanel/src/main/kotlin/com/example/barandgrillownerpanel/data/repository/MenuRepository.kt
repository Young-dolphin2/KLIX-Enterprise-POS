package com.example.barandgrillownerpanel.data.repository

import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.models.CategoryDto
import com.example.barandgrillownerpanel.utils.Logger
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * Repository for menu-related data operations.
 * Centralizes all Supabase calls for menu items, categories, and ingredients.
 */
object MenuRepository {
    private const val TAG = "MENU_REPO"

    // ── Categories ─────────────────────────────────────────────────

    suspend fun fetchCategories(): List<CategoryDto> {
        return withContext(Dispatchers.IO) {
            try {
                val categories = SupabaseManager.client?.postgrest?.get("categories")?.select()?.decodeAs<List<CategoryDto>>() ?: emptyList()
                Logger.info(TAG, "Fetched ${categories.size} categories from Supabase")
                categories
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to fetch categories, using local cache")
                LocalDatabase.getCategories().ifEmpty { throw e }
            }
        }
    }

    suspend fun createCategory(name: String, parentName: String?): CategoryDto? {
        return withContext(Dispatchers.IO) {
            try {
                val payload = mutableMapOf<String, Any?>("name" to name)
                if (parentName != null) {
                    payload["parent_name"] = parentName
                }
                val created = SupabaseManager.client?.postgrest?.get("categories")?.insert(payload) { select() }?.decodeAs<List<CategoryDto>>()?.firstOrNull()
                created
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to create category", e)
                null
            }
        }
    }

    suspend fun updateCategory(id: String, name: String) {
        withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client?.postgrest?.get("categories")?.update(mapOf("name" to name)) { filter { eq("id", id) } }
                Logger.info(TAG, "Updated category $id")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to update category $id", e)
            }
        }
    }

    suspend fun deleteCategory(id: String) {
        withContext(Dispatchers.IO) {
            try {
                SupabaseManager.client?.postgrest?.get("categories")?.delete { filter { eq("id", id) } }
                Logger.info(TAG, "Deleted category $id")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to delete category $id", e)
            }
        }
    }
}




