package com.cemupad.video

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.cemupad.util.Logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Choreographer VSync alignment and frame pacing loop (Phase 7.2).
 *
 * Synchronizes video presentation with Android SurfaceFlinger / Hardware Composer
 * VSync ticks using [MediaCodec.releaseOutputBuffer(index, renderTimestampNs)].
 *
 * When VSync pacing is active, frames are queued to the physical display refresh deadline,
 * preventing micro-stutter and frame judder without dropping packets.
 */
class ChoreographerPacer : Choreographer.FrameCallback {
    companion object {
        private const val TAG = "ChoreographerPacer"
        const val DEFAULT_VSYNC_PERIOD_NANOS = 16_666_666L // 60 Hz fallback
        private const val MIN_VSYNC_PERIOD_NANOS = 4_000_000L   // 240 Hz limit
        private const val MAX_VSYNC_PERIOD_NANOS = 40_000_000L  // 25 Hz limit
    }

    private var choreographer: Choreographer? = null
    private val nextVsyncNanos = AtomicLong(0L)
    private var lastFrameTimeNanos = 0L

    @Volatile
    var vsyncPeriodNanos = DEFAULT_VSYNC_PERIOD_NANOS
        private set

    @Volatile
    var isRunning = false
        private set

    /**
     * Starts the Choreographer frame pacing callback loop on the Main / Looper thread.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        val looper = Looper.myLooper()
        if (looper == null) {
            // If called from non-Looper thread, post to main thread
            Handler(Looper.getMainLooper()).post {
                attachChoreographer()
            }
        } else {
            attachChoreographer()
        }
    }

    private fun attachChoreographer() {
        if (!isRunning) return
        try {
            choreographer = Choreographer.getInstance()
            choreographer?.postFrameCallback(this)
            Logger.i(TAG, "Choreographer pacing callback attached (vsync=${vsyncPeriodNanos / 1_000_000}ms)")
        } catch (e: Exception) {
            Logger.w(TAG, "Unable to get Choreographer instance: ${e.message}")
        }
    }

    /**
     * Stops the frame pacing callback loop.
     */
    fun stop() {
        isRunning = false
        try {
            choreographer?.removeFrameCallback(this)
        } catch (_: Exception) {
        }
        choreographer = null
        lastFrameTimeNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        if (lastFrameTimeNanos > 0L) {
            val delta = frameTimeNanos - lastFrameTimeNanos
            if (delta in MIN_VSYNC_PERIOD_NANOS..MAX_VSYNC_PERIOD_NANOS) {
                // Exponential moving average to calibrate physical display refresh rate
                vsyncPeriodNanos = (vsyncPeriodNanos * 7 + delta) / 8
            }
        }
        lastFrameTimeNanos = frameTimeNanos
        nextVsyncNanos.set(frameTimeNanos + vsyncPeriodNanos)

        try {
            choreographer?.postFrameCallback(this)
        } catch (_: Exception) {
        }
    }

    /**
     * Computes the target presentation timestamp in nanoseconds for releaseOutputBuffer.
     * Guaranteed lock-free and thread-safe for direct decoder thread queries (<10ns).
     */
    fun getTargetVsyncNanos(): Long {
        val next = nextVsyncNanos.get()
        val now = System.nanoTime()
        return if (next > now) {
            next
        } else {
            now + vsyncPeriodNanos
        }
    }

    /**
     * Sets an explicit vsync period in nanoseconds (e.g. for testing or display refresh configuration).
     */
    fun setVsyncPeriodNanosForTest(periodNanos: Long) {
        vsyncPeriodNanos = periodNanos.coerceIn(MIN_VSYNC_PERIOD_NANOS, MAX_VSYNC_PERIOD_NANOS)
    }

    /**
     * Simulates a frame tick (e.g. for headless unit tests).
     */
    fun doFrameForTest(frameTimeNanos: Long) {
        isRunning = true
        doFrame(frameTimeNanos)
    }
}
