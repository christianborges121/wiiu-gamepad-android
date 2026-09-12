# Wii U GamePad for Android - Copilot Project Checklist

This checklist is a Copilot-maintained implementation plan cloned from `PROJECT_CHECKLIST.md` on 2026-09-11. Existing Phase 0 and Phase 1 progress is preserved. Phase 2 includes an implementation audit and a hardening track informed by the local Apollo and Artemis reference checkouts.

`PROJECT_CHECKLIST.md` remains the original backup. This file is the active Copilot plan.

---

## Progress Summary

| Phase | Description | Status | Copilot assessment |
|:---|:---|:---:|:---|
| Phase 0 | Toolchain, scaffolding, protocol specification | Complete | Preserve existing implementation |
| Phase 1 | DSU controller input via stock Cemu | Implemented / live verification pending | Preserve existing implementation |
| Phase 2 | Cemu DRC video capture and Android playback | Hardening required | Color correctness and latency work remain |
| Phase 4 UX | Fullscreen debug UX and drawer configuration | Active | Fit mode, drawer, diagnostics overlay, and persistence implemented; live-confirmation items remain |
| Phase 3 | Bidirectional audio | Not started | Existing roadmap retained |
| Phase 4 | Discovery, pairing, rumble, UX polish | Not started | Existing roadmap retained |

---

## Phase 0: Toolchain, Scaffolding and Protocol Specification

- [x] Audit Vanilla and Cemu architecture.
- [x] Select direct Android DSU server architecture for Phase 1.
- [x] Confirm Android listens on UDP `26760` and Cemu polls outward.
- [x] Resolve DSU accelerometer units to g's.
- [x] Resolve DSU gyroscope units to degrees/second.
- [x] Update motion timestamps only on accelerometer samples.
- [x] Correct standalone PortInfo padding byte.
- [x] Correct stick Y-axis inversion.
- [x] Normalize touch to Cemu's `1920 x 942` space.
- [x] Align face-button bitmasks with Cemu's DSU mapping.
- [x] Initialize Android Gradle project in `android-gamepad-app/`.
- [x] Verify SDK 36, target SDK 36, min SDK 24, Kotlin and Java toolchains.
- [x] Standardize application ID to `com.cemupad`.
- [x] Configure network, vibration, microphone, wake-lock, and landscape permissions.
- [x] Verify Android debug build.

---

## Phase 1: DSU Controller Input

### DSU protocol and server

- [x] Implement CRC32 engine in `CRC32.kt`.
- [x] Implement DSU packet header and message serialization in `DSUPacket.kt`.
- [x] Implement Version, PortInfo, and DataResponse messages.
- [x] Implement Android UDP server on port `26760` in `DSUServer.kt`.
- [x] Reset reused `DatagramPacket` length before each receive.
- [x] Track and prune inactive Cemu clients.
- [x] Use an atomic input snapshot to avoid torn packet state.
- [x] Close the socket during lifecycle shutdown.

### Physical input, touch, and motion

- [x] Implement controller profile abstraction.
- [x] Map Android gamepad buttons to DSU state fields.
- [x] Map both sticks with deadzone and inverted Y axes.
- [x] Map analog triggers and digital trigger fallback.
- [x] Fix sticky D-pad hat behavior and filter unrelated motion sources.
- [x] Implement multi-touch handling.
- [x] Implement aspect-fit 16:9 viewport calculation.
- [x] Reject touches in pillarbox/letterbox margins.
- [x] Pack primary and secondary DSU touch points.
- [x] Convert accelerometer values from m/s2 to g's.
- [x] Convert gyroscope values from rad/s to deg/s.
- [x] Remap sensor axes for display rotation.
- [x] Pair latest gyro values with accelerometer timestamps.

### Phase 1 verification

