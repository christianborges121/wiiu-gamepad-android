package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class UdpVideoPacketTest {

    private fun header(index: Int = 0, count: Int = 2) = UdpVideoPacket.Header(
        frameId = 7L,
        seq = 100L + index,
        packetIndex = index,
        packetCount = count,
        ptsUs = 33333L,
        flags = UdpVideoPacket.FLAG_START or UdpVideoPacket.FLAG_IDR
    )

    @Test
    fun `round trip preserves header and payload`() {
        val payload = byteArrayOf(0, 0, 0, 1, 0x65.toByte(), 0x11, 0x22)
        val datagram = UdpVideoPacket.encode(header(), payload)

        val decoded = UdpVideoPacket.decode(datagram)

        assertTrue(decoded != null)
        decoded!!
        assertEquals(7L, decoded.header.frameId)
        assertEquals(100L, decoded.header.seq)
        assertEquals(0, decoded.header.packetIndex)
        assertEquals(2, decoded.header.packetCount)
        assertEquals(33333L, decoded.header.ptsUs)
        assertTrue(decoded.header.isStart)
        assertTrue(decoded.header.isIdr)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun `rejects bad magic version and truncation`() {
        val good = UdpVideoPacket.encode(header(), byteArrayOf(1, 2, 3))
        assertNull(UdpVideoPacket.decode(byteArrayOf(1, 2, 3, 4)))
        assertNull(UdpVideoPacket.decode(ByteArray(10)))

        val badMagic = good.copyOf()
        badMagic[0] = 0x00
        assertNull(UdpVideoPacket.decode(badMagic))

        val badVersion = good.copyOf()
        badVersion[2] = 0x7F
        assertNull(UdpVideoPacket.decode(badVersion))

        val truncated = good.copyOfRange(0, UdpVideoPacket.HEADER_SIZE - 1)
        assertNull(UdpVideoPacket.decode(truncated))
    }

    @Test
    fun `rejects impossible packet geometry`() {
        val badCount = header(index = 0, count = 0)
        assertNull(UdpVideoPacket.decode(UdpVideoPacket.encode(badCount, byteArrayOf(1))))
        val badIndex = header(index = 5, count = 2)
        assertNull(UdpVideoPacket.decode(UdpVideoPacket.encode(badIndex, byteArrayOf(1))))
    }

    @Test
    fun `header size leaves room under mtu`() {
        assertTrue(UdpVideoPacket.HEADER_SIZE + UdpVideoPacket.MAX_PAYLOAD <= 1500)
    }
}
