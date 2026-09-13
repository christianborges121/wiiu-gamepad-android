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
    HOME("Home", CaptureKind.BUTTON),
    STICK_L_PRESS("Left Stick Press", CaptureKind.BUTTON),
    STICK_R_PRESS("Right Stick Press", CaptureKind.BUTTON),
    STICK_L_MOVE("Left Stick Move", CaptureKind.STICK),
    STICK_R_MOVE("Right Stick Move", CaptureKind.STICK);

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
    // Live ownership code -> target, seeded from base (first wins on dupes)
    // and updated on every assignment, so re-mapping can never stack two
    // targets onto one key across passes (the saved-profile scramble).
    private val ownedCodes = mutableMapOf<Int, MappableControl>()

    private val assignedKeys = mutableMapOf<MappableControl, Int>()
    private val assignedStickAxes = mutableMapOf<MappableControl, Pair<Int, Int>>()
    private val assignedTriggerAxis = mutableMapOf<MappableControl, Int>()
    private val usedKeyCodes = mutableSetOf<Int>()
    private val hatDirections = mutableSetOf<MappableControl>()
    private val skipped = mutableSetOf<MappableControl>()
    private val stickPeaks = mutableMapOf<Int, Float>()

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
        rebuildOwnership()
        hatDirections.clear()
        skipped.clear()
        stickPeaks.clear()
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
        rebuildOwnership()
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
        val owner = ownedCodes[keyCode]
        if (owner != null && owner != target) return RecordResult.Conflict
        usedKeyCodes.add(keyCode)
        // Target abandons its previous codes; the new code is now its own.
        ownedCodes.entries.removeAll { it.value == target }
        ownedCodes[keyCode] = target
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
                for ((axis, value) in axes) {
                    if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) continue
                    val peak = stickPeaks[axis] ?: 0f
                    if (abs(value) > peak) stickPeaks[axis] = abs(value)
                }
                return RecordResult.Assigned
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

    private fun rebuildOwnership() {
        ownedCodes.clear()
        val base = baseProfile ?: return
        val pairs = listOf(
            MappableControl.A to base.keyA,
            MappableControl.B to base.keyB,
            MappableControl.X to base.keyX,
            MappableControl.Y to base.keyY,
            MappableControl.DPAD_UP to base.keyDpadUp,
            MappableControl.DPAD_DOWN to base.keyDpadDown,
            MappableControl.DPAD_LEFT to base.keyDpadLeft,
            MappableControl.DPAD_RIGHT to base.keyDpadRight,
            MappableControl.L to base.keyL,
            MappableControl.R to base.keyR,
            MappableControl.ZL to base.keyZL,
            MappableControl.ZR to base.keyZR,
            MappableControl.PLUS to base.keyPlus,
            MappableControl.MINUS to base.keyMinus,
            MappableControl.HOME to base.keyHome,
            MappableControl.STICK_L_PRESS to base.keyL3,
            MappableControl.STICK_R_PRESS to base.keyR3
        )
        for ((target, code) in pairs) {
            if (code != 0 && code != KeyEvent.KEYCODE_UNKNOWN) {
                ownedCodes.putIfAbsent(code, target)
            }
        }
        // Replay this pass's assignments on top.
        for ((target, code) in assignedKeys) {
            ownedCodes.entries.removeAll { it.value == target }
            ownedCodes[code] = target
        }
    }

    /**
     * Applies captured assignments onto [base] (skipped targets keep base
     * values). Returns null when cancelled.
     */
    fun buildProfile(base: ControllerProfile): ControllerProfile? {
        if (cancelled) return null
        var profile = base
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
        for ((target, axes) in assignedStickAxes) {
            profile = when (target) {
                MappableControl.STICK_L_MOVE -> profile.copy(axisLX = axes.first, axisLY = axes.second)
                MappableControl.STICK_R_MOVE -> profile.copy(axisRX = axes.first, axisRY = axes.second)
                else -> profile
            }
        }
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
