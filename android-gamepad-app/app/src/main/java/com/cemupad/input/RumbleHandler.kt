package com.cemupad.input

import android.content.Context
import android.os.Build
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
    private var isRumbling = false

    /**
     * Triggers vibration with the specified intensity (0-255) and duration (ms).
     */
    fun rumble(intensity: Int, durationMs: Long = 60L) {
        if (!isEnabled || intensity <= 0 || durationMs <= 0) {
            cancel()
            return
        }

        val clampedIntensity = intensity.coerceIn(1, 255)
        isRumbling = true

        try {
            // Internal phone vibrator
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitude = if (v.hasAmplitudeControl()) clampedIntensity else VibrationEffect.DEFAULT_AMPLITUDE
                        val effect = VibrationEffect.createOneShot(durationMs, amplitude)
                        v.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(durationMs)
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
                            val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                            devVib.vibrate(effect)
                        } else {
                            @Suppress("DEPRECATION")
                            devVib.vibrate(durationMs)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Rumble dispatch failed: ${e.message}")
        }
    }

    /**
     * Cancels any active vibration immediately.
     */
    fun cancel() {
        if (!isRumbling) return
        isRumbling = false
        try {
            vibrator?.cancel()
            for (deviceId in InputDevice.getDeviceIds()) {
                val dev = InputDevice.getDevice(deviceId) ?: continue
                dev.vibrator.cancel()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Rumble cancel failed: ${e.message}")
        }
    }
}
