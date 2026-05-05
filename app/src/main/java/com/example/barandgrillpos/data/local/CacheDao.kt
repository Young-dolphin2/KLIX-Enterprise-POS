package com.example.barandgrillpos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    // --- Menu cache ---
    @Query("SELECT * FROM menu_items_cache WHERE isActive = 1 ORDER BY category ASC, subcategory ASC, name ASC")
    fun observeActiveMenu(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items_cache WHERE isActive = 1 AND (branchId IS NULL OR branchId = :branchId) ORDER BY category ASC, subcategory ASC, name ASC")
    fun observeActiveMenuForBranch(branchId: String?): Flow<List<MenuItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenu(items: List<MenuItemEntity>)

    @Query("UPDATE menu_items_cache SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markMenuInactive(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM menu_items_cache")
    suspend fun clearMenu()

    // --- Employees cache ---
    @Query("SELECT * FROM employees_cache WHERE isActive = 1 ORDER BY name ASC")
    fun observeActiveEmployees(): Flow<List<EmployeeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmployees(items: List<EmployeeEntity>)

    @Query("DELETE FROM employees_cache")
    suspend fun clearEmployees()

    @Query("UPDATE employees_cache SET pinHash = :pinHash WHERE id = :id")
    suspend fun updateEmployeePin(id: String, pinHash: String)

    @Query("UPDATE employees_cache SET pinHash = :pinHash WHERE name = :name")
    suspend fun updateEmployeePinByName(name: String, pinHash: String)

    // --- Inventory cache ---
    @Query("SELECT * FROM inventory_cache ORDER BY name ASC")
    fun observeInventory(): Flow<List<InventoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventory(items: List<InventoryItemEntity>)

    @Query("DELETE FROM inventory_cache")
    suspend fun clearInventory()

    // --- Categories cache ---
    @Query("SELECT * FROM categories_cache ORDER BY parentName ASC, name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<CategoryEntity>)

    @Query("DELETE FROM categories_cache")
    suspend fun clearCategories()

    // --- Customers cache ---
    @Query("SELECT * FROM customers_cache ORDER BY name ASC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(items: List<CustomerEntity>)

    @Query("DELETE FROM customers_cache")
    suspend fun clearCustomers()
}
