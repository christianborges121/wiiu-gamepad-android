# Current harness handoff

Last updated: 2026-09-13 (America/New_York) — Phase 4 & Phase 6 completion session

Read this file first. Keep this file current at the end of every session so a new harness can resume without the prior chat.

## Where we left off

- **Cemu fork**: Committed and pushed to `main` (`79fbcb59`).
  - Multi-stage MFT encoder fallback (hardware -> software -> CLSID direct) with 16-aligned dimension handling (`848x480` fallback).
  - OpenGL streaming capture with asynchronous double-buffered PBO readback (`OpenGLRenderer::HandleStreamingCapture`).
  - Boot-time subsystem initialization in `CemuApp::OnInit` / `OnExit`.
  - Phase 4.0 through 4.4 and Phase 6 streaming backends fully integrated.
- **Android App**: Verified live on physical hardware (Samsung Galaxy S23 FE, Razer Edge 5G, and Lenovo Legion Tab TB373FU).
  - Dynamic `DEVICE_AUTO` resolution resolution for standard macroblock alignment.
  - Diagnostics debug bundle export in drawer.
  - Phase 6 Controller Mapping Wizard with Razer Kishi auto-detection.
  - Edge-swipe drawer sensitivity, accordion sections, and live diagnostics HUD.
- **Next Phase**: Phase 5 (Release Packaging, ProGuard/R8 & CI/CD pipeline).

Phase 4.2 is **code-complete and compile-verified but UNCOMMITTED** (awaiting user test):
- C++ (`VideoStreamServer` opcodes `0x14`/`0x15` with exact-read LE parsing, `VideoEncoder`
  `SetBitrate` live via `ICodecAPI` + `SetResolution` allowlisted without deadlock); `CemuBin`
  Release rebuild exit 0. Adaptations noted in plan (no `HandleClientCommands` exists;
  `RequestKeyframe()` not `ForceKeyframe()`; media files still under `Cafe/HW/Latte/Renderer/`).
- Android (`VideoStreamClient.sendBitrate/sendResolution` 5-byte LE packets, `videoBitrateMbps`
  persisted setting, DISPLAY drawer bitrate dropdown, resolution changes now command the encoder);
  `testDebugUnitTest` 63/63 green (new `VideoEncoderControlTest`, extended codec tests) +
  `assembleDebug` exit 0. Fixed one self-inflicted break: positional `decode()` call in old test.
- `plans/PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md` Section 3 + 4.1/4.2 flipped; live check 4.3 (12 Mbps
  + 720p in-game, Cemu log lines, decoder re-sync) left for user.

Deployed 2026-09-13 ~2:11 PM (debug bundle): Logger file mirror (2×2MB rotation),
PixelCopy screenshot + device/codec report + ZIP + share sheet, DEBUG drawer section,
FileProvider wired; FileLogSinkTest 3/3 + DebugBundleTest 3/3, full suite green, phone
installed + file logging verified live on device. UNCOMMITTED.
Remote black-screen case (Razer Edge G3x/144Hz, tester remote): awaiting bundle/photos.
Older deploys: 11:50 AM (Phase 6 APK), 11:25 AM (PIN UI removed, bak-20260913-1230),
11:20 AM (discovery fix), 11:11 AM (4.4 batch), 10:53 AM (4.3 batch), 10:20 AM (4.2 batch).
Deployed 2026-09-13 ~3:05 PM (Cemu boot-time discovery, UNCOMMITTED): `CemuPadBridge`
initializes in `CemuApp::OnInit` (UDP 26763 responder live at GUI boot — previously the phone
could only find Cemu after the pairing dialog opened or a game loaded, so an idle Cemu GUI
was undiscoverable and the Found card never appeared) + `Shutdown` in `OnExit`. Cemu rebuilt
exit 0, deployed, GUI running. Verified live both directions: PC logs tablet broadcasts,
tablet logs GAMING-DESKTOP and ignores own loopback.
Dad packages in R:\Projects: CemuPad-Dad-Test-20260913-05_15pm (latest, friendly timestamp:
exe 27662848 B, apk 12898725 B, README reused, VERSION with SHAs + uncommitted list).

