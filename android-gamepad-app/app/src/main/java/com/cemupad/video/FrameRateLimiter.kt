package com.cemupad.video

/**
 * Pure presentation-timestamp gate for the decode rate cap.
 *
 * Returns true when a frame with [ptsUs] should be decoded given the last
 * decoded timestamp and [maxFps]. Non-monotonic timestamps (stream restart,
 * broken PTS) always pass so a bad clock can never stall the picture;
 * the limiter only drops frames that arrive sooner than the cap interval.
 */
object FrameRateLimiter {
    const val NO_PREVIOUS_PTS = -1L

    fun shouldDecode(ptsUs: Long, lastDecodedPtsUs: Long, maxFps: Int): Boolean {
        if (maxFps <= 0) return true
        if (lastDecodedPtsUs < 0) return true
        if (ptsUs <= lastDecodedPtsUs) return true
        val minIntervalUs = 1_000_000L / maxFps
        return ptsUs - lastDecodedPtsUs >= minIntervalUs
    }
}
