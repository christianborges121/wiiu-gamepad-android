# Current harness handoff

Last updated: 2026-09-12 11:33 (America/New_York)

Read this file first. Then read the dated folders it points to. Keep this file
current at the end of every session so a new harness can resume without the
prior chat.

## Where we left off

Phase 4 fullscreen UX is in progress on the Android app. The latest user-facing
change is a transparent, single-line diagnostics overlay in the top-left
corner. That debug APK is installed on the wireless ADB phone.

Do not restart from Phase 0 or from the reconnect investigation as if they were
unfinished implementation. Those records are evidence. The next code work is
whatever the user asks next, most likely more Phase 4 UX polish on the live
phone session.

## Just completed (this session)

1. Connection-help drawer toggle: new persisted `showConnectionHelp`
   setting hides the startup card; live-verified on the phone including
   force-stop persistence. See `investigation/2026-09-12-ux/04-connection-help-toggle.md`.
2. Resolution presets now drive the SurfaceView buffer size
   (`DisplayLayout.surfaceBufferSize`); live-verified both directions via
   `surfaceChanged` (1920x1080 / 854x480). Fixed a stale-closure stomp and
   made the drawer scrollable. See
   `investigation/2026-09-12-ux/05-resolution-preset-surface.md`.
3. Earlier: overlay defaults off, overlay/fit/resolution persist, overlay
   restyled to one top-left line, checklist reconciled, version control
   started (private app repo, public attached Cemu fork).

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
- Wind Waker HD / Hyrule Warriors color verification still open.
- Original Wii U GamePad fit-mode live confirmation still useful.
- Drawer does not yet have a separate connection/help-text toggle.
- Resolution preset is stored and shown; confirm it actually changes the
  decoder/surface size if the user expects that.
- Do not kill a running Cemu/game to deploy.

## Dated notes

- Reconnect investigation: `investigation/2026-09-12-reconnect/`
- UX session: `investigation/2026-09-12-ux/`
