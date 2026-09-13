# Code Review — android-gamepad-app & Cemu Fork

**Reviewed**: 2026-09-12  
**Reviewer**: Antigravity  
**Scope**: Full source-level review of all custom code in both repositories

---

## 1. Repository Structure & Organization

### android-gamepad-app

| Package | Files | Purpose |
|:---|:---:|:---|
| `com.cemupad` | [`MainActivity.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt), [`Navigation.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/Navigation.kt) | App entry point, lifecycle, input dispatch |
| `com.cemupad.dsu` | [`CRC32.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/CRC32.kt), [`DSUPacket.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUPacket.kt), [`DSUServer.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUServer.kt) | UDP protocol engine |
| `com.cemupad.input` | [`ControllerProfile.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/ControllerProfile.kt), [`GamepadInputHandler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/GamepadInputHandler.kt), [`TouchInputHandler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/TouchInputHandler.kt), [`MotionHandler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/MotionHandler.kt) | Physical & touch input |
| `com.cemupad.video` | [`VideoStreamClient.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt), [`VideoDecoder.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/VideoDecoder.kt), [`UdpVideoReceiver.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/UdpVideoReceiver.kt), [`FrameReassembler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/FrameReassembler.kt), [`UdpFrameSequencer.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/UdpFrameSequencer.kt), [`UdpVideoPacket.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/UdpVideoPacket.kt), [`AvcNalUnits.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/AvcNalUnits.kt), [`FrameRateLimiter.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/FrameRateLimiter.kt) | H.264 video pipeline |
| `com.cemupad.ui` | [`TouchSurfaceView.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/TouchSurfaceView.kt), [`MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt) | Compose UI + custom view |
| `com.cemupad.config` | [`DisplaySettings.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/config/DisplaySettings.kt), [`AppSettingsCodec.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/config/AppSettingsCodec.kt) | Settings model |
| `com.cemupad.util` | [`Logger.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/util/Logger.kt), [`NetworkUtils.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/util/NetworkUtils.kt) | Utilities |

### Cemu Fork (custom files)

| File | Purpose |
|:---|:---|
| [`StreamingCapture.cpp/h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp) | Orchestrator: frame capture → encode → broadcast |
| [`VideoEncoder.cpp/h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp) | MFT H.264 encoder (Windows Media Foundation) |
| [`VideoStreamServer.cpp/h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp) | TCP+UDP framed video server on port 26761 |
| [`VideoPixelFormat.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoPixelFormat.h) | Enum for pixel format dispatch |
| [VulkanRenderer.cpp modifications](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/Vulkan/VulkanRenderer.cpp#L1010-L1125) | DRC capture hook + double-buffered staging |
| [LatteRenderTarget.cpp modifications](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Core/LatteRenderTarget.cpp#L967-L1012) | Hook streaming capture into DRC render pass |
| [LatteThread.cpp modifications](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Core/LatteThread.cpp#L125) | Initialize/shutdown streaming at emulation start/stop |

> [!NOTE]
> All 6 custom files are properly registered in [`CMakeLists.txt`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/CMakeLists.txt#L171-L176). Build integration is correct.

**Assessment**: Clean, well-organized. Package boundaries are sensible.

---

## 2. DSU Protocol Engine (`com.cemupad.dsu`)

### 2.1 CRC32 — ✅ Correct

[`CRC32.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/CRC32.kt): Delegates to `java.util.zip.CRC32` (IEEE 802.3 polynomial `0xEDB88320`). Correctly zeroes the 4-byte CRC field at offset 8 during computation. `compute`, `finalizePacket`, and `verify` are all consistent. No issues.

### 2.2 DSUPacket — ✅ Correct, well-documented

[`DSUPacket.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUPacket.kt):

- **Header layout**: Matches Cemu's `DSUMessages.h` exactly — magic `0x53555344`, version `1001`, length excludes header, CRC at bytes 8-11.
- **VersionResponse** (24 bytes): Correct.
- **PortInfoResponse** (32 bytes): Byte 31 explicitly `0x00` (padding on standalone PortInfo). Correct.
- **DataResponse** (100 bytes): Byte 31 set to `0x01` (is_connected). All field offsets verified correct against Cemu C++ struct layout.
- **State bitmask constants**: `State1Flags` and `State2Flags` match Cemu's `DSUController.cpp` bit indices.

### 2.3 DSUServer — ⚠️ Minor issues

[`DSUServer.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUServer.kt):

