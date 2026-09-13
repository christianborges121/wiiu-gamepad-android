# ACT-2: Self-Assignment No-Op in `onClientDisconnected`

**Severity**: 🟢 Low  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`  
**Line**: 142

## Problem

```kotlin
lastKnownClientIp = lastKnownClientIp
```

This is a self-assignment that does nothing. The intent was to preserve the last known IP, but it's already preserved by not overwriting it.

## Resolution

Remove the no-op line and simplify the callback.

### Step 1: Replace the `onClientDisconnected` callback

In `MainActivity.kt`, replace lines 139-145:

```kotlin
dsuServer.onClientDisconnected = {
    // Keep the most recent client IP so a resumed app can reconnect without waiting for a new DSU packet.
    if (dsuServer.activeClientAddress == null) {
        lastKnownClientIp = lastKnownClientIp
    }
    stopVideoStream()
}
```

With:

```kotlin
dsuServer.onClientDisconnected = {
    // lastKnownClientIp is intentionally preserved across disconnects
    // so a resumed app can reconnect without waiting for a new DSU packet.
    stopVideoStream()
}
```

## Verification

1. Build the Android APK
2. Connect to Cemu, then disconnect
3. Verify that reconnecting (resume or new Cemu session) still auto-connects video
