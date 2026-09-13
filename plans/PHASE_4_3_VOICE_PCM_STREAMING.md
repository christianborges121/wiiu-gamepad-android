# Phase 4.3 Implementation Plan — Direct 32 kHz Voice PCM Audio Streaming

## 1. Overview & Objective
Currently, CemuPad processes microphone input locally on the Android phone using `MicBlowDetector.kt`. It performs RMS amplitude analysis at 16 kHz to detect acoustic blows, and asserts a virtual digital blow button (`kButtonId_Mic`) or sends `OPCODE_MIC_BLOW` over TCP.

While this satisfies blow platforms in *Super Mario 3D World*, titles like *Captain Toad: Treasure Tracker*, *Wii Sports Club*, and voice-based mini-games require raw GamePad microphone audio.

**Objective**:
Stream real-time 32 kHz 16-bit signed mono PCM microphone audio from Android directly into Cemu's internal Cafe OS microphone subsystem (`mic.cpp:mic_feedSamples()`) over UDP port `26764`.

---

## 2. Protocol Specification

- **Microphone Port**: `UDP 26764`
- **Audio Format**:
  - Sample Rate: `32,000 Hz` (matching Cafe OS `MIC_SAMPLERATE = 32000`)
  - Channels: `1 (Mono)`
  - Bit Depth: `16-bit signed PCM (Little-Endian)`
  - Chunk Size: `320 samples (640 bytes, exactly 10ms of audio)`
- **Packet Structure (648 bytes total)**:
  - Header (8 bytes):
    - `uint32_t sequenceNumber` (monotonically incrementing)
    - `uint32_t sampleCount` (typically 320)
  - Payload (640 bytes):
    - Array of `int16_t` PCM samples.

---

## 3. Implementation Checklist & Step-by-Step Code Modifications

- [x] **Step 3.1: Implement CemuPadBridge Mic Sample Delegate** *(adapted for correctness: plan's direct `FeedMicSamples→mic_feedSamples` from the network thread races with `mic_updateOnAXFrame`'s AX-thread writer on the same ringbuffer `writeIndex`. Implemented instead as a thread-safe `QueueMicSamples`/`DequeueMicSamples`/`ClearMicQueue` handoff — network thread enqueues, audio thread consumes; ringbuffer keeps a single writer)*
  - [x] Add `QueueMicSamples` / `DequeueMicSamples` / `ClearMicQueue` in `Cemu/src/streaming/CemuPadBridge.h` (1s cap, drop-oldest).
  - [x] Consume queued phone PCM in `mic.cpp:mic_updateOnAXFrame` (preferred when mic/blow active and queue non-empty; synthetic tone fallback preserved), keeping `mic_feedSamples` as the sole ringbuffer writer.

#### [MODIFY] [`Cemu/src/streaming/CemuPadBridge.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.h)
Add delegate method to feed raw mic samples into Cafe OS `mic.cpp`:
```cpp
    // Feed incoming 32 kHz 16-bit mono PCM microphone samples to Cafe OS
    void FeedMicSamples(const int16_t* samples, size_t sampleCount);
```

#### [MODIFY] [`Cemu/src/streaming/CemuPadBridge.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.cpp)
```cpp
#include "Cafe/OS/libs/mic/mic.h"

void CemuPadBridge::FeedMicSamples(const int16_t* samples, size_t sampleCount)
{
    if (!m_isActive.load() || !samples || sampleCount == 0) return;
    // Feed directly into Cafe OS DRC0 mic ringbuffer
    mic_feedSamples(0, const_cast<sint16*>(reinterpret_cast<const sint16*>(samples)), static_cast<sint32>(sampleCount));
}
```

- [x] **Step 3.2: Implement VideoStreamServer UDP 26764 Mic Receiver Loop**
  - [x] Define `MIC_PORT = 26764` in `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h` (files still under `Cafe/` — media move deferred).
  - [x] Implement `MicRxThreadFunc` in `VideoStreamServer.cpp` (500ms-timeout socket, header validation, drop malformed).
  - [x] Extract PCM samples from packets and forward to `CemuPadBridge::QueueMicSamples(...)`.

#### [MODIFY] [`Cemu/src/streaming/VideoStreamServer.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoStreamServer.h)
Add port definition:
```cpp
    static constexpr uint16 MIC_PORT = 26764;
```

