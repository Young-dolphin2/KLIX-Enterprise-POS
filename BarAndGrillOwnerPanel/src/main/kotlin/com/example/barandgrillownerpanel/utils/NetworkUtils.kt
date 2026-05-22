package com.example.barandgrillownerpanel.utils

import kotlinx.coroutines.delay
import kotlin.random.Random
import java.io.IOException

object NetworkUtils {
    private var circuitTripped = false
    private var trippedAtMillis = 0L
    private const val RESET_TIMEOUT_MILLIS = 30000L // 30 seconds

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
                                  (e.message?.contains("50", ignoreCase = true) == true) // crude 5xx match
                
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
