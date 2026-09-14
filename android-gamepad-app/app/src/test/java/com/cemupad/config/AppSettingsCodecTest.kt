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
        assertFalse(settings.limitTo30Fps)
        assertFalse(settings.showVirtualControls)
        assertEquals(0.5f, settings.virtualControlsOpacity, 0.001f)
        assertTrue(settings.audioEnabled)
        assertEquals(1.0f, settings.audioVolume, 0.001f)
        assertTrue(settings.vibrationEnabled)
        assertEquals(1.0f, settings.vibrationIntensity, 0.001f)
        assertTrue(settings.micEnabled)
        assertEquals(FramePacingMode.IMMEDIATE, settings.framePacing)
        assertEquals(VideoCodecPreference.AUTO, settings.videoCodec)
        assertEquals(6, settings.videoBitrateMbps)
    }

    @Test
    fun `unknown bitrate falls back to 6 Mbps`() {
        val settings = AppSettingsCodec.decode(
            fitModeName = null,
            resolutionName = null,
            videoBitrateMbps = 99,
            diagnosticsOverlayEnabled = null
        )

        assertEquals(6, settings.videoBitrateMbps)
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val settings = AppSettingsCodec.decode(
            fitModeName = "ORIGINAL_WII_U",
            resolutionName = "4K",
            diagnosticsOverlayEnabled = true,
            connectionHelpVisible = false,
            limitTo30Fps = false,
            framePacingName = "TURBO",
            videoCodecName = "AV1_UNKNOWN"
        )

        assertEquals(DisplayFitMode.ASPECT_FIT, settings.fitMode)
        assertEquals(DisplayResolutionPreset.NATIVE_854x480, settings.resolutionPreset)
        assertTrue(settings.diagnosticsOverlayEnabled)
        assertFalse(settings.showConnectionHelp)
        assertFalse(settings.limitTo30Fps)
        assertTrue(settings.micEnabled)
        assertEquals(FramePacingMode.IMMEDIATE, settings.framePacing)
        assertEquals(VideoCodecPreference.AUTO, settings.videoCodec)
    }

    @Test
    fun `round trip preserves fit mode resolution bitrate overlay help and fps cap`() {
        val original = DisplaySettings(
            fitMode = DisplayFitMode.FILL,
            resolutionPreset = DisplayResolutionPreset.FULL_HD_1920x1080,
            videoBitrateMbps = 12,
            diagnosticsOverlayEnabled = true,
            showConnectionHelp = false,
            limitTo30Fps = false,
            showVirtualControls = true,
            virtualControlsOpacity = 0.85f,
            audioEnabled = false,
            audioVolume = 0.42f,
            vibrationEnabled = false,
            vibrationIntensity = 0.35f,
            micEnabled = false,
            framePacing = FramePacingMode.VSYNC,
            videoCodec = VideoCodecPreference.HEVC
        )

        val encoded = AppSettingsCodec.encode(original)
        val restored = AppSettingsCodec.decode(
            fitModeName = encoded.fitModeName,
            resolutionName = encoded.resolutionName,
            videoBitrateMbps = encoded.videoBitrateMbps,
            diagnosticsOverlayEnabled = encoded.diagnosticsOverlayEnabled,
            connectionHelpVisible = encoded.connectionHelpVisible,
            limitTo30Fps = encoded.limitTo30Fps,
            showVirtualControls = encoded.showVirtualControls,
            virtualControlsOpacity = encoded.virtualControlsOpacity,
            audioEnabled = encoded.audioEnabled,
            audioVolume = encoded.audioVolume,
            vibrationEnabled = encoded.vibrationEnabled,
            vibrationIntensity = encoded.vibrationIntensity,
            stickDeadzone = encoded.stickDeadzone,
            micEnabled = encoded.micEnabled,
            framePacingName = encoded.framePacingName,
            videoCodecName = encoded.videoCodecName
        )

        assertEquals(original, restored)
    }
}
