package com.cemupad.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded on-disk mirror for [Logger]. Keeps `baseName` plus one rotated
 * generation (`baseName.1`), each capped at [maxBytesPerFile]. All methods
 * are thread-safe. Pure-JVM friendly (plain [File] I/O) for unit tests.
 */
class FileLogSink(
    directory: File,
    private val baseName: String = "cemupad-log.txt",
    private val maxBytesPerFile: Long = 2L * 1024L * 1024L
) {
    private val lock = Any()
    private val primary = File(directory, baseName)
    private val rotated = File(directory, "$baseName.1")
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun write(level: Char, tag: String, message: String, throwable: Throwable? = null) {
        val line = buildString {
            append(dateFormat.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(message.replace("\n", " | "))
            if (throwable != null) {
                append(" <- ")
                append(throwable.toString().replace("\n", " | "))
            }
        }
        synchronized(lock) {
            try {
                if (primary.exists() && primary.length() >= maxBytesPerFile) {
                    rotate()
                }
                primary.appendText(line + "\n")
            } catch (_: Exception) {
            }
        }
    }

    fun readAll(): String {
        synchronized(lock) {
            return try {
                buildString {
                    if (rotated.exists()) append(rotated.readText())
                    if (primary.exists()) append(primary.readText())
                }
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun files(): List<File> {
        synchronized(lock) {
            return listOf(rotated, primary).filter { it.exists() }
        }
    }

    private fun rotate() {
        try {
            if (rotated.exists()) rotated.delete()
            primary.renameTo(rotated)
        } catch (_: Exception) {
            try {
                primary.delete()
            } catch (_: Exception) {
            }
        }
    }
}
