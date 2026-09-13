package com.cemupad.config

import android.view.KeyEvent
import android.view.MotionEvent
import com.cemupad.input.ControllerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMappingCodecTest {

    @Test
    fun testRoundTripPreservesCustomProfile() {
        val original = ControllerProfile(
            name = "My Pad",
            deadzone = 0.12f,
            keyA = KeyEvent.KEYCODE_BUTTON_B,
            keyB = KeyEvent.KEYCODE_BUTTON_A,
            keyDpadUp = KeyEvent.KEYCODE_BUTTON_Y,
            hatAsDpad = false,
            axisLX = MotionEvent.AXIS_RX,
            deviceDescriptor = "abc123",
            displayName = "My Pad (Custom)"
        )

        val restored = InputMappingCodec.decode(InputMappingCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun testMissingValuesFallBackToDefaults() {
        val restored = InputMappingCodec.decode(emptyMap())
        assertEquals(ControllerProfile.DEFAULT, restored)
    }

    @Test
    fun testCorruptValuesFallBack() {
        val restored = InputMappingCodec.decode(
            mapOf(
                InputMappingCodec.KEY_A to "not_a_number",
                InputMappingCodec.KEY_DEADZONE to "NaN-ok",
                InputMappingCodec.KEY_HAT_AS_DPAD to "maybe"
            )
        )
        assertEquals(ControllerProfile.DEFAULT.keyA, restored.keyA)
        assertEquals(ControllerProfile.DEFAULT.hatAsDpad, restored.hatAsDpad)
    }

    @Test
    fun testNintendoProfileSurvives() {
        val original = com.cemupad.input.ControllerDetector.NINTENDO_LAYOUT.copy(
            deviceDescriptor = "nintendo-1"
        )
        val restored = InputMappingCodec.decode(InputMappingCodec.encode(original))
        assertEquals(original, restored)
        assertTrue(restored.keyA == KeyEvent.KEYCODE_BUTTON_B)
    }
}
