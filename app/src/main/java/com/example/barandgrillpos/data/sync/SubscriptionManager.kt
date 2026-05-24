package com.example.barandgrillpos.data.sync

import com.example.barandgrillpos.data.remote.SupabaseManager
import com.example.barandgrillpos.utils.AppLogger
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    NONE,
    PENDING,
    ERROR
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

data class SubscriptionInfo(
    val status: SubscriptionStatus,
    val plan: String? = null,
    val currentPeriodEnd: String? = null,
    val daysRemaining: Int = 0
)

object SubscriptionManager {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkStatus(tenantId: String): SubscriptionInfo {
        return try {
            val response = SupabaseManager.client.postgrest["subscriptions"]
                .select {
                    filter { eq("tenant_id", tenantId) }
                    filter { eq("status", "active") }
                    limit(1)
                }
                .decodeAs<List<SubscriptionDto>>()

            if (response.isEmpty()) {
                return SubscriptionInfo(SubscriptionStatus.NONE)
            }

            val sub = response.first()

            when (sub.status) {
                "active" -> {
                    val daysRemaining = try {
                        val end = java.time.OffsetDateTime.parse(sub.current_period_end)
                        val now = java.time.OffsetDateTime.now()
                        java.time.Duration.between(now, end).toDays().coerceAtLeast(0).toInt()
                    } catch (_: Exception) { 0 }

                    SubscriptionInfo(
                        status = if (daysRemaining > 0) SubscriptionStatus.ACTIVE else SubscriptionStatus.EXPIRED,
                        plan = sub.plan,
                        currentPeriodEnd = sub.current_period_end,
                        daysRemaining = daysRemaining
                    )
                }
                "pending" -> SubscriptionInfo(SubscriptionStatus.PENDING)
                "expired", "cancelled" -> SubscriptionInfo(SubscriptionStatus.EXPIRED)
                else -> SubscriptionInfo(SubscriptionStatus.NONE)
            }
        } catch (e: Exception) {
            AppLogger.e("SubscriptionManager", "Subscription status fetch failed", e)
            SubscriptionInfo(SubscriptionStatus.ERROR)
        }
    }
}
