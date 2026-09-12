# AI Harness Instructions: Wii U GamePad for Android

Use this file as the onboarding prompt for any AI coding harness working on this project.

## Role

Act as a senior software engineer working directly in the Windows workspace:

```text
C:\Projects\wiiu-gamepad-android
```

The project makes an Android phone act as a Wii U GamePad for Cemu:

- Android sends controller, touch, and motion input to Cemu through the DSU protocol.
- A Cemu fork captures the DRC/GamePad framebuffer and streams video to Android.
- Future phases add audio, microphone input, discovery, pairing, rumble, and UX polish.

Work directly in the repository. Do not only propose changes when implementation and validation are possible.

## Where we left off

The latest pickup file is `investigation/HANDOFF.md`. Read it before any other
project file. It states the last completed work, the installed device, and the
open items. After finishing a task, rewrite that file so a new harness can
resume without this chat.

As of 2026-09-12 11:33:

- Phase 4 UX is active. Diagnostics overlay, fit mode, and resolution persist.
- Overlay is a transparent one-line status in the top-left corner (no card).
- Debug APK is installed on the wireless ADB Galaxy S23 FE.
- Reconnect and per-game color verification remain open evidence items, not
  the current coding starting point unless the user asks.

## First Read

Read these files before changing behavior:

1. `investigation/HANDOFF.md` - current session pickup point.
2. The dated `investigation/` folder named in that handoff.
3. `copilot_project_checklist.md` - active Copilot implementation plan.
4. `architecture_and_plan.md` - system architecture and protocol decisions.
5. The nearest implementation and test files for the behavior being changed.

`PROJECT_CHECKLIST.md` is the original backup. Do not treat it as the live
plan. Update `copilot_project_checklist.md` when completing work. Do not
rewrite completed history without evidence.

For reconnect or Cemu deployment work, also read every file in
`investigation/2026-09-12-reconnect/`. For the current UX settings/overlay
work, read `investigation/2026-09-12-ux/`.

## Repository Layout

```text
android-gamepad-app/     Kotlin Android app
Cemu/                    Cemu source and fork modifications
vanilla/                 Protocol reference implementation
build/                   Generated CMake build output; disposable
references/              Local Apollo and Artemis research checkouts
investigation/           Session notes and HANDOFF.md for the next harness
backup-project.ps1       Dated source-backup script
```

Apollo and Artemis are reference projects only:

```text
references/Apollo   commit adc5c5a
references/Artemis  commit c5cf27f
```

Use them to understand architecture and algorithms. Do not copy code blindly; review their GPL licensing before reuse.

## Current Implementation Status

### Phase 0: Complete

- Android Gradle project exists.
- SDK/target SDK 36, minimum SDK 24.
- Application ID is `com.cemupad`.
- DSU protocol specification and unit conversions were corrected.

### Phase 1: Implemented, live verification pending

- Android DSU server listens on UDP `26760`.
- Cemu acts as the DSU client and polls the Android phone.
- Buttons, sticks, triggers, D-pad, touch, accelerometer, and gyroscope are implemented.
- Unit and UDP loopback tests pass.
- Physical controller and in-game Cemu verification remain open.

### Phase 2: Active hardening

Implemented:

- Cemu Vulkan DRC capture hook.
- Headless DRC rendering while the desktop pad view is closed.
- RGBA/BGRA channel-order propagation.
- Packed `VK_FORMAT_A2B10G10R10_UNORM_PACK32` support.
- Vulkan command-buffer completion checks for staging-buffer reads.
- Bounded two-frame capture queue.
- Background pixel conversion, H.264 encoding, and TCP transmission worker.
- Android AVC decoder capability probing.
- Conditional Android low-latency decoder options.
- TCP video server on port `26761`.
- Android TCP client and direct `MediaCodec` Surface output.

Still open:

- Validate colors in Wind Waker HD and Hyrule Warriors.
- Validate other DRC Vulkan formats and sRGB/range behavior.
- Add OpenGL/PBO capture.
- Add UDP/RTP-style video transport and FEC.
- Add loss statistics and robust IDR recovery.
- Measure render overhead and glass-to-glass latency.
- Add broader hardware encoder selection.

### Phase 4 UX: In progress (current focus)

Implemented on Android and installed on the phone:

- Immersive sticky landscape fullscreen.
- Back-button configuration drawer.
- Fit mode (Aspect Fit / Screen Fill / Stretch) and resolution presets.
- Diagnostics overlay toggle, default **off**, persisted with fit mode and
  resolution in SharedPreferences `cemupad_connection`.
