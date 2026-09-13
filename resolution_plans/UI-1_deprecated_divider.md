# UI-1: Deprecated `Divider` Composable

**Severity**: 🟢 Low  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt`  
**Line**: 146

## Problem

`Divider` is deprecated in Material3 Compose. The replacement is `HorizontalDivider`.

## Current Code (Line 146)

```kotlin
Divider(color = Color(0xFF2A3348))
```

## Resolution

### Step 1: Update the import

In `MainScreen.kt`, replace the import:

```kotlin
import androidx.compose.material3.Divider
```

With:

```kotlin
import androidx.compose.material3.HorizontalDivider
```

### Step 2: Replace the usage

Replace line 146:

```kotlin
Divider(color = Color(0xFF2A3348))
```

With:

```kotlin
HorizontalDivider(color = Color(0xFF2A3348))
```

The API is the same — `HorizontalDivider` accepts the same `color`, `thickness`, and `modifier` parameters.

## Verification

1. Build the Android APK: `gradlew.bat assembleDebug`
2. Open the configuration drawer
3. Verify the divider line still renders correctly below the "Configuration" title
4. Verify no deprecation warnings in build output for `Divider`
