package com.cemupad.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MicVoiceStreamerTest {

    @Test
    fun testVoicePacketHeader() {
        val samples = ShortArray(320) { it.toShort() }
        val packet = MicVoiceStreamer.buildVoicePacket(7, samples)

        assertEquals(MicVoiceStreamer.HEADER_SIZE + 320 * 2, packet.size)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(7, buf.int)
        assertEquals(320, buf.int)
    }

    @Test
    fun testVoicePacketSamplesLittleEndian() {
        val samples = shortArrayOf(0x0102, 0x0304.toShort(), (-2).toShort())
        val packet = MicVoiceStreamer.buildVoicePacket(0, samples)

        // Raw bytes after the 8-byte header must be little-endian int16
        assertEquals(0x02.toByte(), packet[8])
        assertEquals(0x01.toByte(), packet[9])
        assertEquals(0x04.toByte(), packet[10])
        assertEquals(0x03.toByte(), packet[11])

        val buf = ByteBuffer.wrap(packet, 8, 6).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0102.toShort(), buf.short)
        assertEquals(0x0304.toShort(), buf.short)
        assertEquals((-2).toShort(), buf.short)
    }

    @Test
    fun testVoicePacketEmpty() {
        val packet = MicVoiceStreamer.buildVoicePacket(0, ShortArray(0))
        assertEquals(MicVoiceStreamer.HEADER_SIZE, packet.size)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, buf.int)
        assertEquals(0, buf.int)
    }

    @Test
    fun testMicPortConstant() {
        assertEquals(26764, MicVoiceStreamer.MIC_PORT)
        assertEquals(32000, MicVoiceStreamer.SAMPLE_RATE)
        assertEquals(320, MicVoiceStreamer.SAMPLES_PER_CHUNK)
    }
}
