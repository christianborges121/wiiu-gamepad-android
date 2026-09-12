package com.cemupad

import com.cemupad.dsu.CRC32
import com.cemupad.dsu.DSUPacket
import com.cemupad.dsu.DSUServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DSUServerIntegrationTest {

    private val testPort = 26768
    private val clientUid = 0x11223344

    private fun createClientPacket(messageType: Int, payloadSize: Int, writer: (ByteBuffer) -> Unit): ByteArray {
        val totalSize = DSUPacket.HEADER_SIZE + 4 + payloadSize
        val bytes = ByteArray(totalSize)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(DSUPacket.MAGIC_CLIENT) // 'CUSD'
        buf.putShort(DSUPacket.PROTOCOL_VERSION) // 1001
        buf.putShort((totalSize - DSUPacket.HEADER_SIZE).toShort()) // length
        buf.putInt(0) // CRC placeholder
        buf.putInt(clientUid)
        buf.putInt(messageType)
        writer(buf)

        CRC32.finalizePacket(bytes)
        return bytes
    }

    @Test
    fun testClientHeaderParsing() {
        val versionReq = createClientPacket(DSUPacket.TYPE_VERSION, 0) {}
        val parsed = DSUPacket.parseClientHeader(versionReq, versionReq.size)
        assertNotNull("parseClientHeader should not return null", parsed)
        assertEquals(clientUid, parsed!!.first)
        assertEquals(DSUPacket.TYPE_VERSION, parsed.second)
    }

    @Test
    fun testServerFullHandshakeAndDataExchange() {
        val server = DSUServer(port = testPort)
        assertTrue("DSUServer should bind successfully", server.start())
        Thread.sleep(100) // Give server receive loop thread time to enter receive()

        val clientSocket = DatagramSocket()
        clientSocket.soTimeout = 2000
        val localhost = InetAddress.getByName("127.0.0.1")

        try {
            val recvBuf = ByteArray(1024)
            val recvPkt = DatagramPacket(recvBuf, recvBuf.size)

            // 1. Send VersionRequest (messageType = 0x100000)
            val versionReq = createClientPacket(DSUPacket.TYPE_VERSION, 0) {}
            clientSocket.send(DatagramPacket(versionReq, versionReq.size, localhost, testPort))

            clientSocket.receive(recvPkt)
            assertEquals("VersionResponse should be 24 bytes", DSUPacket.VERSION_RESPONSE_SIZE, recvPkt.length)
            assertTrue("VersionResponse CRC32 must be valid", CRC32.verify(recvPkt.data, recvPkt.length))

            val vBuf = ByteBuffer.wrap(recvPkt.data, 0, recvPkt.length).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(DSUPacket.MAGIC_SERVER, vBuf.int)
            assertEquals(DSUPacket.PROTOCOL_VERSION, vBuf.short)
            assertEquals(8.toShort(), vBuf.short)
            vBuf.getInt() // skip crc
            vBuf.getInt() // skip server uid
            assertEquals(DSUPacket.TYPE_VERSION, vBuf.int)
            assertEquals(DSUPacket.PROTOCOL_VERSION, vBuf.short)

            // 2. Send ListPorts (messageType = 0x100001)
            // Payload: count (4 bytes = 1), indices (4 bytes: slot 0, 0, 0, 0)
            val listPortsReq = createClientPacket(DSUPacket.TYPE_PORT_INFO, 8) { buf ->
                buf.putInt(1)
                buf.put(0x00.toByte())
                buf.put(0x00.toByte())
                buf.put(0x00.toByte())
                buf.put(0x00.toByte())
            }
            recvPkt.length = recvBuf.size
            clientSocket.send(DatagramPacket(listPortsReq, listPortsReq.size, localhost, testPort))

            clientSocket.receive(recvPkt)
            assertEquals("PortInfo response should be 32 bytes", DSUPacket.PORT_INFO_RESPONSE_SIZE, recvPkt.length)
            assertTrue("PortInfo CRC32 must be valid", CRC32.verify(recvPkt.data, recvPkt.length))

            val pBuf = ByteBuffer.wrap(recvPkt.data, 0, recvPkt.length).order(ByteOrder.LITTLE_ENDIAN)
            pBuf.position(16) // skip header
            assertEquals(DSUPacket.TYPE_PORT_INFO, pBuf.int)
            assertEquals(0x00.toByte(), pBuf.get()) // slot 0
            assertEquals(DSUPacket.STATE_CONNECTED, pBuf.get())
            assertEquals(DSUPacket.MODEL_FULL_GYRO, pBuf.get())
            assertEquals(DSUPacket.CONNECTION_BLUETOOTH, pBuf.get())
            pBuf.position(pBuf.position() + 6) // skip MAC
            assertEquals(DSUPacket.BATTERY_CHARGING, pBuf.get())
            // Protocol Bug Check: Standalone PortInfo byte 31 MUST be 0x00
            assertEquals(0x00.toByte(), pBuf.get())

            // 3. Send DataRequest (messageType = 0x100002)
            // Payload: reg_flags (1 byte = 0), slot_index (1 byte = 0), mac_address (6 bytes = 0)
            val dataReq = createClientPacket(DSUPacket.TYPE_DATA, 8) { buf ->
                buf.put(0x00.toByte())
                buf.put(0x00.toByte())
                buf.put(ByteArray(6))
            }

            // Set some controller state before requesting data
            server.updateState { state ->
                state.state1 = DSUPacket.State1Flags.DPAD_UP
                state.state2 = DSUPacket.State2Flags.CROSS_A
                state.lx = 210
                state.ly = 240
            }

            recvPkt.length = recvBuf.size
            clientSocket.send(DatagramPacket(dataReq, dataReq.size, localhost, testPort))

            clientSocket.receive(recvPkt)
            assertEquals("DataResponse should be 100 bytes", DSUPacket.DATA_RESPONSE_SIZE, recvPkt.length)
            assertTrue("DataResponse CRC32 must be valid", CRC32.verify(recvPkt.data, recvPkt.length))

            val dBuf = ByteBuffer.wrap(recvPkt.data, 0, recvPkt.length).order(ByteOrder.LITTLE_ENDIAN)
            dBuf.position(20) // skip header + message type
            assertEquals(0x00.toByte(), dBuf.get()) // slot 0
            assertEquals(DSUPacket.STATE_CONNECTED, dBuf.get())
            assertEquals(DSUPacket.MODEL_FULL_GYRO, dBuf.get())
            assertEquals(DSUPacket.CONNECTION_BLUETOOTH, dBuf.get())
            dBuf.position(dBuf.position() + 6) // skip MAC
            assertEquals(DSUPacket.BATTERY_CHARGING, dBuf.get())
            // In DataResponse, byte 31 is is_connected (0x01)
            assertEquals(0x01.toByte(), dBuf.get())

            // DataResponseData
            dBuf.getInt() // packet index
            val s1 = dBuf.get().toInt()
            val s2 = dBuf.get().toInt()
            assertEquals("D-Pad UP should be set", DSUPacket.State1Flags.DPAD_UP, s1 and DSUPacket.State1Flags.DPAD_UP)
            assertEquals("Cross/A button should be set", DSUPacket.State2Flags.CROSS_A, s2 and DSUPacket.State2Flags.CROSS_A)
            dBuf.get() // ps
            dBuf.get() // touch
            assertEquals(210.toByte(), dBuf.get()) // lx
            assertEquals(240.toByte(), dBuf.get()) // ly

            // Verify that server registered the client
            assertEquals(1, server.activeClientCount)

        } finally {
            clientSocket.close()
            server.stop()
        }
    }
}
