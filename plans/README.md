# Wii U GamePad for Android — Autonomous Next Phases Implementation Plans

This directory contains comprehensive, code-level implementation plans for all remaining phases of the **Wii U GamePad for Android** project.

Each document is fully self-contained and formatted specifically so that any AI coding agent (including free-tier or open-weight models) can pick up the task, follow the instructions sequentially, execute the code modifications, and autonomously verify the results using the included test harnesses.

---

## 🗺️ Roadmap & Phase Index

| Plan Document | Target Feature | Primary Tech Stack | Status |
|:---|:---|:---|:---:|
| [**PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md) | **Subsystem Modularization & 1-Click Cemu UI Pairing** | C++ Module Architecture / wxWidgets / Cemu InputManager | **Ready for Execution** |
| [**MODULAR_CEMUPAD_SUBSYSTEM_PLAN.md**](file:///c:/Projects/wiiu-gamepad-android/plans/MODULAR_CEMUPAD_SUBSYSTEM_PLAN.md) | Architectural Design Document: Subsystem Isolation & DSU Bridge | Architecture / Class Diagrams / Specifications | Reference Design |
| [**PHASE_4_1_AUTO_DISCOVERY.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_1_AUTO_DISCOVERY.md) | Zero-Config Auto-Discovery & Cemu UI Pairing Dialog | C++ Winsock / wxWidgets / Kotlin UDP Datagram | Ready for Execution |
| [**PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md) | Dynamic Bitrate & Resolution Encoder Controls | C++ Windows Media Foundation / Jetpack Compose | Ready for Execution |
| [**PHASE_4_3_VOICE_PCM_STREAMING.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_3_VOICE_PCM_STREAMING.md) | Direct 32 kHz Voice PCM Microphone Streaming | C++ Cafe OS `mic.cpp` / Android `AudioRecord` | Ready for Execution |
| [**PHASE_4_4_SESSION_SECURITY_AND_PIN.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_4_SESSION_SECURITY_AND_PIN.md) | Session Security, Host Selection & PIN Pairing | TCP Control Protocol / Crypto / Jetpack Compose | Ready for Execution |
| [**PHASE_5_RELEASE_AND_PACKAGING.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_5_RELEASE_AND_PACKAGING.md) | Release Hardening, ProGuard/R8 & CI/CD Pipeline | Gradle / ProGuard / GitHub Actions CI | Ready for Execution |

| [**AI_HARNESS_TESTING_GUIDE.md**](file:///c:/Projects/wiiu-gamepad-android/plans/AI_HARNESS_TESTING_GUIDE.md) | **Autonomous Testing, Debugging & Log Inspection Harness** | PowerShell / ADB Screencap / Cemu CLI / Logcat | **Operational Manual** |

---

## 🛠️ Instructions for Autonomous AI Models

Before and during execution of any phase:

1. **Review the Master Testing Manual**: Read [**`AI_HARNESS_TESTING_GUIDE.md`**](file:///c:/Projects/wiiu-gamepad-android/plans/AI_HARNESS_TESTING_GUIDE.md) for complete paths, command-line launch commands, game ROM paths, screenshot capture techniques, and log inspection commands.
2. **Select & Open Target Plan**: Open the corresponding `.md` file (e.g. `PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md`).
3. **Execute Step-by-Step Code Changes**: Work sequentially through Section 3's checklists. Use `replace_file_content` or `multi_replace_file_content` targeting the specified lines. Preserve all existing comments and formatting.
4. **Compile & Deploy**:
   - Cemu: `cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target Cemu`
   - Deploy: `Stop-Process -Name Cemu -Force ; Copy-Item c:\Projects\wiiu-gamepad-android\Cemu\build\bin\Release\Cemu.exe C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe -Force`
   - Android: `cd android-gamepad-app ; .\gradlew.bat testDebugUnitTest assembleDebug ; adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. **Autonomous Live Verification**:
   - Launch Cemu with game: `Start-Process "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe" -ArgumentList '-g "D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua"' -WorkingDirectory "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu"`
   - Launch Android app: `adb shell am start -n com.cemupad/.MainActivity`
   - Capture phone screenshot: `adb exec-out screencap -p > phone_screen.png` and view via `view_file`
   - Inspect Cemu log: `Get-Content "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Tail 30`
   - Inspect Android log: `adb logcat -d -s CemuPad:*`
6. **Commit & Push**: Once all verification checkpoints pass, check off the task list, commit the changes with a clear message, and push to GitHub `main`.
