package com.cemupad.input

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import com.cemupad.util.Logger

/**
 * Handles GamePad rumble and haptic feedback.
 * Dispatches vibration effects to the device's internal haptic motor and
 * any connected physical controllers supporting rumble.
 */
class RumbleHandler(context: Context) {

    companion object {
        private const val TAG = "RumbleHandler"
        private const val MIN_PULSE_DURATION_MS = 50L
        private const val STOP_DEBOUNCE_GRACE_MS = 40L
        private const val MIN_AMPLITUDE_FLOOR = 64
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile
    var isEnabled = true

    @Volatile
    var intensityScale: Float = 1.0f

    @Volatile
    private var isRumbling = false

    @Volatile
    private var lastRumbleStartTime = 0L

    /**
     * Triggers vibration with the specified intensity (0-255) and duration (ms).
     */
    fun rumble(intensity: Int, durationMs: Long = 60L) {
        if (!isEnabled || intensityScale <= 0f || intensity <= 0 || durationMs <= 0) {
            cancel(force = true)
            return
        }

        val clampedIntensity = (intensity.coerceIn(1, 255) * intensityScale.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
        val effectiveDuration = maxOf(durationMs, MIN_PULSE_DURATION_MS)
        isRumbling = true
        lastRumbleStartTime = SystemClock.uptimeMillis()

        try {
            // Internal phone vibrator
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = if (v.hasAmplitudeControl()) {
                            val minFloor = (MIN_AMPLITUDE_FLOOR * intensityScale.coerceIn(0f, 1f)).toInt().coerceAtLeast(1)
                            maxOf(clampedIntensity, minFloor)
                        } else {
                            VibrationEffect.DEFAULT_AMPLITUDE
                        }
                        val effect = VibrationEffect.createOneShot(effectiveDuration, amplitude)
                        v.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(effectiveDuration)
                    }
                }
            }

            // Attached physical gamepad vibrators (e.g. Xbox, DualSense, telescopic controllers)
            for (deviceId in InputDevice.getDeviceIds()) {
                val dev = InputDevice.getDevice(deviceId) ?: continue
                if ((dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (dev.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                    val devVib = dev.vibrator
                    if (devVib.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val effect = VibrationEffect.createOneShot(effectiveDuration, VibrationEffect.DEFAULT_AMPLITUDE)
                            devVib.vibrate(effect)
                        } else {
                            @Suppress("DEPRECATION")
                            devVib.vibrate(effectiveDuration)
                        }
                    }
                }
            }
            Logger.i(TAG, "Rumble started: intensity=$clampedIntensity, duration=${effectiveDuration}ms")
        } catch (e: Exception) {
            Logger.w(TAG, "Rumble dispatch failed: ${e.message}")
        }
    }

    /**
     * Triggers a brief preview pulse at the specified or current intensity scale.
     */
    fun preview(scale: Float = intensityScale) {
        if (!isEnabled || scale <= 0f) return
        val effectiveScale = scale.coerceIn(0f, 1f)
        val previewIntensity = (255 * effectiveScale).toInt().coerceIn(1, 255)
        val durationMs = 40L
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = if (v.hasAmplitudeControl()) {
                            val minFloor = (MIN_AMPLITUDE_FLOOR * effectiveScale).toInt().coerceAtLeast(1)
                            maxOf(previewIntensity, minFloor)
                        } else {
                            VibrationEffect.DEFAULT_AMPLITUDE
                        }
                        v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(durationMs)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Preview vibration failed: ${e.message}")
        }
    }

    /**
     * Cancels any active vibration, with an optional debounce grace period so
     * physical motors are not cut off mid-stroke.
     */
    fun cancel(force: Boolean = false) {
        if (!isRumbling) return

        val elapsed = SystemClock.uptimeMillis() - lastRumbleStartTime
        if (!force && elapsed < STOP_DEBOUNCE_GRACE_MS) {
            // Debounce grace period: let motor complete minimal physical oscillation
            return
        }

        isRumbling = false
        try {
            vibrator?.cancel()
            for (deviceId in InputDevice.getDeviceIds()) {
                val dev = InputDevice.getDevice(deviceId) ?: continue
                dev.vibrator.cancel()
            }
            Logger.d(TAG, "Rumble cancelled (elapsed=${elapsed}ms)")
        } catch (e: Exception) {
            Logger.w(TAG, "Rumble cancel failed: ${e.message}")
        }
    }
}
