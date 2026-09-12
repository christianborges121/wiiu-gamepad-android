# App settings persistence

Date: 2026-09-12

## Request

Default the diagnostics overlay to false, but store it as app configuration so
the next launch remembers it. Also persist fit mode and resolution.

## Implementation

`DisplaySettings` now includes `diagnosticsOverlayEnabled` (default `false`)
alongside `fitMode` and `resolutionPreset`.

`AppSettingsCodec` encodes and decodes those fields as:

- `display_fit_mode`
- `display_resolution`
- `diagnostics_overlay_enabled`

`MainActivity` loads them from SharedPreferences `cemupad_connection` at
startup (same file as `last_cemu_ip`) and writes them when the drawer changes
settings. Unknown enum names fall back to Aspect Fit and Native 854x480.

The overlay switch in `MainScreen` now calls `onDisplaySettingsChanged` instead
of keeping a session-only boolean.

## Validation

- `AppSettingsCodecTest` covers missing values, unknown names, and round trip.
- `testDebugUnitTest` passed.
- Debug APK installed on the wireless ADB phone.
