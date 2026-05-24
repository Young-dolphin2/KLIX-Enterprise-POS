package com.example.barandgrillownerpanel.utils

import kotlinx.coroutines.delay
import kotlin.random.Random
import java.io.IOException

enum class SupabaseError {
    SERVER_ERROR,    // 5xx — Supabase down
    AUTH_EXPIRED,    // 401 — token expired
    NOT_FOUND,       // 404 — data missing
    TIMEOUT,         // Connection timeout
    UNKNOWN          // Other
}

object NetworkUtils {
    private var circuitTripped = false
    private var trippedAtMillis = 0L
    private const val RESET_TIMEOUT_MILLIS = 30000L // 30 seconds

    /**
     * Classify a Supabase error for appropriate user feedback.
     */
    fun classifyError(e: Exception): SupabaseError {
        val message = e.message ?: ""
        return when {
            message.contains("401") || message.contains("Unauthorized") || message.contains("JWT") -> SupabaseError.AUTH_EXPIRED
            message.contains("404") || message.contains("Not Found") -> SupabaseError.NOT_FOUND
            message.contains("500") || message.contains("502") || message.contains("503") || 
            message.contains("50") || message.contains("Server Error") -> SupabaseError.SERVER_ERROR
            message.contains("timeout", ignoreCase = true) || 
            message.contains("Timeout", ignoreCase = true) ||
            e is java.util.concurrent.TimeoutException -> SupabaseError.TIMEOUT
            message.contains("timeout", ignoreCase = true) || 
            message.contains("Timed out") -> SupabaseError.TIMEOUT
            e is IOException -> SupabaseError.TIMEOUT // Network error = treat as timeout
            else -> SupabaseError.UNKNOWN
        }
    }

    /**
     * Executes a network-related block with exponential backoff retry.
     */
    suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelayMillis: Long = 1000,
        maxDelayMillis: Long = 10000,
        factor: Double = 2.0,
        tag: String = "NETWORK",
        block: suspend () -> T
    ): T {
        if (circuitTripped) {
            if (System.currentTimeMillis() - trippedAtMillis > RESET_TIMEOUT_MILLIS) {
                circuitTripped = false
                Logger.info(tag, "Circuit breaker reset.")
            } else {
                throw IOException("Circuit breaker is open. Fast failing request.")
            }
        }

        var currentDelay = initialDelayMillis
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                circuitTripped = false
                return result
            } catch (e: Exception) {
                val isTransient = e is IOException || 
                                  e is java.util.concurrent.TimeoutException ||
                                  (e.message?.contains("Timeout", ignoreCase = true) == true) ||
                                  (e.message?.contains("50", ignoreCase = true) == true)

                if (!isTransient) {
                    Logger.error(tag, "Non-transient error, aborting retries: ${e.message}", e)
                    throw e
                }

                val isLastAttempt = attempt == maxRetries - 1
                if (isLastAttempt) {
                    Logger.error(tag, "Max retries reached. Tripping circuit breaker. Final error: ${e.message}", e)
                    circuitTripped = true
                    trippedAtMillis = System.currentTimeMillis()
                    throw e
                }
                
                val delayMillis = Random.nextLong(currentDelay / 2, currentDelay + 1)
                Logger.warn(tag, "Attempt ${attempt + 1} failed (transient): ${e.message}. Retrying in ${delayMillis}ms...")
                delay(delayMillis)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
            }
        }
        return block()
    }
}
