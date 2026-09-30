# Implementation Plan: Fix Black Screen After Connection & Update MainScreen UX

## Executive Summary
After connecting the Android GamePad app to Cemu, two issues occur:
1. **Black Screen**: The phone connects to Cemu, but the screen remains black when a game is running.
2. **MainScreen UX**: The phone displays a toast notification saying "Connected to Cemu!", but the on-screen card continues to show "Waiting for Cemu to connect..." along with outdated Cemu 1.x instructions ("Options → GamePad motion source → DSU Client").

Thorough code inspection and live diagnostics on both the Windows PC (Cemu) and Android phone (via ADB logcat) have identified the exact root causes for both issues.

---

## Root Cause Analysis

### 1. Black Screen Root Causes

#### A. Codec Mismatch Between Phone Decoder and Cemu Encoder
- On connection, Android detects hardware HEVC (Samsung Galaxy S23 FE) and configures its `VideoDecoder` for `MediaFormat.MIMETYPE_VIDEO_HEVC` (`c2.qti.hevc.decoder.low_latency`).
- Android sends `OPCODE_CODEC_SELECT = HEVC` to Cemu.
- `VideoStreamServer` calls `VideoEncoder::SetCodec(VideoCodec::HEVC)`.
- **The Bug**: When the game launches, `StreamingCapture::Initialize()` in [`StreamingCapture.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp#L32) hardcodes:
  ```cpp
  VideoEncoder::GetInstance().Initialize(854, 480, 60, 6000000);
  ```
  Since `VideoEncoder::Initialize` defaults `codec` to `VideoCodec::H264`, `StreamingCapture::Initialize()` **wipes out the client's negotiated HEVC preference and resets the encoder to H.264 (`AMDh264Encoder`)**.
- Cemu then streams **H.264** NAL units (SPS 7, PPS 8, IDR 5) to the Android phone.
- The Android phone's **HEVC** decoder rejects the stream because it never finds HEVC parameter sets (VPS 32, SPS 33, PPS 34), logging in Android logcat:
  ```
  W VideoDecoder: No parameter sets after 30 frames, requesting IDR
  ```
  Result: `MediaCodec` outputs zero frames, leaving the Surface completely black.

#### B. `StreamingCapture::EncodeWorker` Thread Missing COM Initialization
- `VideoEncoder` initializes COM on the main thread via `CoInitializeEx(nullptr, COINIT_MULTITHREADED)`.
- However, frame encoding runs on a background worker thread spawned in [`StreamingCapture.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp#L35):
  ```cpp
  m_workerThread = std::thread(&StreamingCapture::EncodeWorker, this);
  ```
- Windows Media Foundation COM objects require COM initialization on every thread. Without calling `CoInitializeEx` on `m_workerThread`, calls to `MFCreateMemoryBuffer`, `MFCreateSample`, and MFT calls risk failure or unpredictable behavior across different GPU drivers.

#### C. Asynchronous vs. Synchronous MFT Selection
- On AMD GPUs, `AMDh264Encoder` and `AMDh265Encoder` report `MF_TRANSFORM_ASYNC = 1`.
- In `VideoEncoder::CreateEncoderCandidates`, `MFTEnumEx` is called with `MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER`, which selects asynchronous MFTs.
- `VideoEncoder.cpp` uses a synchronous `ProcessInput` followed immediately by `ProcessOutput` drain loop. For asynchronous MFTs, calling `ProcessOutput` before the GPU has completed encoding can return `E_UNEXPECTED` (`0x8000FFFF`), which `drainOneSample` currently treats as no output, dropping frames.
- We must ensure:
  1. Synchronous transforms are prioritized, or async transforms are handled gracefully without aborting the pipeline.
  2. If the client negotiated HEVC, Cemu initializes `HEVCVideoExtensionEncoder` or `AMDh265Encoder` properly and preserves the codec choice.

---

### 2. MainScreen UX Root Causes

#### A. Stale Connection State Machine in `MainScreen.kt`
- In [`MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt#L343), the connection instructions card is gated strictly on:
  ```kotlin
  if (!isVideoStreaming) { ... }
  ```
- `isVideoStreaming` is only set to `true` when actual decoded video frames arrive (`handleVideoFrame`).
- When Cemu connects via DSU (`clientCount > 0`) or the video control socket connects (`videoClient?.isConnected == true`), the phone displays a toast: `"Connected to Cemu!"`.
- However, because a game is not yet running (or video is still establishing), `isVideoStreaming` is `false`, so the card remains visible with the text:
  - `"Waiting for Cemu to connect..."`
  - `"Enter this device's IP in Cemu under Options → GamePad motion source → DSU Client"`
- This confuses the user, making it seem like connection failed.

#### B. Obsolete Cemu 1.x Menu Directions
- The text references `"Options → GamePad motion source → DSU Client"`, which was removed in Cemu 2.0.
- In modern Cemu 2.0, the controller is configured under:
  - **`Options → Input settings → Controller 1 → Wii U GamePad (DSU Client)`**
  - Or automatically using CemuPad auto-discovery ("Connect to Cemu" button).

---

## Proposed Changes

### Phase 1: Android App UX & Connection State Updates

#### 1. [`MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt)
- Add a new parameter `isControlConnected: Boolean = false` to `MainScreen`.
- When `!isVideoStreaming`:
  - If **`isControlConnected || clientCount > 0`**:
    - Display a sleek, modern **"Connected to Cemu"** status card:
      - Green pulsing/active badge: `"Connected"`
      - PC Host / Server info: Connected IP, DSU Port, Latency
      - Informative helper message: `"Gamepad & Motion Active. Launch a Wii U game in Cemu to stream the GamePad screen."`
  - If **Disconnected**:
    - Display updated, modern Cemu 2.0 setup guide:
      - Title: `"Wii U GamePad"`
      - Subtitle: `"Waiting for Cemu to connect..."`
      - Guide: `"In Cemu: Options → Input settings → Controller 1\nSet Emulated controller to Wii U GamePad and API to DSUClient"`
      - IP & Port badges.
      - Discovered Cemu server card with `"Connect to Cemu"` action button.

#### 2. [`MainActivity.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt)
- Expose `isControlConnected` state (tracked from `videoClient?.isConnected` and `dsuServer?.activeClientCount`).
- Pass `isControlConnected` to `MainScreen`.

---

### Phase 2: Cemu Video Streaming Subsystem Fixes

#### 1. [`StreamingCapture.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp)
- **Preserve Client-Negotiated Codec & Bitrate**:
  In `StreamingCapture::Initialize()`:
  ```cpp
  VideoCodec codec = VideoEncoder::GetInstance().GetCodec();
  uint32 bitrate = VideoEncoder::GetInstance().GetBitrate();
  uint32 width = VideoEncoder::GetInstance().GetWidth();
  uint32 height = VideoEncoder::GetInstance().GetHeight();
  if (width == 0 || height == 0) { width = 854; height = 480; }
  if (bitrate == 0) bitrate = 6000000;
  VideoEncoder::GetInstance().Initialize(width, height, 60, bitrate, codec);
  ```
  This prevents `StreamingCapture::Initialize()` from overriding the client's negotiated HEVC or H.264 preference back to the default H.264.
- **Initialize COM on Worker Thread**:
  In `StreamingCapture::EncodeWorker()`:
  Call `CoInitializeEx(nullptr, COINIT_MULTITHREADED)` at thread entry and `CoUninitialize()` at exit.

#### 2. [`VideoEncoder.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp)
- **Encoder Selection**:
  In `CreateEncoderCandidates`, prioritize synchronous transforms (`MFT_ENUM_FLAG_SYNCMFT`) so that synchronous `ProcessInput` / `ProcessOutput` pipelines don't encounter asynchronous driver locks.
- **Robust Output Draining**:
  In `drainOneSample`, when `ProcessOutput` returns `E_UNEXPECTED` (`0x8000FFFF`) on async MFTs, do not abort; retry or continue without breaking the encoder state.
- **Detailed Logging**:
  Add informative debug logging for `ProcessOutput` failures so any encoder stalls are immediately diagnosed in `log.txt`.

---

## Verification & Testing Plan

1. **Build & Deploy Android GamePad App**:
   - Compile via `./gradlew assembleDebug`
   - Install APK to phone via `adb install -r`
   - Verify `MainScreen` states:
     - Disconnected: Shows updated Cemu 2.0 instructions and device IP/port.
     - Connected (before game launch): Shows "Connected to Cemu" card, Green status badge, and "Launch a Wii U game in Cemu to stream the GamePad screen."
2. **Build & Deploy Cemu**:
   - Build `CemuBin` in `Release` configuration via `cmake --build Cemu\build --config Release --target CemuBin`
   - Deploy `Cemu_release.exe` to EmuDeck `Cemu.exe`
3. **End-to-End Game Streaming Test**:
   - Launch Cemu and start *The Legend of Zelda: The Wind Waker HD*.
   - Verify phone displays the live GamePad screen (no black screen!).
   - Verify diagnostics overlay shows 60 FPS streaming and responsive touch/controls.
