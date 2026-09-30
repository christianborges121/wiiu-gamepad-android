# CemuPad - Android App

The Android client for CemuPad, turning your Android device into a second-screen Wii U GamePad for Cemu.

## Features

- **Low-Latency Video**: Hardware-accelerated H.264 decoding via Android `MediaCodec` rendering directly to a Surface.
- **Low-Latency Audio**: 48 kHz stereo PCM audio streamed over UDP with adaptive jitter buffering.
- **Full DSU Emulation**:
  - Touchscreen: Multi-touch normalized to 16:9 854×480 Wii U GamePad coordinates.
  - Motion Sensors: 6-axis gyro and accelerometer reporting with zero-bias calibration.
  - Controls: Physical buttons and analog sticks, plus support for external gamepads (with built-in button mapping) and configurable on-screen controls.
  - Haptics: Wii U rumble forwarded directly to device vibration motors.
  - Microphone: Real-time audio streaming from the phone microphone for GamePad mic mechanics.
- **Display Modes**: Aspect Fit (16:9), Fullscreen Stretch/Fill, and Native (854×480).
- **Auto-Discovery**: Automatic discovery of running Cemu instances via UDP broadcast/mDNS.

## Requirements

- Android 7.0 (API level 24) or higher
- Target SDK: 36
- JDK 17

## Building

### Debug Build
```bash
./gradlew assembleDebug
```
The resulting APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Release Build
```bash
./gradlew assembleRelease
```

### Running Tests
```bash
./gradlew test
```

## Architecture

```text
com.cemupad/
├── audio/       UDP PCM receiver, jitter buffer, AudioTrack playback, and mic streamer
├── config/      Persistent settings (resolution, bitrate, fit mode, audio/mic toggles)
├── dsu/         DSU (cemuhook) protocol client/server implementation
├── input/       Hardware gamepad capture, on-screen touch overlay, motion sensors, and calibration
├── network/     UDP networking clients, packet reassembly, and Cemu auto-discovery
├── theme/       Compose theme and design tokens
├── ui/          Jetpack Compose UI (main screen, settings drawer, mapping wizard, diagnostics)
├── util/        Logging, byte helpers, and CRC computation
└── video/       H.264 frame reassembly, packet loss handling, and MediaCodec surface decoder
```
