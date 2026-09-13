# UDP video transport (v1, no FEC)

Date: 2026-09-12/13 (evening).

Protocol: `03-udp-protocol.md` (24-byte LE header, 1400 B payload cap,
per-client TCP/UDP negotiation via opcodes `0x11`/`0x12`).

## Cemu fork (`christianborges121/Cemu`)

- `VideoStreamServer`: per-client `{socket, addr, useUdp}` (was bare
  sockets); UDP send socket; `frameId`/`seq` atomics; `SendUdpFrame`
  fragments with START/END/IDR flags into stack buffers via `sendto`
  (portable, no heap); `BroadcastFrame` gains `isKeyframe` and routes
  per client; rx thread handles the new opcodes with per-socket lookup.
- `VideoEncoder`: `WasLastFrameKeyframe()` recorded where the force flag
  is consumed (clearing semantics unchanged).
- `StreamingCapture`: passes the flag through.
- Default stays TCP; UDP only after the phone requests it. TCP remains
  control + fallback.

## Android (`wiiu-gamepad-android`)

- `UdpVideoPacket`: header codec, validation (magic/version/geometry/
  MTU budget); 4/4 tests pass.
- `FrameReassembler`: 4 slots, 100 ms expiry, keep-latest eviction,
  seq-gap loss counting, IDR-drop events; 6/6 tests pass.
- `UdpVideoReceiver`: UDP :26761 listener thread, silence watchdog
  (2 s), liveness API for the activity watchdog.
- `MainActivity`: requests UDP on TCP connect; feeds whichever transport
  is live (no duplicate decode); falls back to TCP on UDP silence and
  stays there until reconnect. Decoder path shared.

## Status

- Cemu Release build passed (`Cemu/bin/Cemu_release.exe`).
- Android unit tests pass; APK with receiver installed.
- DEPLOYED 2026-09-12 22:13 (user closed Cemu first): prior exe backed
  up to `Cemu.exe.backup-20260912-221345`, new exe copied with matching
  SHA-256 `A393A038...B621D0`, resources synced. Settings, mlc01,
  profiles, and caches untouched.
- Live UDP test (switch-over, loss stats, fallback) runs once the user
  relaunches and boots a game.
- FEC parity, loss feedback to Cemu, and ref-frame invalidation remain
  open by design.

## Robustness pass (same night, pre-deploy)

Live session went black/0 FPS with a moving level-select on screen.
Prime suspects: blocking `sendto` stalling the shared encode worker,
silence detection only firing on receive timeouts (a trickle of
doomed datagrams suppresses both completion and fallback), and stale
UDP-only clients never pruned. Fixed, untested until deploy:

- Cemu: non-blocking UDP socket + 1 MB send buffer (drop-and-count
  instead of stall); per-600-frame UDP stats log; rx-break removes the
  client entry so UDP-only peers cannot linger.
- Android: silence check runs every loop iteration with reassembly
  stats in the log line.
- New Cemu binary built (`Cemu/bin/Cemu_release.exe`); deploy blocked
  on the running game.
- DEPLOYED 2026-09-12 22:39 (user closed Cemu): backup
  `Cemu.exe.backup-20260912-223926`, SHA-256 `BDA8A2D3...B33D` match.
  Live test runs on relaunch.
