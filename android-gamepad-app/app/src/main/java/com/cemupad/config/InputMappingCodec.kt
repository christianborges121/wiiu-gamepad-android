package com.cemupad.config

import com.cemupad.input.ControllerProfile

/**
 * Pure encode/decode for per-device controller profiles so unit tests do not
 * need SharedPreferences. Unknown or missing values fall back to
 * [ControllerProfile.DEFAULT] fields.
 */
object InputMappingCodec {
    const val KEY_NAME = "name"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_DESCRIPTOR = "descriptor"
    const val KEY_DEADZONE = "deadzone"
    const val KEY_A = "key_a"
    const val KEY_B = "key_b"
    const val KEY_X = "key_x"
    const val KEY_Y = "key_y"
    const val KEY_L = "key_l"
    const val KEY_R = "key_r"
    const val KEY_ZL = "key_zl"
    const val KEY_ZR = "key_zr"
    const val KEY_PLUS = "key_plus"
    const val KEY_MINUS = "key_minus"
    const val KEY_HOME = "key_home"
    const val KEY_L3 = "key_l3"
    const val KEY_R3 = "key_r3"
    const val KEY_DPAD_UP = "key_dpad_up"
    const val KEY_DPAD_DOWN = "key_dpad_down"
    const val KEY_DPAD_LEFT = "key_dpad_left"
    const val KEY_DPAD_RIGHT = "key_dpad_right"
    const val KEY_HAT_AS_DPAD = "hat_as_dpad"
    const val AXIS_LX = "axis_lx"
    const val AXIS_LY = "axis_ly"
    const val AXIS_RX = "axis_rx"
    const val AXIS_RY = "axis_ry"
    const val AXIS_LTRIGGER = "axis_ltrigger"
    const val AXIS_RTRIGGER = "axis_rtrigger"

    fun encode(profile: ControllerProfile): Map<String, String> {
        return mapOf(
            KEY_NAME to profile.name,
            KEY_DISPLAY_NAME to profile.displayName,
            KEY_DESCRIPTOR to profile.deviceDescriptor,
            KEY_DEADZONE to profile.deadzone.toString(),
            KEY_A to profile.keyA.toString(),
            KEY_B to profile.keyB.toString(),
            KEY_X to profile.keyX.toString(),
            KEY_Y to profile.keyY.toString(),
            KEY_L to profile.keyL.toString(),
            KEY_R to profile.keyR.toString(),
            KEY_ZL to profile.keyZL.toString(),
            KEY_ZR to profile.keyZR.toString(),
            KEY_PLUS to profile.keyPlus.toString(),
            KEY_MINUS to profile.keyMinus.toString(),
            KEY_HOME to profile.keyHome.toString(),
            KEY_L3 to profile.keyL3.toString(),
            KEY_R3 to profile.keyR3.toString(),
            KEY_DPAD_UP to profile.keyDpadUp.toString(),
            KEY_DPAD_DOWN to profile.keyDpadDown.toString(),
            KEY_DPAD_LEFT to profile.keyDpadLeft.toString(),
            KEY_DPAD_RIGHT to profile.keyDpadRight.toString(),
            KEY_HAT_AS_DPAD to profile.hatAsDpad.toString(),
            AXIS_LX to profile.axisLX.toString(),
            AXIS_LY to profile.axisLY.toString(),
            AXIS_RX to profile.axisRX.toString(),
            AXIS_RY to profile.axisRY.toString(),
            AXIS_LTRIGGER to profile.axisLTrigger.toString(),
            AXIS_RTRIGGER to profile.axisRTrigger.toString()
        )
    }

    fun decode(values: Map<String, String>): ControllerProfile {
        val fallback = ControllerProfile.DEFAULT
        fun int(key: String, default: Int): Int = values[key]?.toIntOrNull() ?: default
        val name = values[KEY_NAME] ?: fallback.name
        return ControllerProfile(
            name = name,
            deadzone = values[KEY_DEADZONE]?.toFloatOrNull() ?: fallback.deadzone,
            keyA = int(KEY_A, fallback.keyA),
            keyB = int(KEY_B, fallback.keyB),
            keyX = int(KEY_X, fallback.keyX),
            keyY = int(KEY_Y, fallback.keyY),
            keyL = int(KEY_L, fallback.keyL),
            keyR = int(KEY_R, fallback.keyR),
            keyZL = int(KEY_ZL, fallback.keyZL),
            keyZR = int(KEY_ZR, fallback.keyZR),
            keyPlus = int(KEY_PLUS, fallback.keyPlus),
            keyMinus = int(KEY_MINUS, fallback.keyMinus),
            keyHome = int(KEY_HOME, fallback.keyHome),
            keyL3 = int(KEY_L3, fallback.keyL3),
            keyR3 = int(KEY_R3, fallback.keyR3),
            keyDpadUp = int(KEY_DPAD_UP, fallback.keyDpadUp),
            keyDpadDown = int(KEY_DPAD_DOWN, fallback.keyDpadDown),
            keyDpadLeft = int(KEY_DPAD_LEFT, fallback.keyDpadLeft),
            keyDpadRight = int(KEY_DPAD_RIGHT, fallback.keyDpadRight),
            hatAsDpad = values[KEY_HAT_AS_DPAD]?.toBooleanStrictOrNull() ?: fallback.hatAsDpad,
            axisLX = int(AXIS_LX, fallback.axisLX),
            axisLY = int(AXIS_LY, fallback.axisLY),
            axisRX = int(AXIS_RX, fallback.axisRX),
            axisRY = int(AXIS_RY, fallback.axisRY),
            axisLTrigger = int(AXIS_LTRIGGER, fallback.axisLTrigger),
            axisRTrigger = int(AXIS_RTRIGGER, fallback.axisRTrigger),
            deviceDescriptor = values[KEY_DESCRIPTOR] ?: "",
            displayName = values[KEY_DISPLAY_NAME] ?: name
        )
    }
}