Phase 6 is **code-complete and unit-verified but UNCOMMITTED and UNDEPLOYED**:
Phase 6 wizard focus fix (deployed ~12:10): gamepad keys are swallowed while any wizard
screen is open (unrecognized pads fell through to Compose focus nav — the Close-button
jumping); Test screen shows last-pressed key name even with zero row matches; Back closes
non-capture screens. Rebuilt green, phone APK reinstalled + app launched. UNCOMMITTED.
UX batch (deployed, UNCOMMITTED): desensitized edge-swipe drawer restore (pointerInteropFilter,
48dp zone + 96dp travel + drift abort — awaitPointerEvent/composed gone in this BOM),
rewritten 4-step connect card without ports/IP, new NETWORK drawer card with tech details,
found-card hostname-only, drawer reorder (DISPLAY/AUDIO/INPUT/NETWORK/DEBUG) + single-expanded
accordion — screenshot-verified live incl. touch toggle, Diagnostics toggle moved into DEBUG. Phone APK reinstalled + launched.
Dolphin investigation done (`investigation/2026-09-13-wizard-keys/01-dolphin-mapping-investigation.md`,
clone at `AppData/Local/Temp/opencode/dolphin` — deletable): resolution plan P1–P5 written,
P1/P2/P4 IMPLEMENTED + deployed (eat-first dispatch, BACK bindable in capture, long-press
Back exits, BACK-binds test, 11/11 CaptureEngine green, full suite + assemble green, phone
reinstalled + launched). UNCOMMITTED. P5 (user B-press log run) still open.
SCOPING ROOT CAUSE FOUND (empty WizardKeys log = proof): Compose AlertDialog opens a separate
Window whose keys bypass Activity.dispatchKeyEvent entirely — all activity-level capture was
dead code while the wizard showed. Converted wizard to an in-window overlay (same window,
explicit buttons only, no scrim dismiss); rebuilt green, phone reinstalled + launched, log
cleared. UNCOMMITTED. Awaiting user retry (open Map → Remap → press B).
- `ControllerDetector` (PID table + name fallback + generic-axis heuristic, EXACT/HEURISTIC),
  `ControllerProfile` +dpad/hat/descriptor fields (handler now profile-driven, defaults = legacy),
  `InputMappingCodec` + `DeviceProfileStore` (per-descriptor persistence, stored→detected→default),
  `CaptureEngine` (19 targets, conflict/skip/back/timeout, hat+trigger+stick logic),
  `MappingWizard` UI (Detected/Test/Capture/Done + banner + drawer row), MainActivity routing
  (capture intercept, Back-steps-back, first-sight prompt, drawer entry).
- 88/88 unit tests green (22 new: Detector 4, Codec 4, Store 4 w/ fake prefs, Capture 10) +
  `assembleDebug` exit 0. Adaptations in plan (banner vs Snackbar, no Y-inversion detection).
- Plan Section 3 + 4.1 flipped; live checks 4.2 (physical controller pass) + 4.3 (Cemu confirm)
  open — needs phone APK install + real pad.
- Self-inflicted breaks fixed along the way: whitespace-eaten newlines (2×), dropped
  InputDevice import, `return@execute` in thread body, duplicate companion object.
- ROOT CAUSE of empty dialog: phone stops broadcasting once video streams; PC probes came from
  a transient socket so phone replies landed on an unlistened ephemeral port; entries expired
  in 10s. Phone now also unicasts replies to sender-IP:26763; dialog re-probes every 5s.
- KNOWN GAP (future): removing the input controller does not stop video/audio — sessions are
  independent, phone auto-reconnects; no session-teardown path exists yet. Re-pair is the
  workaround (now functional).
Older deploys: 11:11 AM (4.4 batch), 10:53 AM (4.3 batch), 10:20 AM (4.2 batch).

Phase 4.3 is **code-complete and compile-verified but UNCOMMITTED and UNDEPLOYED**
(user session live — do NOT overwrite EmuDeck exe or reinstall phone APK unasked):
- C++ (queue-handoff design — plan's direct `FeedMicSamples` would race the AX-thread writer on the
  mic ringbuffer `writeIndex`): `CemuPadBridge` mic queue (1s cap, drop-oldest),
  `VideoStreamServer::MicRxThreadFunc` on UDP 26764 with header validation, `mic.cpp` consumes
  queued PCM when mic/blow active (synth-tone fallback preserved, `mic_feedSamples` stays sole
  writer); `CemuBin` Release exit 0, no new warnings.
- Android (`MicVoiceStreamer` 32 kHz/320-sample datagrams with permission + device-support guards,
  MainActivity start-on-connect/stop-on-disconnect/live-toggle with mic setting, `MicVoiceStreamerTest`
  4/4, full suite + assemble green).
- Plan Section 3 + 4.1/4.2 flipped with adaptation notes; live check 4.3 open (needs deploy + voice
  mini-game test). Caveats: voice streamer is a SECOND recorder next to the blow detector (watch
  Samsung); hardcoded `Initialized (854x480…)` log line still misleading — fix in a later C++ batch.
