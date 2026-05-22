package com.example.barandgrillownerpanel.utils

import kotlinx.coroutines.delay
import kotlin.random.Random
import java.io.IOException

object NetworkUtils {
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
        var currentDelay = initialDelayMillis
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // If it's a non-recoverable error (like auth failure), we might want to rethrow immediately.
                // But for now, we'll retry all exceptions as most in this app are network/timeout related.
                val isLastAttempt = attempt == maxRetries - 1
                if (isLastAttempt) {
                    Logger.error(tag, "Max retries reached. Final attempt failed: ${e.message}", e)
                    throw e
                }
                
                val delayMillis = Random.nextLong(currentDelay / 2, currentDelay + 1)
                Logger.warn(tag, "Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${delayMillis}ms...")
                delay(delayMillis)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
            }
        }
        return block() // Should never reach here if maxRetries > 0
    }
}
