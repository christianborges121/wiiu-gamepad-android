package com.cemupad.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.cemupad.util.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Hardware-accelerated H.264 (AVC) decoder utilizing Android MediaCodec.
 * Outputs decoded frames directly to a provided Surface for zero-copy GPU presentation.
 */
class VideoDecoder(
    private val surface: Surface,
    private val onRequestIDR: () -> Unit
) {
    companion object {
        const val TAG = "VideoDecoder"
        const val DEFAULT_WIDTH = 854
        const val DEFAULT_HEIGHT = 480
        private const val DEQUEUE_TIMEOUT_US = 5000L // 5ms

        fun normalizeAvcBitstream(input: ByteArray): ByteArray {
            if (input.isEmpty()) return input

            if (input.size >= 4 && input[0] == 0x00.toByte() && input[1] == 0x00.toByte() &&
                ((input[2] == 0x00.toByte() && input[3] == 0x01.toByte()) || input[2] == 0x01.toByte())
            ) {
                return input
            }

            if (input.size >= 4) {
                var offset = 0
                var sawNal = false
                val output = ByteArrayOutputStream(input.size + 16)

                while (offset + 4 <= input.size) {
                    val nalLength = ((input[offset].toInt() and 0xFF) shl 24) or
                        ((input[offset + 1].toInt() and 0xFF) shl 16) or
                        ((input[offset + 2].toInt() and 0xFF) shl 8) or
                        (input[offset + 3].toInt() and 0xFF)

                    if (nalLength <= 0 || offset + 4 + nalLength > input.size) {
                        break
                    }

                    val nalStartCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
                    output.write(nalStartCode, 0, nalStartCode.size)
                    output.write(input, offset + 4, nalLength)
                    offset += 4 + nalLength
                    sawNal = true
                }

                if (sawNal) {
                    return output.toByteArray()
                }
            }

            val output = ByteArrayOutputStream(input.size + 4)
            val nalStartCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
            output.write(nalStartCode, 0, nalStartCode.size)
            output.write(input)
            return output.toByteArray()
        }
    }

    private var codec: MediaCodec? = null
    private val isRunning = AtomicBoolean(false)
    private var isConfigured = false

    private fun selectAvcDecoder(): MediaCodecInfo? {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
        }
    }

    // Telemetry
    val totalFramesDecoded = AtomicLong(0)
    var currentFps = 0f
        private set
    private var lastFpsCalcTime = System.currentTimeMillis()
    private var framesSinceLastFps = 0

    fun init(width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT): Boolean {
        if (isConfigured) return true

        return try {
            val decoderInfo = selectAvcDecoder()
                ?: throw IllegalStateException("No AVC decoder is available")
            val supportsLowLatency = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                try {
                    decoderInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                        .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
                } catch (_: Exception) {
                    false
                }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                if (supportsLowLatency) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_PRIORITY, 0)
                }
            }

            val decoder = MediaCodec.createByCodecName(decoderInfo.name)
            decoder.configure(format, surface, null, 0)
            decoder.start()
            decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)

            codec = decoder
            isRunning.set(true)
            isConfigured = true
            Logger.i(TAG, "MediaCodec AVC hardware decoder initialized ($width x $height)")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize MediaCodec AVC decoder", e)
            false
        }
    }

    /**
     * Feeds Annex B NAL unit payload into the decoder with a presentation timestamp.
     */
    fun decodeFrame(nalData: ByteArray, ptsUs: Long) {
        val decoder = codec ?: return
        if (!isRunning.get()) return

        try {
            val normalizedNal = normalizeAvcBitstream(nalData)

            // First drain any pending output buffers to free up input slots
            drainOutput(decoder)

            var inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex < 0) {
                // Secondary drain attempt with a slightly longer wait if congested
                drainOutput(decoder)
                inputIndex = decoder.dequeueInputBuffer(10000L)
            }

            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(normalizedNal)
                    decoder.queueInputBuffer(inputIndex, 0, normalizedNal.size, ptsUs, 0)
                }
            } else {
                Logger.w(TAG, "Input buffer dequeue timed out (congestion), frame dropped")
            }

            // Drain output again to present decoded frame with minimal latency
            drainOutput(decoder)
        } catch (e: MediaCodec.CodecException) {
            Logger.e(TAG, "CodecException during decode, requesting IDR recovery", e)
            onRequestIDR()
        } catch (e: Exception) {
            Logger.e(TAG, "Error feeding frame to decoder", e)
        }
    }

    private fun drainOutput(decoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)

        while (outputIndex >= 0) {
            // Render directly to SurfaceView Surface
            decoder.releaseOutputBuffer(outputIndex, true)
            totalFramesDecoded.incrementAndGet()
            updateFpsTelemetry()

            outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
        }

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val newFormat = decoder.outputFormat
            Logger.i(TAG, "Decoder output format changed: $newFormat")
        }
    }

    private fun updateFpsTelemetry() {
        framesSinceLastFps++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsCalcTime
        if (elapsed >= 1000) {
            currentFps = (framesSinceLastFps * 1000f) / elapsed
            framesSinceLastFps = 0
            lastFpsCalcTime = now
        }
    }

    fun release() {
        isRunning.set(false)
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Logger.w(TAG, "Error stopping MediaCodec: ${e.message}")
        }
        codec = null
        isConfigured = false
        Logger.i(TAG, "MediaCodec decoder released.")
    }
}
