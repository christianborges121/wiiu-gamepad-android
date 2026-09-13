package com.cemupad.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.cemupad.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-latency UDP audio receiver for Wii U GamePad speaker audio (port 26762).
 * Plays out 48 kHz stereo 16-bit signed PCM audio directly via low-latency AudioTrack.
 */
class AudioStreamReceiver(
    private val port: Int = DEFAULT_PORT
) {
    companion object {
        private const val TAG = "AudioStreamReceiver"
        const val DEFAULT_PORT = 26762
        private const val SAMPLE_RATE = 48000
        private const val MAGIC_A = 0x41.toByte() // 'A'
        private const val MAGIC_P = 0x50.toByte() // 'P'
        private const val HEADER_SIZE = 16
        private const val SOCKET_TIMEOUT_MS = 1000
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var socket: DatagramSocket? = null

    @Volatile
    var isMuted = false
        set(value) {
            field = value
            updateVolume()
        }

    @Volatile
    var volume = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateVolume()
        }

    fun start() {
        if (isRunning.getAndSet(true)) return

        initAudioTrack()

        workerThread = Thread({
            runAudioLoop()
        }, "CemuPad-AudioReceiver").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null

        try {
            socket?.close()
        } catch (ignored: Exception) {}
        socket = null

        releaseAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Use 4x minBufferSize or at least 8192 bytes (~42ms) for jitter resilience
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            track.play()
            audioTrack = track
            updateVolume()
            Logger.i(TAG, "AudioTrack initialized (48 kHz stereo, bufferSize=$bufferSize)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (ignored: Exception) {}
        audioTrack = null
        Logger.i(TAG, "AudioTrack released")
    }

    private fun updateVolume() {
        val gain = if (isMuted) 0f else volume
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioTrack?.setVolume(gain)
            } else {
                @Suppress("DEPRECATION")
                audioTrack?.setStereoVolume(gain, gain)
            }
        } catch (ignored: Exception) {}
    }

    private fun runAudioLoop() {
        val recvBuffer = ByteArray(2048)
        val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

        try {
            socket = DatagramSocket(port).apply {
                soTimeout = SOCKET_TIMEOUT_MS
                reuseAddress = true
                receiveBufferSize = 64 * 1024
            }
            Logger.i(TAG, "UDP audio receiver listening on port $port")

            var audioPacketCount = 0L
            while (isRunning.get()) {
                try {
                    recvPacket.length = recvBuffer.size
                    socket?.receive(recvPacket)

                    val len = recvPacket.length
                    if (len <= HEADER_SIZE) continue

                    // Verify magic: 'A', 'P'
                    if (recvBuffer[0] != MAGIC_A || recvBuffer[1] != MAGIC_P) continue

                    if (++audioPacketCount % 500 == 1L) {
                        Logger.i(TAG, "Audio packet received (count=$audioPacketCount, payloadLen=${len - HEADER_SIZE})")
                    }

                    val payloadLen = len - HEADER_SIZE
                    val track = audioTrack
                    if (track != null && !isMuted && payloadLen > 0) {
                        var written = 0
                        while (written < payloadLen && isRunning.get()) {
                            val res = track.write(recvBuffer, HEADER_SIZE + written, payloadLen - written, AudioTrack.WRITE_BLOCKING)
                            if (res <= 0) break
                            written += res
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal idle timeout
                }
            }
        } catch (e: InterruptedException) {
            // Stopping
        } catch (e: Exception) {
            if (isRunning.get()) {
                Logger.w(TAG, "Audio loop error: ${e.message}")
            }
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
            socket = null
        }
    }
}
