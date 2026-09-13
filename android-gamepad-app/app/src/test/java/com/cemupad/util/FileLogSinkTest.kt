package com.cemupad.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileLogSinkTest {

    private fun tempDir(): File = Files.createTempDirectory("filelogsink").toFile()

    @Test
    fun testWriteAndRead() {
        val sink = FileLogSink(tempDir())
        sink.write('I', "Test", "hello")
        sink.write('E', "Test", "boom", RuntimeException("kablam"))

        val all = sink.readAll()
        assertTrue(all.contains("I/Test: hello"))
        assertTrue(all.contains("E/Test: boom"))
        assertTrue(all.contains("kablam"))
        assertEquals(1, sink.files().size)
    }

    @Test
    fun testRotationKeepsBothGenerations() {
        val dir = tempDir()
        val sink = FileLogSink(dir, maxBytesPerFile = 64L)
        for (i in 0 until 50) {
            sink.write('I', "T", "message number $i padding padding padding")
        }
        // Rotation engaged: exactly the two bounded generations on disk.
        assertEquals(2, sink.files().size)
        val all = sink.readAll()
        // ...and the newest write always survives rotation.
        assertTrue(all.contains("message number 49"))
    }

    @Test
    fun testMultilineFlattened() {
        val sink = FileLogSink(tempDir())
        sink.write('W', "T", "line1\nline2")
        val lines = sink.readAll().trim().split("\n")
        assertEquals(1, lines.size)
    }
}
