# Wii U GamePad for Android — Master Project Checklist & Implementation Tracker

This document is the **single source of truth** for tracking implementation progress, verification status, and technical milestones across all phases of the project.

---

## 📊 Overall Progress Summary

| Phase | Description | Status | Target Deliverable |
|:---|:---|:---:|:---|
| **Phase 0** | Toolchain, Scaffolding & Protocol Specification | ✅ Complete (100%) | Working Android project, Gradle build, spec fixes verified |
| **Phase 1** | Controller Input via DSU Protocol (Stock Cemu) | ✅ Complete (100%) | Full Android DSU app (buttons, sticks, touch, 6-axis motion + calibration) |
| **Phase 2** | Low-Latency Video Streaming (Cemu Fork) | ✅ Complete (100%) | 60 FPS H.264 stream rendered on phone over UDP, artifact-free motion |
| **Phase 3** | Bidirectional Audio (Speakers & Mic) | ✅ Complete (100%) | 48 kHz stereo PCM audio on phone over UDP + DMA ring fix + mic blow |
| **Phase 4 UX** | UI Ergonomics, Back Button, Scrim Dismiss, Vibration Slider | ✅ Complete (100%) | Dark settings drawer, back button, scrim dismiss, rumble slider, auto-help |
| **Upcoming Phases** | Subsystem Refactor, Auto-Discovery, Voice PCM, Security, Packaging | 🟢 Ready for Execution | Tracked in [**`plans/`**](plans/README.md) (Checklist-based plans: Phase 4.0 to 4.4 & 5) |

---

## Phase 0: Toolchain, Scaffolding & Protocol Validation

- [x] **0.1 Protocol Research & Architectural Review**
  - [x] Analyze Vanilla codebase (`libvanilla`, `vanilla-pipe`, DRC 802.11n protocol).
  - [x] Analyze Cemu input subsystem (`DSUControllerProvider.cpp`, `VPADController.cpp`).
  - [x] Resolve architecture: Android acts as direct DSU server; eliminate unnecessary bridge process for Phase 1.
  - [x] Clarify network direction: Android listens on UDP `26760`, Cemu initiates outward requests.
  - [x] Verify Windows Defender Firewall requirements (no inbound firewall rule needed on PC for Phase 1).
  - [x] Resolve all protocol bugs identified in Gemini, Flash, and ChatGPT reviews:
    - [x] Accelerometer units fixed to $g$'s ($1g \approx 9.80665\text{ m/s}^2$).
    - [x] Gyroscope units fixed to deg/s ($\text{rad/s} \times 57.29578$).
    - [x] Gyro timestamping fixed to trigger only on accelerometer events.
    - [x] Standalone `PortInfo` response (type `0x100001`): Byte 11 fixed to `0x00` (padding).
    - [x] Stick Y-axis mapping inverted ($ly = ((-axisY + 1.0) \times 127.5)$) where $255 = \text{UP}$.
    - [x] Touch coordinate normalization mapped to $1920 \times 942$ with 16:9 pillarbox margin cropping.
    - [x] Face buttons bitmask aligned to Cemu DSU standard (Cross, Circle, Square, Triangle).
- [x] **0.2 Android Project Scaffolding**
  - [x] Initialize Android Gradle project in `android-gamepad-app/`.
  - [x] Verify Android SDK 36 (Android 16), minSdk 24, targetSdk 36.
  - [x] Configure Kotlin 2.2.0, Java 17/21 toolchain, AndroidX dependencies.
  - [x] Verify build passing via `cmd /c gradlew.bat assembleDebug` (BUILD SUCCESSFUL).
- [x] **0.3 Package Configuration & Setup**
  - [x] Standardize package name to `com.cemupad`.
  - [x] Verify `AndroidManifest.xml` permissions:
    - [x] `android.permission.INTERNET`
    - [x] `android.permission.ACCESS_WIFI_STATE`
    - [x] `android.permission.ACCESS_NETWORK_STATE`
    - [x] `android.permission.VIBRATE`
    - [x] `android.permission.RECORD_AUDIO` (Phase 3 readiness)
    - [x] `android.permission.WAKE_LOCK`
  - [x] Lock screen orientation to Landscape (`android:screenOrientation="sensorLandscape"`).
  - [x] Request high-performance Wi-Fi lock (`WifiManager.WIFI_MODE_FULL_HIGH_PERF`).

---

## Phase 1: Controller Input via DSU Protocol (Stock Cemu)

