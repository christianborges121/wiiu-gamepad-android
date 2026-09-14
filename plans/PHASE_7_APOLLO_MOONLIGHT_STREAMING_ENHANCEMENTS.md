# Phase 7: High-Stability Low-Latency Streaming Pipeline (Apollo & Moonlight Enhancements)

## 1. Overview & Objective
Adopt the battle-tested streaming techniques from **Apollo (Sunshine)** and **Moonlight Android** to bring the CemuPad streaming subsystem to industry-leading low latency, Wi-Fi packet loss resilience, and frame pacing smoothness.

---

## 2. Implementation Checklist

### Phase 7.0: Zero-Latency Decoder Pipeline & Vendor Directives (Tier 1)
- [x] **Step 7.0.1: Vendor-Specific Low-Latency Keys in `VideoDecoder.kt`**
  - [x] Add Qualcomm Snapdragon keys (`vendor.qti-ext-dec-low-latency.enable=1`, `vendor.qti-ext-dec-picture-order.enable=0`, `vendor.qti-ext-dec-dpb-output-delay.enable=0`, `vendor.qti-ext-dec-frame-drop.enable=1`).
  - [x] Add MediaTek keys (`vendor.mtk.vdec.low-latency.mode=1`, `vendor.mtk.vdec.disable-idle=1`, `vendor.mtk.vdec.preload.frame.count=1`).
  - [x] Add Samsung Exynos keys (`vendor.rtc-ext-dec-low-latency.enable=1`).
  - [x] Set `KEY_OPERATING_RATE = Short.MAX_VALUE` and `KEY_PRIORITY = 0`.
- [x] **Step 7.0.2: Low-Latency Codec Selection Priority**
  - [x] Prioritize codecs ending in `.low_latency` (e.g. `c2.qti.avc.decoder.low_latency`) and check `CodecCapabilities.FEATURE_LowLatency`.
- [x] **Step 7.0.3: Latest-Only Output Draining & Queue Pruning**
  - [x] In `VideoDecoder.drainOutput()`, discard older frames with `releaseOutputBuffer(old, false)` and render only the newest frame with `releaseOutputBuffer(latest, true)`.
- [x] **Step 7.0.4: Asynchronous Lock-Free Decoder Queue**
  - [x] Decoupled `UdpVideoReceiver` from `VideoDecoder` using an asynchronous lock-free concurrent queue (`ArrayBlockingQueue`), eliminating `runOnDecoderThreadSync()` blocking.

---

### Phase 7.1: Reed-Solomon Forward Error Correction (FEC) (Tier 2)
- [x] **Step 7.1.1: Embed Cauchy Reed-Solomon Codec in Cemu Host**
  - [x] Add `Cemu/src/streaming/ReedSolomon.h` and `ReedSolomon.cpp` (Cauchy $GF(2^8)$ erasure coding with polynomial 285 matching `nanors`).
  - [x] Update `Cemu/src/streaming/CMakeLists.txt` to compile `ReedSolomon.cpp`.
- [x] **Step 7.1.2: Host FEC Parity Packet Generation**
  - [x] Update `VideoStreamServer.cpp` to calculate $K \approx 20\% \times N$ parity packets for each frame using `CemuPad::ReedSolomon::Encode()`.
  - [x] Add packet type identifier (`0x01` Data vs `0x02` FEC Parity) and parity count in datagram header byte 15.
- [x] **Step 7.1.3: Client FEC Erasure Reconstruction (`android-gamepad-app`)**
  - [x] Implement `ReedSolomonDecoder.kt` in `com.cemupad.video`.
  - [x] Update `FrameReassembler.kt` to mathematically reconstruct missing UDP datagrams without requesting IDR keyframes.
- [x] **Step 7.1.4: Unit & Loopback Verification**
  - [x] Create `ReedSolomonDecoderTest.kt` and `FrameReassemblerTest.kt` simulating 1, 2, and 3 dropped packets per frame and verifying 100% byte recovery.
  - [x] Live verified on physical Samsung Galaxy S23 FE running Super Mario 3D World over UDP.

---

### Phase 7.2: Choreographer VSync Alignment & Jitter Smoothing (Tier 2)
- [x] **Step 7.2.1: Choreographer Frame Callback Loop**
  - [x] Implement `ChoreographerPacer.kt` to monitor physical display refresh rate and compute next VSync deadline timestamps.
  - [x] Schedule frame presentation timestamps in `VideoDecoder.drainOutput()` via `videoDecoder.releaseOutputBuffer(latestIndex, targetVsyncNanos)`.
- [x] **Step 7.2.2: Settings Drawer Frame Pacing Toggle**
  - [x] Add `Frame Pacing` selector in Settings Drawer DISPLAY accordion (`Lowest Latency (Immediate)` vs `Smooth VSync (Choreographer)`).
  - [x] Persist selection in `AppSettingsCodec.kt` and `DisplaySettings.kt`.
  - [x] Live verified toggle on Samsung Galaxy S23 FE with real-time video stream.

---

### Phase 7.3: HEVC (H.265) Streaming Support (Tier 3)
- [x] **Step 7.3.1: Host HEVC MFT Encoder Support**
  - [x] Add `MFVideoFormat_HEVC` transform pipeline to `VideoEncoder.cpp` (NVENC / AMF / QSV) with automatic H.264 fallback.
- [x] **Step 7.3.2: Codec Negotiation Protocol**
  - [x] Add opcode `0x16 CODEC_SELECT` to `VideoStreamServer.cpp` (0 = H.264, 1 = HEVC).
- [x] **Step 7.3.3: Client HEVC Decoding (`android-gamepad-app`)**
  - [x] Support `MediaFormat.MIMETYPE_VIDEO_HEVC` in `VideoDecoder.kt` with VPS/SPS/PPS parameter-set detection.
  - [x] Add Codec selector (`Auto / HEVC / H.264`) in Settings Drawer.
  - [x] Live verified HEVC hardware stream from AMDh265Encoder to `c2.qti.hevc.decoder.low_latency` on Samsung Galaxy S23 FE.

---

### Phase 7.4: Adaptive Dynamic Bitrate & Congestion Control (Tier 3)
- [x] **Step 7.4.1: Client Network Telemetry Tracker**
  - [x] Implement `NetworkQualityTracker.kt` calculating rolling packet loss %, frame latency, and jitter.
  - [x] Transmit `0x17 STATS_REPORT` telemetry to Cemu every 500ms.
- [x] **Step 7.4.2: Host Adaptive Rate Controller**
  - [x] In `VideoStreamServer.cpp`, dynamically adjust `VideoEncoder` bitrate property (`CODECAPI_AVEncCommonMeanBitRate`) based on client telemetry.
