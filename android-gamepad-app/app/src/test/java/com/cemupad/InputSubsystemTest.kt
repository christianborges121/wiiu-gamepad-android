package com.cemupad

import com.cemupad.input.ControllerProfile
import com.cemupad.input.TouchInputHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSubsystemTest {

    @Test
    fun testStickAxisNormalizationAndInversion() {
        // Center position -> 128
        val center = ControllerProfile.normalizeAxis(0.0f, invertY = false)
        assertEquals(128, center)

        // Deadzone suppression (raw 0.05 within 0.08 default deadzone)
        val withinDeadzone = ControllerProfile.normalizeAxis(0.05f, invertY = false)
        assertEquals(128, withinDeadzone)

        val withinDeadzoneNegative = ControllerProfile.normalizeAxis(-0.07f, invertY = false)
        assertEquals(128, withinDeadzoneNegative)

        // Stick X: -1.0 is Left (0), +1.0 is Right (255)
        val stickLeft = ControllerProfile.normalizeAxis(-1.0f, invertY = false)
        assertEquals(0, stickLeft)

        val stickRight = ControllerProfile.normalizeAxis(1.0f, invertY = false)
        assertEquals(255, stickRight)

        // CRITICAL INVERSION FIX:
        // Android Stick Y gives -1.0 for UP and +1.0 for DOWN.
        // Cemu DSU protocol expects 255 for UP and 0 for DOWN.
        val stickUp = ControllerProfile.normalizeAxis(-1.0f, invertY = true)
        assertEquals("Stick UP (-1.0f raw) must map to 255 in Cemu DSU", 255, stickUp)

        val stickDown = ControllerProfile.normalizeAxis(1.0f, invertY = true)
        assertEquals("Stick DOWN (+1.0f raw) must map to 0 in Cemu DSU", 0, stickDown)
    }

    @Test
    fun testTriggerNormalization() {
        assertEquals(0, ControllerProfile.normalizeTrigger(0.0f))
        assertEquals(255, ControllerProfile.normalizeTrigger(1.0f))
        assertEquals(127, ControllerProfile.normalizeTrigger(0.5f))
        assertEquals(255, ControllerProfile.normalizeTrigger(1.5f)) // clamped
        assertEquals(0, ControllerProfile.normalizeTrigger(-0.5f))  // clamped
    }

    @Test
    fun testTouchPillarboxViewportAndCoordinateNormalization() {
        // Modern phone: 2400x1080 (20:9 aspect ratio)
        val viewport = TouchInputHandler.Viewport(
            offsetX = 240f,  // (2400 - (1080 * 16/9)) / 2 = 240
            offsetY = 0f,
            width = 1920f,
            height = 1080f
        )

        // Point inside left pillarbox margin (x < 240) -> outside active GamePad
        assertFalse(viewport.contains(100f, 500f))

        // Point inside right pillarbox margin (x > 2160) -> outside active GamePad
        assertFalse(viewport.contains(2250f, 500f))

        // Points inside active 16:9 area
        assertTrue(viewport.contains(240f, 0f))
        assertTrue(viewport.contains(2160f, 1080f))
        assertTrue(viewport.contains(1200f, 540f))

        // Normalization to Cemu DSU 1920x942 coordinate space
        // Top-Left corner (240, 0) -> (0, 0)
        assertEquals(0.toShort(), viewport.normalizeX(240f))
        assertEquals(0.toShort(), viewport.normalizeY(0f))

        // Bottom-Right corner (2160, 1080) -> (1919, 941)
        assertEquals(TouchInputHandler.CEMU_TOUCH_MAX_X.toShort(), viewport.normalizeX(2160f))
        assertEquals(TouchInputHandler.CEMU_TOUCH_MAX_Y.toShort(), viewport.normalizeY(1080f))

        // Center point (1200, 540) -> approximately halfway
        val centerX = viewport.normalizeX(1200f).toInt()
        val centerY = viewport.normalizeY(540f).toInt()
        assertEquals(TouchInputHandler.CEMU_TOUCH_MAX_X / 2, centerX)
        assertEquals(TouchInputHandler.CEMU_TOUCH_MAX_Y / 2, centerY)
    }

    @Test
    fun testMotionUnitsConversion() {
        // Accelerometer: 9.80665 m/s^2 should convert to 1.0 g
        val earthGravity = 9.80665f
        val rawMps2 = 9.80665f
        val accelG = rawMps2 / earthGravity
        assertEquals(1.0f, accelG, 0.0001f)

        // Gyroscope: pi rad/s should convert to 180 deg/s
        val piRads = Math.PI.toFloat()
        val degs = piRads * (180.0f / Math.PI.toFloat())
        assertEquals(180.0f, degs, 0.001f)
    }

    @Test
    fun testDpadKeyEventsUpDownLeftRight() {
        val server = com.cemupad.dsu.DSUServer()
        val handler = com.cemupad.input.GamepadInputHandler(server)

        // Press DOWN
        val handledDown = handler.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(handledDown)
        val stateAfterDown = server.controllerState.state1
        assertTrue((stateAfterDown and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_DOWN) != 0)
        assertTrue((stateAfterDown and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_UP) == 0)

        // Release DOWN
        handler.onKeyUp(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        val stateAfterReleaseDown = server.controllerState.state1
        assertEquals(0, stateAfterReleaseDown and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_DOWN)

        // Press LEFT
        handler.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue((server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_LEFT) != 0)
        handler.onKeyUp(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_LEFT)

        // Press RIGHT
        handler.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue((server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_RIGHT) != 0)
        handler.onKeyUp(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_RIGHT)

        // Press UP
        handler.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_UP)
        assertTrue((server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_UP) != 0)
        handler.onKeyUp(android.view.KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_UP)
    }

    @Test
    fun testDpadHatEventsNoStickyHat() {
        val server = com.cemupad.dsu.DSUServer()
        val handler = com.cemupad.input.GamepadInputHandler(server)

        // User pushes D-Pad UP on hat switch (-1.0f on hatY)
        handler.updateHatState(0.0f, -1.0f)
        assertTrue("DPAD_UP must be set when hatY = -1.0f",
            (server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_UP) != 0)

        // CRITICAL BUG VERIFICATION:
        // User releases D-Pad UP (hat returns to 0.0f, 0.0f)
        // In the buggy version, DPAD_UP remained stuck on forever!
        handler.updateHatState(0.0f, 0.0f)
        assertEquals("DPAD_UP must be cleared when hat is centered",
            0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_UP)

        // User pushes D-Pad DOWN (+1.0f on hatY)
        handler.updateHatState(0.0f, 1.0f)
        assertTrue("DPAD_DOWN must be set when hatY = +1.0f",
            (server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_DOWN) != 0)
        handler.updateHatState(0.0f, 0.0f)
        assertEquals("DPAD_DOWN must be cleared when centered",
            0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_DOWN)

        // User pushes D-Pad LEFT (-1.0f on hatX)
        handler.updateHatState(-1.0f, 0.0f)
        assertTrue("DPAD_LEFT must be set when hatX = -1.0f",
            (server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_LEFT) != 0)
        handler.updateHatState(0.0f, 0.0f)
        assertEquals("DPAD_LEFT must be cleared when centered",
            0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_LEFT)

        // User pushes D-Pad RIGHT (+1.0f on hatX)
        handler.updateHatState(1.0f, 0.0f)
        assertTrue("DPAD_RIGHT must be set when hatX = +1.0f",
            (server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_RIGHT) != 0)
        handler.updateHatState(0.0f, 0.0f)
        assertEquals("DPAD_RIGHT must be cleared when centered",
            0, server.controllerState.state1 and com.cemupad.dsu.DSUPacket.State1Flags.DPAD_RIGHT)
    }
}
