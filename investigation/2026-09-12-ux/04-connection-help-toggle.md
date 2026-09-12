# Connection-help drawer toggle

Date: 2026-09-12

## Request

Checklist 4.3 had the connection/help-text toggle as the one explicitly
missing drawer item.

## Implementation

- `DisplaySettings.showConnectionHelp` (default `true`, preserves existing
  behavior for current installs).
- `AppSettingsCodec` key `connection_help_visible`; missing decodes to
  `true`, unlike the diagnostics overlay which defaults off.
- `MainActivity` loads/persists the new key in `cemupad_connection`.
- `MainScreen` drawer gains a "Connection help" switch; the startup card
  renders only when `!isVideoStreaming && showHelp`.
- `AppSettingsCodecTest` covers the default, explicit false, and round trip.

## Validation

- `testDebugUnitTest` passes (3/3 codec tests).
- Debug APK installed on the wireless ADB phone.
- Live: toggled off via UI tap (uiautomator bounds), card disappeared;
  `connection_help_visible=false` in prefs; force-stop + relaunch kept the
  card hidden; toggled back on, card returned, prefs `true`.
