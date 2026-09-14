package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameReassemblerTest {

    private var nowMs = 1000L
    private fun reassembler() = FrameReassembler(clockMs = { nowMs })

    private fun packet(
        frameId: Long,
        index: Int,
        count: Int,
        seq: Long,
        payload: ByteArray = byteArrayOf(index.toByte(), 0x11),
        idr: Boolean = false
    ): ByteArray {
        var flags = 0
        if (index == 0) flags = flags or UdpVideoPacket.FLAG_START
        if (index == count - 1) flags = flags or UdpVideoPacket.FLAG_END
        if (idr) flags = flags or UdpVideoPacket.FLAG_IDR
        return UdpVideoPacket.encode(
            UdpVideoPacket.Header(
                frameId = frameId,
                seq = seq,
                packetIndex = index,
                packetCount = count,
                ptsUs = frameId * 16666L,
                flags = flags
            ),
            payload
        )
    }

    private fun completes(events: List<FrameReassembler.OfferResult>): FrameReassembler.CompletedFrame? {
        return events.filterIsInstance<FrameReassembler.OfferResult.FrameComplete>()
            .firstOrNull()?.frame
    }

    @Test
    fun `in order packets assemble byte identical frame`() {
        val r = reassembler()
        assertTrue(completes(r.offer(packet(1, 0, 3, 10))) == null)
        assertTrue(completes(r.offer(packet(1, 1, 3, 11))) == null)
        val done = completes(r.offer(packet(1, 2, 3, 12)))

        assertTrue(done != null)
        done!!
        assertEquals(1L, done.frameId)
        assertEquals(16666L, done.ptsUs)
        assertEquals(listOf<Byte>(0, 0x11, 1, 0x11, 2, 0x11), done.data.toList())
        assertEquals(1L, r.stats.framesCompleted)
    }

    @Test
    fun `out of order and duplicate packets still complete once`() {
        val r = reassembler()
        assertTrue(completes(r.offer(packet(2, 2, 3, 22))) == null)
        assertTrue(completes(r.offer(packet(2, 0, 3, 20))) == null)
        // Duplicate of packet 0 changes nothing.
        assertTrue(completes(r.offer(packet(2, 0, 3, 20))) == null)
        val done = completes(r.offer(packet(2, 1, 3, 21)))

        assertTrue(done != null)
        assertEquals(listOf<Byte>(0, 0x11, 1, 0x11, 2, 0x11), done!!.data.toList())
    }

    @Test
    fun `expired incomplete frame is dropped and reported`() {
        val r = reassembler()
        assertTrue(completes(r.offer(packet(3, 0, 2, 30, idr = true))) == null)
        nowMs += 500L
        val events = r.offer(packet(4, 0, 1, 40))

        val dropped = events.filterIsInstance<FrameReassembler.OfferResult.FrameDropped>()
        assertEquals(1, dropped.size)
        assertEquals(3L, dropped[0].frameId)
        assertTrue(dropped[0].isIdr)
        assertEquals(1L, r.stats.framesDropped)
        // The new single-packet frame still completes.
        assertTrue(completes(events) != null)
    }

    @Test
    fun `new frame evicts oldest slot when full`() {
        val r = FrameReassembler(maxSlots = 2, clockMs = { nowMs })
        r.offer(packet(10, 0, 2, 100))
        r.offer(packet(11, 0, 2, 102))
        val events = r.offer(packet(12, 0, 2, 104))

        val dropped = events.filterIsInstance<FrameReassembler.OfferResult.FrameDropped>()
        assertEquals(1, dropped.size)
        assertEquals(10L, dropped[0].frameId)
        assertEquals(2, r.pendingFrames())
    }

    @Test
    fun `sequence gaps are counted as loss`() {
        val r = reassembler()
        r.offer(packet(20, 0, 1, 200))
        r.offer(packet(21, 0, 1, 205))

        // seq jumped 200 -> 205: packets 201..204 missing.
        assertEquals(4L, r.stats.packetsLost)
        assertEquals(2L, r.stats.framesCompleted)
    }

    @Test
    fun `foreign datagrams do not disturb reassembly`() {
        val r = reassembler()
        r.offer(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(1L, r.stats.datagramsMalformed)
        val done = completes(r.offer(packet(30, 0, 1, 300)))

        assertTrue(done != null)
        assertEquals(1L, r.stats.framesCompleted)
    }

    @Test
    fun `fec parity packets recover lost data packets`() {
        val r = reassembler()
        val dataCount = 4
        val parityCount = 2
        val blockSize = 100

        // Create 4 data chunks of 50 bytes each
        val rawChunks = Array(dataCount) { i ->
            ByteArray(50) { b -> (i * 10 + b).toByte() }
        }

        // Each data shard has 2-byte LE length prefix (50 = 0x0032), padded to blockSize
        val encodedDataShards = Array(dataCount) { i ->
            val shard = ByteArray(blockSize)
            shard[0] = 50.toByte()
            shard[1] = 0
            rawChunks[i].copyInto(shard, 2)
            shard
        }

        val parityShards = Array(parityCount) { ByteArray(blockSize) }
        ReedSolomonDecoder.encode(encodedDataShards, parityShards, blockSize)

        // Frame packets:
        // Index 0: Data shard 0
        // Index 1: Data shard 1 (DROPPED!)
        // Index 2: Data shard 2
        // Index 3: Data shard 3
        // Index 4: Parity shard 0 (RECEIVED!)
        // Index 5: Parity shard 1 (IGNORED / NOT NEEDED)

        val p0 = UdpVideoPacket.encode(
            UdpVideoPacket.Header(
                frameId = 50,
                seq = 500,
                packetIndex = 0,
                packetCount = dataCount,
                ptsUs = 50000L,
                flags = UdpVideoPacket.FLAG_START,
                parityCount = parityCount
            ),
            encodedDataShards[0]
        )
        val p2 = UdpVideoPacket.encode(
            UdpVideoPacket.Header(
                frameId = 50,
                seq = 502,
                packetIndex = 2,
                packetCount = dataCount,
                ptsUs = 50000L,
                flags = 0,
                parityCount = parityCount
            ),
            encodedDataShards[2]
        )
        val p3 = UdpVideoPacket.encode(
            UdpVideoPacket.Header(
                frameId = 50,
                seq = 503,
                packetIndex = 3,
                packetCount = dataCount,
                ptsUs = 50000L,
                flags = UdpVideoPacket.FLAG_END,
                parityCount = parityCount
            ),
            encodedDataShards[3]
        )
        val pFec0 = UdpVideoPacket.encode(
            UdpVideoPacket.Header(
                frameId = 50,
                seq = 504,
                packetIndex = 4,
                packetCount = dataCount,
                ptsUs = 50000L,
                flags = UdpVideoPacket.FLAG_FEC,
                parityCount = parityCount
            ),
            parityShards[0]
        )

        // Offer 0, 2, 3 (data missing packet 1) -> Frame incomplete
        assertTrue(completes(r.offer(p0)) == null)
        assertTrue(completes(r.offer(p2)) == null)
        assertTrue(completes(r.offer(p3)) == null)

        // Now offer pFec0 -> Total packets = 4 == dataCount!
        val done = completes(r.offer(pFec0))
        assertTrue("Frame must be completed via FEC reconstruction", done != null)
        assertEquals(50L, done!!.frameId)
        assertEquals(1L, r.stats.framesFecRecovered)
        assertEquals(1L, r.stats.framesCompleted)

        // Verify assembled frame content matches concatenation of rawChunks 0, 1, 2, 3
        val expected = ByteArray(200)
        for (i in 0 until dataCount) {
            rawChunks[i].copyInto(expected, i * 50)
        }
        assertEquals(expected.toList(), done.data.toList())
    }
}
