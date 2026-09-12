# Major finding: the failed build was not caused by the running EmuDeck Cemu process

Date: 2026-09-12

## Evidence

The build log reports:

`LINK : fatal error LNK1104: cannot open file 'C:\Projects\wiiu-gamepad-android\Cemu\bin\Cemu_relwithdebinfo.exe'`

The running process is `Cemu.exe` from:

`C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe`

It is a different path and filename from the linker target. The game was left running and was not killed. The updated workspace executable was therefore not deployed to EmuDeck.

The active Cemu log proves that the deployed executable includes the video-stream server, but it does not prove that it includes the newer periodic DSU probe.
