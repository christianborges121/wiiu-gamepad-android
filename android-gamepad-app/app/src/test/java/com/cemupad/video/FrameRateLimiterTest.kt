package com.cemupad.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateLimiterTest {

    @Test
    fun `first frame always passes`() {
        assertTrue(FrameRateLimiter.shouldDecode(1000L, FrameRateLimiter.NO_PREVIOUS_PTS, 30))
    }

    @Test
    fun `frames inside the cap interval are dropped`() {
        // 60 FPS stream against a 30 FPS cap: every second frame goes.
        assertTrue(FrameRateLimiter.shouldDecode(0L, FrameRateLimiter.NO_PREVIOUS_PTS, 30))
        assertFalse(FrameRateLimiter.shouldDecode(16666L, 0L, 30))
        assertTrue(FrameRateLimiter.shouldDecode(33333L, 0L, 30))
        assertFalse(FrameRateLimiter.shouldDecode(50000L, 33333L, 30))
    }

    @Test
    fun `non-positive cap disables limiting`() {
        assertTrue(FrameRateLimiter.shouldDecode(1L, 0L, 0))
        assertTrue(FrameRateLimiter.shouldDecode(1L, 0L, -5))
    }

    @Test
    fun `non-monotonic timestamps never stall`() {
        assertTrue(FrameRateLimiter.shouldDecode(0L, 99999L, 30))
        assertTrue(FrameRateLimiter.shouldDecode(5000L, 5000L, 60))
    }

    @Test
    fun `high caps pass through a 60 fps stream`() {
        assertTrue(FrameRateLimiter.shouldDecode(16666L, 0L, 60))
        assertTrue(FrameRateLimiter.shouldDecode(16666L, 0L, 120))
    }
}
