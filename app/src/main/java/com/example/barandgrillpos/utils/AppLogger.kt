package com.example.barandgrillpos.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val PREFIX = "KLIX"
    private const val LOG_FILE_NAME = "klix_crash.log"

    private var logFile: File? = null

    fun initialize(context: Context) {
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile?.parentFile?.mkdirs()
        } catch (e: Exception) {
            Log.w("$PREFIX/AppLogger", "Unable to initialize log file", e)
        }
    }

    fun i(tag: String, message: String) {
        Log.i("$PREFIX/$tag", message)
        writeToFile("INFO", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$PREFIX/$tag", message, throwable)
        } else {
            Log.w("$PREFIX/$tag", message)
        }
        writeToFile("WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$PREFIX/$tag", message, throwable)
        } else {
            Log.e("$PREFIX/$tag", message)
        }
        writeToFile("ERROR", tag, message, throwable)
    }

    private fun writeToFile(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
        val entry = buildString {
            append("[$timestamp] [$level] [$tag] $message")
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
            append(System.lineSeparator())
        }
        try {
            file.appendText(entry)
        } catch (e: IOException) {
            Log.w("$PREFIX/AppLogger", "Unable to write log file", e)
        }
    }
}
