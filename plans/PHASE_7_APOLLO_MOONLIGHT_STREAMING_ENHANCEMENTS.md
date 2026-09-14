# Phase 7: High-Stability Low-Latency Streaming Pipeline (Apollo & Moonlight Enhancements)

## 1. Overview & Objective
Adopt the battle-tested streaming techniques from **Apollo (Sunshine)** and **Moonlight Android** to bring the CemuPad streaming subsystem to industry-leading low latency, Wi-Fi packet loss resilience, and frame pacing smoothness.

---

## 2. Implementation Checklist

### Phase 7.0: Zero-Latency Decoder Pipeline & Vendor Directives (Tier 1)
- [ ] **Step 7.0.1: Vendor-Specific Low-Latency Keys in `VideoDecoder.kt`**
  - [ ] Add Qualcomm Snapdragon keys (`vendor.qti-ext-dec-low-latency.enable=1`, `vendor.qti-ext-dec-picture-order.enable=0`, `vendor.qti-ext-dec-dpb-output-delay.enable=0`, `vendor.qti-ext-dec-frame-drop.enable=1`).
  - [ ] Add MediaTek keys (`vendor.mtk.vdec.low-latency.mode=1`, `vendor.mtk.vdec.disable-idle=1`).
  - [ ] Add Samsung Exynos keys (`vendor.rtc-ext-dec-low-latency.enable=1`).
  - [ ] Set `KEY_OPERATING_RATE = Short.MAX_VALUE` and `KEY_PRIORITY = 0`.
- [ ] **Step 7.0.2: Low-Latency Codec Selection Priority**
  - [ ] Prioritize codecs ending in `.low_latency` (e.g. `c2.qti.avc.decoder.low_latency`) and check `CodecCapabilities.FEATURE_LowLatency`.
- [ ] **Step 7.0.3: Latest-Only Output Draining & Queue Pruning**
  - [ ] In `VideoDecoder.drainOutput()`, discard older frames with `releaseOutputBuffer(old, false)` and render only the newest frame with `releaseOutputBuffer(latest, true)`.
- [ ] **Step 7.0.4: Asynchronous Lock-Free Decoder Queue**
  - [ ] Decouple `UdpVideoReceiver` from `VideoDecoder` using an asynchronous lock-free concurrent queue, eliminating `runOnDecoderThreadSync()` blocking.

---

### Phase 7.1: Reed-Solomon Forward Error Correction (FEC) (Tier 2)
- [ ] **Step 7.1.1: Embed `nanors` in Cemu Host**
  - [ ] Add `Cemu/src/streaming/nanors.h` and `nanors.c` (SIMD-accelerated Reed-Solomon erasure coding).
  - [ ] Update `Cemu/src/streaming/CMakeLists.txt` to compile `nanors.c`.
- [ ] **Step 7.1.2: Host FEC Parity Packet Generation**
  - [ ] Update `VideoStreamServer.cpp` to calculate $K \approx 20\% \times N$ parity packets for each frame using `reed_solomon_encode()`.
  - [ ] Add packet type identifier (`0x01` Data vs `0x02` FEC Parity) in datagram header.
- [ ] **Step 7.1.3: Client FEC Erasure Reconstruction (`android-gamepad-app`)**
  - [ ] Implement `ReedSolomonDecoder.kt` in `com.cemupad.video`.
  - [ ] Update `FrameReassembler.kt` to mathematically reconstruct missing UDP datagrams without requesting IDR keyframes.
- [ ] **Step 7.1.4: Unit & Loopback Verification**
  - [ ] Create `ReedSolomonDecoderTest.kt` simulating 1, 2, and 3 dropped packets per frame and verifying 100% byte recovery.

---

### Phase 7.2: Choreographer VSync Alignment & Jitter Smoothing (Tier 2)
- [ ] **Step 7.2.1: Choreographer Frame Callback Loop**
  - [ ] Implement `ChoreographerPacer.kt` on a dedicated `URGENT_DISPLAY` HandlerThread.
  - [ ] Schedule frame presentation timestamps with `videoDecoder.releaseOutputBuffer(index, frameTimeNanos)`.
- [ ] **Step 7.2.2: Settings Drawer Frame Pacing Toggle**
  - [ ] Add `Frame Pacing` selector in Settings Drawer (`Lowest Latency (Immediate)` vs `Smooth VSync (Choreographer)`).
  - [ ] Persist selection in `AppSettingsCodec.kt`.

---

### Phase 7.3: HEVC (H.265) Streaming Support (Tier 3)
- [ ] **Step 7.3.1: Host HEVC MFT Encoder Support**
  - [ ] Add `MFVideoFormat_HEVC` transform pipeline to `VideoEncoder.cpp` (NVENC / AMF / QSV) with automatic H.264 fallback.
- [ ] **Step 7.3.2: Codec Negotiation Protocol**
  - [ ] Add opcode `0x16 CODEC_SELECT` to `VideoStreamServer.cpp` (0 = H.264, 1 = HEVC).
- [ ] **Step 7.3.3: Client HEVC Decoding (`android-gamepad-app`)**
  - [ ] Support `MediaFormat.MIMETYPE_VIDEO_HEVC` in `VideoDecoder.kt` with VPS/SPS/PPS parameter-set detection.
  - [ ] Add Codec selector (`Auto / HEVC / H.264`) in Settings Drawer.

---

### Phase 7.4: Adaptive Dynamic Bitrate & Congestion Control (Tier 3)
- [ ] **Step 7.4.1: Client Network Telemetry Tracker**
  - [ ] Implement `NetworkQualityTracker.kt` calculating rolling packet loss %, frame latency, and jitter.
  - [ ] Transmit `0x17 STATS_REPORT` telemetry to Cemu every 500ms.
- [ ] **Step 7.4.2: Host Adaptive Rate Controller**
  - [ ] In `VideoStreamServer.cpp`, dynamically adjust `VideoEncoder` bitrate property (`CODECAPI_AVEncCommonMeanBitRate`) based on client telemetry.
