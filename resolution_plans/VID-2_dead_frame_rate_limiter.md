# VID-2: Dead `FrameRateLimiter` and Misleading UI Toggle

**Severity**: 🟡 Medium  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/video/VideoDecoder.kt` (dead code)  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt` (misleading toggle)

## Problem

`FrameRateLimiter.kt` exists and `maxFps` is maintained in `VideoDecoder`, but the limiter is never called in the decode path. The comment at `VideoDecoder.kt:195-198` explains this was intentional (P-frame dropping corrupts H.264 state), but the UI still shows a "Limit to 30 FPS" toggle that does nothing. Users will toggle it and see no effect.

## Resolution

Disable the UI toggle and add a clear label. Keep the `FrameRateLimiter.kt` file and `maxFps` field for future encoder-side rate capping.

### Step 1: Grey out the FPS toggle in `MainScreen.kt`

In `MainScreen.kt`, find the "Limit to 30 FPS" `Switch` (around line 227). Add `enabled = false` to the `Switch` composable:

Replace:
```kotlin
Switch(
    checked = limitFps,
    onCheckedChange = { enabled ->
        limitFps = enabled
        onDisplaySettingsChanged(
            DisplaySettings(
                fitMode = selectedFitMode,
                resolutionPreset = selectedResolution,
                diagnosticsOverlayEnabled = diagnosticsEnabled,
                showConnectionHelp = showHelp,
                limitTo30Fps = enabled
            )
        )
    }
)
```

With:
```kotlin
Switch(
    checked = limitFps,
    enabled = false,
    onCheckedChange = { enabled ->
        limitFps = enabled
        onDisplaySettingsChanged(
            DisplaySettings(
                fitMode = selectedFitMode,
                resolutionPreset = selectedResolution,
                diagnosticsOverlayEnabled = diagnosticsEnabled,
                showConnectionHelp = showHelp,
                limitTo30Fps = enabled
            )
        )
    }
)
```

### Step 2: Update the description text

In `MainScreen.kt`, find the help text for the FPS toggle (around line 244). Update the text to be clearer:

Replace:
```kotlin
Text(
    text = "Limit to 30 FPS needs Cemu encoder support (coming soon) and is not enforced yet.",
```

With:
```kotlin
Text(
    text = "Coming soon — requires Cemu-side encoder rate cap. Currently disabled.",
```

### Step 3: Optionally add a label color to indicate disabled state

Change the "Limit to 30 FPS" label color on the same row (around line 222):

Replace:
```kotlin
Text(
    text = "Limit to 30 FPS",
    color = Color(0xFFEAF2FF),
    fontSize = 15.sp
)
```

With:
```kotlin
Text(
    text = "Limit to 30 FPS",
    color = Color(0xFF6B7A8E),
    fontSize = 15.sp
)
```

This changes the label to a dim grey to visually indicate it's disabled.

## What NOT to Change

- **Keep** `FrameRateLimiter.kt` — it will be used when encoder-side rate capping is added.
- **Keep** `maxFps` field in `VideoDecoder.kt` — it will be wired in when ready.
- **Keep** the `limitTo30Fps` field in `DisplaySettings.kt` and `AppSettingsCodec.kt` — the preference should persist for when the feature is enabled.

## Verification

1. Build the Android APK: `gradlew.bat assembleDebug`
2. Deploy to phone
3. Open the configuration drawer (Back button)
4. Verify the "Limit to 30 FPS" toggle is greyed out and non-interactive
5. Verify the description text says "Coming soon — requires Cemu-side encoder rate cap. Currently disabled."
