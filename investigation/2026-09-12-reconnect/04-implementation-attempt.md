# Implementation attempt: persist the Cemu peer on Android

Date: 2026-09-12

## Change

`MainActivity` now loads `last_cemu_ip` from the `cemupad_connection` shared preferences on process start and saves each verified non-loopback DSU client address when `onClientConnected` fires. This allows `onResume()` to start the existing `VideoStreamClient` after an Android process restart.

## Validation

`cmd /c gradlew.bat testDebugUnitTest` passed after using a workspace-local Gradle cache. The only compiler output was the pre-existing `FLAG_FULLSCREEN` deprecation warning.

The Cemu RelWithDebInfo target also now builds successfully to
`Cemu/bin/Cemu_relwithdebinfo.exe`. The Android debug APK was rebuilt and
installed successfully on the connected device at 09:38.

## Not yet verified

The installed app had no previously persisted IP (the preference file did not
exist), so startup could not yet exercise the new persisted-host path. End-to-
end reconnect still requires a successful DSU callback once, followed by an
Android restart, and deployment of the newly built Cemu executable. The running
Cemu process was not stopped.