- Transparent one-line overlay in the top-left corner.
- Startup connection card until video starts.
- Last verified Cemu IP persisted for video reconnect attempts.

Still open in this phase:

- Separate connection/help-text toggle in the drawer.
- Confirm resolution presets actually change surface/encoder size.
- Live confirmation of the new overlay on a running stream.
- Original Wii U GamePad fit-mode live confirmation.

### Phase 3 and remaining Phase 4

Audio, microphone streaming, discovery, pairing, rumble, and virtual controls
are not implemented yet. Follow `copilot_project_checklist.md` for the order.

## Build Commands

Run commands from the relevant directory using PowerShell.

### Android unit tests

```powershell
Push-Location C:\Projects\wiiu-gamepad-android\android-gamepad-app
cmd /c gradlew.bat testDebugUnitTest
Pop-Location
```

### Android debug APK

```powershell
Push-Location C:\Projects\wiiu-gamepad-android\android-gamepad-app
cmd /c gradlew.bat assembleDebug
Pop-Location
```

APK path:

```text
android-gamepad-app/app/build/outputs/apk/debug/app-debug.apk
```

### Cemu Release build

```powershell
Push-Location C:\Projects\wiiu-gamepad-android
cmake --build build --config Release --target CemuBin -j 4
Pop-Location
```

Built executable:

```text
Cemu/bin/Cemu_release.exe
```

Existing linker warnings involving `/static`, `MSVCRT`, and `libucrt` are known build warnings. Treat actual compiler errors, link failures, or runtime errors as failures.

## Android Wireless Debugging

Find the active device:

```powershell
adb devices -l
```

The device may appear as an mDNS/TLS serial rather than an IP:port serial. Use the exact serial printed by `adb devices -l`.

Last known wireless device (confirm before use):

```text
adb-R5CWC0G7CKW-WpLfEG._adb-tls-connect._tcp  SM_S711U1  Galaxy S23 FE
```

Install the current APK:

```powershell
adb -s <device-serial> install -r android-gamepad-app/app/build/outputs/apk/debug/app-debug.apk
```

Useful diagnostics:

```powershell
adb -s <device-serial> shell pidof com.cemupad
adb -s <device-serial> logcat -d -t 500 | Select-String -Pattern 'CemuPad|Video|MediaCodec|DSU|FATAL|Exception|error' -CaseSensitive:$false
```

Do not assume the historical endpoint `192.168.68.109:44985` is still active. Prefer the currently listed ADB serial.

## Cemu Deployment

The active EmuDeck installation is:

```text
C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu
```

Before replacing `Cemu.exe`:

1. Check whether Cemu is running.
2. Do not kill the process automatically because game state may be lost.
3. Wait for the user to close it if the executable is locked.
4. Back up the existing executable.
5. Copy `Cemu/bin/Cemu_release.exe` as `Cemu.exe`.
6. Synchronize `Cemu/bin/resources` if present.
7. Verify source and target SHA-256 hashes.

Example deployment command:

```powershell
$source = 'C:\Projects\wiiu-gamepad-android\Cemu\bin'
$target = 'C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Copy-Item "$target\Cemu.exe" "$target\Cemu.exe.backup-$stamp"
Copy-Item "$source\Cemu_release.exe" "$target\Cemu.exe" -Force
robocopy "$source\resources" "$target\resources" /E /COPY:DAT /DCOPY:DAT /R:1 /W:1
Get-FileHash "$source\Cemu_release.exe" -Algorithm SHA256
Get-FileHash "$target\Cemu.exe" -Algorithm SHA256
```

Preserve the EmuDeck user's `settings.xml`, `keys.txt`, `mlc01`, `controllerProfiles`, `gameProfiles`, `graphicPacks`, and shader cache unless the user explicitly requests otherwise.

## Video Troubleshooting Workflow

When Android shows no video, diagnose in this order:

1. Confirm the Android app process is running.
2. Confirm DSU connectivity and the Android phone IP.
3. Confirm Cemu listens on TCP `26761`.
4. Check Android logcat for connection, IDR, decoder, and frame counters.
5. Check Cemu `log.txt` for `VideoStreamServer`, `VideoEncoder`, and `StreamingCapture` messages.
6. Determine whether Cemu accepted the TCP client.
7. Determine whether `IDR_REQUEST` arrived.
8. Determine whether DRC frames were captured.
9. Determine whether frames were rejected by pixel-format handling.
10. Determine whether the encoder produced output.
11. Determine whether Android queued and rendered decoder output.

Cemu runtime log:

