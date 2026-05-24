package com.example.barandgrillpos.utils

import android.util.Log

object AppLogger {
    private const val PREFIX = "KLIX"

    fun i(tag: String, message: String) {
        Log.i("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$PREFIX/$tag", message, throwable)
        } else {
            Log.w("$PREFIX/$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$PREFIX/$tag", message, throwable)
        } else {
            Log.e("$PREFIX/$tag", message)
        }
    }
}