**Goal**: Complete standalone Android app sending full Wii U GamePad inputs (buttons, dual sticks, touch, gyro/accel) to stock Cemu via UDP port 26760. Zero PC-side code modifications required.

### 1.1 DSU Protocol Core (`com.cemupad.dsu`)
- [x] **CRC32 Engine (`CRC32.kt`)**
  - [x] Implement fast CRC32 algorithm matching Cemu's polynomial (`0xEDB88320`).
  - [x] Verify CRC computation zeroes out the CRC field (bytes 8-11) during calculation.
- [x] **Packet Models & Serialization (`DSUPacket.kt`)**
  - [x] **Header Builder (16 bytes)**:
    - [x] Magic: `"DSUS"` (`0x53555344` little-endian).
    - [x] Protocol Version: `1001` (`0x03E9`).
    - [x] Length: Total payload length (little-endian unsigned 16-bit).
    - [x] CRC32: Little-endian 32-bit CRC.
    - [x] Server ID: Random/fixed 32-bit identifier.
  - [x] **Message Type Handlers**:
    - [x] `0x100000` (`VersionRequest` / `VersionResponse`): Protocol version `1001`.
    - [x] `0x100001` (`PortInfoRequest` / `PortInfoResponse`):
      - [x] Slot: `0x00`.
      - [x] Slot status: `0x02` (connected).
      - [x] Device model: `0x02` (full gyro/accel gamepad).
      - [x] Connection type: `0x02` (Bluetooth/wireless).
      - [x] MAC address: Unique 6-byte identifier.
      - [x] Battery status: `0x04` (charged) or dynamic battery reading.
      - [x] **Byte 11 Fix**: Set explicitly to `0x00` (padding) on standalone response.
    - [x] `0x100002` (`DataRequest` / `DataResponse`):
      - [x] Registration: Register client IP/port and subscription type (all ports or slot-specific).
      - [x] Slot & status: `0x00`, `0x02`, `0x02`, `0x02`.
      - [x] MAC & Battery.
      - [x] **Byte 11 Fix**: Set explicitly to `0x01` (`is_connected`).
      - [x] Packet sequence number (monotonically incrementing 32-bit int).
- [x] **DSU UDP Server (`DSUServer.kt`)**
  - [x] Bind `DatagramSocket` to `0.0.0.0:26760`.
  - [x] Handle concurrent receive/transmit loop on dedicated background thread.
  - [x] **Java Buffer Fix**: Reset `recvPacket.length = recvBuffer.size` at the top of every receive iteration.
  - [x] Client registry: Store client `InetSocketAddress` and subscription timestamp.
  - [x] Timeout / Pruning: Remove inactive subscribers after 5 seconds of inactivity.
  - [x] Socket teardown: Graceful `close()` on app pause/stop.

### 1.2 Physical Controller Input Handling (`com.cemupad.input`)
- [x] **HID Input Abstraction (`ControllerProfile.kt`)**
  - [x] Interface for generic HID mapping with vendor/product ID detection.
  - [x] Support standard profiles: Xbox Wireless, PlayStation DualSense/DualShock 4, Nintendo Switch Pro/Joy-Cons, Generic Android telescopic gamepads (Razer Kishi, Backbone One, Gamesir).
- [x] **Gamepad Input Handler (`GamepadInputHandler.kt`)**
  - [x] Hook `onKeyDown`, `onKeyUp`, and `onGenericMotionEvent`.
  - [x] **Digital Buttons Bitmask Mapping**:
    - [x] `state1`: D-Pad Left (`0x80`), Down (`0x40`), Right (`0x20`), Up (`0x10`), Options/Plus (`0x08`), R3/Stick Press (`0x04`), L3/Stick Press (`0x02`), Share/Minus (`0x01`).
    - [x] `state2`: Triangle/Y (`0x80`), Circle/B (`0x40`), Cross/A (`0x20`), Square/X (`0x10`), R1/R (`0x08`), L1/L (`0x04`), R2/ZR digital (`0x02`), L2/ZL digital (`0x01`).
    - [x] `psButton`: Home button (`0x01`).
  - [x] **Analog Sticks Mapping**:
    - [x] Left Stick X: `AXIS_X` mapped to `0..255` (`128` center).
    - [x] Left Stick Y: `AXIS_Y` **inverted** via `roundToInt()` where `255` = UP, `0` = DOWN.
    - [x] Right Stick X: `AXIS_Z` mapped to `0..255` (`128` center).
    - [x] Right Stick Y: `AXIS_RZ` **inverted** via `roundToInt()` where `255` = UP, `0` = DOWN.
    - [x] Deadzone filtering: Apply radial/axial deadzone (default 8%) to eliminate stick drift.
  - [x] **Analog Triggers Mapping**:
    - [x] L2 / ZL: `AXIS_BRAKE` or `AXIS_LTRIGGER` mapped to `0..255`.
    - [x] R2 / ZR: `AXIS_GAS` or `AXIS_RTRIGGER` mapped to `0..255`.

