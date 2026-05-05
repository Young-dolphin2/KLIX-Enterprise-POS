package com.example.barandgrillownerpanel.utils

import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

enum class LogLevel { INFO, WARN, ERROR, FATAL }

@Serializable
data class SystemLogDto(
    val level: String,
    val tag: String,
    val message: String,
    val stack_trace: String? = null
)

object Logger {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) = 
        log(LogLevel.ERROR, tag, message, throwable)
    fun fatal(tag: String, message: String, throwable: Throwable? = null) = 
        log(LogLevel.FATAL, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        // 1. Console Output
        val color = when(level) {
            LogLevel.INFO -> "\u001B[32m"
            LogLevel.WARN -> "\u001B[33m"
            LogLevel.ERROR, LogLevel.FATAL -> "\u001B[31m"
        }
        val reset = "\u001B[0m"
        println("$color[${level.name}] [$tag] $message$reset")
        throwable?.printStackTrace()

        // 2. Supabase Output (Async)
        scope.launch {
            try {
                val logDto = SystemLogDto(
                    level = level.name,
                    tag = tag,
                    message = message,
                    stack_trace = throwable?.stackTraceToString()
                )
                SupabaseManager.client.postgrest["system_logs"].insert(logDto)
            } catch (e: Exception) {
                // Silently fail if Supabase is offline to avoid recursive logging issues
                System.err.println("Failed to push log to Supabase: ${e.message}")
            }
        }
    }
}