- TO DEPLOY for live test: stop Cemu → copy `Cemu/bin/Cemu_release.exe` → EmuDeck `Cemu.exe`;
  `adb install -r` fresh phone APK; speak into mic in a voice mini-game.

Phase 4.4 is **code-complete and compile-verified but UNCOMMITTED and UNDEPLOYED**:
- `CemuPadBridge` PIN API (random 4-digit, token store cap 8, open-session auto-approve so enabling
  PIN later keeps paired phones working); `VideoStreamServer` AUTH `0x30`/`0x31` with unauthorized
  gating on video TCP+UDP/rumble/audio + payload-consuming alignment; pairing dialog gains
  "Require PIN" checkbox + PIN readout (the only enable path — no Cemu settings UI exists).
- Android blocking auth BEFORE the frame reader (unframed 9-byte response), 90s PIN latch + cancel,
  wrong-PIN retry via reconnect, token in SharedPreferences, `PinPairingDialog` in `MainScreen`;
  `SessionAuthTest` 4/4, full suite + assemble green; `CemuBin` Release exit 0, no new warnings.
- Plan Section 3 + 4.1/4.2 flipped with adaptation notes; live checks 4.3 (0000 rejected) / 4.4 (valid
  PIN + token reconnect) open — need deploy + enable PIN in pairing dialog.
- Adaptations: AUTH accepted anytime (token rotation); fail-closed gate; dialog hides on connect.

## Just completed (this session)

1. **Reviewed project + harness + all plans** (`AI_HARNESS_INSTRUCTIONS.md`, `plans/`, `PROJECT_CHECKLIST.md`).
2. **Implemented Phase 4.0 Steps 3.1–3.5 in `Cemu/`** (nested git repo, uncommitted):
   - NEW `src/streaming/CemuPadBridge.{h,cpp}` — callback-based delegate bridge (no-op when inactive, acyclic links).
   - NEW `src/streaming/DiscoveryServer.{h,cpp}` — owns UDP 26763 (respond + track + probe).
   - NEW `src/streaming/CMakeLists.txt` (`CemuStreaming` static lib); wired into `src/CMakeLists.txt` + `CemuBin` link.
   - NEW `src/gui/wxgui/input/CemuPadPairingDialog.{h,cpp}` + registered in wxgui `CMakeLists.txt`.
   - `InputSettings2.cpp`: "Auto-Discover CemuPad..." button → dialog → `update_state()` on OK.
   - `vpad.cpp` / `ax_out.cpp`: additive 1-line `CemuPadBridge` delegate calls (existing paths preserved).
   - `VideoStreamServer.{h,cpp}`: retired legacy 26763 discovery thread (sole owner is now DiscoveryServer).
3. **Corrected two plan inaccuracies** (do NOT apply plan snippets literally):
   - Plan's `AutoConfigureDSUController` misuses `ControllerFactory::CreateController(DSUClient, "ip:port")` — DSU uuid is a numeric slot index. Implementation correctly uses `DSUProviderSettings{ip,port}` + `make_shared<DSUController>(0, settings)` + explicit CemuPad DSU→VPAD mapping (upstream `set_default_mapping` has no DSU branch) + `InputManager::save(0)`.
   - Plan's `refresh_controllers()` does not exist — implementation calls existing `update_state()`. Plan's `save_controller_profile(0)` does not exist — implementation calls `save(0)`.
   - Media-file move (`VideoStreamServer`/`VideoEncoder`/`StreamingCapture` → `src/streaming/`) deliberately DEFERRED: moving without a verified build risks breakage + circular lib deps. New code is isolated; move is a documented follow-up.
4. **Verified what was verifiable**: `gradlew testDebugUnitTest` → BUILD SUCCESSFUL (exit 0, Android untouched); full Cemu-side diff reviewed, no dangling discovery references.

## Next Actionable Step (in order)

1. USER quality testing (no commit until done): deploy `Cemu/bin/Cemu_release.exe` to EmuDeck,
   open Input Settings → "Auto-Discover CemuPad..." → Pair & Connect → confirm `controller0.xml`
   + SM3DW smoke test (video 60fps, audio clear, rumble, touch, gyro — parity with pre-Phase-4.0).
   WARNING: legacy 26763 responder moved from VideoStreamServer to DiscoveryServer; if discovery
   misbehaves in-game, that is the first suspect.
2. After user sign-off: commit Cemu repo (`feat(streaming): phase 4.0 subsystem + 1-click pairing`, no push
   without instruction) — note outer repo + Cemu repo are SEPARATE git repos; plan file lives in outer.
3. Then continue to Phase 4.1 (Android UDP 26763 responder is still missing — dialog can find nothing
   until the phone answers probes). Then deferred media-file move follow-up.

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

