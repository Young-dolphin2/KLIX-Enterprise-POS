package com.example.barandgrillownerpanel.subscription

import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.utils.Logger
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.awt.Desktop

// ============================================================
// DATA MODELS
// ============================================================

enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    NONE,
    PENDING,
    CHECKING,
    ERROR
}

enum class SubscriptionPlan(val displayName: String, val monthlyMwK: Int, val yearlyMwK: Int) {
    PRO("Pro", 15000, 150000),
    ENTERPRISE("Enterprise", 50000, 500000)
}

enum class PaymentGateway(val displayName: String, val currency: String) {
    PAYCHANGU("PayChangu (MWK)", "MWK"),
    FLUTTERWAVE("Flutterwave (USD)", "USD")
}

enum class BillingInterval(val displayName: String) {
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

@Serializable
data class SubscriptionDto(
    val id: String = "",
    val tenant_id: String = "",
    val plan: String = "free",
    val status: String = "pending",
    val current_period_start: String = "",
    val current_period_end: String = "",
    val trial_end: String? = null,
    val cancelled_at: String? = null
)

@Serializable
data class CheckoutRequest(
    val gateway: String,
    val plan: String,
    val interval: String,
    val tenant_id: String,
    val email: String,
    val phone: String = "",
    val business_name: String = ""
)

@Serializable
data class CheckoutResponse(
    val checkout_url: String = "",
    val tx_ref: String = "",
    val amount: Double = 0.0,
    val currency: String = "MWK",
    val transaction_id: String? = null
)

data class SubscriptionInfo(
    val status: SubscriptionStatus,
    val plan: SubscriptionPlan? = null,
    val currentPeriodEnd: String? = null,
    val daysRemaining: Int = 0
)

// ============================================================
// SUBSCRIPTION MANAGER
// ============================================================

object SubscriptionManager {
    private const val TAG = "SUBSCRIPTION"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check the current subscription status for the logged-in user.
     */
    suspend fun checkStatus(tenantId: String): SubscriptionInfo {
        return try {
            val response = SupabaseManager.client?.postgrest?.get("subscriptions")?.select {
                    filter { eq("tenant_id", tenantId) }
                    filter { eq("status", "active") }
                    limit(1)
                }
                ?.decodeAs<List<SubscriptionDto>>() ?: emptyList()

            if (response.isEmpty()) {
                Logger.info(TAG, "No subscription found for tenant: $tenantId")
                return SubscriptionInfo(SubscriptionStatus.NONE)
            }

            val sub = response.first()
            Logger.info(TAG, "Subscription status: ${sub.status} for tenant: $tenantId")

            when (sub.status) {
                "active" -> {
                    val plan = try { SubscriptionPlan.valueOf(sub.plan.uppercase()) } catch (_: Exception) { null }
                    val daysRemaining = try {
                        val end = java.time.OffsetDateTime.parse(sub.current_period_end)
                        val now = java.time.OffsetDateTime.now()
                        java.time.Duration.between(now, end).toDays().coerceAtLeast(0).toInt()
                    } catch (_: Exception) { 0 }

                    SubscriptionInfo(
                        status = if (daysRemaining > 0) SubscriptionStatus.ACTIVE else SubscriptionStatus.EXPIRED,
                        plan = plan,
                        currentPeriodEnd = sub.current_period_end,
                        daysRemaining = daysRemaining
                    )
                }
                "pending" -> SubscriptionInfo(SubscriptionStatus.PENDING)
                "expired", "cancelled" -> SubscriptionInfo(SubscriptionStatus.EXPIRED)
                else -> SubscriptionInfo(SubscriptionStatus.NONE)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to check subscription status", e)
            SubscriptionInfo(SubscriptionStatus.ERROR)
        }
    }

    /**
     * Call the Supabase Edge Function to create a checkout session.
     * Uses REST API directly via postgrest RPC approach.
     */
    suspend fun createCheckout(
        gateway: PaymentGateway,
        plan: SubscriptionPlan,
        interval: BillingInterval,
        tenantId: String,
        email: String,
        phone: String = "",
        businessName: String = ""
    ): Result<CheckoutResponse> {
        return try {
            val request = CheckoutRequest(
                gateway = gateway.name.lowercase(),
                plan = plan.name.lowercase(),
                interval = interval.name.lowercase(),
                tenant_id = tenantId,
                email = email,
                phone = phone,
                business_name = businessName
            )

            // Make HTTP request to Edge Function
            val url = "${SupabaseManager.getUrl()}/functions/v1/create-checkout"
            val anonKey = SupabaseManager.getAnonKey()
            
            val response = java.net.HttpURLConnection::class.java
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $anonKey")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                val jsonBody = json.encodeToString(CheckoutRequest.serializer(), request)
                connection.outputStream.write(jsonBody.toByteArray())
                
                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                }
                connection.disconnect()
                Pair(responseCode, responseBody)
            }
            
            if (result.first == 200) {
                val checkoutResponse = json.decodeFromString(CheckoutResponse.serializer(), result.second)
                Logger.info(TAG, "Checkout created: ${checkoutResponse.tx_ref} for ${checkoutResponse.amount} ${checkoutResponse.currency}")
                Result.success(checkoutResponse)
            } else {
                throw Exception("Edge Function returned ${result.first}: ${result.second}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create checkout", e)
            Result.failure(e)
        }
    }

    /**
     * Open the checkout URL in the default browser.
     */
    fun openCheckoutUrl(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                Logger.info(TAG, "Opened checkout URL in browser: $url")
                true
            } else {
                Logger.warn(TAG, "Desktop browsing not supported, URL: $url")
                false
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to open browser for checkout", e)
            false
        }
    }

    /**
     * Format price for display.
     */
    fun formatPrice(amount: Int, currency: String = "MWK"): String {
        val symbol = when (currency) {
            "MWK" -> "MK"
            "USD" -> "$"
            else -> currency
        }
        return "$symbol ${String.format("%,.0f", amount.toDouble())}"
    }
}




