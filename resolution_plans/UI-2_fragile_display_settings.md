# UI-2: Fragile Inline `DisplaySettings` Construction

**Severity**: 🟡 Medium  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt`  
**Lines**: 162-170, 196-204, 230-238, 266-274, 299-305 (6 sites)

## Problem

Every settings toggle manually constructs a new `DisplaySettings(...)` with all fields listed inline. If a new field is added to `DisplaySettings`, every site must be updated or it silently reverts to defaults. There are 6 such sites in `MainScreen.kt`.

## Resolution

Use a single helper lambda that calls `copy()` on the current settings, then replace all 6 inline constructions.

### Step 1: Add a helper lambda inside `MainScreen`

In `MainScreen.kt`, after the `LaunchedEffect(displaySettings)` block (after line 101), add this helper:

```kotlin
fun currentSettings() = DisplaySettings(
    fitMode = selectedFitMode,
    resolutionPreset = selectedResolution,
    diagnosticsOverlayEnabled = diagnosticsEnabled,
    showConnectionHelp = showHelp,
    limitTo30Fps = limitFps
)
```

### Step 2: Replace all 6 inline `DisplaySettings(...)` constructions

**Site 1** (diagnostics toggle, ~line 163): Replace the `onCheckedChange` lambda body:
```kotlin
onCheckedChange = { enabled ->
    diagnosticsEnabled = enabled
    onDisplaySettingsChanged(currentSettings().copy(diagnosticsOverlayEnabled = enabled))
}
```

**Site 2** (connection help toggle, ~line 196): Replace the `onCheckedChange` lambda body:
```kotlin
onCheckedChange = { enabled ->
    showHelp = enabled
    onDisplaySettingsChanged(currentSettings().copy(showConnectionHelp = enabled))
}
```

**Site 3** (limit FPS toggle, ~line 230): Replace the `onCheckedChange` lambda body:
```kotlin
onCheckedChange = { enabled ->
    limitFps = enabled
    onDisplaySettingsChanged(currentSettings().copy(limitTo30Fps = enabled))
}
```

**Site 4** (fit mode dropdown, ~line 267): Replace the `onClick` lambda body:
```kotlin
onClick = {
    selectedFitMode = mode
    showFitMenu = false
    onDisplaySettingsChanged(currentSettings().copy(fitMode = mode))
}
```

**Site 5** (resolution dropdown, ~line 299): Replace the `onClick` lambda body:
```kotlin
onClick = {
    selectedResolution = preset
    showResolutionMenu = false
    onDisplaySettingsChanged(currentSettings().copy(resolutionPreset = preset))
}
```

### Finding the Sites

To find all 6 sites, search for `DisplaySettings(` in `MainScreen.kt`. Each occurrence inside a lambda is an inline construction that should be replaced.

### What Changed

Instead of manually listing all 5 fields in every construction site, each toggle now:
1. Updates its own local state variable
2. Calls `currentSettings()` to build from all local state
3. Uses `.copy(field = newValue)` for the specific field being changed

When a new field is added to `DisplaySettings`, only `currentSettings()` needs updating.

## Verification

1. Build the Android APK: `gradlew.bat assembleDebug`
2. Deploy to phone
3. Open configuration drawer
4. Toggle each setting (diagnostics, connection help, fit mode, resolution) and verify the setting persists correctly
5. Close and reopen the app to verify SharedPreferences persistence
