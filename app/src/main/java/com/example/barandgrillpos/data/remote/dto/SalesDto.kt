package com.example.barandgrillpos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SaleDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("sold_by") val soldBy: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("branch_id") val branchId: String? = null
)

@Serializable
data class SaleItemDto(
    @SerialName("sale_id") val saleId: String, // UUID from sales table
    @SerialName("name") val name: String,
    @SerialName("price") val price: Double,
    @SerialName("quantity") val quantity: Int,
    @SerialName("category") val category: String
)