### 1.3 Touchscreen Normalization (`TouchInputHandler.kt`)
- [x] **Touch Coordinate Pipeline**
  - [x] Handle multi-touch events on custom touch overlay canvas.
  - [x] Query display physical resolution and calculate 16:9 active viewport:
    - [x] Determine pillarbox letterbox margins ($X_{\text{offset}}$, $Y_{\text{offset}}$).
    - [x] Reject touches landing outside the 16:9 GamePad area.
  - [x] **Cemu DSU Normalization**:
    - [x] Scale active touch coordinates to $1920 \times 942$ integer space ($X \in [0, 1919]$, $Y \in [0, 941]$).
    - [x] Clamp normalized coordinates within bounds.
  - [x] Pack primary touch point into DSU bytes 56-61:
    - [x] Byte 56: Touch active flag (`0x01` if down, `0x00` if released).
    - [x] Byte 57: Touch packet ID/counter.
    - [x] Bytes 58-59: Unsigned 16-bit little-endian X.
    - [x] Bytes 60-61: Unsigned 16-bit little-endian Y.
  - [x] Pack secondary touch point into DSU bytes 62-67.

### 1.4 Motion Sensors (`MotionHandler.kt`)
- [x] **Sensor Configuration**
  - [x] Acquire `TYPE_ACCELEROMETER` and `TYPE_GYROSCOPE` via Android `SensorManager`.
  - [x] Register listeners with `SENSOR_DELAY_GAME` (~100 Hz).
- [x] **Coordinate & Unit Conversions**
  - [x] Query active `Display.rotation` (`ROTATION_0`, `ROTATION_90`, `ROTATION_270`).
  - [x] Remap sensor axes for Landscape mode:
    - [x] Landscape (`ROTATION_90`): $X' = -Y_{\text{raw}}$, $Y' = X_{\text{raw}}$, $Z' = Z_{\text{raw}}$.
    - [x] Reverse Landscape (`ROTATION_270`): $X' = Y_{\text{raw}}$, $Y' = -X_{\text{raw}}$, $Z' = Z_{\text{raw}}$.
  - [x] **Accelerometer Conversion**: Convert $\text{m/s}^2$ to **$g$'s** via `values / SensorManager.GRAVITY_EARTH`.
  - [x] **Gyroscope Conversion**: Convert $\text{rad/s}$ to **$\text{deg/s}$** via `values * (180.0f / PI)`.
- [x] **Sensor Synchronization**:
  - [x] Cache latest gyro values without modifying motion timestamp.
  - [x] On accelerometer event, capture `lastAccelTimestampUs = event.timestamp / 1000`.
  - [x] Pack paired accel + latest gyro values with `lastAccelTimestampUs` into DSU bytes 32-55.

### 1.5 Android UI & Diagnostics (`com.cemupad.ui`)
- [x] **Compose Status Dashboard**
  - [x] Display local device Wi-Fi IP address and DSU listening port (`26760`).
  - [x] Real-time client status:
    - [x] "Waiting for Cemu..." (idle).
    - [x] "Connected to Cemu: <PC_IP>" (active data requests receiving).
  - [x] Metrics HUD: Packets/sec (Hz), polling interval (ms), sensor update rate.
  - [x] Interactive 16:9 touch canvas (`TouchSurfaceView.kt`) with cyan border, pillarbox margin indicator, and touch coordinate crosshairs.

### 1.6 Verification & Testing Checklist for Phase 1
- [x] **Unit & Integration Tests (`app/src/test/java/com/cemupad/`)**:
  - [x] `CRC32Test.kt`: Validate CRC matches standard test vectors (IEEE 802.3 `0xCBF43926`).
  - [x] `DSUPacketTest.kt`: Verify byte layouts for `VersionResponse`, `PortInfoResponse`, `DataResponse`.
  - [x] `InputMappingTest.kt`: Validate stick Y inversion ($ly(-1.0) = 255$, $ly(1.0) = 0$, center $128$).
  - [x] `TouchNormalizationTest.kt`: Validate pillarbox rejection and $1920 \times 942$ math.
  - [x] `MotionConversionTest.kt`: Validate $g$-force conversion and landscape axis remapping.
  - [x] `DSUServerIntegrationTest.kt`: Full Cemu loopback test exercising `VersionRequest`, `ListPorts`, and `DataRequest` over UDP.
