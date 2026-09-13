# AI Harness Instructions: Wii U GamePad for Android

Use this document as the primary onboarding prompt for any AI coding harness or engineer working on this repository.

---

## 🎯 Project Objective

Transform an Android smartphone into a complete, high-fidelity Wii U GamePad for Cemu:
1. **Controller, Touch & 6-Axis Motion Input**: Phone runs an embedded DSU server over UDP `26760` with full buttons, sticks, multi-touch touchscreen, and zero-bias calibrated motion.
2. **Low-Latency 60 FPS Video Streaming**: Cemu captures the GamePad framebuffer via Vulkan and encodes it using hardware H.264 MFT, streaming over UDP `26761` directly to Android's hardware `MediaCodec`.
3. **Crystal-Clear GamePad Audio**: Cemu taps DRC audio from `snd_core` and streams 48 kHz stereo PCM over UDP `26762`.
4. **Haptics / Rumble**: Cemu translates VPAD motor packets into real-time rumble haptics on the phone with customizable perceptual slider intensity.
5. **Upstream Subsystem Modularization**: All streaming, discovery, and pairing logic is isolated into `Cemu/src/streaming/` with non-invasive delegates and a 1-click **"Auto-Discover CemuPad"** GUI button in Cemu Input Settings.

---

## 📍 Current Implementation Status (September 2026)

| Phase | Description | Status | Verification State |
|:---|:---|:---:|:---|
| **Phase 0** | Toolchain, Scaffolding & DSU Protocol Correction | ✅ Complete (100%) | Verified on Android 16 (SDK 36) |
| **Phase 1** | DSU Controller Input (Buttons, Sticks, Touch, Gyro/Accel) | ✅ Complete (100%) | Live-verified in gameplay with zero-bias calibration |
| **Phase 2** | Low-Latency Video Streaming (60 FPS H.264 MFT) | ✅ Complete (100%) | Live-verified in *Super Mario 3D World* with instant IDR recovery |
| **Phase 3** | Audio Streaming (48 kHz Stereo PCM) | ✅ Complete (100%) | Live-verified via DSP DMA tap; zero buzz, crystal clear |
| **Phase 4 UX** | Dark Drawer, Back Button, Scrim Dismiss, Rumble Slider | ✅ Complete (100%) | Live-verified on physical Samsung Galaxy S23 FE |
| **Phase 4.0** | Subsystem Refactor (`Cemu/src/streaming/`) & 1-Click Cemu UI | ✅ Complete (100%) | Release `CemuBin` build exit 0, user quality-tested |
| **Phase 4.1–4.4** | Auto-Discovery, Dynamic Encoding, Voice PCM, PIN Security | 🟢 Ready for Execution | Detailed checklist plans in `plans/` |
| **Phase 5** | Release Packaging, ProGuard/R8 & CI/CD Pipeline | 🟢 Ready for Execution | Detailed checklist plan in `plans/` |

---

## 🗺️ Master Plan & Roadmap Documents

The single source of truth for all upcoming work is located inside the [`plans/`](plans/) directory:

| Document | Purpose |
|:---|:---|
| [**`plans/README.md`**](plans/README.md) | Roadmap index and phase execution order. |
| [**`plans/AI_HARNESS_TESTING_GUIDE.md`**](plans/AI_HARNESS_TESTING_GUIDE.md) | **Master Testing Manual**: Paths, Cemu CLI launch commands, game ROM paths, screenshot capture techniques, and log inspection methods. |
| [**`plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md`**](plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md) | Phase 4.0 code-level checklist: Subsystem isolation & 1-click Cemu UI pairing. |
| [**`plans/PHASE_4_1_AUTO_DISCOVERY.md`**](plans/PHASE_4_1_AUTO_DISCOVERY.md) | Phase 4.1 code-level checklist: Subnet UDP broadcast auto-discovery. |
| [**`plans/PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md`**](plans/PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md) | Phase 4.2 code-level checklist: Dynamic bitrate and resolution controls. |
| [**`plans/PHASE_4_3_VOICE_PCM_STREAMING.md`**](plans/PHASE_4_3_VOICE_PCM_STREAMING.md) | Phase 4.3 code-level checklist: Direct 32 kHz voice PCM microphone streaming. |
| [**`plans/PHASE_4_4_SESSION_SECURITY_AND_PIN.md`**](plans/PHASE_4_4_SESSION_SECURITY_AND_PIN.md) | Phase 4.4 code-level checklist: Optional 4-digit PIN pairing & session security. |
| [**`plans/PHASE_5_RELEASE_AND_PACKAGING.md`**](plans/PHASE_5_RELEASE_AND_PACKAGING.md) | Phase 5 code-level checklist: ProGuard/R8 optimization, APK signing, CI/CD. |

---

## 🛠️ Operating Rules for Autonomous AI Coding Agents

When tasked with implementing any feature or phase:

### 1. Sequential Checklist Execution
- Open the target phase `.md` file (e.g. `plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md`).
- Identify the first unchecked `- [ ]` task in Section 3.
- Complete the code modification.

### 2. Verify Before Checking Off
- Compile the code using the commands in the plan (e.g. CMake Release build for Cemu or `./gradlew.bat testDebugUnitTest` for Android).
- Once compilation succeeds with code `0`, update the checkbox in the `.md` file from `- [ ]` to `- [x]`.
- **CRITICAL RULE**: Never check off a step `- [x]` unless the code has been written and verified to compile cleanly.

### 3. Verification & Live Diagnostics
Consult [**`plans/AI_HARNESS_TESTING_GUIDE.md`**](plans/AI_HARNESS_TESTING_GUIDE.md) for testing commands:
- **Game Launch**: `Start-Process Cemu.exe -ArgumentList '-g "D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua"'`
- **Cemu Log**: `Get-Content "...\cemu\log.txt" -Tail 30`
- **Android Logcat**: `adb logcat -d -s CemuPad:*`
- **Phone Screenshot**: `adb exec-out screencap -p > phone_screen.png`

### 4. Commit and Push at Milestones
- Once a step or section is verified and checked off, commit the code and the updated plan:
  ```powershell
  git add plans/ <source_files>
  git commit -m "feat(<component>): complete Step X.X - <summary>"
  git push origin main
  ```
- This ensures any subsequent model or session can pick up immediately from the next open `- [ ]` task.
