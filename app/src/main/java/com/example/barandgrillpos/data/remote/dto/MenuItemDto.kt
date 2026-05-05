package com.example.barandgrillpos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IngredientDto(
    @SerialName("inventory_name") val inventory_name: String,
    @SerialName("quantity") val quantity: Double = 1.0
)

@Serializable
data class MenuItemDto(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val subcategory: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("ingredients") val ingredients: List<IngredientDto>? = null
)

