package com.cemupad.input

import android.view.InputDevice
import android.view.MotionEvent
import com.cemupad.util.Logger

/**
 * Deduces a [ControllerProfile] for a connected physical controller.
 *
 * Matching runs exact VID/PID first, then case-insensitive name substrings
 * (many Bluetooth pads report VID/PID 0). Standard-layout pads resolve to
 * [ControllerProfile.DEFAULT]; Nintendo-layout pads (right = A) resolve to a
 * swapped profile. Anything else returns null so the mapping wizard falls
 * back to manual capture.
 */
enum class DetectionConfidence { EXACT, HEURISTIC }

data class DetectedProfile(
    val profile: ControllerProfile,
    val confidence: DetectionConfidence,
    val matchedOn: String
)

object ControllerDetector {
    private const val TAG = "ControllerDetector"

    /** Nintendo physical layout: east button is A, south is B. */
    val NINTENDO_LAYOUT = ControllerProfile(
        name = "Nintendo Layout",
        keyA = android.view.KeyEvent.KEYCODE_BUTTON_B,
        keyB = android.view.KeyEvent.KEYCODE_BUTTON_A,
        keyX = android.view.KeyEvent.KEYCODE_BUTTON_Y,
        keyY = android.view.KeyEvent.KEYCODE_BUTTON_X
    )

    private data class PidEntry(val vendorId: Int, val productId: Int, val label: String)

    // Well-known USB/Bluetooth product IDs (heuristic set — name fallback covers the rest).
    private val exactPidProfiles: Map<PidEntry, ControllerProfile> = mapOf(
        // Xbox (standard layout)
        PidEntry(0x045E, 0x028E, "Xbox 360") to ControllerProfile.DEFAULT,
        PidEntry(0x045E, 0x02DD, "Xbox One") to ControllerProfile.DEFAULT,
        PidEntry(0x045E, 0x02E0, "Xbox One S") to ControllerProfile.DEFAULT,
        PidEntry(0x045E, 0x02FD, "Xbox One S (BT)") to ControllerProfile.DEFAULT,
        PidEntry(0x045E, 0x0B12, "Xbox Series") to ControllerProfile.DEFAULT,
        PidEntry(0x045E, 0x0B13, "Xbox Series (BT)") to ControllerProfile.DEFAULT,
        // PlayStation (standard positions on Android)
        PidEntry(0x054C, 0x05C4, "DualShock 4") to ControllerProfile.DEFAULT,
        PidEntry(0x054C, 0x09CC, "DualShock 4 Slim") to ControllerProfile.DEFAULT,
        PidEntry(0x054C, 0x0CE6, "DualSense") to ControllerProfile.DEFAULT,
        // Nintendo (A/B swapped physical layout)
        PidEntry(0x057E, 0x2009, "Switch Pro Controller") to NINTENDO_LAYOUT,
        PidEntry(0x057E, 0x2006, "Joy-Con (L)") to NINTENDO_LAYOUT,
        PidEntry(0x057E, 0x2007, "Joy-Con (R)") to NINTENDO_LAYOUT
    )

    // (name substring, profile, label) — checked in order.
    private val nameProfiles: List<Triple<String, ControllerProfile, String>> = listOf(
        Triple("backbone", ControllerProfile.DEFAULT, "Backbone"),
        Triple("kishi", ControllerProfile.DEFAULT, "Razer Kishi"),
        Triple("gamesir", ControllerProfile.DEFAULT, "Gamesir"),
        Triple("8bitdo", ControllerProfile.DEFAULT, "8BitDo"),
        Triple("xbox", ControllerProfile.DEFAULT, "Xbox"),
        Triple("dualsense", ControllerProfile.DEFAULT, "DualSense"),
        Triple("dualshock", ControllerProfile.DEFAULT, "DualShock"),
        Triple("wireless controller", ControllerProfile.DEFAULT, "PlayStation"),
        Triple("pro controller", NINTENDO_LAYOUT, "Switch Pro Controller"),
        Triple("joy-con", NINTENDO_LAYOUT, "Joy-Con"),
        Triple("nintendo", NINTENDO_LAYOUT, "Nintendo")
    )

    /**
     * Pure fingerprint matcher (JVM-testable, no framework objects).
     * [axes] is the set of present MotionEvent axes (may be empty when unknown).
     */
    fun fingerprint(
        vendorId: Int,
        productId: Int,
        name: String,
        axes: Set<Int> = emptySet()
    ): DetectedProfile? {
        for ((entry, profile) in exactPidProfiles) {
            if (entry.vendorId == vendorId && entry.productId == productId) {
                Logger.i(TAG, "Exact PID match: ${entry.label}")
                return DetectedProfile(profile, DetectionConfidence.EXACT, entry.label)
            }
        }
        val lowerName = name.lowercase()
        for ((substring, profile, label) in nameProfiles) {
            if (lowerName.contains(substring)) {
                Logger.i(TAG, "Name match '$substring': $label")
                return DetectedProfile(profile, DetectionConfidence.HEURISTIC, label)
            }
        }
        // Generic gamepad with the standard axis cluster: assume default layout
        // but only heuristically — the wizard still shows the verify screen.
        if (axes.contains(MotionEvent.AXIS_X) && axes.contains(MotionEvent.AXIS_Y)) {
            Logger.i(TAG, "Generic gamepad axes; assuming standard layout")
            return DetectedProfile(ControllerProfile.DEFAULT, DetectionConfidence.HEURISTIC, "Generic Gamepad")
        }
        return null
    }

    /**
     * Framework entry point: fingerprints a live [InputDevice].
     */
    fun detect(device: InputDevice): DetectedProfile? {
        val axes = try {
            device.motionRanges.map { it.axis }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        return fingerprint(device.vendorId, device.productId, device.name ?: "", axes)
    }

    /**
     * Best-effort scan of all currently attached gamepads. Returns the first
     * gamepad-class device with its detection result (or null when none).
     */
    fun firstGamepad(): Pair<InputDevice, DetectedProfile?>? {
        for (id in InputDevice.getDeviceIds()) {
            try {
                val device = InputDevice.getDevice(id) ?: continue
                val sources = device.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (!isGamepad) continue
                return Pair(device, detect(device))
            } catch (_: Exception) {
            }
        }
        return null
    }
}
