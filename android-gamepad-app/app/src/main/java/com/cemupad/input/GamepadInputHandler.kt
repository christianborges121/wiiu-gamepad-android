package com.cemupad.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.VisibleForTesting
import com.cemupad.dsu.DSUPacket
import com.cemupad.dsu.DSUServer
import kotlin.math.roundToInt

/**
 * Handles physical gamepad events and updates the DSU server controller state.
 */
class GamepadInputHandler(
    private val dsuServer: DSUServer,
    var profile: ControllerProfile = ControllerProfile.DEFAULT
) {
    // Current bitmasks
    private var state1 = 0
    private var state2 = 0
    private var psHome = false

    // D-Pad state tracking (separate Key and Hat states to prevent sticky axes / desync)
    private var dpadKeyUp = false
    private var dpadKeyDown = false
    private var dpadKeyLeft = false
    private var dpadKeyRight = false

    private var dpadHatUp = false
    private var dpadHatDown = false
    private var dpadHatLeft = false
    private var dpadHatRight = false

    // Stick positions
    private var lx = 128
    private var ly = 128
    private var rx = 128
    private var ry = 128

    // Trigger positions
    private var l2 = 0
    private var r2 = 0

    // Configurable stick deadzone
    var deadzone: Float = 0.08f

    fun setVirtualButton(keyCode: Int, isDown: Boolean) {
        if (isDown) {
            onKeyDown(keyCode)
        } else {
            onKeyUp(keyCode)
        }
    }

    fun setVirtualStick(isLeftStick: Boolean, normX: Float, normY: Float) {
        val mag = kotlin.math.sqrt(normX * normX + normY * normY)
        val (filteredX, filteredY) = if (mag < deadzone || mag == 0f) {
            0f to 0f
        } else {
            val scale = (mag - deadzone) / (1f - deadzone)
            (normX / mag * scale) to (normY / mag * scale)
        }
        val mappedX = ((filteredX + 1f) * 127.5f).roundToInt().coerceIn(0, 255)
        val mappedY = (((-filteredY) + 1f) * 127.5f).roundToInt().coerceIn(0, 255)

        if (isLeftStick) {
            lx = mappedX
            ly = mappedY
        } else {
            rx = mappedX
            ry = mappedY
        }

        dsuServer.updateState { state ->
            state.lx = lx
            state.ly = ly
            state.rx = rx
            state.ry = ry
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
            KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
            else -> false
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent? = null): Boolean {
        val source = event?.source ?: 0
        if (!isGamepadEvent(source, keyCode)) return false

        var handled = true
        when (keyCode) {
            // D-Pad
            KeyEvent.KEYCODE_DPAD_UP -> dpadKeyUp = true
            KeyEvent.KEYCODE_DPAD_DOWN -> dpadKeyDown = true
            KeyEvent.KEYCODE_DPAD_LEFT -> dpadKeyLeft = true
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpadKeyRight = true

            // Face buttons (mapped to Cemu DSU state2 layout):
            // Cemu controller0.xml maps:
            // Wii U GamePad A (East) -> DSU Button 13 (Circle)
            // Wii U GamePad B (South) -> DSU Button 14 (Cross)
            // Wii U GamePad X (North) -> DSU Button 12 (Triangle)
            // Wii U GamePad Y (West) -> DSU Button 15 (Square)
            profile.keyA -> state2 = state2 or DSUPacket.State2Flags.CIRCLE_B
            profile.keyB -> state2 = state2 or DSUPacket.State2Flags.CROSS_A
            profile.keyX -> state2 = state2 or DSUPacket.State2Flags.TRIANGLE_Y
            profile.keyY -> state2 = state2 or DSUPacket.State2Flags.SQUARE_X

            // Bumpers & Triggers
            profile.keyL -> state2 = state2 or DSUPacket.State2Flags.L
            profile.keyR -> state2 = state2 or DSUPacket.State2Flags.R
            profile.keyZL -> {
                state2 = state2 or DSUPacket.State2Flags.ZL
                l2 = 255
            }
            profile.keyZR -> {
                state2 = state2 or DSUPacket.State2Flags.ZR
                r2 = 255
            }

            // System buttons
            profile.keyPlus -> state1 = state1 or DSUPacket.State1Flags.OPTIONS_PLUS
            profile.keyMinus -> state1 = state1 or DSUPacket.State1Flags.SHARE_MINUS
            profile.keyHome -> psHome = true
            profile.keyL3 -> state1 = state1 or DSUPacket.State1Flags.L3
            profile.keyR3 -> state1 = state1 or DSUPacket.State1Flags.R3

            else -> handled = false
        }

        if (handled) {
            syncState()
        }
        return handled
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent? = null): Boolean {
        val source = event?.source ?: 0
        if (!isGamepadEvent(source, keyCode)) return false

        var handled = true
        when (keyCode) {
            // D-Pad
            KeyEvent.KEYCODE_DPAD_UP -> dpadKeyUp = false
            KeyEvent.KEYCODE_DPAD_DOWN -> dpadKeyDown = false
            KeyEvent.KEYCODE_DPAD_LEFT -> dpadKeyLeft = false
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpadKeyRight = false

            // Face buttons
            profile.keyA -> state2 = state2 and DSUPacket.State2Flags.CIRCLE_B.inv()
            profile.keyB -> state2 = state2 and DSUPacket.State2Flags.CROSS_A.inv()
            profile.keyX -> state2 = state2 and DSUPacket.State2Flags.TRIANGLE_Y.inv()
            profile.keyY -> state2 = state2 and DSUPacket.State2Flags.SQUARE_X.inv()

            // Bumpers & Triggers
            profile.keyL -> state2 = state2 and DSUPacket.State2Flags.L.inv()
            profile.keyR -> state2 = state2 and DSUPacket.State2Flags.R.inv()
            profile.keyZL -> {
                state2 = state2 and DSUPacket.State2Flags.ZL.inv()
                l2 = 0
            }
            profile.keyZR -> {
                state2 = state2 and DSUPacket.State2Flags.ZR.inv()
                r2 = 0
            }

            // System buttons
            profile.keyPlus -> state1 = state1 and DSUPacket.State1Flags.OPTIONS_PLUS.inv()
            profile.keyMinus -> state1 = state1 and DSUPacket.State1Flags.SHARE_MINUS.inv()
            profile.keyHome -> psHome = false
            profile.keyL3 -> state1 = state1 and DSUPacket.State1Flags.L3.inv()
            profile.keyR3 -> state1 = state1 and DSUPacket.State1Flags.R3.inv()

            else -> handled = false
        }

        if (handled) {
            syncState()
        }
        return handled
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isGamepadMotionEvent(event.source)) return false

        // Left Analog Stick
        val rawLX = event.getAxisValue(profile.axisLX)
        val rawLY = event.getAxisValue(profile.axisLY)
        lx = ControllerProfile.normalizeAxis(rawLX, invertY = false, deadzone = deadzone)
        // Stick Y inverted: UP is 255, DOWN is 0
        ly = ControllerProfile.normalizeAxis(rawLY, invertY = true, deadzone = deadzone)

        // Right Analog Stick (Z and RZ on Android)
        val rawRX = event.getAxisValue(profile.axisRX)
        val rawRY = event.getAxisValue(profile.axisRY)
        rx = ControllerProfile.normalizeAxis(rawRX, invertY = false, deadzone = deadzone)
        ry = ControllerProfile.normalizeAxis(rawRY, invertY = true, deadzone = deadzone)

        // Analog Triggers (GAS/BRAKE or RTRIGGER/LTRIGGER)
        var rawL2 = event.getAxisValue(profile.axisLTrigger)
        if (rawL2 == 0.0f) rawL2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        var rawR2 = event.getAxisValue(profile.axisRTrigger)
        if (rawR2 == 0.0f) rawR2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

        l2 = ControllerProfile.normalizeTrigger(rawL2)
        r2 = ControllerProfile.normalizeTrigger(rawR2)

        // Sync digital ZL/ZR flag based on analog threshold
        if (l2 > 30) {
            state2 = state2 or DSUPacket.State2Flags.ZL
        } else if (event.getAxisValue(profile.axisLTrigger) == 0.0f && event.getAxisValue(MotionEvent.AXIS_LTRIGGER) == 0.0f) {
            state2 = state2 and DSUPacket.State2Flags.ZL.inv()
        }

        if (r2 > 30) {
            state2 = state2 or DSUPacket.State2Flags.ZR
        } else if (event.getAxisValue(profile.axisRTrigger) == 0.0f && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) == 0.0f) {
            state2 = state2 and DSUPacket.State2Flags.ZR.inv()
        }

        // D-Pad Hat Axis (some gamepads report D-pad as HAT_X / HAT_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        updateHatState(hatX, hatY)

        return true
    }

    @VisibleForTesting
    internal fun updateHatState(hatX: Float, hatY: Float) {
        dpadHatLeft = hatX < -0.5f
        dpadHatRight = hatX > 0.5f
        dpadHatUp = hatY < -0.5f
        dpadHatDown = hatY > 0.5f
        syncState()
    }

    private fun syncState() {
        // Merge D-Pad states from both KeyEvents and MotionEvent HAT axes
        val up = dpadKeyUp || dpadHatUp
        val down = dpadKeyDown || dpadHatDown
        val left = dpadKeyLeft || dpadHatLeft
        val right = dpadKeyRight || dpadHatRight

        var s1 = state1 and (DSUPacket.State1Flags.DPAD_UP or DSUPacket.State1Flags.DPAD_DOWN or
                DSUPacket.State1Flags.DPAD_LEFT or DSUPacket.State1Flags.DPAD_RIGHT).inv()
        if (up) s1 = s1 or DSUPacket.State1Flags.DPAD_UP
        if (down) s1 = s1 or DSUPacket.State1Flags.DPAD_DOWN
        if (left) s1 = s1 or DSUPacket.State1Flags.DPAD_LEFT
        if (right) s1 = s1 or DSUPacket.State1Flags.DPAD_RIGHT
        state1 = s1

        dsuServer.updateState { state ->
            state.state1 = state1
            state.state2 = state2
            state.psHome = psHome
            state.lx = lx
            state.ly = ly
            state.rx = rx
            state.ry = ry
            state.l2Analog = l2
            state.r2Analog = r2
        }
    }

    private fun isGamepadEvent(source: Int, keyCode: Int): Boolean {
        if (keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER) return true
        if (KeyEvent.isGamepadButton(keyCode)) return true
        if (keyCode == profile.keyA || keyCode == profile.keyB ||
            keyCode == profile.keyX || keyCode == profile.keyY ||
            keyCode == profile.keyL || keyCode == profile.keyR ||
            keyCode == profile.keyZL || keyCode == profile.keyZR ||
            keyCode == profile.keyPlus || keyCode == profile.keyMinus ||
            keyCode == profile.keyHome || keyCode == profile.keyL3 ||
            keyCode == profile.keyR3) {
            return true
        }
        return (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }

    private fun isGamepadMotionEvent(source: Int): Boolean {
        return (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
    }
}
