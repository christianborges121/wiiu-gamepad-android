package com.cemupad.input

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerDetectorTest {

    @Test
    fun testExactPidMatches() {
        val xbox = ControllerDetector.fingerprint(0x045E, 0x0B12, "Xbox Wireless Controller")
        assertTrue(xbox != null)
        assertEquals(DetectionConfidence.EXACT, xbox!!.confidence)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, xbox.profile.keyA)

        val pro = ControllerDetector.fingerprint(0x057E, 0x2009, "Pro Controller")
        assertTrue(pro != null)
        assertEquals(DetectionConfidence.EXACT, pro!!.confidence)
        // Nintendo layout: physical east (BUTTON_B position) acts as A
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, pro.profile.keyA)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, pro.profile.keyB)

        val dualsense = ControllerDetector.fingerprint(0x054C, 0x0CE6, "DualSense Wireless Controller")
        assertTrue(dualsense != null)
        assertEquals(DetectionConfidence.EXACT, dualsense!!.confidence)
    }

    @Test
    fun testNameFallbackWhenVidPidZero() {
        val backbone = ControllerDetector.fingerprint(0, 0, "Backbone One")
        assertTrue(backbone != null)
        assertEquals(DetectionConfidence.HEURISTIC, backbone!!.confidence)
        assertEquals(MotionEvent.AXIS_RZ, backbone.profile.axisRX)
        assertEquals(MotionEvent.AXIS_Z, backbone.profile.axisRY)

        val kishi = ControllerDetector.fingerprint(0, 0, "Razer Kishi V2 Pro")
        assertTrue(kishi != null)

        val eightBitDo = ControllerDetector.fingerprint(0, 0, "8BitDo Pro 2")
        assertTrue(eightBitDo != null)
    }

    @Test
    fun testBackboneExactPidMatch() {
        val backbone = ControllerDetector.fingerprint(0x358A, 0x0302, "Backbone One")
        assertTrue(backbone != null)
        assertEquals(DetectionConfidence.EXACT, backbone!!.confidence)
        assertEquals("Backbone One", backbone.matchedOn)
        assertEquals(MotionEvent.AXIS_RZ, backbone.profile.axisRX)
        assertEquals(MotionEvent.AXIS_Z, backbone.profile.axisRY)
    }

    @Test
    fun testGenericAxisFallback() {
        val generic = ControllerDetector.fingerprint(
            0, 0, "Mystery Pad 3000",
            setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
        )
        assertTrue(generic != null)
        assertEquals(DetectionConfidence.HEURISTIC, generic!!.confidence)
        assertEquals("Generic Gamepad", generic.matchedOn)
    }

    @Test
    fun testUnknownReturnsNull() {
        assertNull(ControllerDetector.fingerprint(0, 0, "Keyboard", emptySet()))
        assertNull(ControllerDetector.fingerprint(0x1234, 0x5678, "Unknown USB Device", emptySet()))
    }
}