- [x] CRC unit tests.
- [x] DSU packet layout tests.
- [x] Input mapping tests.
- [x] Touch normalization tests.
- [x] Motion conversion tests.
- [x] DSU UDP loopback integration test.
- [x] Build debug APK.
- [x] Install APK on physical Android device.
- [x] Verify live UDP probe and 100-byte DataResponse.
- [ ] Connect physical USB/Bluetooth controller.
- [ ] Configure Cemu DSU client against the phone IP.
- [ ] Verify buttons, sticks, touch, motion, D-pad, Home, and triggers in Cemu.
- [ ] Test representative games including Wind Waker HD and Captain Toad.

---

## Phase 2: Video Streaming

**Target:** 854 x 480, 60 FPS DRC video with low latency, correct colors, bounded buffering, and recovery from packet loss.

### 2.1 Existing Cemu capture path audit

- [x] Locate DRC render target hook in `LatteRenderTarget.cpp`.
- [x] Keep DRC rendering active when the desktop GamePad window is closed.
- [x] Add Vulkan staging-buffer capture path.
- [x] Add TCP server on port `26761`.
- [x] Add Android TCP client and `MediaCodec` surface output.
- [x] Build Cemu Release configuration successfully.
- [x] Run Android unit tests successfully.
- [ ] OpenGL/PBO capture path.
- [x] Confirm capture path uses completed GPU work before reading staging memory.
- [x] Move capture conversion, encoding, and transmission off the render thread.
- [ ] Confirm native DRC Vulkan format for every supported render target.

### 2.2 Color correctness and format normalization

**Problem found:** the capture callback labels data as BGRA while the encoder interprets byte order as RGBA. The DRC image may be `VK_FORMAT_R8G8B8A8_*` or another native format depending on the render target and title. This can explain correct colors in Hyrule Warriors but incorrect colors in Wind Waker HD.

- [ ] Log Vulkan format, dimensions, pitch, image layout, and color-space flags for every captured DRC texture.
- [ ] Stop assuming the DRC image is BGRA or RGBA.
- [x] Carry explicit RGBA/BGRA channel-order metadata from the Vulkan texture into the encoder.
- [x] Decode `VK_FORMAT_A2B10G10R10_UNORM_PACK32` DRC pixels into 8-bit RGB before NV12 conversion.
- [ ] Normalize unsupported captured images to one explicit format, preferably `VK_FORMAT_R8G8B8A8_UNORM`.
- [ ] Preserve or explicitly convert sRGB behavior before YUV conversion.
- [x] Define encoder input as RGBA8 or BGRA8 based on the captured Vulkan format.
- [ ] Set explicit BT.601/BT.709 and limited/full-range policy for NV12.
- [ ] Add a synthetic red/green/blue/white conversion test.
- [ ] Capture comparison frames from Hyrule Warriors and Wind Waker HD.
- [ ] Confirm Android output colors against the Cemu DRC view and source pixels.
- [ ] Add diagnostic logging for channel order and YUV range.

### 2.3 Vulkan synchronization and bounded capture

**Problem found:** the current double-buffer path processes the previous staging buffer without tracking the command buffer that fills it. Two buffers alone do not guarantee that the GPU has finished writing the buffer.

- [x] Associate every staging slot with the command-buffer ID that produced it.
- [x] Process a slot only after `HasCommandBufferFinished()` is true.
- [ ] Add a third staging slot if two slots cause backpressure.
- [ ] Drop the oldest pending frame instead of blocking the render thread.
- [ ] Release or recreate staging resources safely when dimensions/formats change.
- [ ] Validate image-layout transitions around `vkCmdCopyImageToBuffer`.
- [ ] Add counters for submitted, completed, dropped, and stale frames.
- [ ] Verify no GPU wait or `vkQueueWaitIdle` occurs per frame.

### 2.4 Asynchronous encode pipeline

Apollo uses a producer/consumer design where capture and encoding can run concurrently. Apply the same boundary here.

