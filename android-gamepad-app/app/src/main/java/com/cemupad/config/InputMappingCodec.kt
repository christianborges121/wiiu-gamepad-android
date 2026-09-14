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

    /**
     * Translate a ControllerProfile into VPAD mapping entries (mappingId -> DSU buttonId)
     * for pushing to Cemu. Mapping IDs match VPADController::kButtonId_* (see CemuPadBridge).
     * DSU button constants mirror ControllerBase kButton* (kButton14=Cross etc.).
     */
    fun toVpadEntries(profile: com.cemupad.input.ControllerProfile): List<Pair<Int, Int>> {
        // VPAD mapping ids as defined in VPADController.h
        // 1:A 2:B 3:X 4:Y 5:L 6:R 7:ZL 8:ZR 9:Plus 10:Minus 11:Up 12:Down 13:Left 14:Right
        // 15:StickL 16:StickR 17:StickL_Up 18:StickL_Down 19:StickL_Left 20:StickL_Right
        // 21:StickR_Up 22:StickR_Down 23:StickR_Left 24:StickR_Right 25:Mic 48:Home  (see header)
        // We map only the button-like entries that have Android keycodes; stick axes are local.
        // For now, push the core digital mappings that correspond to DSU buttons.
        // DSU button mapping: use stored Android keycodes directly as opaque identifiers is incorrect,
        // so we map via ControllerProfile's intent: keyA->DSU Cross (14), keyB->Circle(13), etc.
        // However for push we need (VPAD mappingId, DSU buttonId). The DSU buttonIds are the
        // logical DSU ids derived from the profile's purpose, not raw Android keycodes.
        // We therefore emit the canonical DSU mapping used in CemuPadBridge::kMapping:
        // This keeps Cemu's side identical to default but allows future per-profile overrides
        // where Android profile intentionally swaps A/B. For swapped profiles (e.g. Nintendo),
        // we detect by comparing to NINTENDO_LAYOUT.
        val isNintendo = profile.keyA == android.view.KeyEvent.KEYCODE_BUTTON_B
        return if (isNintendo) {
            listOf(
                1 to 13, // A -> Circle
                2 to 14, // B -> Cross (swapped)
                3 to 12, // X -> Triangle swapped with Y
                4 to 15, // Y -> Square
                5 to 10, 6 to 11, 7 to 42, 8 to 43, 9 to 3, 10 to 0, 11 to 4, 12 to 6, 13 to 7, 14 to 5,
                15 to 1, 16 to 2
            )
        } else {
            listOf(
                1 to 14, 2 to 13, 3 to 15, 4 to 12,
                5 to 10, 6 to 11, 7 to 42, 8 to 43, 9 to 3, 10 to 0, 11 to 4, 12 to 6, 13 to 7, 14 to 5,
                15 to 1, 16 to 2
            )
        }
    }
}
