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
- NOT YET DEPLOYED: game running, Cemu.exe locked. Deploy + live UDP
  test (switch-over, loss stats, fallback) next session.
- FEC parity, loss feedback to Cemu, and ref-frame invalidation remain
  open by design.
