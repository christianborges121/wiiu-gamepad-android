# Current harness handoff

Last updated: 2026-09-13 08:33 (America/New_York)

Read this file first. Keep this file current at the end of every session so a new harness can resume without the prior chat.

## Where we left off

The system is fully functional end-to-end with high performance:
- **Video**: 60 FPS H.264 hardware encoding (NVENC/AMF) over low-latency UDP (`26761`). Motion artifacts and reference frame corruption completely resolved.
- **Audio**: 48 kHz stereo PCM audio tapped from Cemu's DSP (`snd_core`) streaming over UDP (`26762`). Buzzing and slowdown resolved.
- **Input & Motion**: DSU over UDP (`26760`) with buttons, sticks (deadzones + inverted Y), multi-touch viewport normalization, and 6-axis gyro/accel with in-app zero-bias calibration.
- **UI & Ergonomics**: Refactored modern dark card-based settings drawer. Back button access only, scrim tap dismissal with auto-save, microphone toggle with red dot indicator, and logarithmic vibration slider.
- **Next Phase Ready**: Phase 4.0 Subsystem Modularization (`Cemu/src/streaming/`) and 1-Click Cemu UI Pairing.

## Just completed (this session)

1. **Vibration Slider & Tactile Perception Investigation**:
   - Implemented 0–100% vibration intensity slider with live pulse preview in the settings drawer.
   - Diagnosed root cause of flat vibration: Samsung One UI clamps touch haptics to 40% motor power; tactile perception is logarithmic. Documented in `investigation/2026-09-13-vibration-intensity/01-vibration-intensity-deep-dive.md`.
2. **Subsystem Modularization & 1-Click Cemu UI Auto-Configuration Design**:
   - Isolated all streaming, capture, and networking into `Cemu/src/streaming/` with non-invasive delegate `CemuPadBridge.h`.
   - Designed 1-click wxWidgets `CemuPadPairingDialog` and "Auto-Discover CemuPad..." button in Cemu Input Settings that programmatically binds Cemu's native `DSUClient` controller.
3. **Checklist-Based Implementation Plans**:
   - Converted all upcoming phase plans in `plans/` into strict, task-by-task `- [ ]` checklist documents.
   - Created `plans/AI_HARNESS_TESTING_GUIDE.md` with exact Cemu CLI commands, ROM paths (`SUPER MARIO 3D WORLD (US).wua`), log inspection commands, and screenshot methods.
   - Cleaned up obsolete documentation (`copilot_project_checklist.md`, `code_review.md`, `architecture_and_plan.md`, `resolution_plans/`).

## Next Actionable Step
Begin executing **Phase 4.0** following [**`plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md`**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md), checking off `- [ ]` steps as you go.

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

