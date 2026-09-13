# SRV-1: Mutex Held During Blocking TCP `send()`

**Severity**: 🔴 High  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`  
**Lines**: 203-228

## Problem

`BroadcastFrame()` holds `m_clientsMutex` while calling `send()` on TCP sockets. If a client's TCP buffer is full, `send()` blocks, holding the mutex. This stalls the encode worker, the accept thread, and all RX threads that need the mutex.

## Current Code (Lines 203-228)

```cpp
std::lock_guard<std::mutex> lock(m_clientsMutex);
for (auto it = m_clients.begin(); it != m_clients.end();)
{
    // ... UDP path (non-blocking, fine) ...
    // ... TCP path:
    int sent = send((SOCKET)s, reinterpret_cast<const char*>(packet.data()), static_cast<int>(packet.size()), 0);
    if (sent <= 0)
    {
        // disconnect client
    }
}
```

## Resolution

Copy the client list snapshot under the lock, then send outside the lock. Remove failed clients afterward under the lock.

### Step 1: Replace the entire `BroadcastFrame()` method body

In `VideoStreamServer.cpp`, replace the `BroadcastFrame()` method (lines 178-229) with:

```cpp
void VideoStreamServer::BroadcastFrame(uint8 packetType, uint64 ptsUs, const uint8* data, size_t size, bool isKeyframe)
{
	if (!data || size == 0)
		return;

	std::vector<uint8> packet;
	packet.reserve(13 + size);

	// 1 byte: packet type (0x01 = H264 NAL)
	packet.push_back(packetType);

	// 4 bytes: size (little-endian uint32)
	uint32 len = static_cast<uint32>(size);
	packet.push_back(static_cast<uint8>(len & 0xFF));
	packet.push_back(static_cast<uint8>((len >> 8) & 0xFF));
	packet.push_back(static_cast<uint8>((len >> 16) & 0xFF));
	packet.push_back(static_cast<uint8>((len >> 24) & 0xFF));

	// 8 bytes: ptsUs (little-endian uint64)
	for (int i = 0; i < 8; ++i)
		packet.push_back(static_cast<uint8>((ptsUs >> (i * 8)) & 0xFF));

	// Payload
	packet.insert(packet.end(), data, data + size);

	// Snapshot the client list under the lock
	std::vector<ClientInfo> clientSnapshot;
	{
		std::lock_guard<std::mutex> lock(m_clientsMutex);
		clientSnapshot = m_clients;
	}

	// Send to each client WITHOUT holding the lock
	std::vector<uintptr_t> failedSockets;
	for (auto& client : clientSnapshot)
	{
		if (client.useUdp)
		{
			SendUdpFrame(client.addr, ptsUs, data, size, isKeyframe);
			continue;
		}

		uintptr_t s = client.socket;
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
			failedSockets.push_back(s);
		}
	}

	// Remove failed clients under the lock
	if (!failedSockets.empty())
	{
		std::lock_guard<std::mutex> lock(m_clientsMutex);
		for (auto failedSocket : failedSockets)
		{
			for (auto it = m_clients.begin(); it != m_clients.end(); ++it)
			{
				if (it->socket == failedSocket)
				{
					m_clients.erase(it);
					break;
				}
			}
		}
	}
}
```

### What Changed

1. **Snapshot pattern**: The client list is copied under the lock, then the lock is released before any `send()` calls. This prevents blocking the mutex.
2. **Send loop**: `send()` is now called in a `while (remaining > 0)` loop to handle partial writes (this also resolves **SRV-4**).
3. **Deferred removal**: Failed sockets are collected in a vector and removed under the lock after all sends complete.

### Step 2: No changes needed to `VideoStreamServer.h`

The method signature is unchanged.

## Verification

1. Build the Cemu fork
2. Connect the Android app and stream video
3. Simulate network congestion (e.g., `tc` on Linux or Clumsy on Windows) to verify that a slow client doesn't stall the encoder
4. Verify that client disconnects are still detected and cleaned up
