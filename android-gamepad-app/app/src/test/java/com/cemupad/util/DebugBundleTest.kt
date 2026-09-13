package com.cemupad.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class DebugBundleTest {

    private fun sampleReport() = DeviceReport(
        manufacturer = "Razer",
        model = "Edge",
        device = "ling",
        board = "kalama",
        socModel = "Snapdragon G3x Gen 1",
        sdkInt = 34,
        androidRelease = "14",
        screenPx = "1080x2400 @440dpi",
        refreshHz = 144f,
        appVersion = "1.0 (1)",
        settingsSummary = "fit=ASPECT_FIT res=NATIVE_854x480 bitrate=6Mbps audio=true mic=true",
        avcDecoders = listOf("c2.qti.avc.decoder [hw]", "c2.android.avc.decoder [sw]")
    )

    @Test
    fun testFormatReportContainsKeyFields() {
        val text = DebugBundle.formatReport(sampleReport())
        assertTrue(text.contains("model=Edge"))
        assertTrue(text.contains("soc=Snapdragon G3x Gen 1"))
        assertTrue(text.contains("refresh=144.0Hz"))
        assertTrue(text.contains("c2.qti.avc.decoder [hw]"))
        assertTrue(text.contains("bitrate=6Mbps"))
    }

    @Test
    fun testBuildZipEntries() {
        val dir = Files.createTempDirectory("debugbundle").toFile()
        val zip = File(dir, "bundle.zip")
        DebugBundle.buildZip(
            output = zip,
            reportText = "report",
            logText = "log",
            screenshotPng = byteArrayOf(1, 2, 3)
        )

        ZipFile(zip).use { zipped ->
            val names = zipped.entries().toList().map { it.name }
            assertEquals(
                listOf("device-info.txt", "app-log.txt", "screenshot.png").sorted(),
                names.sorted()
            )
            val log = zipped.getInputStream(zipped.getEntry("app-log.txt")).readBytes()
            assertEquals("log", String(log, Charsets.UTF_8))
        }
    }

    @Test
    fun testBuildZipWithoutScreenshot() {
        val dir = Files.createTempDirectory("debugbundle").toFile()
        val zip = File(dir, "bundle.zip")
        DebugBundle.buildZip(zip, "report", "log", null)

        ZipFile(zip).use { zipped ->
            val names = zipped.entries().toList().map { it.name }
            assertTrue(names.contains("device-info.txt"))
            assertTrue(names.contains("app-log.txt"))
            assertTrue(!names.contains("screenshot.png"))
        }
    }
}
