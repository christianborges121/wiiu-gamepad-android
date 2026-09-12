# Major finding: Android starts with no reconnect target

Date: 2026-09-12

## Evidence

- The captured phone screen (`android-reconnect-20260912-085735.png`) shows `Awaiting stream | Clients 0` and `Tx 0 | Rx 0`.
- A live ADB check found `com.cemupad` running and resumed in `MainActivity`; the process was healthy, not crashed.
- The Android log capture contains no `VideoStreamClient`, `MainActivity`, `DSUServer`, or `VideoDecoder` connection attempt after the restart.

## Code path

`MainActivity` stores the peer only in the process-local field `lastKnownClientIp`. It is assigned only by `dsuServer.onClientConnected`. On a new app process it is null. `onResume()` can start video only when either `activeClientAddress` or `lastKnownClientIp` is available.

Therefore a fresh app start cannot initiate TCP video on its own; it must first receive a new DSU data request from Cemu.
