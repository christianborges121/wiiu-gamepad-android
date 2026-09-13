# VID-1: Thread Churn on Opcode Send

**Severity**: 🟡 Medium  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt`  
**Lines**: 108-121

## Problem

`sendOpcode()` spawns a new `Thread` on every call. Under IDR recovery storms (frame loss → 30-frame window → repeat), this can create many short-lived threads. Should reuse a single-thread executor.

## Current Code (Lines 107-121)

```kotlin
private fun sendOpcode(opcode: Int, name: String) {
    Thread {
        try {
            synchronized(this) {
                outStream?.let {
                    it.writeByte(opcode)
                    it.flush()
                    Logger.i(TAG, "Sent $name to Cemu video server")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to send $name: ${e.message}")
        }
    }.start()
}
```

## Resolution

Replace the per-call `Thread` with a cached single-thread executor.

### Step 1: Add an executor field to `VideoStreamClient`

In `VideoStreamClient.kt`, add this import at the top of the file:

```kotlin
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
```

Add a field after the `outStream` declaration (after line 46):

```kotlin
private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "CemuPad-OpcodeSender").apply { isDaemon = true }
}
```

### Step 2: Replace the `sendOpcode` method

Replace the entire `sendOpcode` method (lines 107-121) with:

```kotlin
private fun sendOpcode(opcode: Int, name: String) {
    sendExecutor.execute {
        try {
            synchronized(this) {
                outStream?.let {
                    it.writeByte(opcode)
                    it.flush()
                    Logger.i(TAG, "Sent $name to Cemu video server")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to send $name: ${e.message}")
        }
    }
}
```

### Step 3: Shut down the executor in `stop()`

In the `stop()` method (lines 66-71), add executor shutdown after `workerThread = null`:

Replace:
```kotlin
fun stop() {
    if (!isRunning.getAndSet(false)) return
    closeSocket()
    workerThread?.interrupt()
    workerThread = null
}
```

With:
```kotlin
fun stop() {
    if (!isRunning.getAndSet(false)) return
    closeSocket()
    workerThread?.interrupt()
    workerThread = null
    sendExecutor.shutdownNow()
}
```

## Verification

1. Build the Android APK: `gradlew.bat assembleDebug`
2. Deploy to phone and connect to Cemu
3. Trigger IDR recovery by temporarily blocking video packets (e.g., pull Cemu window focus)
4. Verify logcat does not show excessive thread creation
5. Verify IDR requests still work (video recovers after glitch)