- **Correct**: `recvPacket.length = recvBuffer.size` reset before each `receive()`. Client timeout at 5s. Push loop at 100 Hz.
- **Watchdog**: `ensureThreads()` cleanly restarts dead workers.

> [!WARNING]
> **Issue DS-1: Push loop sends same packet bytes to multiple clients.** [`DSUServer.kt:236-244`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUServer.kt#L236-L244) — The same `ByteArray` is reused for all clients. Since there's only ever one Cemu client in practice this is benign, but the `packetCounter` is also shared across all clients. The DSU spec assigns per-client sequence numbers. **Low priority** since single-client is the design target, but worth noting.

> [!NOTE]
> **Issue DS-2: `socket` race on `stop()`.** [`DSUServer.kt:130-134`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/dsu/DSUServer.kt#L130-L134) — `stop()` sets `isRunning = false`, closes socket, then nulls it. The receive thread may still be in `sock.receive()` and the `val sock = socket ?: break` check in the push loop uses the field directly. The `SocketException` catch on the receive side handles this correctly, so it's non-critical but slightly racy. Closing the socket while the other thread blocks on `receive()` is the standard Java idiom, so this is **acceptable**.

---

## 3. Input Subsystem (`com.cemupad.input`)

### 3.1 ControllerProfile — ✅ Correct

[`ControllerProfile.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/ControllerProfile.kt): Clean mapping abstraction. `normalizeAxis` correctly maps `[-1,1]` → `[0,255]` with `128` center, deadzone filtering, and Y-axis inversion. `normalizeTrigger` maps `[0,1]` → `[0,255]`. Math verified correct.

### 3.2 GamepadInputHandler — ✅ Correct

[`GamepadInputHandler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/GamepadInputHandler.kt):

- **D-Pad**: Properly separates `KeyEvent`-based D-pad (digital buttons) from `MotionEvent`-based HAT axis D-pad. Merges both in `syncState()`. This is the fix for the sticky hat lock bug.
- **Button mapping**: Face buttons → DSU `State2Flags`, system buttons → `State1Flags`. Correct bit masking with `or` on press and `and inv()` on release.
- **Trigger zones**: Digital ZL/ZR flag synced from analog threshold (`> 30`). Properly clears when analog returns to 0.
- **Source filtering**: `isGamepadEvent()` checks source flags and profile key codes.

> [!NOTE]
> **Minor observation**: The `onGenericMotionEvent` at line 187 returns `true` unconditionally even for motion events that may not contain stick data. This means all joystick motion events are consumed by the handler. This is correct behavior since the app IS the controller, but worth noting.

### 3.3 TouchInputHandler — ✅ Correct

[`TouchInputHandler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/TouchInputHandler.kt):

- 16:9 aspect-fit viewport calculation is correct for both wider-than and taller-than screen orientations.
- Coordinates normalized to `1920 × 942` integer space with `coerceIn(0f, 1f)` clamping.
- Multi-touch (2 pointers) correctly packed into DSU `touch1`/`touch2`.
- `touchButton` set to `true` when any touch is active.

> [!WARNING]
> **Issue TOUCH-1: ACTION_POINTER_UP doesn't update remaining touches.** [`TouchInputHandler.kt:138-147`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/TouchInputHandler.kt#L138-L147) — When `ACTION_POINTER_UP` fires and `pointerCount > 1`, the handler does nothing (comment says "re-evaluate on next move/down"). This means there's a brief period where `touch2` remains active in DSU state even though the finger was lifted. The next `ACTION_MOVE` will fix it, but there's a race window of 1-2 frames of stale touch data. **Low impact** since Cemu touch polling is coarse.

### 3.4 MotionHandler — ✅ Correct

[`MotionHandler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/input/MotionHandler.kt):

- Accelerometer → g's (`/ GRAVITY_EARTH`), gyroscope → deg/s (`× RAD_TO_DEG`). Correct.
- Landscape remapping for ROTATION_90 (`-y, x, z`) and ROTATION_270 (`y, -x, z`). Correct.
- Motion timestamp only advances on accelerometer events (synchronized pairing). Correct.
- `@Volatile` on gyro cache fields for cross-thread visibility. Correct.

---

## 4. Video Pipeline — Android Side

### 4.1 VideoStreamClient — ✅ Solid

[`VideoStreamClient.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt):

- **Framing**: 13-byte header (1 type + 4 size + 8 PTS), then payload. Matches Cemu server.
- **Reconnect loop**: Automatic retry with 1.5s backoff. Catches `Throwable` to prevent silent death.
- **IDR request**: Sends opcode `0x10` on connection and on demand.
- **Transport negotiation**: `0x11` (UDP) / `0x12` (TCP) opcodes.
- **Payload guard**: Rejects payloads > 4MB or ≤ 0.
- **Socket**: `TCP_NODELAY` enabled, 64KB read buffer.

> [!WARNING]
> **Issue VID-1: `sendOpcode` spawns a new `Thread` for every call.** [`VideoStreamClient.kt:108-121`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt#L108-L121) — Each `requestIDR()` or `requestTransport()` creates a one-shot thread. Under IDR recovery storms (frame loss → 30-frame window → repeat), this could spawn many short-lived threads. Should use the existing worker thread or a shared single-thread executor. **Medium priority** (perf, not correctness).

### 4.2 VideoDecoder — ✅ Solid, well-engineered

[`VideoDecoder.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/VideoDecoder.kt):

- **Dedicated decoder thread**: All `MediaCodec` operations dispatched to a `HandlerThread` via `runOnDecoderThreadSync`. This is the correct pattern — `MediaCodec` is not thread-safe.
- **Low-latency flags**: `KEY_LOW_LATENCY`, `KEY_PRIORITY = 0` when supported.
- **SPS/PPS tracking**: Correctly detects parameter set presence via `AvcNalUnits.describe()`. Requests IDR every 30 frames if SPS/PPS haven't been seen.
- **Bitstream normalization**: `normalizeAvcBitstream()` handles AVCC length-prefixed → Annex B conversion. Correct.
- **No P-frame dropping**: Comment at line 195-198 explains why: "Dropping P-frames ahead of a stateful H.264 decoder corrupts its reference chain." This is the right decision.

> [!IMPORTANT]
> **Issue VID-2: `FrameRateLimiter` is dead code.** [`FrameRateLimiter.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/FrameRateLimiter.kt) exists and `maxFps` field is maintained in `VideoDecoder`, but the limiter is never actually called. The comment says "rate caps belong at the encoder." The `limitTo30Fps` setting in the UI does set `videoDecoder.maxFps` but it has no effect. **The UI setting is misleading** — users can toggle "Limit to 30 FPS" but nothing happens. The drawer hint text at line 244 of `MainScreen.kt` says "not enforced yet", which is accurate, but the toggle itself shouldn't appear or should be greyed out. **Medium priority** (UX).

### 4.3 UDP Video Pipeline — ✅ Well-designed

[`UdpVideoPacket.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/UdpVideoPacket.kt), [`FrameReassembler.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/FrameReassembler.kt), [`UdpFrameSequencer.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/UdpFrameSequencer.kt), [`UdpVideoReceiver.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/UdpVideoReceiver.kt):

- **Packet format**: 24-byte header (magic `0x5043`, version 1, flags, frameId, seq, packetIndex/Count, PTS). Matches Cemu server.
- **Reassembly**: Bounded slot pool (4 max), 100ms expiry, keep-latest eviction. Handles out-of-order fragments.
- **Sequencer**: Feeds frames to decoder in `frameId` order. Holds newer frames, drops old. On any loss in unfed region, flushes and waits for IDR. Modulo 2³² arithmetic for wrapping. Clean design.
- **Silence detection**: Falls back to TCP after 2s of no complete UDP frames. IDR rate-limited to 1/sec.

> [!TIP]
> The UDP → TCP fallback is a good reliability strategy. The 2-second silence window is reasonable.

### 4.4 AvcNalUnits — ✅ Correct

[`AvcNalUnits.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/AvcNalUnits.kt): Minimal Annex B start code scanner. Correctly handles 3-byte and 4-byte start codes, trims trailing zeros, extracts NAL types. No issues.

---

## 5. UI Layer

### 5.1 MainScreen — ⚠️ Functional but has issues

[`MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt):

- **Drawer-based settings**: Clean slide-out drawer via `ModalNavigationDrawer`.
- **Display modes**: Aspect fit, fill, stretch. Layout calculations delegate to `DisplayLayout`.
- **SurfaceView**: Correctly manages lifecycle via `SurfaceHolder.Callback`.
- **Diagnostics HUD**: Monospace overlay with shadow for readability.
- **Connection card**: Shows IP/ports when idle, hides when streaming.

> [!WARNING]
> **Issue UI-1: Deprecated API usage.** [`MainScreen.kt:146`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt#L146) — `Divider` is deprecated in Material3. Use `HorizontalDivider` instead.

> [!WARNING]
> **Issue UI-2: `DisplaySettings` constructed inline on every toggle change.** Lines 162-170, 196-204, 230-238, etc. all construct a new `DisplaySettings(...)` manually with all fields. This is brittle — if a new field is added, every site must be updated. Extract a `copy(...)` helper or use the data class `copy()` method: `displaySettings.copy(diagnosticsOverlayEnabled = enabled)`. **Medium priority** (maintainability).

### 5.2 TouchSurfaceView — ✅ Correct

[`TouchSurfaceView.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/TouchSurfaceView.kt): Custom View drawing the touch surface with pillarbox margins, border, touch crosshairs. Correctly delegates touch events to `TouchInputHandler` while drawing indicators locally. Handles video/idle mode visuals.

### 5.3 Navigation.kt — ⚠️ Dead code

[`Navigation.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/Navigation.kt): `MainNavigation` composable wraps `MainScreen` but is never called from `MainActivity`. `MainActivity.kt` calls `MainScreen` directly at line 155. This file is **dead code** and should be removed. **Low priority**.

---

## 6. Activity Lifecycle & Wiring

### 6.1 MainActivity — ⚠️ Has issues

[`MainActivity.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt):

- **Correct lifecycle**: DSU starts in `onStart()`, stops in `onStop()`. Motion sensors follow same lifecycle. WiFi lock acquired/released. Watchdog runs every 5s.
- **Auto-reconnect**: Saves last Cemu IP in SharedPreferences. Reconnects on `onResume`.
- **Input dispatch**: Both `dispatchKeyEvent` and `onKeyDown`/`onKeyUp` forward to handler. This double-dispatch is intentional — `dispatchKeyEvent` catches system keys before the default handler swallows them.

> [!IMPORTANT]
> **Issue ACT-1: Double input dispatch.** [`MainActivity.kt:314-347`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt#L314-L347) — Both `dispatchKeyEvent()` AND `onKeyDown()`/`onKeyUp()` delegate to the same `GamepadInputHandler`. If a key event reaches `dispatchKeyEvent` first and is handled, it returns `true` and `onKeyDown` is never called. But if the system dispatches to `onKeyDown` directly (some input devices bypass `dispatchKeyEvent`), the handler still works. The concern is that **both could fire for the same event** on some devices, causing a double-register. Since `onKeyDown` calls `gamepadHandler.onKeyDown(keyCode, event)` and `dispatchKeyEvent` calls `gamepadHandler.handleKeyEvent(event)` which calls the same `onKeyDown`, the handler's idempotent bit-or means no harm. But `onKeyUp` followed by another `onKeyUp` from dispatch could double-release (no-op since `and inv()` on already-cleared bit is no-op). **Not a bug, but fragile**.

> [!WARNING]
> **Issue ACT-2: No-op in `onClientDisconnected`.** [`MainActivity.kt:142`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt#L142) — `lastKnownClientIp = lastKnownClientIp` is a self-assignment that does nothing. The comment says "keep the most recent client IP so a resumed app can reconnect," but this line is literally a no-op. It should either be removed or replaced with actual logic. **Low priority** (cosmetic/dead code).

> [!WARNING]
> **Issue ACT-3: `FLAG_FULLSCREEN` is deprecated.** [`MainActivity.kt:117`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt#L117) — `FLAG_FULLSCREEN` is deprecated on API 30+. The preceding `WindowInsetsControllerCompat` call at lines 113-116 already handles immersive mode correctly. The `FLAG_FULLSCREEN` is redundant and should be removed to silence deprecation. **Low priority**.

---

## 7. Cemu Fork — StreamingCapture

### 7.1 StreamingCapture — ✅ Well-architected

[`StreamingCapture.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp):

- **Bounded queue**: `kMaxQueuedFrames = 2`. Older frames dropped on congestion. Correct latency behavior.
- **Dedicated encode worker**: Blocks on condition variable, wakes on new frame. Doesn't encode when no active client (`IsStreamingActive()`).
- **Frame copy**: Row-by-row `memcpy` using pitch. Correct for GPU textures where row pitch may differ from `width * bpp`.
- **Initialization**: Called from `LatteThread.cpp:125` at emulation start. Shutdown at line 253. Clean.

### 7.2 Vulkan Capture Hook — ✅ Excellent

[`VulkanRenderer.cpp:1010-1125`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/Vulkan/VulkanRenderer.cpp#L1010-L1125):

- **Double-buffered staging**: Two host-visible `VkBuffer`s alternating read/write. Processes the **previous** frame's buffer while copying the current one. Zero GPU stall.
- **Format dispatch**: Handles `RGBA8`, `BGRA8`, and `A2B10G10R10` pixel formats.
- **Command buffer tracking**: `HasCommandBufferFinished(s_commandBufferIds[readIdx])` ensures DMA is complete before processing. Correct.
- **DRC guard**: Only captures when streaming is active AND this is the pad (DRC) render target.

> [!WARNING]
> **Issue CEMU-1: Static locals in `HandleStreamingCapture`.** [`VulkanRenderer.cpp:1055-1064`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/Vulkan/VulkanRenderer.cpp#L1055-L1064) — All staging buffers, mapped pointers, command buffer IDs, etc. are `static`. This works because there's only one `VulkanRenderer` instance and one render thread, but it's fragile and would break with any future multi-GPU or multi-instance work. **Low priority** (design smell, not a bug).

### 7.3 LatteRenderTarget Hook — ✅ Correct

[`LatteRenderTarget.cpp:967-968,1012`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Core/LatteRenderTarget.cpp#L967-L1012):

- Line 968: Calls `HandleStreamingCapture(textureView)` only for pad view. Correct.
- Line 1012: DRC render pass fires whenever either the pad window is open OR streaming is active. This ensures the GamePad framebuffer continues rendering even when the Cemu pad window is closed. **Critical for headless streaming**.

---

## 8. Cemu Fork — VideoEncoder

### 8.1 H.264 Encoder — ⚠️ Functional, some concerns

[`VideoEncoder.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp):

- **MFT pipeline**: Uses `CLSID_CMSH264EncoderMFT` directly. This is the Microsoft software H.264 encoder, **not hardware-accelerated** (NVENC/AMF/QSV). The code comment says "standard Microsoft H.264 Encoder MFT."
- **ICodecAPI**: Low latency (`CODECAPI_AVLowLatencyMode`), CBR rate control, GOP size = 120 (2s at 60fps). Correct.
- **NV12 conversion**: BT.601 limited-range coefficients. Handles RGBA8, BGRA8, and A2B10G10R10 with correct bit unpacking.
- **Keyframe forcing**: Uses `CODECAPI_AVEncVideoForceKeyFrame`. Correct.

> [!IMPORTANT]
> **Issue ENC-1: Software MFT, not hardware.** [`VideoEncoder.cpp:54-56`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp#L54-L56) — `CLSID_CMSH264EncoderMFT` is the **software** encoder. On systems with NVENC, AMF, or Intel QuickSync, this wastes CPU doing software encoding while a hardware encoder sits idle. The PROJECT_CHECKLIST claims "Hardware acceleration via Windows Media Foundation (NVENC / AMF / Intel QuickSync with software MFT fallback)" but the implementation **only uses the software MFT**. Should enumerate hardware MFTs via `MFTEnumEx` with `MFT_ENUM_FLAG_HARDWARE` flag first, falling back to software. **High priority** — software encoding at 854×480@60 FPS is CPU-expensive and adds latency.

> [!WARNING]
> **Issue ENC-2: CPU-bound NV12 conversion.** [`VideoEncoder.cpp:196-247`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp#L196-L247) — `ConvertRGBAToNV12` is a pixel-by-pixel C++ loop with nearest-neighbor scaling. At 854×480 = 410K pixels, this runs on every frame. With the `A2B10G10R10` path doing per-pixel `memcpy` of 4 bytes + bit shifting, this could be slow. Consider SIMD (SSE2/AVX2) for batch processing. **Medium priority** (performance).

> [!WARNING]
> **Issue ENC-3: No encoder flush on keyframe request path.** [`VideoEncoder.cpp:266-276`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp#L266-L276) — `CODECAPI_AVEncVideoForceKeyFrame` is set, but the MFT may not honor it immediately if its internal pipeline is buffered. The `AVLowLatencyMode` should mitigate this, but there's no guarantee the next output frame will actually be an IDR. `WasLastFrameKeyframe()` reflects the *request*, not the *output*. The Android side's SPS/PPS detection (`AvcNalUnits.describe`) is the correct way to detect actual IDRs, so this is non-critical.

---

## 9. Cemu Fork — VideoStreamServer

### 9.1 TCP+UDP Server — ⚠️ Has issues

[`VideoStreamServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp):

- **TCP framing**: 13-byte header, matches client. `TCP_NODELAY` enabled. 512KB send buffer.
- **UDP fragmentation**: Splits frames into 1400-byte datagrams with 24-byte header. Protocol matches Android receiver.
- **Transport switching**: Client sends `0x11`/`0x12` opcode, server toggles `useUdp` flag per client.
- **Client cleanup**: RX thread removes client from list when control connection drops. UDP-only peers can't linger.

> [!IMPORTANT]
> **Issue SRV-1: `BroadcastFrame` holds `m_clientsMutex` during blocking `send()`.** [`VideoStreamServer.cpp:203-228`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp#L203-L228) — The mutex is held while iterating clients and calling `send()` (blocking TCP send). If a client's TCP buffer is full and `send()` blocks, the mutex is held for the entire stall duration, blocking accept, RX threads, and the encode worker. The non-blocking UDP path is fine, but TCP `send()` can block under congestion. Should use non-blocking send with `WOULDBLOCK` handling, or send from a separate per-client queue. **High priority** (latency, deadlock risk).

> [!WARNING]
> **Issue SRV-2: `m_rxThreads` grows unboundedly.** [`VideoStreamServer.cpp:299`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp#L299) — Each new client connection `emplace_back`s a thread into `m_rxThreads`. Disconnected client threads terminate but the `std::thread` objects are never joined or removed until `Stop()`. If the phone reconnects frequently, this vector grows with dangling thread handles. **Medium priority** (resource leak).

> [!WARNING]
> **Issue SRV-3: Portability — `DWORD` for SO_RCVTIMEO.** [`VideoStreamServer.cpp:263`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp#L263) — `DWORD timeout = 500` is Windows-specific. On Linux, `SO_RCVTIMEO` expects a `struct timeval`. The `#if defined(_WIN32)` guards around socket operations handle this in most places, but this specific line is not guarded. **Low priority** (Windows-only target for now).

> [!WARNING]
> **Issue SRV-4: TCP `send()` may short-write.** [`VideoStreamServer.cpp:213`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp#L213) — `send()` can return fewer bytes than requested. The code checks `sent <= 0` for error but doesn't handle partial sends. A partially-sent frame corrupts the TCP stream for that client. Should loop until all bytes are sent or error. **High priority** (correctness).

---

## 10. Config & Settings

### 10.1 DisplaySettings — ✅ Clean

[`DisplaySettings.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/config/DisplaySettings.kt): Three fit modes (aspect fit, fill, stretch), four resolution presets. `DisplayLayout` has clean aspect-fit and aspect-fill calculations. `surfaceBufferSize` returns preset dimensions or device auto.

### 10.2 AppSettingsCodec — ✅ Clean

[`AppSettingsCodec.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/config/AppSettingsCodec.kt): Pure encode/decode without Android dependencies. Testable. Uses `values().firstOrNull()` for safe enum parsing with defaults. No issues.

---

## 11. Test Coverage

| Test File | Count | Coverage Area |
|:---|:---:|:---|
| [`DSUPacketTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/DSUPacketTest.kt) | ✅ | Packet byte layout, CRC, field offsets |
| [`DSUServerIntegrationTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/DSUServerIntegrationTest.kt) | ✅ | Full UDP loopback with real socket |
| [`InputSubsystemTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/InputSubsystemTest.kt) | ✅ | Stick inversion, deadzones, button mapping, D-pad HAT |
| [`DSUServerLifecycleTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/dsu/DSUServerLifecycleTest.kt) | ✅ | Server start/stop lifecycle |
| [`AvcNalUnitsTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/video/AvcNalUnitsTest.kt) | ✅ | NAL type detection, SPS/PPS/IDR identification |
| [`FrameRateLimiterTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/video/FrameRateLimiterTest.kt) | ✅ | PTS-based gate logic |
| [`FrameReassemblerTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/video/FrameReassemblerTest.kt) | ✅ | Fragmentation, expiry, eviction |
| [`UdpFrameSequencerTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/video/UdpFrameSequencerTest.kt) | ✅ | In-order feeding, loss handling, IDR resync |
| [`UdpVideoPacketTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/video/UdpVideoPacketTest.kt) | ✅ | Encode/decode roundtrip |
| [`VideoDecoderTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/video/VideoDecoderTest.kt) | ✅ | Bitstream normalization |
| [`AppSettingsCodecTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/config/AppSettingsCodecTest.kt) | ✅ | Settings encode/decode |
| [`DisplayLayoutTest.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/test/java/com/cemupad/config/DisplayLayoutTest.kt) | ✅ | Aspect calculations |

**Assessment**: Good coverage for a project this size (12 test files). No Cemu-side unit tests (expected — C++ testing infrastructure is separate).

---

## 12. Major Issues Summary (Prioritized)

### 🔴 High Priority

| ID | Component | Issue | Impact |
|:---|:---|:---|:---|
| **ENC-1** | Cemu `VideoEncoder` | Uses software MFT only, no hardware encoder enumeration | High CPU load, added latency, claims HW accel but doesn't deliver |
| **SRV-1** | Cemu `VideoStreamServer` | `BroadcastFrame` holds mutex during blocking `send()` | Potential deadlock / full pipeline stall under TCP congestion |
| **SRV-4** | Cemu `VideoStreamServer` | TCP `send()` partial write not handled | Stream corruption for TCP clients on congestion |

### 🟡 Medium Priority

| ID | Component | Issue | Impact |
|:---|:---|:---|:---|
| **VID-1** | Android `VideoStreamClient` | New `Thread` spawned per opcode send | Thread churn under IDR recovery storms |
| **VID-2** | Android `VideoDecoder` | `FrameRateLimiter` is dead code, "Limit to 30 FPS" toggle misleading | Confusing UX — toggle does nothing |
| **UI-2** | Android `MainScreen` | `DisplaySettings` constructed inline at every toggle (6 sites) | Fragile, easy to miss a field on change |
| **ENC-2** | Cemu `VideoEncoder` | Scalar NV12 conversion (no SIMD) | CPU overhead at 60 FPS |
| **SRV-2** | Cemu `VideoStreamServer` | RX thread vector grows unboundedly | Resource leak on reconnect cycling |

### 🟢 Low Priority

| ID | Component | Issue | Impact |
|:---|:---|:---|:---|
| **DS-1** | Android `DSUServer` | Shared packet counter across all clients | Spec noncompliance (single-client OK) |
| **TOUCH-1** | Android `TouchInputHandler` | `ACTION_POINTER_UP` stale touch for 1-2 frames | Minimal gameplay impact |
| **ACT-1** | Android `MainActivity` | Redundant input dispatch (idempotent, no harm) | Code smell |
| **ACT-2** | Android `MainActivity` | Self-assignment `lastKnownClientIp = lastKnownClientIp` | Dead code |
| **ACT-3** | Android `MainActivity` | Deprecated `FLAG_FULLSCREEN` | Warning noise |
| **UI-1** | Android `MainScreen` | Deprecated `Divider` composable | Warning noise |
| **CEMU-1** | Cemu `VulkanRenderer` | Static locals in `HandleStreamingCapture` | Design smell, single-instance OK |
| **SRV-3** | Cemu `VideoStreamServer` | `DWORD` for `SO_RCVTIMEO` not guarded | Linux portability |
| **NAV-1** | Android `Navigation.kt` | Entire file is dead code | Cleanup |

---

## 13. Overall Assessment

The codebase is **well-structured and thoughtfully engineered** for a project at this stage. Key strengths:

1. **Protocol correctness**: The DSU implementation matches Cemu's native expectations byte-for-byte, with all known protocol quirks addressed (byte 11 fix, stick Y inversion, accelerometer-gated timestamps).

2. **Robust resilience**: Watchdog threads, automatic reconnect with backoff, IDR recovery, UDP→TCP fallback, and thread death detection throughout. The code is clearly battle-tested against real-world failure modes.

3. **Solid Vulkan integration**: The double-buffered staging DMA with command-buffer-completion tracking is the right approach for zero-stall GPU readback.

4. **Good test coverage**: 12 test files covering protocol serialization, input math, video reassembly, and settings.

The **three high-priority issues** (software-only H.264, mutex-during-send, TCP partial writes) are the most impactful areas to address next. The software encoder is the biggest performance bottleneck — switching to hardware MFT enumeration would dramatically reduce CPU usage and encoding latency.
