package com.example.barandgrillownerpanel.utils

import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class LogLevel { INFO, WARN, ERROR, FATAL }

@Serializable
data class SystemLogDto(
    val level: String,
    val tag: String,
    val message: String,
    val stack_trace: String? = null
)

object Logger {
    var isRemoteLoggingEnabled = false
    private val logFile = File("klix-desktop.log")
    private val fileLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            logFile.parentFile?.mkdirs()
        } catch (_: Exception) {
            // ignore
        }
    }

    // Redaction patterns
    private val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val ccRegex = Regex("\\b(?:\\d[ -]*?){13,16}\\b")

    private fun redact(message: String): String {
        return message
            .replace(emailRegex, "***@***.***").replace(ccRegex, "****-****-****-****")
    }

    private fun truncateStackTrace(throwable: Throwable?): String {
        if (throwable == null) return ""
        val stack = throwable.stackTraceToString()
        val lines = stack.lines()
        return if (lines.size > 15) {
            lines.take(15).joinToString("\n") + "\n... [truncated]"
        } else {
            stack
        }
    }

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
        val redactedMessage = redact(message)
        val stack = truncateStackTrace(throwable)
        if (stack.isNotEmpty()) {
            println("$color[${level.name}] [$tag] $redactedMessage\n$stack$reset")
        } else {
            println("$color[${level.name}] [$tag] $redactedMessage$reset")
        }

        writeToFile(level, tag, redactedMessage, stack)

        if (!isRemoteLoggingEnabled) return

        // 2. Supabase Output (Async)
        scope.launch {
            try {
                val logDto = SystemLogDto(
                    level = level.name,
                    tag = tag,
                    message = redact(message),
                    stack_trace = truncateStackTrace(throwable)
                )
                SupabaseManager.client?.postgrest?.get("system_logs")?.insert(logDto)
            } catch (e: Exception) {
                // Silently fail if Supabase is offline to avoid recursive logging issues
                System.err.println("Failed to push log to Supabase: ${e.message}")
            }
        }
    }

    private fun writeToFile(level: LogLevel, tag: String, message: String, stackTrace: String?) {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val entry = buildString {
            append("[$timestamp] [${level.name}] [$tag] $message")
            if (!stackTrace.isNullOrEmpty()) {
                append("\n$stackTrace")
            }
            append(System.lineSeparator())
        }
        synchronized(fileLock) {
            try {
                logFile.appendText(entry)
            } catch (e: IOException) {
                System.err.println("Failed to write desktop log file: ${e.message}")
            }
        }
    }
}