#### [MODIFY] [`Cemu/src/streaming/VideoStreamServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoStreamServer.cpp)
Implement UDP receiver loop for port `26764` that forwards to `CemuPadBridge`:
```cpp
void VideoStreamServer::RunMicReceiverLoop()
{
    SOCKET sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    sockaddr_in bindAddr{};
    bindAddr.sin_family = AF_INET;
    bindAddr.sin_addr.s_addr = htonl(INADDR_ANY);
    bindAddr.sin_port = htons(MIC_PORT);
    bind(sock, (sockaddr*)&bindAddr, sizeof(bindAddr));

    char buffer[1024];
    while (m_isRunning.load())
    {
        int bytes = recv(sock, buffer, sizeof(buffer), 0);
        if (bytes > 8)
        {
            uint32 sampleCount = *(uint32*)(buffer + 4);
            const int16_t* samples = reinterpret_cast<const int16_t*>(buffer + 8);
            if (sampleCount * sizeof(int16_t) <= static_cast<size_t>(bytes - 8))
            {
                CemuPadBridge::GetInstance().FeedMicSamples(samples, sampleCount);
            }
        }
    }
    closesocket(sock);
}
```

- [x] **Step 3.3: Implement Android 32 kHz Voice PCM Streamer** *(plus 32kHz-support check, permission guards, pure packet builder for tests)*
  - [x] Create `MicVoiceStreamer.kt` in `android-gamepad-app/app/src/main/java/com/cemupad/audio/`.
  - [x] Record 32 kHz 16-bit mono audio with `AudioRecord`.
  - [x] Stream 320-sample chunks (10ms) via UDP datagrams to port `26764`.

#### [NEW] [`android-gamepad-app/app/src/main/java/com/cemupad/audio/MicVoiceStreamer.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/audio/MicVoiceStreamer.kt)
Handles recording and UDP packet streaming:
```kotlin
package com.cemupad.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.cemupad.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class MicVoiceStreamer(
    private val hostIp: String,
    private val port: Int = 26764
) {
    companion object {
        private const val TAG = "MicVoiceStreamer"
        private const val SAMPLE_RATE = 32000
        private const val SAMPLES_PER_CHUNK = 320 // 10ms
    }

    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) return
        thread = Thread({
            runAudioLoop()
        }, "CemuPad-VoiceStreamer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        thread?.interrupt()
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, SAMPLES_PER_CHUNK * 2 * 4)

        var record: AudioRecord? = null
        var socket: DatagramSocket? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            socket = DatagramSocket()
            val hostAddr = InetAddress.getByName(hostIp)
            val pcmBuffer = ShortArray(SAMPLES_PER_CHUNK)
            val packetData = ByteArray(8 + SAMPLES_PER_CHUNK * 2)
            val byteBuf = ByteBuffer.wrap(packetData).order(ByteOrder.LITTLE_ENDIAN)
            val packet = DatagramPacket(packetData, packetData.size, hostAddr, port)
            var seq = 0

            record.startRecording()
            Logger.i(TAG, "Recording started at 32 kHz -> $hostIp:$port")

            while (isRunning.get()) {
                val read = record.read(pcmBuffer, 0, SAMPLES_PER_CHUNK)
                if (read > 0) {
                    byteBuf.clear()
                    byteBuf.putInt(seq++)
                    byteBuf.putInt(read)
                    for (i in 0 until read) {
                        byteBuf.putShort(pcmBuffer[i])
                    }
                    socket.send(packet)
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Voice streamer error: ${e.message}")
        } finally {
            try {
                record?.stop()
                record?.release()
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
```

- [x] **Step 3.4: Wire Microphone Streaming in MainActivity** *(starts on video connect when mic toggle is on, stops with the stream, toggles live with the setting)*
  - [x] Initialize `MicVoiceStreamer` on connection in `MainActivity.kt`.
  - [x] Toggle streaming dynamically when user enables/disables microphone in settings.

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt)
- Instantiate `micVoiceStreamer = MicVoiceStreamer(serverIp)` when video connects.
- Start and stop synchronously with `displaySettings.micEnabled`.

---

## 4. Verification & Testing Checklist

- [x] **4.1 Android Unit Testing** *(green, incl. new `MicVoiceStreamerTest` 4/4)*
  - [x] Run Gradle unit tests:
    ```powershell
    cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
    .\gradlew.bat testDebugUnitTest
    ```
- [x] **4.2 Cemu Release Build Verification** *(verified 2026-09-13, `CemuBin` Release exit 0, no new warnings)*
  - [x] Build Cemu target:
    ```powershell
    cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target CemuBin
    ```
- [ ] **4.3 Live Device Audio Verification**
  - [ ] Speak into the phone's microphone with mic setting enabled.
  - [ ] Verify Cemu log: `mic_feedSamples: fed 320 samples into DRC0 ringbuffer`.
  - [ ] Verify clean audio rendering in voice-enabled mini-games without buffer underruns.
