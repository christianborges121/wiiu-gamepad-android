package com.cemupad.input

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureEngineTest {

    @Test
    fun testButtonCaptureInOrder() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.A, MappableControl.B))

        assertEquals(MappableControl.A, engine.current)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(MappableControl.B, engine.current)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(engine.isFinished)

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, profile.keyB)
    }

    @Test
    fun testDuplicateKeyConflicts() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.A, MappableControl.B))

        engine.recordKey(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(CaptureEngine.RecordResult.Conflict, engine.recordKey(KeyEvent.KEYCODE_BUTTON_A))
        // Still on B after conflict
        assertEquals(MappableControl.B, engine.current)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_B))

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, profile.keyB)
    }

    @Test
    fun testIgnoredSystemKeys() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.A))

        assertEquals(CaptureEngine.RecordResult.Ignored, engine.recordKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(MappableControl.A, engine.current)
    }

    @Test
    fun testCrossPassDuplicateConflicts() {
        // Simulates the saved-profile scramble: base has L and ZL on one key.
        val scrambled = ControllerProfile.DEFAULT.copy(keyL = KeyEvent.KEYCODE_BUTTON_L2)
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.L, MappableControl.ZL), base = scrambled)

        // Re-confirming L with its own key is fine.
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_L2)
        )
        // ZL pressing the same key must conflict, not stack silently.
        assertEquals(
            CaptureEngine.RecordResult.Conflict,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_L2)
        )
        // An unbound key assigns cleanly.
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_1)
        )

        val profile = engine.buildProfile(scrambled)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_L2, profile.keyL)
        assertEquals(KeyEvent.KEYCODE_BUTTON_1, profile.keyZL)
    }

    @Test
    fun testReassignFreesOldCode() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.A, MappableControl.B), base = ControllerProfile.DEFAULT)

        // Move A onto an unbound key...
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_1)
        )
        // ...then the freed standard key is claimable by B.
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_A)
        )

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_1, profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, profile.keyB)
    }

    @Test
    fun testBackIsBindable() {
        // Pads that emit physical buttons as KEYCODE_BACK (Dolphin rule):
        // short Back binds like any key; exit is via long-press (activity).
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.B))

        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BACK))
        assertTrue(engine.isFinished)

        val bound = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BACK, bound.keyB)
    }

    @Test
    fun testHatDpadCapture() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.DPAD_LEFT, MappableControl.DPAD_UP))

        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordHat(MotionEvent.AXIS_HAT_X, -0.9f)
        )
        assertEquals(MappableControl.DPAD_UP, engine.current)
        // Wrong direction ignored
        assertEquals(
            CaptureEngine.RecordResult.Ignored,
            engine.recordHat(MotionEvent.AXIS_HAT_X, 0.9f)
        )
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordHat(MotionEvent.AXIS_HAT_Y, -0.9f)
        )

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, profile.keyDpadLeft)
        assertTrue(profile.hatAsDpad)
    }

    @Test
    fun testStickAxisPairCapture() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.STICK_L_MOVE))

        // Wiggle: X full right, Y full up
        engine.recordAxes(mapOf(MotionEvent.AXIS_X to 1.0f, MotionEvent.AXIS_Y to -1.0f))
        // Hat must not leak into stick peaks
        engine.recordAxes(mapOf(MotionEvent.AXIS_HAT_X to 1.0f))
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.confirmStick())

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        val axes = setOf(profile.axisLX, profile.axisLY)
        assertEquals(setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y), axes)
    }

    @Test
    fun testStickNeedsTwoAxes() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.STICK_L_MOVE))

        engine.recordAxes(mapOf(MotionEvent.AXIS_X to 1.0f))
        assertEquals(CaptureEngine.RecordResult.Ignored, engine.confirmStick())
        assertEquals(MappableControl.STICK_L_MOVE, engine.current)
    }

    @Test
    fun testTriggerAnalogPreferredDigitalFallback() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.ZL, MappableControl.ZR))

        // Analog travel on BRAKE wins for ZL
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_BRAKE to 0.8f))
        )
        // Digital key for ZR
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_R2)
        )

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(MotionEvent.AXIS_BRAKE, profile.axisLTrigger)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R2, profile.keyZR)
        assertEquals(CaptureEngine.AXIS_UNUSED, profile.axisRTrigger)
    }

    @Test
    fun testSkipBackCancel() {
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B, MappableControl.X))

        engine.recordKey(KeyEvent.KEYCODE_BUTTON_A)
        engine.skip()
        assertTrue(engine.skippedTargets().contains(MappableControl.B))
        assertEquals(MappableControl.X, engine.current)

        assertTrue(engine.back())
        assertEquals(MappableControl.B, engine.current)
        assertFalse(engine.skippedTargets().contains(MappableControl.B))
        engine.recordKey(KeyEvent.KEYCODE_BUTTON_B)
        engine.recordKey(KeyEvent.KEYCODE_BUTTON_X)
        assertTrue(engine.isFinished)

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, profile.keyX)

        engine.start(listOf(MappableControl.A))
        engine.cancel()
        assertNull(engine.buildProfile(ControllerProfile.DEFAULT))
    }

    @Test
    fun testTimeout() {
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A))

        assertFalse(engine.checkTimeout())
        now = CaptureEngine.TARGET_TIMEOUT_MS + 1
        assertTrue(engine.checkTimeout())
    }

    @Test
    fun testUpProducesMaxByte() {
        // Handler contract the capture relies on: inverted Y maps full-up to 255.
        assertEquals(255, ControllerProfile.normalizeAxis(-1.0f, invertY = true, deadzone = 0f))
        assertEquals(0, ControllerProfile.normalizeAxis(1.0f, invertY = true, deadzone = 0f))
    }
}
