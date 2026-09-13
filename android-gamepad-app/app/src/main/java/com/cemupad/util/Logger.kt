package com.cemupad.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Safe logging abstraction that logs via android.util.Log on Android
 * and falls back cleanly to standard streams when running under plain JVM unit tests.
 *
 * Call [init] once (e.g. from the main activity) to also mirror every line
 * into a bounded on-disk file for the debug-bundle export.
 */
object Logger {
    @Volatile
    private var sink: FileLogSink? = null

    fun init(context: Context) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            sink = FileLogSink(dir)
            i("Logger", "File logging to ${dir.absolutePath}")
        } catch (_: Exception) {
        }
    }

    /** Test/seam hook: direct the mirror at a custom sink (or null to disable). */
    fun setSinkForTesting(custom: FileLogSink?) {
        sink = custom
    }

    fun logFiles(): List<File> = sink?.files() ?: emptyList()

    private fun mirror(level: Char, tag: String, msg: String, tr: Throwable? = null) {
        try {
            sink?.write(level, tag, msg, tr)
        } catch (_: Throwable) {
        }
    }

    fun d(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] DEBUG: $msg")
        }
        mirror('D', tag, msg)
    }

    fun v(tag: String, msg: String) {
        try {
            Log.v(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] VERBOSE: $msg")
        }
        mirror('V', tag, msg)
    }

    fun i(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] INFO: $msg")
        }
        mirror('I', tag, msg)
    }

    fun w(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] WARN: $msg")
        }
        mirror('W', tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
        mirror('E', tag, msg, tr)
    }
}
