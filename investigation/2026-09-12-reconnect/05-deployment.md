# Deployment milestone

Date: 2026-09-12 09:44

The running Cemu process was confirmed absent before deployment. The existing
EmuDeck executable was backed up to:

`C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe.backup-20260912-094453`

The newly built `Cemu/bin/Cemu_relwithdebinfo.exe` was copied to the EmuDeck
`Cemu.exe` path, and resources were synchronized successfully. SHA-256 matched
on both sides:

`461AE997B3B69FCC820366DD1706F5444484F78FBCB7F10DA75A979EE443C7C6`

Runtime verification is still pending: launch Cemu, start the game, then
restart CemuPad and confirm DSU client recovery, TCP video reconnect, and a new
IDR request in both logs.
