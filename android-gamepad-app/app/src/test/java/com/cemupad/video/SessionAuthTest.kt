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

    @Test
    fun testClientIsNotConnectedBeforeAuth() {
        val client = VideoStreamClient(
            host = "127.0.0.1",
            port = 65432,
            onFrameReceived = { _, _ -> }
        )
        assertFalse(client.isConnected)
    }

    @Test
    fun testAuthHandshakeSuccessTriggersCallback() {
        val server = java.net.ServerSocket(0)
        val port = server.localPort
        var authSucceeded = false
        var connected = false

        val client = VideoStreamClient(
            host = "127.0.0.1",
            port = port,
            onFrameReceived = { _, _ -> },
            getAuthCredential = { 42L },
            onAuthSucceeded = { authSucceeded = true }
        ).apply {
            onConnected = { connected = true }
        }

        val serverThread = Thread {
            try {
                val sock = server.accept()
                val inp = java.io.DataInputStream(sock.getInputStream())
                val out = java.io.DataOutputStream(sock.getOutputStream())
                val req = ByteArray(9)
                inp.readFully(req)
                val resp = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put(0x00.toByte())
                    putLong(9999L)
                }.array()
                out.write(resp)
                out.flush()
            } catch (_: Exception) {}
        }
        serverThread.start()

        client.start()
        val deadline = System.currentTimeMillis() + 3000L
        while (System.currentTimeMillis() < deadline && (!authSucceeded || !client.isConnected)) {
            Thread.sleep(50)
        }

        client.stop()
        server.close()
        serverThread.join(1000)

        assertTrue("onAuthSucceeded should have been called", authSucceeded)
        assertTrue("onConnected should have been called", connected)
    }

    @Test
    fun testCancelAuthAbortsImmediately() {
        val server = java.net.ServerSocket(0)
        val port = server.localPort
        var pinRequired = false

        val client = VideoStreamClient(
            host = "127.0.0.1",
            port = port,
            onFrameReceived = { _, _ -> },
            getAuthCredential = { 0L },
            onPinRequired = { pinRequired = true }
        )

        val serverThread = Thread {
            try {
                val sock = server.accept()
                val inp = java.io.DataInputStream(sock.getInputStream())
                val out = java.io.DataOutputStream(sock.getOutputStream())
                val req = ByteArray(9)
                inp.readFully(req)
                val resp = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put(0x01.toByte())
                    putLong(0L)
                }.array()
                out.write(resp)
                out.flush()
            } catch (_: Exception) {}
        }
        serverThread.start()

        client.start()
        val deadline = System.currentTimeMillis() + 3000L
        while (System.currentTimeMillis() < deadline && !pinRequired) {
            Thread.sleep(50)
        }
        assertTrue("PIN should have been requested", pinRequired)

        val startCancel = System.currentTimeMillis()
        client.cancelAuth()
        client.stop()
        val elapsed = System.currentTimeMillis() - startCancel

        server.close()
        serverThread.join(1000)

        assertTrue("cancelAuth should terminate worker promptly (took ${elapsed}ms)", elapsed < 2000)
    }
}