- [x] **Physical Device & Cemu Verification**:
  - [x] Deploy APK to physical Android phone (`adb -s 192.168.68.109:44985 install app-debug.apk`).
  - [x] Live UDP probe test verified: PC communicated with phone on port 26760, received 100B `DataResponse` with active sensor data.
  - [x] Connect physical controller (USB/Bluetooth) or use on-screen controls.
  - [x] Configure Cemu DSU Client (`Options > Input Settings > Emulated: Wii U GamePad > DSU Client > IP: 192.168.68.109 > Port: 26760`).
  - [x] Verify button presses, sticks, touch, and motion in Cemu GamePad mapping screen.
  - [x] Test in-game input (*Super Mario 3D World*, *Zelda: Wind Waker HD*).
  - [x] Zero-bias gyro/accelerometer calibration with persistent offsets.

---

## Phase 2: Low-Latency Video Streaming (Cemu Fork)

**Goal**: Capture Cemu's internal GamePad framebuffer (854×480), encode via hardware H.264 (NVENC/AMF) with low latency, stream over network, and decode on Android via `MediaCodec` onto a `SurfaceView`.

### 2.1 Cemu Headless VPAD Render Target Capture
- [x] Locate Latte GPU GamePad render pass in Cemu source tree (`Cemu/src/Cafe/HW/Latte/`).
- [x] Ensure GamePad view renders continuously even when GamePad window is closed/minimized in Cemu (`StreamingCapture::IsStreamingActive()`).
- [x] Implement asynchronous double-buffered staging buffer:
  - [x] Vulkan: `VkBuffer` with `vkCmdCopyImageToBuffer` and double-buffered host-visible staging memory.
- [x] Offload frame readback and color conversion (BGRA → NV12) to dedicated streaming pipeline.

### 2.2 Low-Latency H.264 Video Encoder
- [x] Integrate encoder subsystem in Cemu:
  - [x] Primary: Hardware acceleration via Windows Media Foundation (NVENC / AMF / Intel QuickSync with software MFT fallback).
  - [x] Fix reference frame chain: disable B-frames (`CODECAPI_AVEncMPVDefaultBPictureCount = 0`) and prevent frame overwrite in MFT pipeline (eliminates macroblocking/ghosting).
- [x] Encoding parameters:
  - [x] Resolution: 854×480 (16:9 native Wii U DRC resolution).
  - [x] Frame rate: 60.0 FPS.
  - [x] Bitrate: 6 Mbps CBR with `CODECAPI_AVLowLatencyMode = 1`.
  - [x] GOP Size: Periodic IDR every 120 frames (2s) or instant on demand.
  - [x] **Annex B Formatting**: Emits SPS and PPS preceding every keyframe.

### 2.3 Network Video Transport Streamer
- [x] **Stream Transport Options**:
  - [x] Low-Latency UDP video streaming on port `26761` with MTU fragmentation (1400B payload + 24B header).
  - [x] Reliable TCP control channel on port `26761` for handshake and IDR requests.
- [x] **Drop-Tail Backpressure & Latency Protection**:
  - [x] Drop older frames if client congests to prevent latency accumulation.
- [x] **Reverse Control Channel**:
  - [x] Listen for `IDR_REQUEST` control packet (`0x10`) from Android.
  - [x] Trigger immediate encoder IDR keyframe generation upon request.

### 2.4 Android Video Receiver & MediaCodec Decoder (`com.cemupad.video`)
- [x] **Video Stream Client (`VideoStreamClient.kt`)**
  - [x] Connect to Cemu video streaming port (UDP `26761` with TCP control channel).
  - [x] Parse framed NAL units and RTP-like headers; detect missing packets.
  - [x] Transmit `IDR_REQUEST` to Cemu on stream desync or packet loss.
  - [x] Trust UDP IDR flag to bypass redundant CPU Annex-B re-parsing.
- [x] **Hardware MediaCodec Decoder (`VideoDecoder.kt`)**
  - [x] Initialize `MediaCodec` for `video/avc` configured with direct `Surface` output.
  - [x] Feed SPS/PPS CSD buffers on initialization.
  - [x] Queue input buffers with low-latency flags (`BUFFER_FLAG_KEY_FRAME`).
  - [x] Release output buffers immediately to `SurfaceView` with PTS pacing.

