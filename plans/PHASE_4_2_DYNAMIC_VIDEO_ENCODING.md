# Phase 4.2 Implementation Plan — Dynamic Video Encoder Bitrate & Resolution Controls

## 1. Overview & Objective
Currently, Cemu encodes the GamePad stream at a fixed resolution (854×480) and fixed bitrate (~6 Mbps).
While the Android settings drawer allows selecting display fit modes and surface resolutions, the encoder on the host PC never changes its output parameters dynamically.

**Objective**:
Allow the Android client to dynamically instruct Cemu's video encoder at runtime over the TCP control connection (port `26761`):
1. **Dynamic Bitrate Adjustment**: Switch between 4 Mbps, 6 Mbps (default), 8 Mbps, and 12 Mbps on the fly using `ICodecAPI::SetValue(&CODECAPI_AVEncCommonMeanBitRate)`.
2. **Dynamic Resolution Scaling**: Switch between Native 480p (`854x480`), 720p HD (`1280x720`), and 1080p Full HD (`1920x1080`) with runtime MFT reconfiguration without restarting Cemu or interrupting the game loop.

---

## 2. Protocol Specification (TCP Port 26761 Control Channel)

The Android client sends small command packets over the established TCP socket:

| Opcode | Name | Payload Format | Description |
|:---|:---|:---|:---|
| `0x14` | `OPCODE_SET_BITRATE` | 4 bytes (uint32 LE, bps) | Changes target bitrate (e.g. `6000000` = 6 Mbps) |
| `0x15` | `OPCODE_SET_RESOLUTION` | 4 bytes (`uint16 LE width`, `uint16 LE height`) | Reconfigures encoder resolution (e.g. `1280, 720`) |

---

## 3. Implementation Checklist & Step-by-Step Code Modifications