```text
C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt
```

Useful PowerShell filter:

```powershell
Select-String -Path 'C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt' -Pattern 'VideoStream|StreamingCapture|VideoEncoder|26761|IDR|format|error|failed' -CaseSensitive:$false
```

Interpretation:

- Android connects but Cemu logs no client: firewall, address, or wrong executable.
- Cemu logs client and IDR but no captured frames: DRC rendering/capture path.
- Cemu logs unsupported format: add or correct format conversion.
- Cemu logs frames but Android decoder is silent: inspect Annex B, SPS/PPS, decoder configuration, and MediaCodec errors.
- Android receives frames but falls behind: inspect decoder queue depth, PTS, frame dropping, and display pacing.

### Known Wind Waker issue

The observed failure was:

```text
StreamingCapture: Unsupported DRC Vulkan format 64
```

Vulkan format `64` is:

```text
VK_FORMAT_A2B10G10R10_UNORM_PACK32
```

This was the reason Wind Waker produced no video. The current source includes unpacking for this format. Rebuild and redeploy before retesting.

For packed `A2B10G10R10`, Vulkan names the format MSB-first, so the 32-bit
word is interpreted as:

- R: bits 0-9
- G: bits 10-19
- B: bits 20-29
- A: bits 30-31

(This matches DXGI `R10G10B10A2` and Cemu's
`TextureDecoder_R10_G10_B10_A2`. An earlier note here had R and B
backwards; the code in `VideoEncoder.cpp` was correct all along.)

Convert 10-bit values to 8-bit with approximately:

```text
(value * 255 + 511) / 1023
```

Do not confuse the desktop swapchain's BGRA format with the native DRC texture format.

### Reconnect investigation (2026-09-12, still unverified end-to-end)

The durable record is `investigation/2026-09-12-reconnect/`. First stream
worked, then Cemu disconnected when the phone stopped; after the phone
restarted it had zero DSU clients. Android now persists last Cemu IP. Cemu DSU
probe code exists and a RelWithDebInfo build was deployed. Do not treat
reconnect as proven until a fresh runtime session is exercised. Do not kill a
running game to deploy.

Fit-mode clipping was resolved the same day: `DisplayLayout` constrains Aspect
Fit by width *and* height. See `07-fit-mode-fix.md` before changing layout or
touch viewport mapping.

### UX session (2026-09-12, current)

See `investigation/2026-09-12-ux/` and `investigation/HANDOFF.md`. Persistence
and overlay restyle are implemented and installed. Next harness should continue
from the user's latest UX request, not from reconnect unless asked.

## Engineering Rules

- Start from the narrowest concrete code path and nearby test.
- State one falsifiable hypothesis before editing.
- After every substantive edit, run the narrowest relevant executable validation immediately.
- Keep changes focused; do not reformat unrelated files.
- Preserve user changes and do not reset or checkout over them.
- Do not commit or create branches unless explicitly requested.
- Do not kill Cemu automatically during deployment.
- Do not use synchronous GPU waits per frame.
- Do not encode on the Vulkan render thread.
- Keep queues bounded and drop stale video rather than accumulating latency.
- Treat color format, channel order, color space, and YUV range as explicit data.
- Prefer standard structured parsers and APIs over ad hoc string manipulation.
- Add tests for packet layouts, color conversion, frame dropping, and recovery when practical.
- Avoid copying Apollo/Artemis code without license review.
- After each completed task, update `investigation/HANDOFF.md` and add a short
  dated note under `investigation/` so the next harness can resume after token
  exhaustion. Also update `copilot_project_checklist.md` with evidence.

## Backup Rules

For source backups, the following are disposable:

```text
build/
android-gamepad-app/.gradle/
android-gamepad-app/build/
android-gamepad-app/.kotlin/
Cemu/build/
Cemu/.vs/
Cemu/.idea/
Cemu/dependencies/vcpkg_installed/
Cemu/dependencies/vcpkg/downloads/
Cemu/dependencies/vcpkg/buildtrees/
Cemu/dependencies/vcpkg/packages/
Cemu/bin/
references/
android-gamepad-app/local.properties
```

Keep source, tests, CMake/Gradle files, manifests, documentation, `vcpkg.json`, and the active checklists. Use `backup-project.ps1` for a dated backup.

## Definition of Done

A task is complete only when:

1. The relevant source change is implemented.
2. The nearest build/test/check passes.
3. Runtime behavior is verified when the device and emulator are available.
4. The active checklist is updated with evidence.
5. Remaining limitations or unverified claims are recorded explicitly.
