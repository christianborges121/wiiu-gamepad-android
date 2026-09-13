package com.cemupad.video

/**
 * Reassembles fragmented H.264 frames from [UdpVideoPacket] datagrams.
 *
 * Policy: bounded slots, keep-latest eviction, time expiry for incomplete
 * frames. Concatenated output is byte-identical to the encoder's frame in
 * packet-index order. Sequence gaps are counted for loss statistics
 * (reordering may overcount slightly; ground truth for recovery stays
 * IDR loss + expiry).
 */
class FrameReassembler(
    private val maxSlots: Int = 4,
    private val expiryMs: Long = 100L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    data class CompletedFrame(
        val data: ByteArray,
        val ptsUs: Long,
        val isIdr: Boolean,
        val frameId: Long
    )

    sealed interface OfferResult {
        data class FrameComplete(val frame: CompletedFrame) : OfferResult
        data class FrameDropped(val frameId: Long, val isIdr: Boolean) : OfferResult
        data object Waiting : OfferResult
    }

    data class Stats(
        var datagramsReceived: Long = 0,
        var datagramsMalformed: Long = 0,
        var packetsLost: Long = 0,
        var framesCompleted: Long = 0,
        var framesDropped: Long = 0
    )

    val stats = Stats()

    private data class Slot(
        val frameId: Long,
        val packetCount: Int,
        var ptsUs: Long,
        var isIdr: Boolean,
        var firstSeenMs: Long,
        val parts: Array<ByteArray?>,
        var received: Int = 0
    )

    private val slots = LinkedHashMap<Long, Slot>()
    private var lastSeq: Long = -1L

    fun offer(datagram: ByteArray, length: Int = datagram.size): List<OfferResult> {
        val events = mutableListOf<OfferResult>()
        val decoded = UdpVideoPacket.decode(datagram, length)
        if (decoded == null) {
            stats.datagramsMalformed++
            return events
        }
        stats.datagramsReceived++
        countSeqGap(decoded.header.seq)

        val now = clockMs()
        events += evictExpired(now)

        var slot = slots[decoded.header.frameId]
        if (slot == null) {
            while (slots.size >= maxSlots) {
                evictOldest()?.let { events += it }
            }
            slot = Slot(
                frameId = decoded.header.frameId,
                packetCount = decoded.header.packetCount,
                ptsUs = decoded.header.ptsUs,
                isIdr = decoded.header.isIdr,
                firstSeenMs = now,
                parts = arrayOfNulls(decoded.header.packetCount)
            )
            slots[decoded.header.frameId] = slot
        }
        if (decoded.header.packetCount != slot.packetCount) {
            stats.datagramsMalformed++
            return events
        }
        slot.isIdr = slot.isIdr || decoded.header.isIdr
        if (slot.parts[decoded.header.packetIndex] == null) {
            slot.parts[decoded.header.packetIndex] = decoded.payload
            slot.received++
        }
        if (slot.received >= slot.packetCount) {
            slots.remove(decoded.header.frameId)
            stats.framesCompleted++
            var total = 0
            for (part in slot.parts) total += part!!.size
            val out = ByteArray(total)
            var pos = 0
            for (part in slot.parts) {
                part!!.copyInto(out, pos)
                pos += part.size
            }
			// Trust the IDR flag from the UDP header (set by Cemu's encoder);
			// re-parsing the full Annex-B payload adds unnecessary CPU on every frame.
			events += OfferResult.FrameComplete(
				CompletedFrame(
					data = out,
					ptsUs = slot.ptsUs,
					isIdr = slot.isIdr,
					frameId = slot.frameId
				)
			)
        }
        return events
    }

    private fun countSeqGap(seq: Long) {
        if (lastSeq < 0) {
            lastSeq = seq
            return
        }
        val gap = (seq - lastSeq - 1) and 0xFFFFFFFFL
        if (gap > 0 && gap < 0x80000000L) {
            stats.packetsLost += gap
        }
        if (gap < 0x80000000L) {
            lastSeq = seq
        }
    }

    private fun evictExpired(now: Long): List<OfferResult.FrameDropped> {
        val dropped = mutableListOf<OfferResult.FrameDropped>()
        val it = slots.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now - entry.value.firstSeenMs > expiryMs) {
                it.remove()
                stats.framesDropped++
                dropped += OfferResult.FrameDropped(entry.value.frameId, entry.value.isIdr)
            }
        }
        return dropped
    }

    private fun evictOldest(): OfferResult.FrameDropped? {
        val it = slots.entries.iterator()
        if (!it.hasNext()) return null
        val entry = it.next()
        it.remove()
        stats.framesDropped++
        return OfferResult.FrameDropped(entry.value.frameId, entry.value.isIdr)
    }

    fun pendingFrames(): Int = slots.size

    fun reset() {
        slots.clear()
        lastSeq = -1L
    }
}