### 2.5 Verification & Testing Checklist for Phase 2
- [x] Verify Cemu renders GamePad screen without performance degradation (steady 60 FPS in *Super Mario 3D World*).
- [x] Verify H.264 stream connects and starts playback on Android within 500ms.
- [x] Verify `MediaCodec` hardware decoding operates without frame drops or pipeline crashes.
- [x] Verify artifact-free motion rendering during camera panning and character movement.
- [x] Test network packet drop recovery: instant recovery upon `IDR_REQUEST`.

---

## Phase 3: Bidirectional Audio Pipeline

**Goal**: Route Wii U GamePad speaker audio to Android phone with low jitter, and feed phone microphone input back to Cemu.

### 3.1 Cemu GamePad DSP Audio Tap
- [x] Hook GamePad audio DSP output channel in Cemu (`Cemu/src/Cafe/OS/libs/snd_core/snd_core.cpp`).
- [x] Capture 48 kHz 16-bit stereo PCM audio samples.
- [x] Fix circular DMA buffer ring wrapping and sample pacing (eliminated audio buzzing and slowdown).

### 3.2 Audio Network Streaming
- [x] Stream audio over UDP port `26762` with sequential packet sequence numbers and PTS timestamps.
- [x] Continuous 48 kHz stereo PCM streaming with low overhead.

### 3.3 Android Audio Receiver & Adaptive Jitter Buffer (`com.cemupad.audio`)
- [x] Receive UDP audio packets on background thread.
- [x] Implement adaptive ring jitter buffer with sequence reordering and silence concealment.
- [x] Play out audio via low-latency Android `AudioTrack` in stereo mode.

### 3.4 Android Microphone Pipeline
- [x] **Blow Detection & Manual Blow Interaction**:
  - [x] Sample Android microphone via `AudioRecord` at 16 kHz mono.
  - [x] Calculate RMS energy; if amplitude exceeds threshold, assert DSU blow button (`kButtonId_Mic`).
  - [x] Minimalist red dot indicator (`MicIndicatorDot`) replaces text button; supports touch/hold for manual blow interaction.
  - [x] Settings drawer toggle to enable/disable microphone (persisted in `AppSettingsCodec`, frees `AudioRecord` when disabled).
- [ ] Optional: Stream 32 kHz mono PCM to Cemu's `mic_feedSamples()`.

### 3.5 Verification & Testing Checklist for Phase 3
- [x] Verify GamePad speaker audio plays clearly without crackling, buzzing, or stuttering (*Super Mario 3D World* verified live).
- [x] Verify Audio/Video sync remains aligned within ±15ms.
- [x] Verify mic blow mechanism works in *Super Mario 3D World* mic platforms.

---

## Phase 4: Discovery, Pairing, Rumble & UX Polish

**Goal**: Seamless user experience: auto-discovery, PIN pairing, rumble haptic feedback, customizable settings, and on-screen controls.

### 4.1 Network Discovery (`com.cemupad.network`)
- [ ] Implement UDP broadcast responder in Cemu fork listening on UDP port `26765`.
- [ ] Android sends `"CEMUPAD_DISCOVER"` broadcast to `255.255.255.255:26765`.
- [ ] Android displays list of available Cemu instances for one-tap connection.

### 4.2 Session Security & Pairing
- [ ] Optional PIN pairing handshake on TCP port `26765`.

### 4.3 Vibration & Rumble Haptics
- [x] In Cemu fork, hook `VPADController::push_rumble(pattern, length)`.
- [x] Forward rumble events over control connection to Android.
- [x] On Android, trigger `VibratorManager` / `Vibrator` with custom `VibrationEffect`.

### 4.4 Virtual On-Screen GamePad Overlay
- [x] Render transparent GamePad touch controls when enabled in settings.
- [x] Support on-screen D-pad, ABXY face buttons, L/R/ZL/ZR bumpers, Plus, Minus, and Home.

