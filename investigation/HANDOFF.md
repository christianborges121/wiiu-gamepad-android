# Current harness handoff

Last updated: 2026-09-12 13:10 (America/New_York)

Read this file first. Then read the dated folders it points to. Keep this file
current at the end of every session so a new harness can resume without the
prior chat.

## Where we left off

Phase 2 color verification is active with Wind Waker HD running in Cemu
and streaming to the phone. Wind Waker inventory colors verified correct
at 30 FPS (packed A2B10G10R10 path works). Hyrule Warriors color check is
still open. Phase 4 UX drawer/resolution work from this morning is done
and installed on the wireless ADB phone.

Do not restart from Phase 0 or from the reconnect investigation as if they were
unfinished implementation. Those records are evidence. The next verification
is whatever game session the user has up, most likely Hyrule Warriors colors.

## Just completed (this session)

1. Wind Waker HD color verified live: inventory screen streams at 30 FPS
   with correct blue/white/parchment/yellow (packed A2B10G10R10 path
   works, no format-64 failure). See
   `investigation/2026-09-12-color/01-wind-waker.md`.
2. Connection-help drawer toggle: new persisted `showConnectionHelp`
   setting hides the startup card; live-verified on the phone including
   force-stop persistence. See `investigation/2026-09-12-ux/04-connection-help-toggle.md`.
3. Resolution presets now drive the SurfaceView buffer size
   (`DisplayLayout.surfaceBufferSize`); live-verified both directions via
   `surfaceChanged` (1920x1080 / 854x480). Fixed a stale-closure stomp and
   made the drawer scrollable. See
   `investigation/2026-09-12-ux/05-resolution-preset-surface.md`.
4. Earlier: overlay defaults off, overlay/fit/resolution persist, overlay
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
- Hyrule Warriors color verification still open (Wind Waker done 2026-09-12).
- Original Wii U GamePad fit-mode live confirmation still useful.
- Overlay toggle against a running stream, and end-to-end resolution scaling
  with live video, still need a watched game session.
- Do not kill a running Cemu/game to deploy.

## Dated notes

- Reconnect investigation: `investigation/2026-09-12-reconnect/`
- UX session: `investigation/2026-09-12-ux/`
- Color verification: `investigation/2026-09-12-color/`
