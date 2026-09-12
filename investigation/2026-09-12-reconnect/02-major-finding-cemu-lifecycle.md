# Major finding: Cemu did not renew DSU after the phone restart

Date: 2026-09-12

## Evidence

The active Cemu log at `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt` records:

- `08:49:49.468` — `VideoStreamServer: Started on TCP port 26761`
- `08:49:49.469` — `StreamingCapture: Initialized and listening for GamePad streaming connections`
- `08:49:52.697` — `VideoStreamServer: Android client connected to video stream!`
- `08:49:52.700` — `VideoStreamServer: Received IDR_REQUEST opcode (0x10) from Android client!`
- `08:54:17.233` — `VideoStreamServer: Client disconnected on send error`

There is no later Android-client connection or IDR request in that log. This matches the phone remaining at zero DSU clients and awaiting a stream.

## Source comparison

The workspace Cemu source now has `DSUControllerProvider::probe_thread()`, which queues a DSU data request every second. That source change was not present in the running executable when the game started at 08:49; the later RelWithDebInfo build attempt failed before deployment.
