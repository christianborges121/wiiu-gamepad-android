package com.cemupad.dsu

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Data structures and binary serialization for the DSU (CemuHook) protocol.
 * Conforms precisely to Cemu's native C++ implementation (DSUMessages.h).
 */
object DSUPacket {
    const val MAGIC_CLIENT = 0x43555344 // 'CUSD' little-endian ("DSUC")
    const val MAGIC_SERVER = 0x53555344 // 'SUSD' little-endian ("DSUS")
    const val PROTOCOL_VERSION: Short = 1001

    const val TYPE_VERSION = 0x00100000
    const val TYPE_PORT_INFO = 0x00100001
    const val TYPE_DATA = 0x00100002

    const val HEADER_SIZE = 16
    const val VERSION_RESPONSE_SIZE = 24
    const val PORT_INFO_RESPONSE_SIZE = 32
    const val DATA_RESPONSE_SIZE = 100

    // Device status constants
    const val STATE_DISCONNECTED: Byte = 0x00
    const val STATE_RESERVED: Byte = 0x01
    const val STATE_CONNECTED: Byte = 0x02

    const val MODEL_NONE: Byte = 0x00
    const val MODEL_NO_GYRO: Byte = 0x01
    const val MODEL_FULL_GYRO: Byte = 0x02

    const val CONNECTION_NONE: Byte = 0x00
    const val CONNECTION_USB: Byte = 0x01
    const val CONNECTION_BLUETOOTH: Byte = 0x02

    const val BATTERY_NONE: Byte = 0x00
    const val BATTERY_DYING: Byte = 0x01
    const val BATTERY_LOW: Byte = 0x02
    const val BATTERY_MEDIUM: Byte = 0x03
    const val BATTERY_HIGH: Byte = 0x04
    const val BATTERY_FULL: Byte = 0x05
    const val BATTERY_CHARGING: Byte = -0x12 // 0xEE

    /**
     * D-Pad and generic button bitmask flags for state1 (byte 36).
     */
    object State1Flags {
        const val SHARE_MINUS = 0x01
        const val L3 = 0x02
        const val R3 = 0x04
        const val OPTIONS_PLUS = 0x08
        const val DPAD_UP = 0x10
        const val DPAD_RIGHT = 0x20
        const val DPAD_DOWN = 0x40
        const val DPAD_LEFT = 0x80
    }

    /**
     * Face and trigger button bitmask flags for state2 (byte 37).
     * Aligns with Cemu's DSUController bit index mapping:
     * Bit 0: ZL, Bit 1: ZR, Bit 2: L, Bit 3: R,
     * Bit 4: Triangle (Y), Bit 5: Circle (B), Bit 6: Cross (A), Bit 7: Square (X).
     */
    object State2Flags {
        const val ZL = 0x01
        const val ZR = 0x02
        const val L = 0x04
        const val R = 0x08
        const val TRIANGLE_Y = 0x10
        const val CIRCLE_B = 0x20
        const val CROSS_A = 0x40
        const val SQUARE_X = 0x80
    }

    data class TouchPointData(
        val active: Boolean = false,
        val id: Byte = 0,
        val x: Short = 0,
        val y: Short = 0
    )

    data class ControllerState(
        var state1: Int = 0,
        var state2: Int = 0,
        var psHome: Boolean = false,
        var touchButton: Boolean = false,

        // Analog Sticks: 0..255 (128 center).
        // Y axes: 255 = UP, 0 = DOWN (inverted by DSU convention)
        var lx: Int = 128,
        var ly: Int = 128,
        var rx: Int = 128,
        var ry: Int = 128,

        // Analog Triggers: 0..255
        var l2Analog: Int = 0,
        var r2Analog: Int = 0,

        // Touch points
        var touch1: TouchPointData = TouchPointData(),
        var touch2: TouchPointData = TouchPointData(),

        // Motion data
        var motionTimestampUs: Long = 0L,
        var accelX: Float = 0.0f, // in g's
        var accelY: Float = 0.0f, // in g's
        var accelZ: Float = 0.0f, // in g's
        var gyroPitch: Float = 0.0f, // in deg/s
        var gyroYaw: Float = 0.0f,   // in deg/s
        var gyroRoll: Float = 0.0f   // in deg/s
    )

    /**
     * Inspects an incoming client packet to extract client UID and requested message type.
     */
    fun parseClientHeader(buffer: ByteArray, length: Int): Pair<Int, Int>? {
        if (length < 20) return null
        val byteBuf = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val magic = byteBuf.int
        if (magic != MAGIC_CLIENT) return null
        val version = byteBuf.short
        if (version != PROTOCOL_VERSION) return null
        val packetSize = byteBuf.short.toInt() and 0xFFFF
        if (length < HEADER_SIZE + packetSize) return null

        if (!CRC32.verify(buffer, length)) return null

        byteBuf.int // skip CRC field (bytes 8..11)
        val clientUid = byteBuf.int // bytes 12..15
        val messageType = byteBuf.int // bytes 16..19
        return Pair(clientUid, messageType)
    }

    /**
     * Writes the standard 16-byte DSU message header into [byteBuf].
     */
    private fun writeHeader(byteBuf: ByteBuffer, totalPacketSize: Int, serverUid: Int) {
        byteBuf.putInt(MAGIC_SERVER)
        byteBuf.putShort(PROTOCOL_VERSION)
        byteBuf.putShort((totalPacketSize - HEADER_SIZE).toShort())
        byteBuf.putInt(0) // CRC field placeholder (zeroed)
        byteBuf.putInt(serverUid)
    }

