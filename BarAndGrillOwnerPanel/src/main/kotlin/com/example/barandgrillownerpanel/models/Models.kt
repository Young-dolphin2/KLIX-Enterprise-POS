package com.example.barandgrillownerpanel.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class MenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val subcategory: String
)

@Serializable
@Stable
data class SaleItem(
    val item: MenuItem,
    val quantity: Int = 1
)

@Serializable
data class SaleRecord(
    val id: String,
    val items: List<SaleItem>,
    val totalAmount: Double,
    val paymentMethod: String = "Cash",
    val soldBy: String = "",
    val branchId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class Employee(
    val id: String? = null,
    val name: String,
    val role: String,
    val pin: String? = null,
    val is_active: Boolean = true,
    @SerialName("branch_id") val branchId: String? = null
)

@Serializable
@Stable
data class BranchDto(
    val id: String? = null,
    val name: String,
    val type: String = "BAR",  // BAR | LIQUOR_SHOP
    val address: String? = null,
    val is_active: Boolean = true,
    @SerialName("parent_id") val parentId: String? = null
)

@Serializable
data class ExpenseDto(
    val id: String? = null,
    @SerialName("branch_id") val branchId: String? = null,
    val category: String,
    val description: String,
    val amount: Double,
    @SerialName("expense_date") val expenseDate: String? = null,
    val created_at: String? = null
)

@Serializable
data class InventoryItemDto(
    val id: String? = null,
    val name: String,
    val category: String,
    val subcategory: String = "",
    val stock_quantity: Double,
    val min_threshold: Double,
    val cost_price: Double,
    @SerialName("selling_price") val sellingPrice: Double,
    val unit: String = "Units",
    @SerialName("is_portion_tracked") val isPortionTracked: Boolean = false,
    @SerialName("portions_per_unit") val portionsPerUnit: Double? = null,
    @SerialName("linked_menu_item_name") val linkedMenuItemName: String? = null,
    @SerialName("sold_by_shot") val soldByShot: Boolean = false,
    @SerialName("bottle_volume_ml") val bottleVolumeMl: Double? = null,
    @SerialName("shot_size_ml") val shotSizeMl: Double? = null,
    val status: String = "AVAILABLE", // AVAILABLE, RENTED, MAINTENANCE
    @SerialName("branch_id") val branchId: String? = null,
    val created_at: String? = null
)

@Serializable
data class IngredientDto(
    val inventory_name: String,
    val quantity: Double = 1.0 // Usually 1 shot
)

@Serializable
data class MenuItemDto(
    val id: String? = null,
    val name: String,
    val price: Double,
    val category: String,
    val subcategory: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val ingredients: List<IngredientDto>? = null
)

@Serializable
data class CategoryDto(
    val id: String? = null,
    val name: String,
    @SerialName("parent_name") val parentName: String? = null,
    val created_at: String? = null
)

@Serializable
data class CategoryInsertDto(
    val name: String,
    @SerialName("parent_name") val parentName: String? = null
)

@Serializable
data class CustomerDto(
    val id: String? = null,
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    @SerialName("id_type") val idType: String? = null,
    @SerialName("id_number") val idNumber: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("membership_status") val membershipStatus: String = "ACTIVE",
    @SerialName("membership_expiry") val membershipExpiry: String? = null,
    val notes: String? = null,
    @SerialName("branch_id") val branchId: String? = null,
    val created_at: String? = null
)

@Serializable
data class AssetHistoryDto(
    val id: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("inventory_id") val inventoryId: String,
    @SerialName("action_type") val actionType: String, // CHECK_OUT, CHECK_IN
    @SerialName("action_timestamp") val actionTimestamp: String? = null,
    val notes: String? = null,
    @SerialName("fuel_level") val fuelLevel: String? = null,
    val mileage: Int? = null,
    @SerialName("damage_report") val damageReport: String? = null,
    @SerialName("performed_by") val performedBy: String? = null,
    @SerialName("branch_id") val branchId: String? = null
)

@Serializable
data class CreditDto(
    val id: String? = null,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("contact_name") val contactName: String,
    val description: String,
    val amount: Double,
    @SerialName("credit_type") val creditType: String, // "GIVEN" or "RECEIVED"
    @SerialName("is_settled") val isSettled: Boolean = false,
    @SerialName("settled_at") val settledAt: String? = null,
    val notes: String? = null,
    val created_at: String? = null
)

@Serializable
data class BranchRef(
    val id: String,
    val name: String
)
@Serializable
data class IngredientMenuPortionRow(
    val inventory_id: String,
    val menu_item_name: String,
    val portions_per_sale: Double,
    val branch_id: String? = null
)