- [x] Create a bounded two-frame latest-frame queue between Vulkan capture and encoding.
- [x] Copy only completed frame data into the queue.
- [x] Run NV12 conversion on an encoder worker thread.
- [x] Run Media Foundation/H.264 encoding and transmission on the encoder worker thread.
- [x] Drop stale frames when the encoder falls behind.
- [ ] Record capture-to-encode, encode-to-send, and queue-wait durations.
- [ ] Target less than 1 ms average render-thread contribution.
- [ ] Target less than 2 ms at the 99th percentile.
- [ ] Verify emulation remains above 59 FPS during streaming.

### 2.5 Encoder implementation

- [x] Configure 854 x 480 output.
- [x] Configure 60 FPS output.
- [x] Configure approximately 6 Mbps bitrate.
- [x] Enable low-latency Media Foundation settings.
- [x] Configure two-second GOP target.
- [ ] Verify encoder output format is Annex B.
- [ ] Verify SPS/PPS are present before every IDR frame.
- [ ] Drain delayed Media Foundation output instead of assuming one output packet per input.
- [ ] Add encoder capability logging.
- [ ] Add NVENC backend or a well-defined hardware backend abstraction.
- [ ] Add AMF/QSV selection where practical.
- [ ] Retain Media Foundation as fallback.
- [ ] Add libx264 fallback only if a supported build dependency is available.
- [ ] Tune CBR/VBV and reference-frame count for interactive streaming.
- [ ] Evaluate slices per frame after transport fragmentation is implemented.
- [ ] Implement intra-refresh only when the selected encoder supports it.
- [ ] Keep reference-frame invalidation as a later optimization; fall back to IDR.

### 2.6 Low-latency transport

The current TCP framing is simple but has head-of-line blocking. Apollo uses RTP/UDP, packet-level FEC, frame metadata, and a separate reliable control channel.

- [x] Keep TCP as a diagnostic/fallback transport.
- [ ] Add UDP video transport on port `26761`.
- [ ] Fragment encoded H.264 frames into packets below the path MTU.
- [ ] Add RTP-like sequence number, frame ID, packet index, packet count, PTS, and flags.
- [ ] Mark start/end of frame and IDR frames.
- [ ] Drop incomplete or late frames immediately.
- [ ] Add configurable Reed-Solomon FEC, initially 10-20% parity.
- [ ] Bound FEC group size and avoid excessive recovery overhead for large IDR frames.
- [ ] Add Android packet reassembly and FEC recovery.
- [ ] Add frame-loss statistics from Android to Cemu.
- [ ] Add a small reliable control channel for IDR requests and recovery state.
- [ ] Add reference-frame invalidation messages only after encoder support exists.
- [ ] Add optional AES/authentication only when pairing/session design is implemented.

### 2.7 Android decoder and frame pacing

Artemis probes codec capabilities and uses device-specific low-latency options rather than applying every MediaCodec key unconditionally.

- [x] Configure AVC decoder with direct `Surface` output.
- [x] Forward packet PTS to `MediaCodec`.
- [x] Probe `MediaCodecInfo` and select an AVC decoder.
- [x] Apply `KEY_LOW_LATENCY` only when the decoder advertises support.
- [x] Remove the unconditional `KEY_OPERATING_RATE = 120` request.
- [ ] Use a fallback configuration sequence when `configure()` rejects an option.
- [ ] Keep all `MediaCodec` calls on one decoder thread.
- [ ] Add explicit Annex B NAL validation and SPS/PPS detection.
- [ ] Handle decoder reset and request IDR after codec failure.
- [ ] Drop queued stale frames when decode latency grows.
- [ ] Use timestamped `releaseOutputBuffer()` where supported.
- [ ] Add optional Choreographer-based display pacing.
- [ ] Track decode latency from enqueue to rendered output.
- [ ] Track decoder queue depth, dropped frames, and output FPS.
- [ ] Verify behavior on at least two Android codec vendors.

### 2.8 Phase 2 verification