    /**
     * Constructs a VersionResponse packet (24 bytes).
     */
    fun createVersionResponse(serverUid: Int): ByteArray {
        val bytes = ByteArray(VERSION_RESPONSE_SIZE)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(buf, VERSION_RESPONSE_SIZE, serverUid)
        buf.putInt(TYPE_VERSION)
        buf.putShort(PROTOCOL_VERSION)
        buf.putShort(0) // 2 bytes padding
        CRC32.finalizePacket(bytes)
        return bytes
    }

    /**
     * Constructs a PortInfo response packet (32 bytes) for a standalone ListPorts inquiry.
     * Note: byte 31 is explicitly 0x00 (padding).
     */
    fun createPortInfoResponse(
        serverUid: Int,
        slotIndex: Byte = 0x00,
        macAddress: ByteArray = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55),
        battery: Byte = BATTERY_CHARGING
    ): ByteArray {
        val bytes = ByteArray(PORT_INFO_RESPONSE_SIZE)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(buf, PORT_INFO_RESPONSE_SIZE, serverUid)
        buf.putInt(TYPE_PORT_INFO)

        buf.put(slotIndex)
        buf.put(STATE_CONNECTED)
        buf.put(MODEL_FULL_GYRO)
        buf.put(CONNECTION_BLUETOOTH)
        buf.put(macAddress, 0, 6)
        buf.put(battery)
        buf.put(0x00.toByte()) // is_active = 0 (padding in standalone PortInfo)

        CRC32.finalizePacket(bytes)
        return bytes
    }

    /**
     * Constructs a full DataResponse packet (100 bytes) containing button, stick, touch, and motion states.
     */
    fun createDataResponse(
        serverUid: Int,
        packetCounter: Int,
        state: ControllerState,
        slotIndex: Byte = 0x00,
        macAddress: ByteArray = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55),
        battery: Byte = BATTERY_CHARGING
    ): ByteArray {
        val bytes = ByteArray(DATA_RESPONSE_SIZE)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(buf, DATA_RESPONSE_SIZE, serverUid)
        buf.putInt(TYPE_DATA)

        // PortInfoData (12 bytes)
        buf.put(slotIndex)
        buf.put(STATE_CONNECTED)
        buf.put(MODEL_FULL_GYRO)
        buf.put(CONNECTION_BLUETOOTH)
        buf.put(macAddress, 0, 6)
        buf.put(battery)
        buf.put(0x01.toByte()) // is_connected = 0x01

        // DataResponseData (68 bytes)
        buf.putInt(packetCounter)

        // Digital states & buttons
        buf.put((state.state1 and 0xFF).toByte())
        buf.put((state.state2 and 0xFF).toByte())
        buf.put(if (state.psHome) 0x01.toByte() else 0x00.toByte())
        buf.put(if (state.touchButton) 0x01.toByte() else 0x00.toByte())

        // Joysticks (0..255)
        buf.put((state.lx.coerceIn(0, 255)).toByte())
        buf.put((state.ly.coerceIn(0, 255)).toByte())
        buf.put((state.rx.coerceIn(0, 255)).toByte())
        buf.put((state.ry.coerceIn(0, 255)).toByte())

        // Analog D-Pad (0 or 255)
        buf.put(if ((state.state1 and State1Flags.DPAD_LEFT) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state1 and State1Flags.DPAD_DOWN) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state1 and State1Flags.DPAD_RIGHT) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state1 and State1Flags.DPAD_UP) != 0) (-1).toByte() else 0.toByte())

        // Analog Face Buttons (Square, Cross, Circle, Triangle)
        buf.put(if ((state.state2 and State2Flags.SQUARE_X) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state2 and State2Flags.CROSS_A) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state2 and State2Flags.CIRCLE_B) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state2 and State2Flags.TRIANGLE_Y) != 0) (-1).toByte() else 0.toByte())

        // Analog R1, L1, R2, L2
        buf.put(if ((state.state2 and State2Flags.R) != 0) (-1).toByte() else 0.toByte())
        buf.put(if ((state.state2 and State2Flags.L) != 0) (-1).toByte() else 0.toByte())
        buf.put((state.r2Analog.coerceIn(0, 255)).toByte())
        buf.put((state.l2Analog.coerceIn(0, 255)).toByte())

        // Touchpad 1 (6 bytes)
        buf.put(if (state.touch1.active) 0x01.toByte() else 0x00.toByte())
        buf.put(state.touch1.id)
        buf.putShort(state.touch1.x)
        buf.putShort(state.touch1.y)

        // Touchpad 2 (6 bytes)
        buf.put(if (state.touch2.active) 0x01.toByte() else 0x00.toByte())
        buf.put(state.touch2.id)
        buf.putShort(state.touch2.x)
        buf.putShort(state.touch2.y)

        // Motion: timestamp (microseconds), accel (g's), gyro (deg/s)
        buf.putLong(state.motionTimestampUs)
        buf.putFloat(state.accelX)
        buf.putFloat(state.accelY)
        buf.putFloat(state.accelZ)
        buf.putFloat(state.gyroPitch)
        buf.putFloat(state.gyroYaw)
        buf.putFloat(state.gyroRoll)

        CRC32.finalizePacket(bytes)
        return bytes
    }
}
