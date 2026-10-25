package com.example.minedtv.util

import android.util.Log

/** App-wide logging. Filter Logcat with `tag:MinedIt`. */
object Logger {
    private const val TAG = "MinedIt"

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String) = Log.w(TAG, message)
    fun e(message: String, error: Throwable? = null) = Log.e(TAG, message, error)
}
