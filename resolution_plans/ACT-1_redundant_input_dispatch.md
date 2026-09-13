# ACT-1: Redundant Input Dispatch

**Severity**: 🟢 Low  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`  
**Lines**: 314-347

## Problem

Both `dispatchKeyEvent()` AND `onKeyDown()`/`onKeyUp()` delegate to the same `GamepadInputHandler`. This means the same key event could potentially be processed twice on some devices. The handler's idempotent bit-or/and-inv operations make this harmless, but it's unnecessarily redundant.

## Current Code

```kotlin
// dispatchKeyEvent handles events at the activity level
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.handleKeyEvent(event)) {
        return true
    }
    return super.dispatchKeyEvent(event)
}

// onKeyDown/onKeyUp are called by the default dispatch chain
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.onKeyDown(keyCode, event)) {
        return true
    }
    return super.onKeyDown(keyCode, event)
}

override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.onKeyUp(keyCode, event)) {
        return true
    }
    return super.onKeyUp(keyCode, event)
}
```

## Resolution

Keep only `dispatchKeyEvent` (which catches all key events earliest in the dispatch chain) and remove `onKeyDown`/`onKeyUp` overrides.

### Step 1: Remove `onKeyDown` and `onKeyUp` overrides

In `MainActivity.kt`, delete these two methods (lines 328-346):

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.onKeyDown(keyCode, event)) {
        return true
    }
    return super.onKeyDown(keyCode, event)
}

override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.onKeyUp(keyCode, event)) {
        return true
    }
    return super.onKeyUp(keyCode, event)
}
```

### Step 2: Keep `dispatchKeyEvent` and `dispatchGenericMotionEvent`

These two methods remain and handle all input:

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.handleKeyEvent(event)) {
        return true
    }
    return super.dispatchKeyEvent(event)
}

override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.onGenericMotionEvent(event)) {
        return true
    }
    return super.dispatchGenericMotionEvent(event)
}
```

### Step 3: Also remove `onGenericMotionEvent` override (lines 342-347)

Delete this method since `dispatchGenericMotionEvent` already handles it:

```kotlin
override fun onGenericMotionEvent(event: MotionEvent): Boolean {
    if (::gamepadHandler.isInitialized && gamepadHandler.onGenericMotionEvent(event)) {
        return true
    }
    return super.onGenericMotionEvent(event)
}
```

## Verification

1. Build the Android APK
2. Connect a physical gamepad to the phone
3. Test all buttons, sticks, and D-pad
4. Verify no input is lost or doubled
