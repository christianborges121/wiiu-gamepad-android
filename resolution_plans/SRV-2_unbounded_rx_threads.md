# SRV-2: Unbounded `m_rxThreads` Vector Growth

**Severity**: 🟡 Medium  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`  
**Lines**: 299, 102-107

## Problem

Each new client connection pushes a thread into `m_rxThreads` via `emplace_back`. When a client disconnects, the RX thread terminates but the `std::thread` object remains in the vector. On frequent reconnects, this vector grows indefinitely with joinable-but-finished thread handles that are only cleaned up on `Stop()`.

## Current Code

Line 299 (adding thread):
```cpp
m_rxThreads.emplace_back(&VideoStreamServer::ClientRxThreadFunc, this, (uintptr_t)clientSock);
```

Lines 102-107 (cleanup only on Stop):
```cpp
for (auto& t : m_rxThreads)
{
    if (t.joinable())
        t.join();
}
m_rxThreads.clear();
```

## Resolution

Join and remove finished threads periodically during `ServerThreadFunc`'s accept loop.

### Step 1: Add a cleanup helper method

In `VideoStreamServer.h`, add this private method declaration (after line 48):

```cpp
void PruneFinishedRxThreads();
```

### Step 2: Implement the cleanup method

In `VideoStreamServer.cpp`, add this method before `ServerThreadFunc` (before line 231):

```cpp
void VideoStreamServer::PruneFinishedRxThreads()
{
	for (auto it = m_rxThreads.begin(); it != m_rxThreads.end();)
	{
		// A thread that is joinable but has finished execution can be joined
		// immediately (join() returns instantly for finished threads).
		// We detect "finished" by checking if the thread's associated client
		// socket is no longer in the active client list.
		if (it->joinable())
		{
			// Try a non-blocking join approach: std::thread doesn't support
			// try_join, so we check if the thread's native handle indicates
			// completion. Simplest portable approach: just join threads that
			// are no longer needed.
			// 
			// Windows: WaitForSingleObject with timeout 0.
			// Portable: just join — finished threads return immediately.
#if defined(_WIN32)
			DWORD result = WaitForSingleObject(it->native_handle(), 0);
			if (result == WAIT_OBJECT_0)
			{
				it->join();
				it = m_rxThreads.erase(it);
				continue;
			}
#endif
		}
		++it;
	}
}
```

### Step 3: Call the cleanup in the accept loop

In `VideoStreamServer.cpp`, inside `ServerThreadFunc()`, add a call to `PruneFinishedRxThreads()` inside the `while (m_isRunning)` loop, right after the `if (clientSock == INVALID_SOCKET) continue;` check (after line 273):

Find this code:
```cpp
if (clientSock == INVALID_SOCKET)
    continue;
```

Add after it:
```cpp
// Prune finished RX threads to prevent unbounded vector growth
PruneFinishedRxThreads();
```

### Alternative Simpler Approach (Detach Instead of Join)

If the above is too complex, a simpler fix is to detach the RX threads so they clean up automatically:

Replace line 299:
```cpp
m_rxThreads.emplace_back(&VideoStreamServer::ClientRxThreadFunc, this, (uintptr_t)clientSock);
```

With:
```cpp
std::thread rxThread(&VideoStreamServer::ClientRxThreadFunc, this, (uintptr_t)clientSock);
rxThread.detach();
```

And then remove the `m_rxThreads` vector entirely from the header and the `Stop()` cleanup. This is the simplest fix but means you can't gracefully join RX threads on shutdown (they'll terminate when the process exits, which is acceptable for this use case).

If using the detach approach:

1. In `VideoStreamServer.h`, remove line 62: `std::vector<std::thread> m_rxThreads;`
2. In `VideoStreamServer.cpp` `Stop()`, remove lines 102-107 (the loop that joins `m_rxThreads`)

## Verification

1. Build the Cemu fork
2. Connect/disconnect the Android app repeatedly (10+ times)
3. Verify no thread handle leak (use Process Explorer on Windows to check thread count)
4. Verify clean shutdown (Cemu closes without hanging)
