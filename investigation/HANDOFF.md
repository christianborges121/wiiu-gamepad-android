# Current harness handoff

Last updated: 2026-09-12 23:35 (America/New_York)

Read this file first. Then read the dated folders it points to. Keep this file
current at the end of every session so a new harness can resume without the
prior chat.

## Where we left off

Black screen on video streaming is resolved: Cemu Winsock `SO_RCVTIMEO` socket
inheritance bug and Android `idleControlMode` timing fixed. Live Super Mario 3D
World session verified streaming at 60 FPS over UDP with zero errors.

## Just completed (this session)

1. Black screen root cause identified and resolved:
   - Cemu: reset `SO_RCVTIMEO` to 0 on accepted client sockets and ignore non-fatal timeouts in `ClientRxThreadFunc`.
   - Android: enable `idleControlMode` immediately upon negotiating UDP transport in `MainActivity.kt`.
   - Deployed updated `Cemu.exe` and debug APK wirelessly.
   - In-game live verification: Super Mario 3D World streaming 60 FPS without disconnects.
   - See `investigation/2026-09-12-video/05-black-screen-so-rcvtimeo-fix.md`.
2. Code review and 15 resolution plans completed and indexed in `resolution_plans/`.

## Device

- Product: Galaxy S23 FE (`SM_S711U1`, `r11q`)
- Last ADB serial: `adb-R5CWC0G7CKW-WpLfEG._adb-tls-connect._tcp`
- Confirm with `adb devices -l` before install. Do not assume this serial.
- Prefs left at: Native 854x480, Aspect Fit, overlay off, help on.

## Device

- Product: Galaxy S23 FE (`SM_S711U1`, `r11q`)
- Last ADB serial: `adb-R5CWC0G7CKW-WpLfEG._adb-tls-connect._tcp`
- Confirm with `adb devices -l` before install. Do not assume this serial.

## Key files

- `android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`
- `android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt`
- `android-gamepad-app/app/src/main/java/com/cemupad/config/DisplaySettings.kt`
- `android-gamepad-app/app/src/main/java/com/cemupad/config/AppSettingsCodec.kt`
- `android-gamepad-app/app/src/test/java/com/cemupad/config/AppSettingsCodecTest.kt`

## Validation already run

- `testDebugUnitTest` passed after persistence work.
- `assembleDebug` and `adb install -r` succeeded after overlay restyle.

## Still open (do not claim done)

- Live reconnect of Cemu DSU + video after phone restart is still unverified.
  See `investigation/2026-09-12-reconnect/`.
- Hyrule Warriors color verification blocked (title has issues running; also seen: Vulkan device-loss crash 13:54:38, Error -4).
- Brightness parity parked (see `investigation/2026-09-12-color/02-brightness-parity.md`).
- Input outage resolved 2026-09-12 evening: DHCP churn (.193 -> .109 -> .114)
  vs static Cemu DSU config; fixed via Cemu IP update + router DHCP
  reservation. Phase 1 in-game verification unblocked (see
  `investigation/2026-09-12-reconnect/09-dhcp-config-drift.md`).
- Original Wii U GamePad fit-mode live confirmation still useful.
- Overlay toggle against a running stream, and end-to-end resolution scaling
  with live video, still need a watched game session.
- Do not kill a running Cemu/game to deploy.

## Dated notes

- Reconnect investigation: `investigation/2026-09-12-reconnect/`
- UX session: `investigation/2026-09-12-ux/`
- Color verification: `investigation/2026-09-12-color/`
