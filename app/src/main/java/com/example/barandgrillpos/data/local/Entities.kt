package com.example.barandgrillpos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.barandgrillpos.models.OrderItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val id: String,
    val itemsJson: String,
    val totalAmount: Double,
    val paymentMethod: String,
    val soldBy: String = "",
    val timestamp: Long,
    val branchId: String? = null,
    val isSynced: Boolean = false
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val queueId: Int = 0,
    val action: String, // e.g., "INSERT_SALE"
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "menu_items_cache",
    indices = [
        Index(value = ["branchId"]),
        Index(value = ["isActive"])
    ]
)
data class MenuItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val subcategory: String,
    val branchId: String? = null,
    val isActive: Boolean = true,
    val ingredientsJson: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "employees_cache",
    indices = [
        Index(value = ["isActive"])
    ]
)
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val isActive: Boolean = true,
    val pinHash: String? = null, // Hashed 4-digit PIN
    val updatedAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromOrderItemList(value: List<OrderItem>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toOrderItemList(value: String): List<OrderItem> {
        return Json.decodeFromString(value)
    }
}

@Entity(
    tableName = "inventory_cache",
    indices = [
        Index(value = ["branchId"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stock_quantity: Double,
    val min_threshold: Double,
    val status: String = "AVAILABLE",
    val branchId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories_cache")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val parentName: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customers_cache",
    indices = [
        Index(value = ["branchId"]),
        Index(value = ["membershipStatus"])
    ]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val idType: String? = null,
    val idNumber: String? = null,
    val profileImageUrl: String? = null,
    val membershipStatus: String = "ACTIVE",
    val membershipExpiry: String? = null,
    val notes: String? = null,
    val branchId: String? = null,
    val createdAt: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
