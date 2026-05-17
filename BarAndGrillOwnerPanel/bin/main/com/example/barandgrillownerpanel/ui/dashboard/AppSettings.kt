package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.runtime.Stable
import com.example.barandgrillownerpanel.models.BranchDto
import kotlinx.serialization.Serializable

@Stable
data class AppSettings(
    val businessName: String = "KLIX ENTERPRISE POS",
    val phoneNumber: String = "+1 234 567 890",
    val email: String = "hello@klix.com",
    val address: String = "123 Business Blvd",
    val country: String = "Malawi",
    val currencyCode: String = "MWK",
    val currencySymbol: String = "MK",
    val paymentMethods: List<PaymentMethod> = listOf(
        PaymentMethod("CASH"),
        PaymentMethod("MOBILE_MONEY", "Airtel Money"),
        PaymentMethod("MOBILE_MONEY", "TNM Mpamba"),
        PaymentMethod("BANK_TRANSFER", "Standard Bank")
    ),
    val dailySalesGoal: Double = 1500000.0,
    val lowStockThresholdPercent: Double = 20.0,
    val adminPin: String = "0000",
    val managerPin: String = "1234",
    val primaryColorHex: String = "#FF9800",
    val companyLogoUrl: String? = null,
    val lockTimeoutMinutes: Int = 0,
    val branches: List<BranchDto> = emptyList()
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
