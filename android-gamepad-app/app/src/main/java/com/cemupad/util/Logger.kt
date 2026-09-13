package com.cemupad.util

import android.util.Log

/**
 * Safe logging abstraction that logs via android.util.Log on Android
 * and falls back cleanly to standard streams when running under plain JVM unit tests.
 */
object Logger {
    fun d(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] DEBUG: $msg")
        }
    }

    fun v(tag: String, msg: String) {
        try {
            Log.v(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] VERBOSE: $msg")
        }
    }

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
