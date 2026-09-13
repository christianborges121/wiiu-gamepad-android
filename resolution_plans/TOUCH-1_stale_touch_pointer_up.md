# TOUCH-1: Stale Touch State on `ACTION_POINTER_UP`

**Severity**: 🟢 Low  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/input/TouchInputHandler.kt`  
**Lines**: 137-148

## Problem

When `ACTION_POINTER_UP` fires with `pointerCount > 1`, the handler does nothing. This means `touch2` remains active in DSU state for 1-2 frames until the next `ACTION_MOVE` updates it.

## Current Code (Lines 137-148)

```kotlin
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
    if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.pointerCount > 1) {
        // Remaining pointer logic can re-evaluate on next move/down
    } else {
        // All touches released
        dsuServer.updateState { state ->
            state.touchButton = false
            state.touch1 = DSUPacket.TouchPointData(active = false)
            state.touch2 = DSUPacket.TouchPointData(active = false)
        }
    }
    return true
}
```

## Resolution

When a pointer lifts, re-evaluate the remaining pointers and update DSU state immediately.

### Step 1: Replace the `ACTION_POINTER_UP` handling

Replace the entire `ACTION_UP/ACTION_CANCEL/ACTION_POINTER_UP` block (lines 137-149) with:

```kotlin
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
    if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.pointerCount > 1) {
        // A secondary pointer lifted. Re-evaluate remaining pointers.
        val liftedIndex = event.actionIndex
        var touch1Active = false
        var touch1X: Short = 0
        var touch1Y: Short = 0

        // Find the first remaining pointer (skip the one being lifted)
        for (i in 0 until event.pointerCount) {
            if (i == liftedIndex) continue
            val x = event.getX(i)
            val y = event.getY(i)
            if (currentViewport.contains(x, y)) {
                touch1Active = true
                touch1X = currentViewport.normalizeX(x)
                touch1Y = currentViewport.normalizeY(y)
            }
            break // Only need the first remaining pointer
        }

        touchPacketCounter = ((touchPacketCounter + 1) and 0xFF).toByte()
        dsuServer.updateState { state ->
            state.touchButton = touch1Active
            state.touch1 = DSUPacket.TouchPointData(
                active = touch1Active,
                id = touchPacketCounter,
                x = touch1X,
                y = touch1Y
            )
            state.touch2 = DSUPacket.TouchPointData(active = false)
        }
    } else {
        // All touches released
        dsuServer.updateState { state ->
            state.touchButton = false
            state.touch1 = DSUPacket.TouchPointData(active = false)
            state.touch2 = DSUPacket.TouchPointData(active = false)
        }
    }
    return true
}
```

## Verification

1. Build the Android APK
2. Deploy to phone and connect to Cemu
3. Test 2-finger touch on the GamePad surface, then lift one finger
4. Verify the remaining touch is still tracked and the lifted touch clears immediately