- [ ] Verify correct colors in Hyrule Warriors.
- [x] Verify channel/hue correctness in Zelda: Wind Waker HD (live-verified 2026-09-12: pixel-measured blue banner B>>R, gold title R>>B, green card G-dominant; no swap).
- [ ] Resolve phone-vs-desktop brightness parity (phone darker/more saturated; TV and pad view match each other and look brighter; parked 2026-09-12, see `investigation/2026-09-12-color/02-brightness-parity.md`).
- [x] Retest Wind Waker HD after packed-format support is deployed (this session: no format-64 failure, stream flows at 30 FPS).
- [ ] Verify correct colors in a title using a different DRC render format.
- [ ] Verify no color-channel swap with red/green/blue test content.
- [ ] Verify Cemu remains above 59 FPS while streaming.
- [ ] Verify Android begins playback within 500 ms after connection.
- [ ] Verify no decoder crashes or black screen on reconnect.
- [ ] Measure glass-to-glass latency on 5 GHz Wi-Fi.
- [ ] Measure glass-to-glass latency over USB networking.
- [ ] Verify late frames are dropped instead of queued.
- [ ] Verify packet loss recovery using FEC.
- [ ] Verify IDR recovery after unrecoverable frame loss.
- [ ] Verify TCP fallback remains functional.

---

## Phase 4 UX: Fullscreen Debug UX and Drawer Configuration

**Target:** full-screen, gesture-friendly app layout optimized for live troubleshooting and quick validation. The GamePad view should dominate the screen while settings remain available from a back-button drawer.

### 4.1 Full-screen immersive mode

- [x] Hide system bars in immersive sticky mode so the notification shade and nav buttons stay hidden until the user swipes.
- [x] Keep the app in landscape fullscreen with no extra window chrome or persistent header.
- [x] Remove the current padded app frame so the GamePad surface can occupy the entire display area.
- [ ] Verify the app still renders correctly when the user rotates or resumes after sleep.

### 4.2 GamePad-first layout

- [x] Make the Wii U GamePad surface fill the available screen area without a dedicated window or border.
- [x] Keep touch and decoder overlays anchored to the same 16:9 viewport as the GamePad image.
- [x] Preserve the active video surface as the primary visual element while allowing a debug overlay to sit on top.
- [x] Keep all nonessential UI off the main stage until the user opens the configuration drawer.

### 4.3 Configuration drawer and diagnostics controls

- [x] Add a configuration drawer opened via the back button.
- [x] Include a diagnostics overlay toggle; persist overlay, fit mode, resolution, and connection help.
- [x] Include a separate connection-help toggle in the drawer (hides the startup card; live-verified 2026-09-12).
- [x] Keep the settings panel compact and easy to access during live troubleshooting.
- [x] Make the drawer content scrollable so all settings stay reachable on short landscape displays.
- [x] Show a clear visual indication when the drawer is opened or closed.

### 4.4 Connection and startup instructions

- [x] Until the app connects and loads the video stream, display the current connection instructions on the main screen.
- [x] Show the local IP address and port prominently while the app is waiting for a Cemu client.
- [x] Hide the startup instructions automatically once a valid stream connection is established.
- [x] Preserve the quick connection information even if diagnostics are toggled off.

### 4.5 Debug overlay behavior

- [x] Add a compact diagnostics overlay that can be enabled or disabled from the drawer.
- [x] Keep the overlay small and unobtrusive so it does not obstruct the GamePad display.
- [x] Include local IP, port, client count, packet counters, and video FPS on one line when enabled.
- [x] Keep overlay text legible without blocking the active GamePad content (no background card; text shadow only).
- [ ] Live-confirm the restyled overlay on a running stream after the 2026-09-12 restyle.

### 4.6 Phase 4 verification

