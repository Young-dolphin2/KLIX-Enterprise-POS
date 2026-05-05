package com.example.barandgrillownerpanel.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SaleDto(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("payment_method") val paymentMethod: String = "Cash",
    @SerialName("sold_by") val soldBy: String = "",
    val timestamp: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("sale_items") val items: List<SaleItemDto> = emptyList()
)

@Serializable
data class SaleItemDto(
    @SerialName("sale_id") val saleId: String,
    val name: String,
    val price: Double,
    val quantity: Int,
    val category: String
)
