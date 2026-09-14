# Wii U GamePad for Android — Autonomous Next Phases Implementation Plans

This directory contains comprehensive, code-level implementation plans for all remaining phases of the **Wii U GamePad for Android** project.

Each document is fully self-contained and formatted specifically so that any AI coding agent (including free-tier or open-weight models) can pick up the task, follow the instructions sequentially, execute the code modifications, and autonomously verify the results using the included test harnesses.

---

## 🗺️ Roadmap & Phase Index

| Plan Document | Target Feature | Primary Tech Stack | Status |
|:---|:---|:---|:---:|
| [**PHASE_7_APOLLO_MOONLIGHT_STREAMING_ENHANCEMENTS.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_7_APOLLO_MOONLIGHT_STREAMING_ENHANCEMENTS.md) | **Apollo & Moonlight Streaming Enhancements (Vendor Keys, FEC, VSync, HEVC)** | C++ nanors / MediaCodec / Choreographer / HEVC | **Ready for Execution** |
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