- [x] **Step 3.1: VideoStreamServer Control Opcode Handlers** *(adapted: control protocol is inline opcode bytes in `ClientRxThreadFunc` — the plan's `HandleClientCommands(payload/payloadSize)` does not exist; implemented with an exact-read helper + LE parsing. Files still at `Cafe/HW/Latte/Renderer/` — media move deferred per 4.0 note)*
  - [x] Add `OPCODE_SET_BITRATE` (`0x14`) and `OPCODE_SET_RESOLUTION` (`0x15`) in `VideoStreamServer.h`.
  - [x] Parse opcodes and dispatch to `VideoEncoder` in `VideoStreamServer.cpp`.

#### [MODIFY] [`Cemu/src/streaming/VideoStreamServer.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoStreamServer.h)
Add new control opcode definitions:
```cpp
    // Transport opcodes phone -> Cemu on the TCP control channel
    static constexpr uint8 OPCODE_IDR_REQUEST = 0x10;
    static constexpr uint8 OPCODE_TRANSPORT_UDP = 0x11;
    static constexpr uint8 OPCODE_TRANSPORT_TCP = 0x12;
    static constexpr uint8 OPCODE_MIC_BLOW = 0x13;
    static constexpr uint8 OPCODE_SET_BITRATE = 0x14;     // uint32 bitrate_bps
    static constexpr uint8 OPCODE_SET_RESOLUTION = 0x15;  // uint16 width, uint16 height
```

#### [MODIFY] [`Cemu/src/streaming/VideoStreamServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoStreamServer.cpp)
In `VideoStreamServer::HandleClientCommands(ClientConnection& client)`:
```cpp
    case OPCODE_SET_BITRATE:
    {
        if (payloadSize >= 4)
        {
            uint32 bitrate = *(uint32*)payload;
            cemuLog_log(LogType::Force, "VideoStreamServer: Received SET_BITRATE = {} bps", bitrate);
            VideoEncoder::GetInstance().SetBitrate(bitrate);
        }
        break;
    }
    case OPCODE_SET_RESOLUTION:
    {
        if (payloadSize >= 4)
        {
            uint16 width = *(uint16*)payload;
            uint16 height = *(uint16*)(payload + 2);
            cemuLog_log(LogType::Force, "VideoStreamServer: Received SET_RESOLUTION = {}x{}", width, height);
            VideoEncoder::GetInstance().SetResolution(width, height);
            // Request an immediate IDR keyframe so client can re-sync with new SPS/PPS
            VideoEncoder::GetInstance().ForceKeyframe();
        }
        break;
    }
```

- [x] **Step 3.2: Runtime Reconfiguration in VideoEncoder** *(adapted: plan's snippet deadlocks — `SetResolution` snapshots under lock then calls `Initialize()` unlocked; keyframe via existing `RequestKeyframe()`, resolution allowlisted 480p/720p/1080p, bitrate clamped 0.5–20 Mbps. Files still at `Cafe/HW/Latte/Renderer/`)*
  - [x] Declare `SetBitrate` and `SetResolution` in `VideoEncoder.h`.
  - [x] Implement live bitrate updating via `ICodecAPI` in `VideoEncoder.cpp`.
  - [x] Implement resolution reconfiguration and IDR keyframe forcing in `VideoEncoder.cpp`.

#### [MODIFY] [`Cemu/src/streaming/VideoEncoder.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoEncoder.h)
Declare runtime reconfiguration methods:
```cpp
    bool SetBitrate(uint32 bitrateBps);
    bool SetResolution(uint16 width, uint16 height);
```

#### [MODIFY] [`Cemu/src/streaming/VideoEncoder.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoEncoder.cpp)
Implement `SetBitrate` and `SetResolution`:
```cpp
bool VideoEncoder::SetBitrate(uint32 bitrateBps)
{
    std::lock_guard<std::mutex> lock(m_encoderMutex);
    m_bitrate = bitrateBps;

#ifdef _WIN32
    if (m_pTransform)
    {
        ICodecAPI* pCodecAPI = nullptr;
        if (SUCCEEDED(m_pTransform->QueryInterface(IID_PPV_ARGS(&pCodecAPI))))
        {
            VARIANT var;
            var.vt = VT_UI4;
            var.ulVal = bitrateBps;
            pCodecAPI->SetValue(&CODECAPI_AVEncCommonMeanBitRate, &var);
            pCodecAPI->Release();
            cemuLog_log(LogType::Force, "VideoEncoder: Live updated bitrate to {} bps", bitrateBps);
            return true;
        }
    }
#endif
    return false;
}

bool VideoEncoder::SetResolution(uint16 width, uint16 height)
{
    std::lock_guard<std::mutex> lock(m_encoderMutex);
    uint32 alignedW = width & ~1;
    uint32 alignedH = height & ~1;

    if (m_width == alignedW && m_height == alignedH)
        return true;

    cemuLog_log(LogType::Force, "VideoEncoder: Reconfiguring resolution from {}x{} to {}x{}",
        m_width, m_height, alignedW, alignedH);

    // Reinitialize encoder under the lock
    return Initialize(alignedW, alignedH, m_fps, m_bitrate);
}
```

- [x] **Step 3.3: Android Client Control Packet Dispatcher** *(adapted to existing `sendMicBlow` executor pattern + LE `ByteBuffer` packets; pure builder functions unit-tested)*
  - [x] Add `sendBitrate(bitrateBps)` in `VideoStreamClient.kt`.
  - [x] Add `sendResolution(width, height)` in `VideoStreamClient.kt`.

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/video/VideoStreamClient.kt)
Add helper methods to send opcodes:
```kotlin
    fun sendBitrate(bitrateBps: Int) {
        val payload = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x14.toByte()) // OPCODE_SET_BITRATE
            putInt(bitrateBps)
        }.array()
        sendControlPacket(payload)
    }

    fun sendResolution(width: Int, height: Int) {
        val payload = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x15.toByte()) // OPCODE_SET_RESOLUTION
            putShort(width.toShort())
            putShort(height.toShort())
        }.array()
        sendControlPacket(payload)
    }
```

- [x] **Step 3.4: App Settings & Dynamic UI Controls** *(resolution preset selection now also commands the encoder; `DEVICE_AUTO` skipped locally-only)*
  - [x] Add `videoBitrateMbps` to `DisplaySettings.kt` and `AppSettingsCodec.kt`.
  - [x] Add Bitrate dropdown in `MainScreen.kt` drawer.
  - [x] Wire UI selection to invoke `sendBitrate` and `sendResolution`.

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/config/DisplaySettings.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/config/DisplaySettings.kt)
Add `val videoBitrateMbps: Int = 6`.

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/config/AppSettingsCodec.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/config/AppSettingsCodec.kt)
Add `KEY_VIDEO_BITRATE = "video_bitrate"` and update `encode()` / `decode()`.

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt)
- In the `DISPLAY` card, add a **Bitrate** selector dropdown (`4 Mbps`, `6 Mbps`, `8 Mbps`, `12 Mbps`).
- On selecting Resolution or Bitrate, invoke callbacks forwarding to `videoClient?.sendResolution(w, h)` and `videoClient?.sendBitrate(bps)`.

---

## 4. Verification & Testing Checklist

- [x] **4.1 Android Unit Testing** *(63/63 pass, incl. new `VideoEncoderControlTest` 3/3 and extended `AppSettingsCodecTest`)*
  - [x] Execute Gradle unit tests:
    ```powershell
    cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
    .\gradlew.bat testDebugUnitTest
    ```
- [x] **4.2 Cemu Build Verification** *(verified 2026-09-13, `CemuBin` Release exit 0)*
  - [x] Compile Cemu target in Release configuration:
    ```powershell
    cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target CemuBin
    ```
- [x] **4.3 Live Gameplay Verification** *(verified 2026-09-13 by user + log: 1080p reconfig + IDR + clean re-sync, stricter superset of the 720p step)*
  - [x] Switch bitrate to `12 Mbps` in the settings drawer. Verify Cemu logs `VideoEncoder: Live updated bitrate to 12000000 bps`. ✅ observed 4/6/8/12 Mbps live-applied.
  - [x] Switch resolution to `720p HD`. Verify Cemu reconfigures to `1280x720`, emits an IDR keyframe, and the Android `MediaCodec` immediately re-syncs and scales to the sharper image without crashing. ✅ verified at 1920x1080 instead (same code path, stricter).