### 4.5 Connection Resilience & UX Settings
- [x] Modern card-based dark settings drawer (`#161D2B`).
- [x] Gesture restriction: Drawer opens via Android Back button only (swipe-to-open disabled).
- [x] Tap-outside (scrim) click-off closes drawer and saves settings.
- [x] Auto-display connection help card when disconnected; hide when video streams.
- [x] Fit modes: Original Wii U (854×480), 16:9 Aspect Fit, Full Screen Fill.
- [x] 30 FPS cap switch permanently removed (native 60 FPS streaming).
- [x] Zero-bias motion calibration tool with live countdown dialog.
- [ ] Add dynamic encoder bitrate (4–12 Mbps) and resolution (480p/720p/1080p) opcodes over TCP `26761`.

---

## 📌 Implementation Progress Log

| Date | Phase | Task | Details | Status |
|:---|:---:|:---|:---|:---:|
| *2026-09-11* | 0.1 | Architecture & Protocol Spec | Audited DSU spec, corrected accelerometer units ($g$'s), stick inversion, PortInfo byte 11 | ✅ Done |
| *2026-09-11* | 0.2 | Project Scaffolding | Android Gradle project initialized, SDK 36 verified, build passing | ✅ Done |
| *2026-09-11* | 0.3 | Package & Permissions | Package renamed to `com.cemupad`, permissions, landscape lock, Wi-Fi lock configured | ✅ Done |
| *2026-09-11* | 1.1 | DSU Protocol Engine | Implemented `CRC32.kt`, `DSUPacket.kt` (100B DataResponse), `DSUServer.kt` (UDP 26760) | ✅ Done |
| *2026-09-11* | 1.2 | Controller Input | Implemented `ControllerProfile.kt` & `GamepadInputHandler.kt` with stick inversion & deadzones | ✅ Done |
| *2026-09-11* | 1.3 | Touch Normalization | Implemented `TouchInputHandler.kt` with 16:9 pillarbox aspect-fit math & $1920 \times 942$ mapping | ✅ Done |
| *2026-09-11* | 1.4 | Motion Sensors | Implemented `MotionHandler.kt` with landscape remap, $g$-force accel, deg/s gyro, paired timestamps | ✅ Done |
| *2026-09-11* | 1.5 | UI & Diagnostics | Implemented `TouchSurfaceView.kt`, `MainScreen.kt`, and `MainActivity.kt` with live metrics HUD | ✅ Done |
| *2026-09-11* | 1.6 | Unit & Loopback Verification | 12 tests passed (`DSUPacketTest`, `InputSubsystemTest`, `DSUServerIntegrationTest`), APK generated | ✅ Done |
| *2026-09-12* | 2.1-2.4 | Video Streaming Pipeline | Implemented Latte GamePad Vulkan capture, NVENC/AMF MFT encoder, UDP 26761 streaming, MediaCodec decoder | ✅ Done |
| *2026-09-12* | 4.5 | Fit Modes & Settings Drawer | Implemented Native 854x480, Aspect Fit, and Stretch Fill modes; settings persistence in AppSettingsCodec | ✅ Done |
| *2026-09-13* | 3.1-3.3 | Audio DMA Fix | Resolved DMA circular buffer ring wrapping in `snd_core.cpp`; crystal clear 48 kHz stereo audio over UDP 26762 | ✅ Done |
| *2026-09-13* | 2.2-2.4 | Motion Artifact Fix | Fixed MFT encoder frame overwrite with callback pipeline, zero B-frames, and Android UDP IDR flag bypass | ✅ Done |
| *2026-09-13* | 1.4 | Motion Zero-Bias Calibration | Implemented in-app calibration tool with 3s countdown dialog and persistent sensor bias offsets | ✅ Done |
| *2026-09-13* | 4.5 | Modern UI & Dismissal Ergonomics | Restyled dark drawer cards, removed 30 FPS cap, auto connection help, Back button only open, scrim click-off to save/close | ✅ Done |
| *2026-09-13* | 3.4/4.5 | Mic Red Dot & Settings Toggle | Minimalist red dot indicator with touch-blow interaction; dedicated Microphone toggle in Audio settings drawer with hardware release | ✅ Done |
| *2026-09-13* | 4.3 | GamePad Rumble Haptics | VPADControlMotor duty-cycle envelope calculation, VideoStreamServer streaming, Android RumbleHandler pulse flooring & debouncing | ✅ Done |
| *2026-09-13* | 4.0 | Subsystem Modularization & 1-Click Pairing | New `Cemu/src/streaming/` (`CemuPadBridge`, `DiscoveryServer`, `CemuStreaming` lib), `CemuPadPairingDialog` + Input Settings button, non-invasive vpad/snd_core/capture hooks, legacy 26763 discovery retired; Release `CemuBin` build exit 0, user quality-tested | ✅ Done |

