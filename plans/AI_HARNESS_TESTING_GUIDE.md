# AI Harness Testing & Autonomous Debugging Guide

This document is the **standard operating manual** for any AI agent or developer executing, verifying, or debugging features across the **Wii U GamePad for Android** and **Cemu** codebases.

---

## 🗂️ 1. Environment & Path Reference

| Component | Path / Location |
|:---|:---|
| **Android Workspace** | `c:\Projects\wiiu-gamepad-android\android-gamepad-app` |
| **Cemu Workspace** | `c:\Projects\wiiu-gamepad-android\Cemu` |
| **Cemu CMake Build Dir** | `c:\Projects\wiiu-gamepad-android\Cemu\build` |
| **Cemu Compiled Binary** | `c:\Projects\wiiu-gamepad-android\Cemu\build\bin\Release\Cemu.exe` |
| **EmuDeck Cemu Runtime Dir** | `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\` |
| **EmuDeck Active Executable** | `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe` |
| **Cemu Runtime Log File** | `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt` |
| **Controller Profiles Dir** | `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\controllerProfiles\` |
| **GamePad Slot 0 Profile** | `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\controllerProfiles\controller0.xml` |
| **Wii U ROMs Directory** | `D:\Emulation\roms\wiiu\` |
| **Primary Test Game 1** | `D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua` |
| **Primary Test Game 2** | `D:\Emulation\roms\wiiu\The Wind Waker HD (US).wua` |

---

## 🖥️ 2. Cemu Process Management (PowerShell)

### A. Compile Cemu Release Binary
Always build the `Cemu` target in `Release` configuration:
```powershell
cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target Cemu
```

### B. Kill Existing Cemu Process
Before replacing the binary or starting a clean test run, kill any lingering Cemu processes:
```powershell
Stop-Process -Name Cemu -Force -ErrorAction SilentlyContinue
```

### C. Deploy Compiled Binary to EmuDeck
Overwrite the active EmuDeck executable:
```powershell
Copy-Item c:\Projects\wiiu-gamepad-android\Cemu\build\bin\Release\Cemu.exe C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe -Force
```

### D. Launch Cemu Standalone (for GUI & Input Settings Testing)
Launches Cemu without a game loaded so you can test Input Settings and the Pairing Dialog:
```powershell
Start-Process "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe" -WorkingDirectory "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu"
```

### E. Launch Cemu Directly into Super Mario 3D World (for Gameplay Testing)
Launches the game directly in background:
```powershell
Start-Process "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe" -ArgumentList '-g "D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua"' -WorkingDirectory "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu"
```

---

## 📜 3. Log Inspection & Diagnostics

### A. Host PC (Cemu Logs)
Cemu flushes all logging immediately to `log.txt`:

1. **View Last 30 Lines**:
   ```powershell
   Get-Content "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Tail 30
   ```

2. **Filter Specifically for Streaming, Video, Audio, or Discovery**:
   ```powershell
   Select-String -Path "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Pattern "StreamingCapture|VideoStream|VideoEncoder|CemuPad|Audio|Rumble|Discovery|mic_" | Select-Object -Last 25
   ```

3. **Verify DSU Controller Loading on Slot 0**:
   ```powershell
   Select-String -Path "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Pattern "controller0|DSUClient|vpad" | Select-Object -Last 15
   ```

### B. Android Phone Logs (ADB Logcat)
The connected Samsung Galaxy S23 FE logs with tag prefixes:

1. **Dump Recent App Logs**:
   ```powershell
   adb logcat -d -s CemuPad:* DSUServer:* VideoStreamClient:* AudioStreamClient:* MicVoiceStreamer:*
   ```

2. **Filter for Touch, Gyro, or Vibration**:
   ```powershell
   adb logcat -d -s CemuPad:* | Select-String -Pattern "Rumble|vibrat|touch|gyro" | Select-Object -Last 20
   ```

3. **Clear Log Buffer Before a New Test**:
   ```powershell
   adb logcat -c
   ```

---

## 📸 4. Visual Verification via Screenshots

### A. Android Device Screenshot Capture
To inspect the phone's screen state (app layout, settings drawer, streaming feed, video quality):
```powershell
adb exec-out screencap -p > c:\Projects\wiiu-gamepad-android\phone_screen.png
```
Then use `view_file` on `c:\Projects\wiiu-gamepad-android\phone_screen.png` to review the visual result.

### B. Host PC Desktop / Cemu Screen Capture
To inspect Cemu's rendering window or wxWidgets pairing dialog:
```powershell
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$screen = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
$bitmap = New-Object System.Drawing.Bitmap $screen.Width, $screen.Height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.CopyFromScreen($screen.Location, [System.Drawing.Point]::Empty, $screen.Size)
$bitmap.Save("c:\Projects\wiiu-gamepad-android\cemu_desktop.png", [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
```
Then use `view_file` on `c:\Projects\wiiu-gamepad-android\cemu_desktop.png`.

---

## 📱 5. Interactive Android Control via ADB

### A. Tap Coordinate
Useful for toggling buttons, opening drawers, or interacting with dialogs:
```powershell
adb shell input tap <X> <Y>
```

### B. Swipe Gesture
Useful for scrolling settings or opening the drawer:
```powershell
adb shell input swipe <X1> <Y1> <X2> <Y2> <durationMs>
```
*Example: Open drawer from left edge*:
```powershell
adb shell input swipe 10 500 600 500 250
```

### C. Android Hardware Key Events
- **Back Button** (closes drawer or dialog):
  ```powershell
  adb shell input keyevent 4
  ```
- **Home Button**:
  ```powershell
  adb shell input keyevent 3
  ```
- **Controller Buttons** (for Mapping Wizard testing):
  ```powershell
  adb shell input keyevent 96  # BUTTON_A
  adb shell input keyevent 97  # BUTTON_B
  adb shell input keyevent 99  # BUTTON_X
  adb shell input keyevent 100 # BUTTON_Y
  # D-Pad via keyevents or hat axis via sendevent
  ```
- **Controller Sticks / Triggers** (for stick mapping):
  ```powershell
  # Use sendevent or input motionevent via physical controller; for harness, prefer real controller
  # or inject via: adb shell am broadcast -a com.cemupad.INJECT_INPUT --es button A --ez down true
  ```

### D. App Lifecycle Control
- **Launch CemuPad**:
  ```powershell
  adb shell am start -n com.cemupad/.MainActivity
  ```
- **Force-Stop CemuPad**:
  ```powershell
  adb shell am force-stop com.cemupad
  ```

---

## 🧪 6. Android Build & Deployment

### A. Run Android Unit Tests
```powershell
cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
.\gradlew.bat testDebugUnitTest
```

### B. Compile & Deploy Debug APK to Phone
```powershell
cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
.\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.cemupad/.MainActivity
```

---

## 🔄 7. Standard End-to-End Verification Flow

Follow this exact loop whenever you make a change:

1. **Compile Both Ends**:
   - `cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target Cemu`
   - `cd android-gamepad-app ; .\gradlew.bat testDebugUnitTest assembleDebug`
2. **Deploy**:
   - Kill old Cemu: `Stop-Process -Name Cemu -Force -ErrorAction SilentlyContinue`
   - Copy Cemu.exe to EmuDeck folder.
   - Install APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. **Launch & Test**:
   - Launch Cemu: `Start-Process "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe" -ArgumentList '-g "D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua"' -WorkingDirectory "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu"`
   - Launch Android app: `adb shell am start -n com.cemupad/.MainActivity`
4. **Diagnose**:
   - Inspect Cemu log: `Get-Content "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Tail 30`
   - Inspect Android logcat: `adb logcat -d -s CemuPad:*`
   - Capture screenshot: `adb exec-out screencap -p > phone_screen.png` and view with `view_file`.
5. **Confirm and Commit**:
   - Verify feature is functional.
   - Check off the completed steps in the plan file.
   - Commit changes with clear, descriptive commit message and push to GitHub `main`.

---

## ✅ 8. Step-by-Step Checklist Workflow (Checking Off Steps As You Go)

To maintain rigorous progress tracking and prevent regressions or repeated work, all autonomous models must adhere to this checklist protocol:

1. **Locate the Active Task**:
   - Open the active phase plan file (e.g. `plans/PHASE_4_0_MODULAR_SUBSYSTEM_REFACTOR.md`).
   - Identify the first unchecked `- [ ]` task in Section 3.
2. **Implement the Code Change**:
   - Apply the edits using `replace_file_content` or `write_to_file`.
   - Preserve existing comments, formatting, and file structure.
3. **Verify Build & Correctness**:
   - Run the automated compilation or test command for that task (e.g. `cmake --build ...` or `.\gradlew.bat testDebugUnitTest`).
   - Ensure the command exits with code `0`.
4. **Immediately Check Off the Item**:
   - In the active plan `.md` file, update the checkbox from `- [ ]` to `- [x]`.
   - Example:
     ```diff
     -- [ ] **Step 3.1: Subsystem Directory & Build Isolation**
     +- [x] **Step 3.1: Subsystem Directory & Build Isolation**
     ```
   - **MANDATORY RULE**: NEVER mark `- [x]` before the code is actually written and verified to compile.
5. **Complete Section 4 Live Verification**:
   - Once all code steps in Section 3 are `- [x]`, run through Section 4's verification checklist.
   - Launch Cemu, run the game, capture screenshots/logs, and mark off each `- [ ]` as verified.
6. **Commit & Push at Completed Milestones**:
   - Commit the updated plan file alongside the code changes:
     ```powershell
     git add plans/ <source_files>
     git commit -m "feat(streaming): complete Step X.X - <description>"
     git push origin main
     ```
   - This ensures that if the agent run stops or hands off, the next model picks up seamlessly from the exact next open `- [ ]` checkbox.
