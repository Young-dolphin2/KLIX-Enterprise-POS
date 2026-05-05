package com.example.barandgrillpos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InventoryItemDto(
    val id: String? = null,
    val name: String,
    val stock_quantity: Double,
    val min_threshold: Double,
    val status: String = "AVAILABLE",
    @SerialName("branch_id") val branchId: String? = null
)
