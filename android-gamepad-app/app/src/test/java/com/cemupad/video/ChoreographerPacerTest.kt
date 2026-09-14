package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreographerPacerTest {

    @Test
    fun `default vsync period is 60Hz`() {
        val pacer = ChoreographerPacer()
        assertEquals(ChoreographerPacer.DEFAULT_VSYNC_PERIOD_NANOS, pacer.vsyncPeriodNanos)
        assertFalse(pacer.isRunning)
    }

    @Test
    fun `frame callback adjusts vsync period smoothly`() {
        val pacer = ChoreographerPacer()
        // Simulate 120Hz ticks (~8.33ms = 8_333_333ns)
        var t = 1_000_000_000L
        pacer.doFrameForTest(t)
        for (i in 0 until 10) {
            t += 8_333_333L
            pacer.doFrameForTest(t)
        }

        // Period should have adapted downwards towards 8.33ms from 16.66ms
        assertTrue("VSync period should adapt to higher refresh rate: ${pacer.vsyncPeriodNanos}",
            pacer.vsyncPeriodNanos < 12_000_000L)
    }

    @Test
    fun `target vsync nanos returns future timestamp`() {
        val pacer = ChoreographerPacer()
        val now = System.nanoTime()
        val target = pacer.getTargetVsyncNanos()
        assertTrue("Target timestamp must be in the future (target=$target, now=$now)", target >= now)
    }

    @Test
    fun `explicit vsync period clamp works`() {
        val pacer = ChoreographerPacer()
        pacer.setVsyncPeriodNanosForTest(1_000L) // Under minimum
        assertEquals(4_000_000L, pacer.vsyncPeriodNanos)

        pacer.setVsyncPeriodNanosForTest(100_000_000L) // Over maximum
        assertEquals(40_000_000L, pacer.vsyncPeriodNanos)
    }
}
