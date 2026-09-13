# DS-1: Shared Packet Counter Across All Clients

**Severity**: 🟢 Low  
**File**: `android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUServer.kt`  
**Lines**: 236-244

## Problem

The `packetCounter` is a single `AtomicInteger` shared across all DSU clients. The DSU spec assigns per-client sequence numbers. Since the project targets single-client (one Cemu instance), this is benign in practice.

## Resolution

No change recommended at this time. If multi-client support is ever added, convert `packetCounter` to a per-client counter stored in `activeClients` alongside the timestamp.

### Future Implementation (If Needed)

1. Change `activeClients` from `ConcurrentHashMap<InetSocketAddress, Long>` to `ConcurrentHashMap<InetSocketAddress, ClientState>` where:
   ```kotlin
   data class ClientState(
       var lastSeenMs: Long,
       var packetCounter: Int = 0
   )
   ```

2. In `handlePacket`, increment `activeClients[clientAddr]!!.packetCounter` instead of the global counter.

3. In `runPushLoop`, iterate with per-client counters.

## Status: Won't Fix (Acceptable)
