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
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B))

        assertEquals(MappableControl.A, engine.current)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_A))
        now += 1000
        assertEquals(MappableControl.B, engine.current)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(engine.isFinished)

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, profile.keyB)
    }

    @Test
    fun testDuplicateKeyConflicts() {
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B))

        engine.recordKey(KeyEvent.KEYCODE_BUTTON_A)
        now += 1000
        assertEquals(CaptureEngine.RecordResult.Conflict, engine.recordKey(KeyEvent.KEYCODE_BUTTON_A))
        // Still on B after conflict
        assertEquals(MappableControl.B, engine.current)
        now += 1000
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_B))

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, profile.keyB)
    }

    @Test
    fun testDebounceSwallowsImmediateSecondCapture() {
        // One press must not capture two targets: the tail of the press
        // (repeat, release wobble, fast double-tap) is ignored for 600 ms.
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B))

        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(MappableControl.B, engine.current)
        // Immediate second press (same moment): swallowed, stays on B.
        now += 100
        assertEquals(CaptureEngine.RecordResult.Ignored, engine.recordKey(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(MappableControl.B, engine.current)
        // After the window it assigns normally.
        now += 1000
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(engine.isFinished)
    }

    @Test
    fun testTriggerAxisKeyDualFire() {
        // Left trigger reports BOTH an analog axis and a digital key for one
        // press. The axis half assigns ZL; the key half arriving right after
        // must not also capture ZR.
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.ZL, MappableControl.ZR))

        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_LTRIGGER to 0.9f))
        )
        assertEquals(MappableControl.ZR, engine.current)
        now += 50
        assertEquals(
            CaptureEngine.RecordResult.Ignored,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_L2)
        )
        assertEquals(MappableControl.ZR, engine.current)
        now += 1000
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_R2)
        )
        assertTrue(engine.isFinished)
    }

    @Test
    fun testSharedTriggerAxisConflicts() {
        // Pads reporting both triggers on one axis can't serve ZL and ZR.
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.ZL, MappableControl.ZR))

        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_Z to 0.9f))
        )
        now += 1000
        assertEquals(
            CaptureEngine.RecordResult.Conflict,
            engine.recordAxes(mapOf(MotionEvent.AXIS_Z to 0.9f))
        )
        assertEquals(MappableControl.ZR, engine.current)
    }

    @Test
    fun testDebounceResetOnSkipAndBack() {
        // Explicit navigation stays snappy: skip/back clear the window.
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B, MappableControl.X))

        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_A))
        engine.skip() // skip B -> on X, debounce cleared
        assertEquals(MappableControl.X, engine.current)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordKey(KeyEvent.KEYCODE_BUTTON_X))
        assertTrue(engine.isFinished)
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
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.L, MappableControl.ZL), base = scrambled)

        // Re-confirming L with its own key is fine.
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_L2)
        )
        now += 1000
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
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B), base = ControllerProfile.DEFAULT)

        // Move A onto an unbound key...
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_1)
        )
        now += 1000
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
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.DPAD_LEFT, MappableControl.DPAD_UP))

        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordHat(MotionEvent.AXIS_HAT_X, -0.9f)
        )
        assertEquals(MappableControl.DPAD_UP, engine.current)
        now += 1000
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
    fun testStickDirectionCapturePerDirection() {
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.STICK_L_UP, MappableControl.STICK_R_RIGHT))

        // Left stick up: Y negative
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordAxes(mapOf(MotionEvent.AXIS_Y to -1.0f)))
        assertEquals(MappableControl.STICK_R_RIGHT, engine.current)
        now += 1000
        // Right stick right: Z positive (or RX)
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordAxes(mapOf(MotionEvent.AXIS_Z to 1.0f)))
        assertTrue(engine.isFinished)
    }

    @Test
    fun testStickDirectionNeedsThreshold() {
        val engine = CaptureEngine(clock = { 0L })
        engine.start(listOf(MappableControl.STICK_L_UP))

        // Small deflection ignored, wrong axis ignored
        assertEquals(CaptureEngine.RecordResult.Ignored, engine.recordAxes(mapOf(MotionEvent.AXIS_Y to -0.3f)))
        assertEquals(CaptureEngine.RecordResult.Ignored, engine.recordAxes(mapOf(MotionEvent.AXIS_X to 1.0f)))
        assertEquals(MappableControl.STICK_L_UP, engine.current)
        // Correct threshold passes
        assertEquals(CaptureEngine.RecordResult.Assigned, engine.recordAxes(mapOf(MotionEvent.AXIS_Y to -0.8f)))
        assertTrue(engine.isFinished)
    }

    @Test
    fun testTriggerAnalogPreferredDigitalFallback() {
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.ZL, MappableControl.ZR))

        // Analog travel on BRAKE wins for ZL
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_BRAKE to 0.8f))
        )
        now += 1000
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
        now += 1000
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

    @Test
    fun testRemapSwapDoesNotFalseConflict() {
        // User wants to swap A and B: A gets physical B, B gets physical A.
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(listOf(MappableControl.A, MappableControl.B), base = ControllerProfile.DEFAULT)

        // Mapping A to physical B must succeed cleanly, not say "Already assigned"
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_B)
        )
        now += 1000
        // Mapping B to physical A must succeed cleanly
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordKey(KeyEvent.KEYCODE_BUTTON_A)
        )

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, profile.keyB)
    }

    @Test
    fun testStickAxisCaptureUpdatesProfile() {
        var now = 0L
        val engine = CaptureEngine(clock = { now })
        engine.start(
            listOf(
                MappableControl.STICK_R_UP,
                MappableControl.STICK_R_DOWN,
                MappableControl.STICK_R_LEFT,
                MappableControl.STICK_R_RIGHT
            ),
            base = ControllerProfile.DEFAULT
        )

        // Controller with swapped axes (e.g. Backbone One):
        // Moving right stick Up deflects AXIS_Z negative
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_Z to -0.8f))
        )
        now += 1000
        // Moving right stick Down deflects AXIS_Z positive
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_Z to 0.8f))
        )
        now += 1000
        // Moving right stick Left deflects AXIS_RZ negative
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_RZ to -0.8f))
        )
        now += 1000
        // Moving right stick Right deflects AXIS_RZ positive
        assertEquals(
            CaptureEngine.RecordResult.Assigned,
            engine.recordAxes(mapOf(MotionEvent.AXIS_RZ to 0.8f))
        )

        val profile = engine.buildProfile(ControllerProfile.DEFAULT)!!
        assertEquals(MotionEvent.AXIS_RZ, profile.axisRX)
        assertEquals(MotionEvent.AXIS_Z, profile.axisRY)
    }

    @Test
    fun testHomeButtonIsLastInOrder() {
        assertEquals(MappableControl.HOME, MappableControl.ORDER.last())
    }
}
