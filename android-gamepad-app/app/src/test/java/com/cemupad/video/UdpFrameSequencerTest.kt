package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpFrameSequencerTest {

    private fun complete(
        frameId: Long,
        idr: Boolean = false
    ): FrameReassembler.OfferResult.FrameComplete {
        return FrameReassembler.OfferResult.FrameComplete(
            FrameReassembler.CompletedFrame(
                data = byteArrayOf(frameId.toByte()),
                ptsUs = frameId * 16666L,
                isIdr = idr,
                frameId = frameId
            )
        )
    }

    private fun dropped(frameId: Long, idr: Boolean = false) =
        FrameReassembler.OfferResult.FrameDropped(frameId, idr)

    private fun feeds(out: List<UdpFrameSequencer.Out>): List<Long> {
        return out.filterIsInstance<UdpFrameSequencer.Out.Feed>()
            .map { it.ptsUs / 16666L }
    }

    @Test
    fun `in order frames feed immediately`() {
        val s = UdpFrameSequencer()
        assertEquals(listOf(10L), feeds(s.onOffer(listOf(complete(10L)))))
        assertEquals(listOf(11L), feeds(s.onOffer(listOf(complete(11L)))))
    }

    @Test
    fun `gap holds newer frames until the missing one arrives`() {
        val s = UdpFrameSequencer()
        assertTrue(feeds(s.onOffer(listOf(complete(20L)))) == listOf(20L))
        // 21 missing: 22 held.
        assertTrue(s.onOffer(listOf(complete(22L))).isEmpty())
        // 21 arrives: both release in order.
        assertEquals(listOf(21L, 22L), feeds(s.onOffer(listOf(complete(21L)))))
    }

    @Test
    fun `lost frame triggers resync and resumes at idr`() {
        val s = UdpFrameSequencer()
        s.onOffer(listOf(complete(30L, idr = true)))
        s.onOffer(listOf(complete(32L))) // 31 missing, held
        val out = s.onOffer(listOf(dropped(31L)))

        assertTrue(out.any { it is UdpFrameSequencer.Out.NeedsIdr })
        // Held 32 was flushed, non-IDR 33 is dropped while syncing.
        assertTrue(s.onOffer(listOf(complete(33L))).isEmpty())
        // Next IDR resumes the chain.
        assertEquals(listOf(34L), feeds(s.onOffer(listOf(complete(34L, idr = true)))))
        assertEquals(listOf(35L), feeds(s.onOffer(listOf(complete(35L)))))
    }

    @Test
    fun `replayed frame ids are ignored`() {
        val s = UdpFrameSequencer()
        s.onOffer(listOf(complete(40L, idr = true)))
        s.onOffer(listOf(complete(41L)))
        assertTrue(s.onOffer(listOf(complete(41L))).isEmpty())
        assertTrue(s.onOffer(listOf(complete(40L))).isEmpty())
        assertEquals(listOf(42L), feeds(s.onOffer(listOf(complete(42L)))))
    }

    @Test
    fun `frame ids wrap around`() {
        val s = UdpFrameSequencer()
        val max = 0xFFFFFFFFL
        assertEquals(listOf(max - 1), feeds(s.onOffer(listOf(complete(max - 1, idr = true)))))
        assertEquals(listOf(max), feeds(s.onOffer(listOf(complete(max)))))
        assertEquals(listOf(0L), feeds(s.onOffer(listOf(complete(0L)))))
        assertEquals(listOf(1L), feeds(s.onOffer(listOf(complete(1L)))))
    }

    @Test
    fun `transport start resets to accept any frame`() {
        val s = UdpFrameSequencer()
        s.onOffer(listOf(complete(50L, idr = true)))
        s.onTransportStart()
        assertEquals(listOf(500L), feeds(s.onOffer(listOf(complete(500L)))))
    }
}
