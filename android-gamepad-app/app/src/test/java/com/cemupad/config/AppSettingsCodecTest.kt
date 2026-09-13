package com.cemupad.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsCodecTest {

    @Test
    fun `missing values default to overlay off help on fps cap on aspect fit and native resolution`() {
        val settings = AppSettingsCodec.decode(
            fitModeName = null,
            resolutionName = null,
            diagnosticsOverlayEnabled = null,
            connectionHelpVisible = null,
            limitTo30Fps = null
        )

        assertEquals(DisplayFitMode.ASPECT_FIT, settings.fitMode)
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, settings.resolutionPreset)
        assertFalse(settings.diagnosticsOverlayEnabled)
        assertTrue(settings.showConnectionHelp)
        assertTrue(settings.limitTo30Fps)
        assertFalse(settings.showVirtualControls)
        assertEquals(0.5f, settings.virtualControlsOpacity, 0.001f)
        assertTrue(settings.audioEnabled)
        assertEquals(1.0f, settings.audioVolume, 0.001f)
        assertTrue(settings.vibrationEnabled)
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val settings = AppSettingsCodec.decode(
            fitModeName = "ORIGINAL_WII_U",
            resolutionName = "4K",
            diagnosticsOverlayEnabled = true,
            connectionHelpVisible = false,
            limitTo30Fps = false
        )

        assertEquals(DisplayFitMode.ASPECT_FIT, settings.fitMode)
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, settings.resolutionPreset)
        assertTrue(settings.diagnosticsOverlayEnabled)
        assertFalse(settings.showConnectionHelp)
        assertFalse(settings.limitTo30Fps)
    }

    @Test
    fun `round trip preserves fit mode resolution overlay help and fps cap`() {
        val original = DisplaySettings(
            fitMode = DisplayFitMode.FILL,
            resolutionPreset = DisplayResolutionPreset.FULL_HD_1920x1080,
            diagnosticsOverlayEnabled = true,
            showConnectionHelp = false,
            limitTo30Fps = false,
            showVirtualControls = true,
            virtualControlsOpacity = 0.85f,
            audioEnabled = false,
            audioVolume = 0.42f,
            vibrationEnabled = false
        )

        val encoded = AppSettingsCodec.encode(original)
        val restored = AppSettingsCodec.decode(
            fitModeName = encoded.fitModeName,
            resolutionName = encoded.resolutionName,
            diagnosticsOverlayEnabled = encoded.diagnosticsOverlayEnabled,
            connectionHelpVisible = encoded.connectionHelpVisible,
            limitTo30Fps = encoded.limitTo30Fps,
            showVirtualControls = encoded.showVirtualControls,
            virtualControlsOpacity = encoded.virtualControlsOpacity,
            audioEnabled = encoded.audioEnabled,
            audioVolume = encoded.audioVolume,
            vibrationEnabled = encoded.vibrationEnabled
        )

        assertEquals(original, restored)
    }
}
