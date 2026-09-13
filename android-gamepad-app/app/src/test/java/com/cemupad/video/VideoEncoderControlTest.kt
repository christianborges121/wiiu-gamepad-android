package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoEncoderControlTest {

    @Test
    fun testBitratePacketBytes() {
        val packet = VideoStreamClient.buildBitratePacket(6000000)
        assertEquals(5, packet.size)
        assertEquals(VideoStreamClient.OPCODE_SET_BITRATE, packet[0].toInt() and 0xFF)
        val bps = ByteBuffer.wrap(packet, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(6000000, bps)
    }

    @Test
    fun testResolutionPacketBytes() {
        val packet = VideoStreamClient.buildResolutionPacket(1280, 720)
        assertEquals(5, packet.size)
        assertEquals(VideoStreamClient.OPCODE_SET_RESOLUTION, packet[0].toInt() and 0xFF)
        val buf = ByteBuffer.wrap(packet, 1, 4).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1280, buf.short.toInt() and 0xFFFF)
        assertEquals(720, buf.short.toInt() and 0xFFFF)
    }

    @Test
    fun testNativePresetPacket() {
        val packet = VideoStreamClient.buildResolutionPacket(854, 480)
        val buf = ByteBuffer.wrap(packet, 1, 4).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(854, buf.short.toInt() and 0xFFFF)
        assertEquals(480, buf.short.toInt() and 0xFFFF)
    }
}
