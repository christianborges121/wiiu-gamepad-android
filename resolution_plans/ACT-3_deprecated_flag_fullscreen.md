# ACT-3: Deprecated `FLAG_FULLSCREEN`

**Severity**: 🟢 Low  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`  
**Line**: 117

## Problem

`FLAG_FULLSCREEN` is deprecated on API 30+ (Android 11). The `WindowInsetsControllerCompat` call at lines 113-116 already handles immersive mode correctly, making `FLAG_FULLSCREEN` redundant.

## Current Code (Lines 112-118)

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
WindowInsetsControllerCompat(window, window.decorView).apply {
    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

## Resolution

Remove the deprecated `FLAG_FULLSCREEN` line.

### Step 1: Delete line 117

Remove this line:
```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
```

Keep `FLAG_KEEP_SCREEN_ON` — it is not deprecated.

### Step 2: Remove the `@Suppress("DEPRECATION")` if present

Check if there's a `@Suppress("DEPRECATION")` annotation on `onCreate` or nearby. If it only exists for `FLAG_FULLSCREEN`, remove it.

## Verification

1. Build the Android APK
2. Deploy to phone
3. Verify the app still runs fullscreen with no system bars
4. Verify no deprecation warning in build output for this line
