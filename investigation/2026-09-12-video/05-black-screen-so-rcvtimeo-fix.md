# Black screen root cause: Winsock SO_RCVTIMEO inheritance & client prune

Date: 2026-09-12

## Symptom
Gamepad DSU inputs worked normally over UDP 26760, but the video stream on the Android phone was completely black. Cemu logs showed:
```text
VideoStreamServer: Android client connected to video stream!
VideoStreamServer: Client switched to UDP transport
VideoStreamServer: Received IDR_REQUEST opcode (0x10) from Android client!
VideoStreamServer: Received IDR_REQUEST opcode (0x10) from Android client!
VideoStreamServer: Client control connection closed
```
The client connection was dropped exactly 500ms after connection. Android entered an endless disconnect-reconnect loop every 16.5s.

## Root Cause
1. In `VideoStreamServer.cpp:264`, `listenSock` set `SO_RCVTIMEO` to 500ms so `accept()` could periodically evaluate `m_isRunning`.
2. On Windows Winsock, sockets returned by `accept()` inherit `SO_RCVTIMEO` from the listening socket.
3. In commit `262f9796`, `ClientRxThreadFunc` added client pruning when `recv` returned `<= 0`. Once Android negotiated UDP video transport, reverse TCP opcodes ceased. Exactly 500ms later, `recv` on Windows returned `SOCKET_ERROR` with `WSAGetLastError() == WSAETIMEDOUT`.
4. `ClientRxThreadFunc` treated this timeout as a disconnected peer, erased the client from `m_clients`, and logged `Client control connection closed`.
5. With `m_clients` empty, `StreamingCapture::IsStreamingActive()` became false, preventing `LatteRenderTarget` and `VulkanRenderer` from capturing DRC frames or dispatching them to the encoder.
6. On Android (`MainActivity.kt`), `idleControlMode` was only set to true in `UdpVideoReceiver` upon receiving the *first* UDP frame. Since Cemu stopped streaming before sending UDP frames, the Android TCP socket read timed out after 15,000ms (`READ_TIMEOUT_MS`), throwing `SocketTimeoutException` and looping.

## Resolution
1. **Cemu (`VideoStreamServer.cpp`)**:
   - Explicitly reset `SO_RCVTIMEO` to 0 (infinite) on the accepted `clientSock`.
   - In `ClientRxThreadFunc`, ignore non-fatal `WSAETIMEDOUT` / `WSAEWOULDBLOCK` / `EAGAIN` errors instead of breaking and pruning.
   - Clean up socket upon legitimate disconnect.
2. **Android (`MainActivity.kt`)**:
   - Set `idleControlMode = true` immediately in `onConnected` when requesting UDP transport.
   - Reset `idleControlMode = false` on disconnect or when falling back to TCP (`onUdpSilence`).

## Verification
- Rebuilt `Cemu_release.exe` and deployed to EmuDeck emulator folder.
- Rebuilt debug APK and installed/launched wirelessly on Galaxy S23 FE.
- Launched Super Mario 3D World (`D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua`).
- Streamed continuously at 60 FPS without disconnects (`UDP video 1200 frames, 8237 packets, 0 send errors`).
- Live phone frame capture (`screencap`) confirmed full color, crisp rendering of the file-select screen at 60 FPS.
