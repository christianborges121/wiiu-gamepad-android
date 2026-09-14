package com.cemupad.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkQualityTrackerTest {

    @Test
    fun `perfect network reports zero loss and zero drops`() {
        val tracker = NetworkQualityTracker()
        tracker.onPacketExpected(100)
        tracker.onPacketReceived(100)
        tracker.onFrameEvaluated(dropped = false)
        tracker.onFrameEvaluated(dropped = false)

        val stats = tracker.sample()
        assertEquals(0.0f, stats.packetLossPercent, 0.001f)
        assertEquals(0, stats.lossHundredths)
        assertEquals(0.0f, stats.frameDropPercent, 0.001f)
        assertEquals(0, stats.dropHundredths)
    }

    @Test
    fun `packet loss accurately calculated in hundredths`() {
        val tracker = NetworkQualityTracker()
        tracker.onPacketExpected(100)
        tracker.onPacketReceived(95) // 5 lost = 5.0%

        val stats = tracker.sample()
        assertEquals(5.0f, stats.packetLossPercent, 0.01f)
        assertEquals(500, stats.lossHundredths)
    }

    @Test
    fun `frame drop accurately calculated`() {
        val tracker = NetworkQualityTracker()
        tracker.onFrameEvaluated(dropped = false)
        tracker.onFrameEvaluated(dropped = true)
        tracker.onFrameEvaluated(dropped = false)
        tracker.onFrameEvaluated(dropped = false) // 1 of 4 dropped = 25%

        val stats = tracker.sample()
        assertEquals(25.0f, stats.frameDropPercent, 0.01f)
        assertEquals(2500, stats.dropHundredths)
    }

    @Test
    fun `sampling resets delta between intervals`() {
        val tracker = NetworkQualityTracker()
        tracker.onPacketExpected(100)
        tracker.onPacketReceived(90) // 10%

        val stats1 = tracker.sample()
        assertEquals(10.0f, stats1.packetLossPercent, 0.01f)

        // Next interval: perfect
        tracker.onPacketExpected(50)
        tracker.onPacketReceived(50)

        val stats2 = tracker.sample()
        assertEquals(0.0f, stats2.packetLossPercent, 0.01f)
        assertEquals(0, stats2.lossHundredths)
    }
}
