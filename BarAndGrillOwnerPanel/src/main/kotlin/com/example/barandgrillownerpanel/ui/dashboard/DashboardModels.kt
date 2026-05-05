package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.runtime.Stable
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.models.CreditDto
import com.example.barandgrillownerpanel.models.InventoryItemDto
import com.example.barandgrillownerpanel.models.IngredientDto

@Stable
data class InventoryItem(
    val id: String,
    val name: String,
    val category: String,
    val subcategory: String = "",
    val currentStock: Double,
    val capacity: Double,
    val lowStockThreshold: Double,
    val unit: String,
    val unitCost: Double,
    val retailPrice: Double,
    val isPortionTracked: Boolean = false,
    val portionsPerUnit: Double? = null,
    val linkedMenuItemName: String? = null,
    val soldByShot: Boolean = false,
    val bottleVolumeMl: Double? = null,
    val shotSizeMl: Double? = null,
    val status: String = "AVAILABLE", // AVAILABLE, RENTED, MAINTENANCE
    val branchId: String? = null
)

@Stable
data class DesktopMenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val subcategory: String,
    val branchId: String? = null,
    val ingredients: List<IngredientDto>? = null
)

enum class InventoryCreditKind {
    /** New row inserted; stock is on [InventoryItemDto.stock_quantity]. */
    NEW_INBOUND,
    /** Existing row: add [quantityDelta] to stock, update cost only. */
    EXISTING_INBOUND,
    /** Existing row: subtract [quantityDelta] from stock; credit is GIVEN. */
    EXISTING_OUTBOUND
}

data class CreditInventorySubmission(
    val credit: CreditDto,
    val inventoryItem: InventoryItemDto,
    val kind: InventoryCreditKind,
    val existingInventoryId: String?,
    val quantityDelta: Double,
    val cloneBranches: Set<BranchDto> = emptySet()
)

sealed interface CreditFormSubmission {
    data class Other(val credit: CreditDto) : CreditFormSubmission
    data class Inventory(val payload: CreditInventorySubmission) : CreditFormSubmission
}
