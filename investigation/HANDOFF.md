# Current harness handoff

Last updated: 2026-09-13 06:25 (America/New_York)

Read this file first. Keep this file current at the end of every session so a new harness can resume without the prior chat.

## Where we left off

The system is fully functional end-to-end with high performance:
- **Video**: 60 FPS H.264 hardware encoding (NVENC/AMF) over low-latency UDP (`26761`). Motion artifacts and reference frame corruption completely resolved.
- **Audio**: 48 kHz stereo PCM audio tapped from Cemu's DSP (`snd_core`) streaming over UDP (`26762`). Buzzing and slowdown resolved.
- **Input & Motion**: DSU over UDP (`26760`) with buttons, sticks (deadzones + inverted Y), multi-touch viewport normalization, and 6-axis gyro/accel with in-app zero-bias calibration.
- **UI & Ergonomics**: Refactored modern dark card-based settings drawer. 30 FPS limit removed. Descriptions streamlined. Connection card auto-displays when offline. Drawer opens via Back button only; closes and persists settings via "Done" button or tapping outside (scrim click-off).

## Just completed (this session)

1. **Menu UI & Interaction Polish**:
   - Removed 30 FPS limit switch, label, and codec serialization (permanently out of scope; native 60 FPS).
   - Removed verbose subtitles under GamePad audio, Vibration, Resolution, and Diagnostics overlay.
   - Renamed "Vibration & rumble" to "Vibration".
   - Removed manual "Connection help" toggle; startup help card now displays dynamically when disconnected (`!isVideoStreaming`).
   - Disabled edge-swipe to open drawer (`gesturesEnabled = drawerState.isOpen`); only the Android Back button opens the drawer.
   - Enabled click-off (scrim tap) dismissal with automatic settings persistence on close.
   - Restyled drawer with dark cards (`#161D2B`), cyan section headers, custom controls, and header "Done" button.

2. **Video Motion Artifact Resolution**:
   - Fixed encoder frame overwrite in Cemu's `VideoEncoder.cpp` by using a callback-driven encoding pipeline and disabling B-frames (`CODECAPI_AVEncMPVDefaultBPictureCount = 0`).
   - Removed redundant CPU Annex-B re-parsing in Android's `VideoStreamClient.kt`, trusting the UDP packet header's IDR flag.
   - Recorded and verified in-game movement in *Super Mario 3D World*: crisp, artifact-free 60 FPS video.

3. **Audio Quality Fix**:
   - Fixed circular DMA buffer ring wrapping and sample pacing in Cemu's `snd_core`.
   - Continuous 48 kHz stereo PCM transmission eliminated audio buzzing, crackling, and half-speed playback.

4. **Workspace & Documentation Cleanup**:
   - Removed loose temporary `.png`, `.log`, and `.ps1` files from project root.
   - Updated checklist and handoff documentation to reflect 100% completion of Phases 0–3 and active polish in Phase 4.

## Device & Environment

- **Product**: Samsung Galaxy S23 FE (`SM_S711U1`, `r11q`)
- **Transport**: Wireless ADB over Wi-Fi (`192.168.68.109:44985` / mDNS)
- **Cemu Host**: Windows 11, running *Super Mario 3D World (US)* via Cemu fork with Latte GamePad capture.
- **Network Ports**:
  - DSU Controller Input: UDP `26760` (phone listens, Cemu polls)
  - Video Streaming: UDP `26761` (phone listens, Cemu streams; TCP `26761` control)
  - Audio Streaming: UDP `26762` (phone listens, Cemu streams)
  - UDP Auto-Discovery: UDP `26765` broadcast

## Key Files

- `android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt` (settings drawer, fit-mode, touch surface)
- `android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt` (lifecycle, DSU, video, and audio coordination)
- `android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt` (UDP video receiver & packet assembly)
- `android-gamepad-app/app/src/main/java/com/cemupad/audio/AudioStreamClient.kt` (UDP audio receiver & AudioTrack player)
- `Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp` (hardware MFT H.264 encoder)
- `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp` (UDP packet framing & client transport)
- `Cemu/src/Cafe/OS/libs/snd_core/snd_core.cpp` (GamePad audio DSP tap)

## Validation State

- `./gradlew.bat testDebugUnitTest` passing (100%).
- Real-time in-game verification on physical Galaxy S23 FE: video streaming, audio playback, button/analog controls, gyro motion, and menu navigation fully verified.

## What's Next in the Roadmap

1. **Dynamic Encoder Bitrate & Resolution Switching**:
   - Send opcode from Android drawer (e.g. 480p, 720p, 1080p; 4–12 Mbps) over the TCP control channel (`26761`) so Cemu reconfigures MFT encoder on the fly without restarting emulation.
2. **Automated Discovery Handshake Polish**:
   - Refine UDP broadcast discovery (`26765`) so Android automatically discovers and connects to Cemu without manual IP entry.
3. **Server Thread Reaping (SRV-2)**:
   - Ensure cleanly detached/joined `m_rxThreads` in `VideoStreamServer.cpp` across rapid app restart cycles.

