# SRV-3: `DWORD` for `SO_RCVTIMEO` Not Guarded

**Severity**: 🟢 Low  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`  
**Line**: 263

## Problem

```cpp
DWORD timeout = 500;
setsockopt(listenSock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));
```

`DWORD` is a Windows type. On Linux/macOS, `SO_RCVTIMEO` expects a `struct timeval`. This line is not wrapped in a `#if defined(_WIN32)` guard.

## Resolution

Wrap the timeout setting in a platform guard.

### Step 1: Replace lines 262-264

In `VideoStreamServer.cpp`, replace:

```cpp
// Set 500ms accept timeout so we can exit cleanly
DWORD timeout = 500;
setsockopt(listenSock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));
```

With:

```cpp
// Set 500ms accept timeout so we can exit cleanly
#if defined(_WIN32)
DWORD timeout = 500;
setsockopt(listenSock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));
#else
struct timeval tv;
tv.tv_sec = 0;
tv.tv_usec = 500000;
setsockopt(listenSock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
#endif
```

## Verification

1. Build the Cemu fork on Windows — verify no change in behavior
2. (If ever building on Linux) Verify the listen socket times out correctly
