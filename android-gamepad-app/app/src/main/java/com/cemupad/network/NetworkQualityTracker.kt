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

    // Latency / jitter tracking for adaptive bitrate (rolling window)
    private val latencySumMs = AtomicLong(0)
    private val latencySumSqMs = AtomicLong(0)
    private val latencyCount = AtomicLong(0)
    private var lastLatencySum = 0L
    private var lastLatencySumSq = 0L
    private var lastLatencyCount = 0L

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

    fun onFrameLatency(latencyMs: Long) {
        if (latencyMs < 0) return
        latencySumMs.addAndGet(latencyMs)
        latencySumSqMs.addAndGet(latencyMs * latencyMs)
        latencyCount.incrementAndGet()
    }

    data class NetworkStats(
        val packetLossPercent: Float, // 0.0 .. 100.0
        val frameDropPercent: Float,  // 0.0 .. 100.0
        val lossHundredths: Int,       // e.g. 500 = 5.00%
        val dropHundredths: Int,       // e.g. 250 = 2.50%
        val sampleIntervalMs: Long,
        val avgLatencyMs: Float = 0f,
        val jitterMs: Float = 0f
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

        // Latency / jitter over this interval
        val curLatencySum = latencySumMs.get()
        val curLatencySumSq = latencySumSqMs.get()
        val curLatencyCount = latencyCount.get()
        val deltaLatencySum = curLatencySum - lastLatencySum
        val deltaLatencySumSq = curLatencySumSq - lastLatencySumSq
        val deltaLatencyCount = curLatencyCount - lastLatencyCount
        lastLatencySum = curLatencySum
        lastLatencySumSq = curLatencySumSq
        lastLatencyCount = curLatencyCount

        val avgLatencyMs = if (deltaLatencyCount > 0) deltaLatencySum.toFloat() / deltaLatencyCount else 0f
        val jitterMs = if (deltaLatencyCount > 1) {
            val mean = avgLatencyMs.toDouble()
            val variance = (deltaLatencySumSq.toDouble() / deltaLatencyCount) - (mean * mean)
            kotlin.math.sqrt(variance.coerceAtLeast(0.0)).toFloat().coerceIn(0f, 1000f)
        } else 0f

        return NetworkStats(
            packetLossPercent = packetLossPercent,
            frameDropPercent = frameDropPercent,
            lossHundredths = lossHundredths,
            dropHundredths = dropHundredths,
            sampleIntervalMs = interval,
            avgLatencyMs = avgLatencyMs,
            jitterMs = jitterMs
        )
    }

    fun reset() {
        expectedPackets.set(0)
        receivedPackets.set(0)
        totalFrames.set(0)
        droppedFrames.set(0)
        latencySumMs.set(0)
        latencySumSqMs.set(0)
        latencyCount.set(0)
        lastExpected = 0L
        lastReceived = 0L
        lastFrames = 0L
        lastDroppedFrames = 0L
        lastLatencySum = 0L
        lastLatencySumSq = 0L
        lastLatencyCount = 0L
        lastSampleTime = System.currentTimeMillis()
    }
}
