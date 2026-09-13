package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SessionAuthTest {

    @Test
    fun testAuthRequestBytes() {
        val packet = VideoStreamClient.buildAuthRequest(123456789012345L)
        assertEquals(9, packet.size)
        assertEquals(VideoStreamClient.OPCODE_AUTH_REQUEST, packet[0].toInt() and 0xFF)
        val credential = ByteBuffer.wrap(packet, 1, 8).order(ByteOrder.LITTLE_ENDIAN).long
        assertEquals(123456789012345L, credential)
    }

    @Test
    fun testAuthResponseSuccess() {
        val response = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x00.toByte())
            putLong(987654321L)
        }.array()

        val (ok, token) = VideoStreamClient.parseAuthResponse(response)!!
        assertTrue(ok)
        assertEquals(987654321L, token)
    }

    @Test
    fun testAuthResponseDenied() {
        val response = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x01.toByte())
            putLong(0L)
        }.array()

        val (ok, token) = VideoStreamClient.parseAuthResponse(response)!!
        assertFalse(ok)
        assertEquals(0L, token)
    }

    @Test
    fun testAuthResponseTooShort() {
        assertNull(VideoStreamClient.parseAuthResponse(ByteArray(0)))
        assertNull(VideoStreamClient.parseAuthResponse(ByteArray(8)))
    }
}
