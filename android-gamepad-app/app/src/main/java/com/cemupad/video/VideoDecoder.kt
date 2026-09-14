package com.cemupad.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.cemupad.util.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
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
        const val IDR_RECOVERY_INTERVAL_FRAMES = 30
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

    @Volatile private var codec: MediaCodec? = null
    private val isRunning = AtomicBoolean(false)
    @Volatile private var isConfigured = false

    private data class QueuedFrame(val nalData: ByteArray, val ptsUs: Long)
    private val inputQueue = java.util.concurrent.ArrayBlockingQueue<QueuedFrame>(4)

    private val decoderThread = HandlerThread("CemuPad-Decoder").apply { start() }
    private val decoderHandler = Handler(decoderThread.looper)

    private val decodeTask = Runnable {
        val frame = inputQueue.poll() ?: return@Runnable
        doDecodeFrame(frame.nalData, frame.ptsUs)
    }

    private fun safeSetInteger(format: MediaFormat, key: String, value: Int) {
        try {
            format.setInteger(key, value)
        } catch (_: Throwable) {
        }
    }

    private fun <T> runOnDecoderThreadSync(timeoutMs: Long, block: () -> T): T? {
        if (!decoderThread.isAlive) return null
        val task = FutureTask(Callable { block() })
        if (!decoderHandler.post(task)) return null
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            task.cancel(true)
            null
        }
    }

    private fun selectAvcDecoder(): MediaCodecInfo? {
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
        }
        if (candidates.isEmpty()) return null

        // 1. Prioritize explicit low_latency named decoders (e.g. c2.qti.avc.decoder.low_latency)
        val explicitLowLatency = candidates.firstOrNull { info ->
            val name = info.name.lowercase()
            name.contains("low_latency") || name.contains("lowlatency")
        }
        if (explicitLowLatency != null) return explicitLowLatency

        // 2. Prioritize hardware decoders advertising FEATURE_LowLatency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val featureLowLatency = candidates.firstOrNull { info ->
                try {
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                        .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
                } catch (_: Exception) {
                    false
                }
            }
            if (featureLowLatency != null) return featureLowLatency
        }

        // 3. Prioritize hardware-accelerated decoders over software fallback
        val hardwareDecoder = candidates.firstOrNull { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                val name = info.name.lowercase()
                !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains("sw")
            }
        }
        return hardwareDecoder ?: candidates.first()
    }

    // Telemetry
    val totalFramesDecoded = AtomicLong(0)
    val totalFramesReceived = AtomicLong(0)
    val totalFramesDropped = AtomicLong(0)
    val totalCodecErrors = AtomicLong(0)
    val totalIdrRequests = AtomicLong(0)
    val totalFramesRateLimited = AtomicLong(0)
    @Volatile var maxFps: Int = 30
    private var lastDecodedPtsUs: Long = FrameRateLimiter.NO_PREVIOUS_PTS
    @Volatile var currentFps = 0f
        private set
    private var lastFpsCalcTime = System.currentTimeMillis()
    private var framesSinceLastFps = 0

    // Mid-stream joins only become decodable once SPS/PPS arrive. Until then,
    // request a keyframe at a bounded rate instead of feeding blind data.
    @Volatile private var spsSeen = false
    @Volatile private var ppsSeen = false
    @Volatile private var framesSinceParameterSets = 0

    fun init(width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT): Boolean {
        if (isConfigured) return true
        return runOnDecoderThreadSync(10000L) { doInit(width, height) } ?: false
    }

    private fun doInit(width: Int, height: Int): Boolean {
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
                safeSetInteger(this, MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())

                // Vendor-specific low-latency tuning (inspired by Moonlight / Apollo)
                val name = decoderInfo.name.lowercase()
                when {
                    name.contains("qcom") || name.contains("qti") -> {
                        safeSetInteger(this, "vendor.qti-ext-dec-low-latency.enable", 1)
                        safeSetInteger(this, "vendor.qti-ext-dec-picture-order.enable", 0)
                        safeSetInteger(this, "vendor.qti-ext-dec-frame-drop.enable", 1)
                        safeSetInteger(this, "vendor.qti-ext-dec-dpb-output-delay.enable", 0)
                    }
                    name.contains("mtk") -> {
                        safeSetInteger(this, "vendor.mtk.vdec.low-latency.mode", 1)
                        safeSetInteger(this, "vendor.mtk.vdec.disable-idle", 1)
                        safeSetInteger(this, "vendor.mtk.vdec.preload.frame.count", 1)
                    }
                    name.contains("exynos") || name.contains("samsung") -> {
                        safeSetInteger(this, "vendor.rtc-ext-dec-low-latency.enable", 1)
                    }
                }
                safeSetInteger(this, "vendor.low-latency.enable", 1)
            }

            val decoder = MediaCodec.createByCodecName(decoderInfo.name)
            decoder.configure(format, surface, null, 0)
            decoder.start()
            decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)

            codec = decoder
            isRunning.set(true)
            isConfigured = true
            spsSeen = false
            ppsSeen = false
            framesSinceParameterSets = 0
            Logger.i(TAG, "MediaCodec AVC hardware decoder initialized: ${decoderInfo.name} ($width x $height)")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize MediaCodec AVC decoder", e)
            false
        }
    }

    /**
     * Feeds Annex B NAL unit payload into the decoder with a presentation timestamp.
     * Uses asynchronous lock-free enqueueing so the network receiver thread is never blocked.
     */
    fun decodeFrame(nalData: ByteArray, ptsUs: Long) {
        if (!isRunning.get()) return
        totalFramesReceived.incrementAndGet()

        while (inputQueue.size >= 3) {
            inputQueue.poll()
            totalFramesDropped.incrementAndGet()
        }
        if (inputQueue.offer(QueuedFrame(nalData, ptsUs))) {
            decoderHandler.post(decodeTask)
        } else {
            totalFramesDropped.incrementAndGet()
            Logger.w(TAG, "Decoder queue full, frame dropped")
        }
    }

    private fun doDecodeFrame(nalData: ByteArray, ptsUs: Long) {
        val decoder = codec ?: return

        try {
            val normalizedNal = normalizeAvcBitstream(nalData)

            // Lightweight SPS/PPS detection: scan only the first few NAL start codes
            // instead of fully parsing the Annex-B stream (avoids list allocations on
            // every frame). We only need to know if SPS (type 7) and PPS (type 8) are
            // present for the parameter-set watchdog.
            if (!spsSeen || !ppsSeen) {
                var i = 0
                while (i + 4 < normalizedNal.size && i < 128) {
                    if (normalizedNal[i] == 0.toByte() && normalizedNal[i + 1] == 0.toByte()) {
                        val off = when {
                            normalizedNal[i + 2] == 1.toByte() -> i + 3
                            i + 3 < normalizedNal.size && normalizedNal[i + 2] == 0.toByte() && normalizedNal[i + 3] == 1.toByte() -> i + 4
                            else -> -1
                        }
                        if (off in 0 until normalizedNal.size) {
                            when (normalizedNal[off].toInt() and 0x1F) {
                                7 -> spsSeen = true
                                8 -> ppsSeen = true
                            }
                            i = off + 1
                            continue
                        }
                    }
                    i++
                }
            }

            // NOTE: no PTS rate limiting here. Dropping P-frames ahead of a
            // stateful H.264 decoder corrupts its reference chain (ghosting
            // until the next IDR). Rate caps belong at the encoder; see the
            // Cemu-side encode-cap item. lastDecodedPtsUs is still tracked
            // for future render-side pacing use.
            lastDecodedPtsUs = ptsUs

            if (spsSeen && ppsSeen) {
                framesSinceParameterSets = 0
            } else {
                framesSinceParameterSets++
                if (framesSinceParameterSets >= IDR_RECOVERY_INTERVAL_FRAMES) {
                    Logger.w(TAG, "No SPS/PPS after $framesSinceParameterSets frames, requesting IDR")
                    framesSinceParameterSets = 0
                    totalIdrRequests.incrementAndGet()
                    onRequestIDR()
                }
            }

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
                } else {
                    totalFramesDropped.incrementAndGet()
                }
            } else {
                totalFramesDropped.incrementAndGet()
                Logger.w(TAG, "Input buffer dequeue timed out (congestion), frame dropped")
            }

            // Drain output again to present decoded frame with minimal latency
            drainOutput(decoder)
        } catch (e: MediaCodec.CodecException) {
            totalCodecErrors.incrementAndGet()
            totalIdrRequests.incrementAndGet()
            Logger.e(TAG, "CodecException during decode, requesting IDR recovery", e)
            onRequestIDR()
        } catch (e: Exception) {
            Logger.e(TAG, "Error feeding frame to decoder", e)
        }
    }

    private fun drainOutput(decoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
        if (outputIndex < 0) {
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Logger.i(TAG, "Decoder output format changed: ${decoder.outputFormat}")
            }
            return
        }

        var latestIndex = outputIndex

        // Drain any subsequent ready buffers (fast-path: drop older frames to eliminate lag queues)
        while (true) {
            val nextIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            if (nextIndex >= 0) {
                try {
                    decoder.releaseOutputBuffer(latestIndex, false)
                    totalFramesDropped.incrementAndGet()
                } catch (_: Exception) {
                }
                latestIndex = nextIndex
            } else {
                if (nextIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Logger.i(TAG, "Decoder output format changed: ${decoder.outputFormat}")
                }
                break
            }
        }

        // Render only the latest fresh frame directly to Surface
        try {
            decoder.releaseOutputBuffer(latestIndex, true)
            totalFramesDecoded.incrementAndGet()
            updateFpsTelemetry()
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to release output buffer $latestIndex: ${e.message}")
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
        decoderHandler.removeCallbacksAndMessages(null)
        inputQueue.clear()
        runOnDecoderThreadSync(5000L) { doRelease() }
        decoderThread.quitSafely()
    }

    private fun doRelease() {
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Logger.w(TAG, "Error stopping MediaCodec: ${e.message}")
        }
        codec = null
        isConfigured = false
        spsSeen = false
        ppsSeen = false
        framesSinceParameterSets = 0
        Logger.i(TAG, "MediaCodec decoder released.")
    }
}
