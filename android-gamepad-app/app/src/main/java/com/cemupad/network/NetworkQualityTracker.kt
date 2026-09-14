package com.cemupad.network

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks rolling network packet loss, frame dropping, and link stability
 * to provide adaptive dynamic bitrate telemetry to the Cemu host.
 */
class NetworkQualityTracker {

    private val expectedPackets = AtomicLong(0)
    private val receivedPackets = AtomicLong(0)
    private val totalFrames = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)

    private var lastExpected = 0L
    private var lastReceived = 0L
    private var lastFrames = 0L
    private var lastDroppedFrames = 0L
    private var lastSampleTime = System.currentTimeMillis()

    fun onPacketExpected(count: Int = 1) {
        if (count > 0) {
            expectedPackets.addAndGet(count.toLong())
        }
    }

    fun onPacketReceived(count: Int = 1) {
        if (count > 0) {
            receivedPackets.addAndGet(count.toLong())
        }
    }

    fun onFrameEvaluated(dropped: Boolean) {
        totalFrames.incrementAndGet()
        if (dropped) {
            droppedFrames.incrementAndGet()
        }
    }

    data class NetworkStats(
        val packetLossPercent: Float, // 0.0 .. 100.0
        val frameDropPercent: Float,  // 0.0 .. 100.0
        val lossHundredths: Int,       // e.g. 500 = 5.00%
        val dropHundredths: Int,       // e.g. 250 = 2.50%
        val sampleIntervalMs: Long
    )

    fun sample(): NetworkStats {
        val now = System.currentTimeMillis()
        val curExpected = expectedPackets.get()
        val curReceived = receivedPackets.get()
        val curFrames = totalFrames.get()
        val curDroppedFrames = droppedFrames.get()

        val deltaExpected = curExpected - lastExpected
        val deltaReceived = curReceived - lastReceived
        val deltaFrames = curFrames - lastFrames
        val deltaDroppedFrames = curDroppedFrames - lastDroppedFrames
        val interval = (now - lastSampleTime).coerceAtLeast(1L)

        lastExpected = curExpected
        lastReceived = curReceived
        lastFrames = curFrames
        lastDroppedFrames = curDroppedFrames
        lastSampleTime = now

        val packetLossPercent = if (deltaExpected > 0) {
            val lost = deltaExpected - deltaReceived
            if (lost > 0) {
                (lost.toFloat() / deltaExpected.toFloat() * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
        } else {
            0f
        }

        val frameDropPercent = if (deltaFrames > 0) {
            (deltaDroppedFrames.toFloat() / deltaFrames.toFloat() * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }

        val lossHundredths = (packetLossPercent * 100).toInt().coerceIn(0, 10000)
        val dropHundredths = (frameDropPercent * 100).toInt().coerceIn(0, 10000)

        return NetworkStats(
            packetLossPercent = packetLossPercent,
            frameDropPercent = frameDropPercent,
            lossHundredths = lossHundredths,
            dropHundredths = dropHundredths,
            sampleIntervalMs = interval
        )
    }

    fun reset() {
        expectedPackets.set(0)
        receivedPackets.set(0)
        totalFrames.set(0)
        droppedFrames.set(0)
        lastExpected = 0L
        lastReceived = 0L
        lastFrames = 0L
        lastDroppedFrames = 0L
        lastSampleTime = System.currentTimeMillis()
    }
}
