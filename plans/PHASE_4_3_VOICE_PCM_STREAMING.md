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

## 3. Files to Modify & Create

### Cemu Backend (`Cemu`)

#### [MODIFY] [`Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h)
Add port and forward declaration:
```cpp
    static constexpr uint16 MIC_PORT = 26764;
```

#### [MODIFY] [`Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp)
Include `Cafe/OS/libs/mic/mic.h` and implement a UDP socket listener on port `26764`:
```cpp
#include "Cafe/OS/libs/mic/mic.h"

// In Worker thread or UDP receiver loop for mic:
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
            sint16* samples = (sint16*)(buffer + 8);
            if (sampleCount * sizeof(sint16) <= (size_t)(bytes - 8))
            {
                // Feed directly into Cafe OS DRC0 mic ringbuffer
                mic_feedSamples(0, samples, (sint32)sampleCount);
            }
        }
    }
    closesocket(sock);
}
```

---

### Android Frontend (`android-gamepad-app`)

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

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt)
- Instantiate `micVoiceStreamer = MicVoiceStreamer(serverIp)` when video connects.
- Start and stop synchronously with `displaySettings.micEnabled`.

---

## 4. Automated Testing & Verification

1. Run unit tests:
   ```powershell
   cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
   .\gradlew.bat testDebugUnitTest
   ```
2. Build Cemu Release:
   ```powershell
   cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target Cemu
   ```
3. Live Device Verification:
   - Speak into the phone's microphone.
   - Verify Cemu log: `mic_feedSamples: fed 320 samples into DRC0 ringbuffer`.
   - Verify no buffer overflows or underruns in Cafe OS `snd_core`.
