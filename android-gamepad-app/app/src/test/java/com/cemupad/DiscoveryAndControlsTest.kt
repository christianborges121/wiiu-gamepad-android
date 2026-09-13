package com.cemupad

import android.view.KeyEvent
import com.cemupad.config.AppSettingsCodec
import com.cemupad.config.DisplaySettings
import com.cemupad.dsu.DSUPacket
import com.cemupad.dsu.DSUServer
import com.cemupad.input.GamepadInputHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryAndControlsTest {

    @Test
    fun testVirtualControlsStickMapping() {
        val dsuServer = DSUServer()
        val handler = GamepadInputHandler(dsuServer)

        // Center stick: 0f, 0f -> lx=128, ly=128
        handler.setVirtualStick(isLeftStick = true, normX = 0f, normY = 0f)
        assertEquals(128, dsuServer.controllerState.lx)
        assertEquals(128, dsuServer.controllerState.ly)

        // Stick UP: normX=0f, normY=-1f (touch up) -> ly=255
        handler.setVirtualStick(isLeftStick = true, normX = 0f, normY = -1f)
        assertEquals(128, dsuServer.controllerState.lx)
        assertEquals(255, dsuServer.controllerState.ly)

        // Stick DOWN: normX=0f, normY=1f (touch down) -> ly=0
        handler.setVirtualStick(isLeftStick = true, normX = 0f, normY = 1f)
        assertEquals(128, dsuServer.controllerState.lx)
        assertEquals(0, dsuServer.controllerState.ly)

        // Stick RIGHT: normX=1f, normY=0f -> lx=255, ly=128
        handler.setVirtualStick(isLeftStick = true, normX = 1f, normY = 0f)
        assertEquals(255, dsuServer.controllerState.lx)
        assertEquals(128, dsuServer.controllerState.ly)

        // Stick LEFT: normX=-1f, normY=0f -> lx=0, ly=128
        handler.setVirtualStick(isLeftStick = true, normX = -1f, normY = 0f)
        assertEquals(0, dsuServer.controllerState.lx)
        assertEquals(128, dsuServer.controllerState.ly)
    }

    @Test
    fun testVirtualControlsButtonMapping() {
        val dsuServer = DSUServer()
        val handler = GamepadInputHandler(dsuServer)

        // Press Virtual A (mapped to Cemu DSU Circle / Wii U A)
        handler.setVirtualButton(handler.profile.keyA, true)
        assertTrue((dsuServer.controllerState.state2 and DSUPacket.State2Flags.CIRCLE_B) != 0)

        // Release Virtual A
        handler.setVirtualButton(handler.profile.keyA, false)
        assertTrue((dsuServer.controllerState.state2 and DSUPacket.State2Flags.CIRCLE_B) == 0)

        // Press Virtual D-Pad Up
        handler.setVirtualButton(KeyEvent.KEYCODE_DPAD_UP, true)
        assertTrue((dsuServer.controllerState.state1 and DSUPacket.State1Flags.DPAD_UP) != 0)

        // Release Virtual D-Pad Up
        handler.setVirtualButton(KeyEvent.KEYCODE_DPAD_UP, false)
        assertTrue((dsuServer.controllerState.state1 and DSUPacket.State1Flags.DPAD_UP) == 0)
    }

    @Test
    fun testAppSettingsCodecVirtualControls() {
        val defaultSettings = AppSettingsCodec.decode(null, null, null)
        assertFalse("Default showVirtualControls should be false", defaultSettings.showVirtualControls)

        val customSettings = DisplaySettings(showVirtualControls = true)
        val encoded = AppSettingsCodec.encode(customSettings)
        assertTrue(encoded.showVirtualControls)

        val decoded = AppSettingsCodec.decode(
            encoded.fitModeName,
            encoded.resolutionName,
            encoded.diagnosticsOverlayEnabled,
            encoded.connectionHelpVisible,
            encoded.limitTo30Fps,
            encoded.showVirtualControls
        )
        assertTrue("Decoded showVirtualControls must match encoded value", decoded.showVirtualControls)
    }
}
