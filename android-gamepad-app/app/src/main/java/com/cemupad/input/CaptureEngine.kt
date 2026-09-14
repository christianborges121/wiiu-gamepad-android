package com.cemupad.input

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * UI-independent manual input-capture state machine.
 *
 * Walks [MappableControl] targets in order. Buttons capture the first key
 * down; sticks capture the strongest axis pair while the user wiggles them;
 * D-pad accepts keys or hat deflection; triggers accept an analog axis or a
 * digital key. All timing goes through [clock] for unit tests.
 */
enum class CaptureKind { BUTTON, DPAD, STICK, TRIGGER }

enum class MappableControl(val label: String, val kind: CaptureKind) {
    A("A", CaptureKind.BUTTON),
    B("B", CaptureKind.BUTTON),
    X("X", CaptureKind.BUTTON),
    Y("Y", CaptureKind.BUTTON),
    DPAD_UP("D-Pad Up", CaptureKind.DPAD),
    DPAD_DOWN("D-Pad Down", CaptureKind.DPAD),
    DPAD_LEFT("D-Pad Left", CaptureKind.DPAD),
    DPAD_RIGHT("D-Pad Right", CaptureKind.DPAD),
    L("L", CaptureKind.BUTTON),
    R("R", CaptureKind.BUTTON),
    ZL("ZL", CaptureKind.TRIGGER),
    ZR("ZR", CaptureKind.TRIGGER),
    PLUS("Plus (+)", CaptureKind.BUTTON),
    MINUS("Minus (-)", CaptureKind.BUTTON),
    STICK_L_PRESS("Left Stick Press", CaptureKind.BUTTON),
    STICK_R_PRESS("Right Stick Press", CaptureKind.BUTTON),
    STICK_L_UP("Stick L Up", CaptureKind.STICK),
    STICK_L_DOWN("Stick L Down", CaptureKind.STICK),
    STICK_L_LEFT("Stick L Left", CaptureKind.STICK),
    STICK_L_RIGHT("Stick L Right", CaptureKind.STICK),
    STICK_R_UP("Stick R Up", CaptureKind.STICK),
    STICK_R_DOWN("Stick R Down", CaptureKind.STICK),
    STICK_R_LEFT("Stick R Left", CaptureKind.STICK),
    STICK_R_RIGHT("Stick R Right", CaptureKind.STICK),
    HOME("Home", CaptureKind.BUTTON);

    companion object {
        val ORDER: List<MappableControl> = values().toList()
    }
}

