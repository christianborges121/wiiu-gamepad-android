# CemuPad - Wii U GamePad for Android

CemuPad turns an Android device into a second-screen Wii U GamePad for [Cemu](https://cemu.info). It streams GamePad video and stereo audio to your device while sending touch, button, and motion controls back to the emulator over your local network.

## Features

- **Video Streaming**: Low-latency H.264 video streaming over UDP (`26761`) with hardware-accelerated decoding via Android `MediaCodec`.
- **Audio Streaming**: 48 kHz stereo audio stream over UDP (`26762`) with adaptive jitter buffering.
- **Controller Input**: Full DSU (cemuhook) protocol support over UDP (`26760`):
  - Physical buttons and analog sticks with configurable deadzones
  - 16:9 multi-touch touchscreen input
  - 6-axis motion controls (gyroscope and accelerometer) with zero-bias calibration
- **Haptic Feedback**: Wii U GamePad rumble routed to device vibration with adjustable intensity.
- **Input Flexibility**: Support for external gamepads (with a built-in button mapping wizard) as well as on-screen touch controls.
- **Microphone**: Stream microphone input to Cemu for games that use GamePad audio/blow mechanics.
- **Display Fit Modes**: 16:9 Aspect Fit, Fullscreen Stretch, and Native Wii U resolution (854×480).
- **Auto-Discovery**: Automatic network discovery of running Cemu instances on your local network.

## Network Protocol & Ports

| Stream | Protocol | Default Port | Description |
|---|---|---|---|
| **Input / DSU** | UDP | `26760` | Button states, analog sticks, touchscreen, motion, and rumble |
| **Video** | UDP | `26761` | H.264 encoded GamePad (DRC) video stream |
| **Audio** | UDP | `26762` | 48 kHz stereo PCM audio and mic stream |

## Requirements

- **Android Device**: Android 7.0 (API level 24) or newer.
- **Cemu**: A compatible Cemu build with streaming support (included in the [`Cemu/`](Cemu/) directory).
- **Network**: Low-latency 5 GHz Wi-Fi or USB tethering recommended.

## Getting Started

1. **Install the App**: Install the CemuPad APK on your Android device.
2. **Launch Cemu**: Run the streaming-enabled Cemu build on your PC.
3. **Configure Cemu Input**:
   - In Cemu, go to **Options** > **Input Settings**.
   - Set **Emulate Controller** to **Wii U GamePad**.
   - Set **API** to **DSUClient**.
   - Configure the IP to match your Android device (or host broadcast address).
4. **Connect**:
   - Ensure your Android device and PC are on the same local network.
   - Open CemuPad on your device. It will search for your Cemu host automatically, or you can enter the PC's IP address manually.
   - Tap connect to start streaming.

## Building from Source

### Android App
- Prerequisites: Android Studio or Android SDK command-line tools, JDK 17.

```bash
cd android-gamepad-app
./gradlew assembleDebug
```
The compiled APK will be located at `android-gamepad-app/app/build/outputs/apk/debug/app-debug.apk`.

To run tests:
```bash
cd android-gamepad-app
./gradlew test
```

### Cemu Companion
See [`Cemu/README.md`](Cemu/README.md) and [`Cemu/BUILD.md`](Cemu/BUILD.md) for compilation instructions for Windows, Linux, and macOS.

## Repository Layout

```text
android-gamepad-app/   Kotlin Android application (com.cemupad)
Cemu/                  Cemu fork with DRC framebuffer capture, H.264 encoder, and audio streaming
investigation/         Technical investigation reports, audits, and handoff notes
plans/                 Feature implementation plans and roadmaps
```

## Contributing & Developer Documentation

- [AI Harness & Testing Guide](plans/AI_HARNESS_TESTING_GUIDE.md)
- [Project Checklist](PROJECT_CHECKLIST.md)
- [Handoff & Session Notes](investigation/HANDOFF.md)


