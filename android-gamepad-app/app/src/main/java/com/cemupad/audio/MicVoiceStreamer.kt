package com.cemupad.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.cemupad.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams live 32 kHz 16-bit mono microphone PCM to Cemu's Cafe OS microphone
 * subsystem (UDP 26764, consumed in mic_updateOnAXFrame).
 *
 * Wire format per datagram: [uint32 sequence][uint32 sampleCount] (both
 * little-endian) followed by `sampleCount` int16 LE samples. Chunks carry
 * [SAMPLES_PER_CHUNK] samples (10 ms of audio).
 *
 * Runs alongside [MicBlowDetector] (separate recorder); both are gated by the
 * microphone settings toggle and torn down when disabled.
 */
class MicVoiceStreamer(
    private val context: Context,
    private val hostIp: String,
    private val port: Int = MIC_PORT
) {
    companion object {
        private const val TAG = "MicVoiceStreamer"
        const val MIC_PORT = 26764
        const val SAMPLE_RATE = 32000
        const val SAMPLES_PER_CHUNK = 320 // 10 ms
        const val HEADER_SIZE = 8

        /**
         * Builds a voice datagram for [samples] with [sequenceNumber].
         * Pure function so unit tests can verify the wire format without audio.
         */
        fun buildVoicePacket(sequenceNumber: Int, samples: ShortArray): ByteArray {
            val data = ByteArray(HEADER_SIZE + samples.size * 2)
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(sequenceNumber)
            buf.putInt(samples.size)
            for (sample in samples) {
                buf.putShort(sample)
            }
            return data
        }
    }

    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    fun isActive(): Boolean = isRunning.get()

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w(TAG, "RECORD_AUDIO not granted; voice streaming not started")
            isRunning.set(false)
            return
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Logger.w(TAG, "32 kHz capture unsupported on this device (minBuffer=$minBufferSize)")
            isRunning.set(false)
            return
        }
        thread = Thread({
            runAudioLoop(minBufferSize)
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
    private fun runAudioLoop(minBufferSize: Int) {
        // Check permission again on the worker thread (revocable at runtime).
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w(TAG, "RECORD_AUDIO revoked; voice streaming stopped")
            isRunning.set(false)
            return
        }
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
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Logger.w(TAG, "AudioRecord failed to initialize at 32 kHz")
                return
            }

            socket = DatagramSocket()
            val hostAddr = InetAddress.getByName(hostIp)
            val pcmBuffer = ShortArray(SAMPLES_PER_CHUNK)
            var seq = 0

            record.startRecording()
            Logger.i(TAG, "Voice streaming started at 32 kHz -> $hostIp:$port")

            while (isRunning.get()) {
                val read = record.read(pcmBuffer, 0, SAMPLES_PER_CHUNK)
                if (read > 0) {
                    val packetData = buildVoicePacket(seq++, pcmBuffer.copyOf(read))
                    socket.send(DatagramPacket(packetData, packetData.size, hostAddr, port))
                } else if (read < 0) {
                    Logger.w(TAG, "AudioRecord read error: $read")
                    break
                }
            }
        } catch (e: InterruptedException) {
            // Stopping
        } catch (e: Exception) {
            Logger.w(TAG, "Voice streamer error: ${e.message}")
        } finally {
            isRunning.set(false)
            try {
                record?.stop()
            } catch (_: Exception) {
            }
            try {
                record?.release()
            } catch (_: Exception) {
            }
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }
}