class CaptureEngine(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        const val TARGET_TIMEOUT_MS = 15_000L
        const val STICK_DEFLECTION = 0.6f
        const val HAT_THRESHOLD = 0.5f
        const val TRIGGER_TRAVEL = 0.5f
        /** Axis id meaning "no analog axis assigned". `getAxisValue(-1)` is 0. */
        const val AXIS_UNUSED = -1

        // Volume/power/menu stay structural (menu opens maps on some shells).
        // NOTE: BACK is deliberately bindable (Dolphin rule): many pads emit
        // physical buttons as KEYCODE_BACK. Exit via long-press Back (handled
        // by MainActivity) or the on-screen Cancel/Skip/Back touch buttons.
        private val IGNORED_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_UNKNOWN
        )
    }

    sealed interface RecordResult {
        data object Assigned : RecordResult
        data object Conflict : RecordResult
        data object Ignored : RecordResult
        data object NotArmed : RecordResult
    }

    private var targets: List<MappableControl> = MappableControl.ORDER
    private var index: Int = 0
    private var cancelled: Boolean = false
    private var targetStartedAt: Long = clock()

    private var baseProfile: ControllerProfile? = null

    private val assignedKeys = mutableMapOf<MappableControl, Int>()
    private val assignedStickAxes = mutableMapOf<MappableControl, Pair<Int, Int>>()
    private val assignedTriggerAxis = mutableMapOf<MappableControl, Int>()
    private val usedKeyCodes = mutableSetOf<Int>()
    private val hatDirections = mutableSetOf<MappableControl>()
    private val skipped = mutableSetOf<MappableControl>()
    private val stickPeaks = mutableMapOf<Int, Float>()

    private var capturedAxisLX: Int? = null
    private var capturedAxisLY: Int? = null
    private var capturedAxisRX: Int? = null
    private var capturedAxisRY: Int? = null

    val current: MappableControl?
        get() = if (cancelled || index >= targets.size) null else targets[index]

    val progress: Pair<Int, Int>
        get() = Pair(index.coerceAtMost(targets.size), targets.size)

    val isFinished: Boolean
        get() = !cancelled && index >= targets.size

    val isCancelled: Boolean
        get() = cancelled

    fun start(
        targets: List<MappableControl> = MappableControl.ORDER,
        base: ControllerProfile? = null
    ) {
        this.targets = targets
        index = 0
        cancelled = false
        assignedKeys.clear()
        assignedStickAxes.clear()
        assignedTriggerAxis.clear()
        usedKeyCodes.clear()
        baseProfile = base
        hatDirections.clear()
        skipped.clear()
        stickPeaks.clear()
        capturedAxisLX = null
        capturedAxisLY = null
        capturedAxisRX = null
        capturedAxisRY = null
        targetStartedAt = clock()
    }

    fun cancel() {
        cancelled = true
    }

    fun skip() {
        current?.let { skipped.add(it) }
        advance()
    }

    fun back(): Boolean {
        if (index <= 0) return false
        index--
        val target = targets[index]
        skipped.remove(target)
        assignedKeys.remove(target)?.let { usedKeyCodes.remove(it) }
        assignedStickAxes.remove(target)
        assignedTriggerAxis.remove(target)
        hatDirections.remove(target)
        when (target) {
            MappableControl.STICK_L_UP, MappableControl.STICK_L_DOWN -> capturedAxisLY = null
            MappableControl.STICK_L_LEFT, MappableControl.STICK_L_RIGHT -> capturedAxisLX = null
            MappableControl.STICK_R_UP, MappableControl.STICK_R_DOWN -> capturedAxisRY = null
            MappableControl.STICK_R_LEFT, MappableControl.STICK_R_RIGHT -> capturedAxisRX = null
            else -> {}
        }
        stickPeaks.clear()
        targetStartedAt = clock()
        return true
    }

    /** True when the armed target timed out (caller should auto-skip). */
    fun checkTimeout(): Boolean {
        if (current == null) return false
        return clock() - targetStartedAt >= TARGET_TIMEOUT_MS
    }

    fun recordKey(keyCode: Int): RecordResult {
        val target = current ?: return RecordResult.NotArmed
        if (keyCode in IGNORED_KEYS) return RecordResult.Ignored
        if (target.kind != CaptureKind.BUTTON &&
            target.kind != CaptureKind.DPAD &&
            target.kind != CaptureKind.TRIGGER
        ) {
            return RecordResult.Ignored
        }
        if (keyCode in usedKeyCodes) return RecordResult.Conflict
        usedKeyCodes.add(keyCode)
        assignedKeys[target] = keyCode
        skipped.remove(target)
        advance()
        return RecordResult.Assigned
    }

    /**
     * Records hat deflection for DPAD targets. Using the hat marks the
     * direction hat-driven and clears its key binding (set to UNKNOWN) so a
     * single source feeds the handler.
     */
    fun recordHat(axis: Int, value: Float): RecordResult {
        val target = current ?: return RecordResult.NotArmed
        if (target.kind != CaptureKind.DPAD) return RecordResult.Ignored
        val active = when (target) {
            MappableControl.DPAD_LEFT -> axis == MotionEvent.AXIS_HAT_X && value < -HAT_THRESHOLD
            MappableControl.DPAD_RIGHT -> axis == MotionEvent.AXIS_HAT_X && value > HAT_THRESHOLD
            MappableControl.DPAD_UP -> axis == MotionEvent.AXIS_HAT_Y && value < -HAT_THRESHOLD
            MappableControl.DPAD_DOWN -> axis == MotionEvent.AXIS_HAT_Y && value > HAT_THRESHOLD
            else -> false
        }
        if (!active) return RecordResult.Ignored
        assignedKeys[target] = KeyEvent.KEYCODE_UNKNOWN
        hatDirections.add(target)
        skipped.remove(target)
        advance()
        return RecordResult.Assigned
    }

    /** Feeds live axis values while a STICK or TRIGGER target is armed. */
    fun recordAxes(axes: Map<Int, Float>): RecordResult {
        val target = current ?: return RecordResult.NotArmed
        when (target.kind) {
            CaptureKind.STICK -> {
                val nonStickAxes = setOf(
                    MotionEvent.AXIS_BRAKE,
                    MotionEvent.AXIS_GAS,
                    MotionEvent.AXIS_LTRIGGER,
                    MotionEvent.AXIS_RTRIGGER,
                    MotionEvent.AXIS_HAT_X,
                    MotionEvent.AXIS_HAT_Y
                )
                val candidates = axes.filter { (axis, _) -> axis !in nonStickAxes }

                var bestAxis: Int? = null
                var bestValue = 0f
                for ((axis, value) in candidates) {
                    if (abs(value) > abs(bestValue)) {
                        bestAxis = axis
                        bestValue = value
                    }
                }

                if (bestAxis == null || abs(bestValue) < STICK_DEFLECTION) {
                    return RecordResult.Ignored
                }

                val active = when (target) {
                    MappableControl.STICK_L_UP -> bestValue < -STICK_DEFLECTION
                    MappableControl.STICK_L_DOWN -> bestValue > STICK_DEFLECTION
                    MappableControl.STICK_L_LEFT -> bestValue < -STICK_DEFLECTION
                    MappableControl.STICK_L_RIGHT -> bestValue > STICK_DEFLECTION
                    MappableControl.STICK_R_UP -> bestValue < -STICK_DEFLECTION
                    MappableControl.STICK_R_DOWN -> bestValue > STICK_DEFLECTION
                    MappableControl.STICK_R_LEFT -> bestValue < -STICK_DEFLECTION
                    MappableControl.STICK_R_RIGHT -> bestValue > STICK_DEFLECTION
                    else -> false
                }

                if (active) {
                    when (target) {
                        MappableControl.STICK_L_UP, MappableControl.STICK_L_DOWN -> capturedAxisLY = bestAxis
                        MappableControl.STICK_L_LEFT, MappableControl.STICK_L_RIGHT -> capturedAxisLX = bestAxis
                        MappableControl.STICK_R_UP, MappableControl.STICK_R_DOWN -> capturedAxisRY = bestAxis
                        MappableControl.STICK_R_LEFT, MappableControl.STICK_R_RIGHT -> capturedAxisRX = bestAxis
                        else -> {}
                    }
                    skipped.remove(target)
                    advance()
                    return RecordResult.Assigned
                }
                return RecordResult.Ignored
            }
            CaptureKind.TRIGGER -> {
                for ((axis, value) in axes) {
                    if (abs(value) >= TRIGGER_TRAVEL) {
                        assignedTriggerAxis[target] = axis
                        skipped.remove(target)
                        advance()
                        return RecordResult.Assigned
                    }
                }
                return RecordResult.Ignored
            }
            else -> return RecordResult.Ignored
        }
    }

    /**
     * Confirms a STICK target from accumulated peaks: the two strongest
     * distinct axes win. Axis ids (not directions) are all the handler needs —
     * Y inversion is a fixed handler contract (`normalizeAxis(invertY = true)`).
     */
    fun confirmStick(): RecordResult {
        val target = current ?: return RecordResult.NotArmed
        if (target.kind != CaptureKind.STICK) return RecordResult.Ignored
        val top = stickPeaks.entries
            .filter { it.value >= STICK_DEFLECTION }
            .sortedByDescending { it.value }
            .map { it.key }
            .distinct()
        if (top.size < 2) return RecordResult.Ignored
        assignedStickAxes[target] = Pair(top[0], top[1])
        stickPeaks.clear()
        skipped.remove(target)
        advance()
        return RecordResult.Assigned
    }

    fun skippedTargets(): Set<MappableControl> = skipped.toSet()

    /**
     * Applies captured assignments onto [base] (skipped targets keep base
     * values). Returns null when cancelled.
     */
    fun buildProfile(base: ControllerProfile): ControllerProfile? {
        if (cancelled) return null
        var profile = base
        // If a key was assigned to target T in this pass, any other target in base
        // that had that key but wasn't assigned in this pass should have its key cleared
        // so we don't produce duplicate key mappings for skipped targets.
        val assignedKeyCodes = assignedKeys.values.toSet()
        if (assignedKeys.isNotEmpty()) {
            profile = profile.copy(
                keyA = if (MappableControl.A in assignedKeys) profile.keyA else if (profile.keyA in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyA,
                keyB = if (MappableControl.B in assignedKeys) profile.keyB else if (profile.keyB in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyB,
                keyX = if (MappableControl.X in assignedKeys) profile.keyX else if (profile.keyX in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyX,
                keyY = if (MappableControl.Y in assignedKeys) profile.keyY else if (profile.keyY in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyY,
                keyL = if (MappableControl.L in assignedKeys) profile.keyL else if (profile.keyL in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyL,
                keyR = if (MappableControl.R in assignedKeys) profile.keyR else if (profile.keyR in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyR,
                keyZL = if (MappableControl.ZL in assignedKeys) profile.keyZL else if (profile.keyZL in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyZL,
                keyZR = if (MappableControl.ZR in assignedKeys) profile.keyZR else if (profile.keyZR in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyZR,
                keyPlus = if (MappableControl.PLUS in assignedKeys) profile.keyPlus else if (profile.keyPlus in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyPlus,
                keyMinus = if (MappableControl.MINUS in assignedKeys) profile.keyMinus else if (profile.keyMinus in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyMinus,
                keyHome = if (MappableControl.HOME in assignedKeys) profile.keyHome else if (profile.keyHome in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyHome,
                keyL3 = if (MappableControl.STICK_L_PRESS in assignedKeys) profile.keyL3 else if (profile.keyL3 in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyL3,
                keyR3 = if (MappableControl.STICK_R_PRESS in assignedKeys) profile.keyR3 else if (profile.keyR3 in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyR3,
                keyDpadUp = if (MappableControl.DPAD_UP in assignedKeys) profile.keyDpadUp else if (profile.keyDpadUp in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyDpadUp,
                keyDpadDown = if (MappableControl.DPAD_DOWN in assignedKeys) profile.keyDpadDown else if (profile.keyDpadDown in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyDpadDown,
                keyDpadLeft = if (MappableControl.DPAD_LEFT in assignedKeys) profile.keyDpadLeft else if (profile.keyDpadLeft in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyDpadLeft,
                keyDpadRight = if (MappableControl.DPAD_RIGHT in assignedKeys) profile.keyDpadRight else if (profile.keyDpadRight in assignedKeyCodes) KeyEvent.KEYCODE_UNKNOWN else profile.keyDpadRight
            )
        }
        for ((target, key) in assignedKeys) {
            profile = when (target) {
                MappableControl.A -> profile.copy(keyA = key)
                MappableControl.B -> profile.copy(keyB = key)
                MappableControl.X -> profile.copy(keyX = key)
                MappableControl.Y -> profile.copy(keyY = key)
                MappableControl.DPAD_UP -> profile.copy(keyDpadUp = key)
                MappableControl.DPAD_DOWN -> profile.copy(keyDpadDown = key)
                MappableControl.DPAD_LEFT -> profile.copy(keyDpadLeft = key)
                MappableControl.DPAD_RIGHT -> profile.copy(keyDpadRight = key)
                MappableControl.L -> profile.copy(keyL = key)
                MappableControl.R -> profile.copy(keyR = key)
                MappableControl.ZL -> profile.copy(keyZL = key)
                MappableControl.ZR -> profile.copy(keyZR = key)
                MappableControl.PLUS -> profile.copy(keyPlus = key)
                MappableControl.MINUS -> profile.copy(keyMinus = key)
                MappableControl.HOME -> profile.copy(keyHome = key)
                MappableControl.STICK_L_PRESS -> profile.copy(keyL3 = key)
                MappableControl.STICK_R_PRESS -> profile.copy(keyR3 = key)
                else -> profile
            }
        }
        // Hat-driven directions keep working only when the hat path is on.
        if (hatDirections.isNotEmpty()) profile = profile.copy(hatAsDpad = true)
        // Captured stick axes update the profile
        capturedAxisLX?.let { profile = profile.copy(axisLX = it) }
        capturedAxisLY?.let { profile = profile.copy(axisLY = it) }
        capturedAxisRX?.let { profile = profile.copy(axisRX = it) }
        capturedAxisRY?.let { profile = profile.copy(axisRY = it) }
        for ((target, axis) in assignedTriggerAxis) {
            profile = when (target) {
                MappableControl.ZL -> profile.copy(axisLTrigger = axis)
                MappableControl.ZR -> profile.copy(axisRTrigger = axis)
                else -> profile
            }
        }
        // Triggers captured as digital keys have no analog axis.
        for (target in listOf(MappableControl.ZL, MappableControl.ZR)) {
            if (assignedKeys.containsKey(target) && !assignedTriggerAxis.containsKey(target)) {
                profile = if (target == MappableControl.ZL) {
                    profile.copy(axisLTrigger = AXIS_UNUSED)
                } else {
                    profile.copy(axisRTrigger = AXIS_UNUSED)
                }
            }
        }
        return profile
    }

    private fun advance() {
        index++
        stickPeaks.clear()
        targetStartedAt = clock()
    }
}