- [x] Confirm the app launches in immersive fullscreen on the phone.
- [x] Confirm the GamePad image fills the screen without a framed window (aspect-fit clipping fix 2026-09-12).
- [x] Confirm back-button drawer toggles settings cleanly.
- [ ] Confirm diagnostics overlay can be toggled on/off without affecting the stream after the one-line restyle.
- [x] Confirm startup instructions disappear as soon as streaming begins (user-verified round trip 2026-09-12: card hidden while Cemu streamed, reappeared on stop with no stuck state; screenshot in notes).
- [x] Confirm the resolution preset changes the SurfaceView buffer size (live-verified 2026-09-12: `surfaceChanged` reports 1920x1080 on Full HD, 854x480 on Native; decoder output stays stream-determined 854x480 with scale-to-fit).
- [ ] Validate the layout under a real device session and adjust spacing if the UI overlaps the stream.

---

## Phase 3: Bidirectional Audio

### Cemu audio capture

- [ ] Hook the DRC DSP output before optional `g_padAudio` routing.
- [ ] Capture 48 kHz 16-bit PCM with dynamic channel/sample counts.
- [ ] Add Opus encoding option with 5-10 ms frames.
- [ ] Retain raw PCM diagnostic mode.

### Audio transport and playback

- [ ] Stream speaker audio over UDP port `26762`.
- [ ] Add sequence numbers and monotonic PTS.
- [ ] Add a 20-40 ms adaptive jitter buffer.
- [ ] Reorder packets and conceal loss with Opus PLC or silence.
- [ ] Play through low-latency `AudioTrack`.
- [ ] Keep audio and video on a shared monotonic clock.

### Microphone

- [ ] Implement Android `AudioRecord` capture.
- [ ] Implement RMS blow detection and DSU mic-button fallback.
- [ ] Stream 32 kHz mono PCM/Opus over UDP port `26764`.
- [ ] Inject network microphone samples into Cemu DRC microphone input.
- [ ] Verify microphone behavior in representative games.

### Phase 3 verification

- [ ] Verify clear speaker audio without crackle or underrun.
- [ ] Verify audio/video synchronization within 15 ms.
- [ ] Verify blow detection in mic-enabled titles.
- [ ] Verify full microphone streaming in titles requiring raw audio.

---

## Phase 4: Discovery, Pairing, Rumble and UX

- [ ] Add versioned UDP discovery on port `26763`.
- [ ] Add optional mDNS/NSD discovery for production use.
- [ ] Add PIN pairing over TCP `26765`.
- [ ] Generate and validate session tokens.
- [ ] Restrict input and media streams to paired clients.
- [ ] Forward VPAD rumble events to Android haptics.
- [ ] Add virtual on-screen GamePad controls.
- [ ] Add configurable control opacity and placement.
- [ ] Add reconnect with exponential backoff.
- [ ] Add latency, loss, decoder, and encoder telemetry.
- [ ] Add bitrate and FPS controls.
- [ ] Add deadzone, motion, audio, and microphone settings.
- [ ] Handle Android sleep/wake and Wi-Fi changes cleanly.

---

## Reference Repositories

These are local research checkouts and must not be copied into the product implementation without reviewing their licenses and architecture:

- Apollo: `references/Apollo` at commit `adc5c5a`
- Artemis: `references/Artemis` at commit `c5cf27f`

Useful reference areas:

- Apollo encoder capability and recovery logic: `references/Apollo/src/video.cpp`
- Apollo RTP/FEC and loss metadata: `references/Apollo/src/stream.cpp`
- Artemis MediaCodec capability probing: `references/Artemis/app/src/main/java/com/limelight/binding/video/MediaCodecHelper.java`
- Artemis frame pacing and latest-frame policies: `references/Artemis/app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java`

Apollo and Artemis are GPL-licensed projects. Use them as architectural references unless a deliberate licensing review approves code reuse.

---

## Copilot Implementation Order

1. Fix DRC format normalization and collect per-game capture diagnostics.
2. Add Vulkan command-buffer completion tracking.
3. Move conversion and encoding off the render thread.
4. Verify Wind Waker HD and Hyrule Warriors colors.
5. Harden Android MediaCodec selection and decoder threading.
6. Add UDP frame transport with bounded reassembly.
7. Add FEC and loss statistics.
8. Add hardware encoder backends after the pipeline is measurable.
9. Implement audio, discovery, pairing, rumble, and remaining UX work.

