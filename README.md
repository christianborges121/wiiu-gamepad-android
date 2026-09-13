# Wii U GamePad for Android (`CemuPad`)

Transform an Android smartphone into a full-fidelity Wii U GamePad for Cemu:
- **Low-Latency Video Streaming**: Hardware H.264 video encoding (NVENC / AMF) streaming at 60 FPS over UDP port `26761` with hardware `MediaCodec` surface rendering, artifact-free motion, and instant IDR keyframe recovery.
- **Crystal-Clear GamePad Audio**: Direct 48 kHz stereo PCM audio tapped from Cemu's DSP subsystem (`snd_core`) streaming over UDP port `26762` with adaptive low-latency jitter buffering.
- **Complete Controller Input**: Full DSU protocol emulation over UDP port `26760` (buttons, analog sticks with deadzone filtering and inverted Y axes, multi-touch 16:9 normalized touchscreen, and 6-axis motion sensors with in-app zero-bias calibration).
- **Haptics & Controls**: VPAD rumble vibration forwarded directly to phone haptics; optional virtual on-screen controls.
- **Polished UI & Ergonomics**: Fullscreen immersive landscape with dark settings drawer (`#161D2B`), fit modes (Original Wii U 854×480, 16:9 Aspect Fit, Full Screen Fill), Back-button-only drawer access, and outside scrim tap-to-dismiss with auto-save.

## Documentation & Developer Guides

- **AI Harness & Autonomous Dev Guide**: [`AI_HARNESS_INSTRUCTIONS.md`](AI_HARNESS_INSTRUCTIONS.md)
- **Testing & Debugging Operational Manual**: [`plans/AI_HARNESS_TESTING_GUIDE.md`](plans/AI_HARNESS_TESTING_GUIDE.md)
- **Roadmap & Phase Execution Plans**: [`plans/README.md`](plans/README.md)
- **Master Project Progress Tracker**: [`PROJECT_CHECKLIST.md`](PROJECT_CHECKLIST.md)
- **Active Resume & Handoff Notes**: [`investigation/HANDOFF.md`](investigation/HANDOFF.md)

## Repository Layout

```text
android-gamepad-app/   Kotlin Android application (com.cemupad)
Cemu/                  Cemu fork with Latte GPU streaming, MFT encoder, and DSP audio tap
investigation/         Technical investigation reports, audits, and HANDOFF.md
```

