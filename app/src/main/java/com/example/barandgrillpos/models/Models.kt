package com.example.barandgrillpos.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    val inventory_name: String,
    val quantity: Double = 1.0
)

@Serializable
@Immutable
data class MenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val subcategory: String,
    @kotlinx.serialization.SerialName("branch_id") val branchId: String? = null,
    val barcode: String? = null,
    val ingredients: List<Ingredient>? = null
)

@Serializable
@Stable
data class OrderItem(
    val item: MenuItem,
    val quantity: Int = 1
)

@Serializable
data class SaleRecord(
    val id: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val paymentMethod: String,
    val soldBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val branchId: String? = null
)

enum class Screen { POS, STATS, EMPLOYEE_PROFILE, EMPLOYEE_LOGIN, ONBOARDING, LOGIN, SIGN_UP }

@Serializable
data class BranchRef(
    @kotlinx.serialization.SerialName("id") val id: String,
    @kotlinx.serialization.SerialName("name") val name: String,
    @kotlinx.serialization.SerialName("type") val type: String = "RETAIL",
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null
)

enum class PrinterStatus {
    READY,
    DISCONNECTED,
    BLUETOOTH_OFF
}

@Serializable
data class Employee(
    val id: String? = null,
    val name: String,
    val role: String,
    val pin: String? = null,
    val is_active: Boolean = true
)

@Serializable
data class PaymentMethod(
    val type: String, // CASH, BANK_TRANSFER, CHEQUE, POS, MOBILE_MONEY
    val operatorName: String? = null // e.g. "Airtel Money"
) {
    override fun toString(): String {
        return if (operatorName != null) "$operatorName ($type)" else type
    }
}

@Serializable
data class AppSettingsDto(
    val business_name: String = "KLIX",
    val country: String = "Malawi",
    val currency_symbol: String = "MK",
    val primary_color_hex: String = "#FF9800",
    val payment_options: List<PaymentMethod> = emptyList()
)