---

## Copilot Progress Log

| Date | Area | Change | Status |
|:---|:---:|:---|:---:|
| 2026-09-11 | Review | Audited Android and Cemu video paths against the original checklist | Done |
| 2026-09-11 | Review | Identified possible per-title color issue from unnormalized Vulkan format/channel order | Open |
| 2026-09-11 | Review | Identified missing staging-buffer completion tracking | Open |
| 2026-09-11 | Review | Identified synchronous conversion/encoding in capture callback | Open |
| 2026-09-11 | Research | Cloned Apollo and Artemis locally for encoder, transport, and decoder references | Done |
| 2026-09-11 | Plan | Added Apollo/Artemis-derived asynchronous, UDP/FEC, and decoder hardening plan | Done |
| 2026-09-11 | Phase 2 | Added Vulkan RGBA/BGRA format propagation and unsupported-format guard | Done |
| 2026-09-11 | Phase 2 | Added command-buffer completion checks and per-staging-slot metadata | Done |
| 2026-09-11 | Phase 2 | Moved capture conversion, encoding, and TCP transmission to a bounded worker queue | Done |
| 2026-09-11 | Phase 2 | Added Android AVC decoder capability probing and conditional low-latency options | Done |
| 2026-09-11 | Phase 2 | Added packed A2B10G10R10 DRC decoding after Wind Waker runtime logs identified Vulkan format 64 | Done |
| 2026-09-11 | Deployment | Deployed rebuilt Cemu fork to the EmuDeck installation after preserving the prior executable | Done |
| 2026-09-11 | Verification | Cemu Release build and Android `testDebugUnitTest` pass; game/color/latency tests remain open | Partial |
| 2026-09-12 | Phase 4 UX | Persist diagnostics overlay, fit mode, and resolution; overlay defaults off until enabled | Done |
| 2026-09-12 | Phase 4 UX | Restyle overlay to one transparent top-left line; APK installed on wireless ADB phone | Done |
| 2026-09-12 | Phase 4 UX | Fixed fit-mode clipping: `DisplayLayout` computes aspect-fit/aspect-fill within both container dimensions; added `DisplayLayoutTest` | Done |
| 2026-09-12 | Phase 4 UX | Captured aspect-fit, screen-fill, original-mode, and drawer screenshots from the connected phone | Done |
| 2026-09-12 | Docs | Wrote `investigation/HANDOFF.md` and `investigation/2026-09-12-ux/` for next-harness pickup | Done |
| 2026-09-12 | Docs | Reconciled checklist: split implemented vs open UX items, added resolution-preset-effect item, aligned phase numbering | Done |
| 2026-09-12 | Infra | Initialized `Cemu/` and workspace-root git repos on `main`, pushed to private `christianborges121/Cemu` and `christianborges121/wiiu-gamepad-android` | Done |
| 2026-09-12 | Infra | Rebuilt Cemu as a public attached fork: one commit on upstream `3310f3b8` with a clean 24-file delta; redundant snapshot branch deleted, tarball kept at `C:\Projects\cemu-snapshot-backup-20260912.tar` | Done |
| 2026-09-12 | Phase 4 UX | Added persisted connection-help drawer toggle; hides/shows startup card, survives force-stop; APK installed and live-verified | Done |
| 2026-09-12 | Phase 4 UX | Wired resolution presets to SurfaceView fixed size via `DisplayLayout.surfaceBufferSize`; fixed stale-closure stomp and unreachable drawer items (scroll); live-verified both directions | Done |
| 2026-09-12 | Phase 2 | Wind Waker HD color verified live (inventory: blue/white/parchment/yellow correct; no format-64 failure; 30 FPS); evidence screenshot archived | Done |
