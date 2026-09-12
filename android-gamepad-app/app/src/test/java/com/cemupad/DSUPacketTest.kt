package com.cemupad

import com.cemupad.dsu.CRC32
import com.cemupad.dsu.DSUPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DSUPacketTest {

    @Test
    fun testStandardCRC32Calculation() {
        val testData = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = CRC32.compute(testData, testData.size, crcOffset = -1)
        assertEquals(0xCBF43926L, crc)
    }

    @Test
    fun testVersionResponseStructure() {
        val serverUid = 0x12345678
        val packet = DSUPacket.createVersionResponse(serverUid)

        assertEquals(DSUPacket.VERSION_RESPONSE_SIZE, packet.size)
        assertTrue("CRC32 validation must pass", CRC32.verify(packet))

        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(DSUPacket.MAGIC_SERVER, buf.int)
        assertEquals(DSUPacket.PROTOCOL_VERSION, buf.short)
        assertEquals((DSUPacket.VERSION_RESPONSE_SIZE - DSUPacket.HEADER_SIZE).toShort(), buf.short) // length = 8
        val crc = buf.int
        assertTrue("CRC field must be non-zero", crc != 0)
        assertEquals(serverUid, buf.int)
        assertEquals(DSUPacket.TYPE_VERSION, buf.int)
        assertEquals(DSUPacket.PROTOCOL_VERSION, buf.short)
    }

    @Test
    fun testPortInfoResponseByte11IsZeroPadding() {
        val serverUid = 0x55AA55AA
        val packet = DSUPacket.createPortInfoResponse(serverUid)

        assertEquals(DSUPacket.PORT_INFO_RESPONSE_SIZE, packet.size)
        assertTrue(CRC32.verify(packet))

        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(16) // skip header
        assertEquals(DSUPacket.TYPE_PORT_INFO, buf.int)

        assertEquals(0x00.toByte(), buf.get()) // slot index
        assertEquals(DSUPacket.STATE_CONNECTED, buf.get()) // slot state
        assertEquals(DSUPacket.MODEL_FULL_GYRO, buf.get()) // model
        assertEquals(DSUPacket.CONNECTION_BLUETOOTH, buf.get()) // connection

        // MAC address (6 bytes)
        val mac = ByteArray(6)
        buf.get(mac)

        assertEquals(DSUPacket.BATTERY_CHARGING, buf.get())

        // Protocol bug verification: Standalone PortInfo byte 31 MUST be 0x00 (padding)
        val byte31 = buf.get()
        assertEquals("Byte 31 on standalone PortInfo must be 0x00 padding", 0x00.toByte(), byte31)
    }

    @Test
    fun testDataResponseStructureAndValues() {
        val serverUid = 0x01020304
        val state = DSUPacket.ControllerState(
            state1 = DSUPacket.State1Flags.DPAD_UP or DSUPacket.State1Flags.OPTIONS_PLUS,
            state2 = DSUPacket.State2Flags.CROSS_A or DSUPacket.State2Flags.ZR,
            psHome = true,
            touchButton = true,
            lx = 200,
            ly = 255, // Inverted: 255 = UP
            rx = 50,
            ry = 0,   // Inverted: 0 = DOWN
            l2Analog = 120,
            r2Analog = 255,
            touch1 = DSUPacket.TouchPointData(active = true, id = 1, x = 1920, y = 942),
            motionTimestampUs = 9876543210L,
            accelX = 0.05f,
            accelY = 0.98f, // in g's
            accelZ = -0.10f,
            gyroPitch = 12.5f, // deg/s
            gyroYaw = -45.0f,
            gyroRoll = 0.5f
        )

        val packet = DSUPacket.createDataResponse(
            serverUid = serverUid,
            packetCounter = 42,
            state = state
        )

        assertEquals(DSUPacket.DATA_RESPONSE_SIZE, packet.size)
        assertTrue("CRC32 validation must pass", CRC32.verify(packet))

        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(20) // skip 16-byte header + 4-byte message type

        // PortInfoData
        assertEquals(0x00.toByte(), buf.get()) // slot
        assertEquals(DSUPacket.STATE_CONNECTED, buf.get())
        assertEquals(DSUPacket.MODEL_FULL_GYRO, buf.get())
        assertEquals(DSUPacket.CONNECTION_BLUETOOTH, buf.get())
        buf.position(buf.position() + 6) // skip MAC
        assertEquals(DSUPacket.BATTERY_CHARGING, buf.get())

        // In DataResponse, byte 31 is is_connected (0x01)
        val isConnectedByte = buf.get()
        assertEquals("Byte 31 in DataResponse must be 0x01", 0x01.toByte(), isConnectedByte)

        // DataResponseData
        assertEquals(42, buf.int) // packet counter
        assertEquals(state.state1.toByte(), buf.get())
        assertEquals(state.state2.toByte(), buf.get())
        assertEquals(0x01.toByte(), buf.get()) // PS
        assertEquals(0x01.toByte(), buf.get()) // Touch button

        // Sticks
        assertEquals(200.toByte(), buf.get()) // lx
        assertEquals(255.toByte(), buf.get()) // ly (255 = UP)
        assertEquals(50.toByte(), buf.get())  // rx
        assertEquals(0.toByte(), buf.get())   // ry (0 = DOWN)

        // Skip analog buttons to reach touch1 (offset 56)
        buf.position(56)
        assertEquals(0x01.toByte(), buf.get()) // touch1 active
        assertEquals(1.toByte(), buf.get())    // touch1 id
        assertEquals(1920.toShort(), buf.short) // touch1 x
        assertEquals(942.toShort(), buf.short)  // touch1 y

        // Motion data (offset 68)
        buf.position(68)
        assertEquals(9876543210L, buf.long)
        assertEquals(0.05f, buf.float, 0.0001f)
        assertEquals(0.98f, buf.float, 0.0001f)
        assertEquals(-0.10f, buf.float, 0.0001f)
        assertEquals(12.5f, buf.float, 0.0001f)
        assertEquals(-45.0f, buf.float, 0.0001f)
        assertEquals(0.5f, buf.float, 0.0001f)
    }

    @Test
    fun testCorruptedPacketFailsCRC() {
        val packet = DSUPacket.createVersionResponse(0x1234)
        assertTrue(CRC32.verify(packet))

        // Corrupt single byte
        packet[20] = (packet[20] + 1).toByte()
        assertFalse("Corrupted packet must fail CRC32 verification", CRC32.verify(packet))
    }
}
