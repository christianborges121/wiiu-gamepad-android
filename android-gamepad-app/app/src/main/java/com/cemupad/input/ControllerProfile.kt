package com.cemupad.input

import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sign

/**
 * Controller mapping profiles and analog processing utilities.
 */
data class ControllerProfile(
    val name: String,
    val deadzone: Float = 0.08f,
    // Face buttons mapping to Wii U actions
    val keyA: Int = KeyEvent.KEYCODE_BUTTON_A,
    val keyB: Int = KeyEvent.KEYCODE_BUTTON_B,
    val keyX: Int = KeyEvent.KEYCODE_BUTTON_X,
    val keyY: Int = KeyEvent.KEYCODE_BUTTON_Y,
    // Shoulder buttons
    val keyL: Int = KeyEvent.KEYCODE_BUTTON_L1,
    val keyR: Int = KeyEvent.KEYCODE_BUTTON_R1,
    val keyZL: Int = KeyEvent.KEYCODE_BUTTON_L2,
    val keyZR: Int = KeyEvent.KEYCODE_BUTTON_R2,
    // System buttons
    val keyPlus: Int = KeyEvent.KEYCODE_BUTTON_START,
    val keyMinus: Int = KeyEvent.KEYCODE_BUTTON_SELECT,
    val keyHome: Int = KeyEvent.KEYCODE_BUTTON_MODE,
    val keyL3: Int = KeyEvent.KEYCODE_BUTTON_THUMBL,
    val keyR3: Int = KeyEvent.KEYCODE_BUTTON_THUMBR,
    // Axes
    val axisLX: Int = MotionEvent.AXIS_X,
    val axisLY: Int = MotionEvent.AXIS_Y,
    val axisRX: Int = MotionEvent.AXIS_Z,
    val axisRY: Int = MotionEvent.AXIS_RZ,
    val axisLTrigger: Int = MotionEvent.AXIS_BRAKE,
    val axisRTrigger: Int = MotionEvent.AXIS_GAS
) {
    companion object {
        val DEFAULT = ControllerProfile(name = "Standard Gamepad")

        /**
         * Normalizes an Android analog axis value [-1.0f..1.0f] to DSU byte range [0..255] (128 center).
         *
         * @param rawValue Raw axis value from MotionEvent.
         * @param invertY For Y-axes: Android gives -1.0f for UP, +1.0f for DOWN.
         *                Cemu DSU expects 255 for UP and 0 for DOWN.
         *                Therefore, invertY MUST be true for stick Y axes.
         * @param deadzone Deadzone threshold (default 0.08f).
         */
        fun normalizeAxis(rawValue: Float, invertY: Boolean = false, deadzone: Float = 0.08f): Int {
            var value = rawValue.coerceIn(-1.0f, 1.0f)
            val magnitude = abs(value)

            value = if (magnitude < deadzone) {
                0.0f
            } else {
                sign(value) * ((magnitude - deadzone) / (1.0f - deadzone))
            }

            if (invertY) {
                value = -value
            }

            // Map [-1.0f..1.0f] -> [0..255] with 128 as exact center
            val mapped = kotlin.math.round(((value + 1.0f) * 127.5f)).toInt()
            return mapped.coerceIn(0, 255)
        }

        /**
         * Normalizes analog trigger [0.0f..1.0f] to [0..255].
         */
        fun normalizeTrigger(rawValue: Float): Int {
            return (rawValue.coerceIn(0.0f, 1.0f) * 255.0f).toInt().coerceIn(0, 255)
        }
    }
}
