package com.cemupad.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsCodecTest {

    @Test
    fun `missing values default to overlay off aspect fit and native resolution`() {
        val settings = AppSettingsCodec.decode(
            fitModeName = null,
            resolutionName = null,
            diagnosticsOverlayEnabled = null
        )

        assertEquals(DisplayFitMode.ASPECT_FIT, settings.fitMode)
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, settings.resolutionPreset)
        assertFalse(settings.diagnosticsOverlayEnabled)
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val settings = AppSettingsCodec.decode(
            fitModeName = "ORIGINAL_WII_U",
            resolutionName = "4K",
            diagnosticsOverlayEnabled = true
        )

        assertEquals(DisplayFitMode.ASPECT_FIT, settings.fitMode)
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, settings.resolutionPreset)
        assertTrue(settings.diagnosticsOverlayEnabled)
    }

    @Test
    fun `round trip preserves fit mode resolution and overlay`() {
        val original = DisplaySettings(
            fitMode = DisplayFitMode.FILL,
            resolutionPreset = DisplayResolutionPreset.FULL_HD_1920x1080,
            diagnosticsOverlayEnabled = true
        )

        val encoded = AppSettingsCodec.encode(original)
        val restored = AppSettingsCodec.decode(
            fitModeName = encoded.fitModeName,
            resolutionName = encoded.resolutionName,
            diagnosticsOverlayEnabled = encoded.diagnosticsOverlayEnabled
        )

        assertEquals(original, restored)
    }
}
