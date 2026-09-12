package com.cemupad.util

import android.util.Log

/**
 * Safe logging abstraction that logs via android.util.Log on Android
 * and falls back cleanly to standard streams when running under plain JVM unit tests.
 */
object Logger {
    fun i(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] INFO: $msg")
        }
    }

    fun w(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] WARN: $msg")
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
    }
}
