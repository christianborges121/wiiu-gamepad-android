# SRV-4: TCP `send()` Partial Write Not Handled

**Severity**: 🔴 High  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`  
**Lines**: 213

## Problem

`send()` on a TCP socket may return fewer bytes than requested (partial write). The code checks `sent <= 0` for error but does not loop on partial sends. A partially-sent frame corrupts the TCP stream for that client because the receiver expects exactly `13 + payloadSize` bytes.

## Current Code (Line 213)

```cpp
int sent = send((SOCKET)s, reinterpret_cast<const char*>(packet.data()), static_cast<int>(packet.size()), 0);
if (sent <= 0)
{
    // disconnect
}
```

## Resolution

> **NOTE**: If you are implementing **SRV-1** (mutex-during-send fix), that plan already includes the partial write fix in the rewritten `BroadcastFrame()` method. In that case, skip this plan — it is already resolved.

If implementing this standalone (without SRV-1):

### Step 1: Replace the TCP send block

In `VideoStreamServer.cpp`, replace the TCP send block inside the `for` loop in `BroadcastFrame()` (approximately lines 212-228):

Replace this:
```cpp
uintptr_t s = it->socket;
int sent = send((SOCKET)s, reinterpret_cast<const char*>(packet.data()), static_cast<int>(packet.size()), 0);
if (sent <= 0)
{
    cemuLog_log(LogType::Force, "VideoStreamServer: Client disconnected on send error");
#if defined(_WIN32)
    closesocket((SOCKET)s);
#else
    close((int)s);
#endif
    it = m_clients.erase(it);
}
else
{
    ++it;
}
```

With this:
```cpp
uintptr_t s = it->socket;
const char* buf = reinterpret_cast<const char*>(packet.data());
int remaining = static_cast<int>(packet.size());
bool sendFailed = false;

while (remaining > 0)
{
    int sent = send((SOCKET)s, buf, remaining, 0);
    if (sent <= 0)
    {
        sendFailed = true;
        break;
    }
    buf += sent;
    remaining -= sent;
}

if (sendFailed)
{
    cemuLog_log(LogType::Force, "VideoStreamServer: Client disconnected on send error");
#if defined(_WIN32)
    closesocket((SOCKET)s);
#else
    close((int)s);
#endif
    it = m_clients.erase(it);
}
else
{
    ++it;
}
```

### What Changed

The single `send()` call is now wrapped in a `while (remaining > 0)` loop. Each iteration advances the buffer pointer and decrements the remaining count. The send is only considered failed when `send()` returns `<= 0`.

## Verification

1. Build the Cemu fork
2. Stream video to the Android app over a congested network
3. Verify that the video stream remains stable without corruption
4. Verify that intentional disconnects are still detected
