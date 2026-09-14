package com.cemupad.video

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * UDP video datagram codec (protocol v1, see
 * investigation/2026-09-12-video/03-udp-protocol.md).
 *
 * All multi-byte fields are little-endian to match the TCP framing.
 */
object UdpVideoPacket {
    const val MAGIC = 0x5043
    const val VERSION = 1
    const val HEADER_SIZE = 24
    const val MAX_PAYLOAD = 1400

    const val FLAG_START = 0x01
    const val FLAG_END = 0x02
    const val FLAG_IDR = 0x04
    const val FLAG_FEC = 0x08

    data class Header(
        val frameId: Long,
        val seq: Long,
        val packetIndex: Int,
        val packetCount: Int,
        val ptsUs: Long,
        val flags: Int,
        val parityCount: Int = 0
    ) {
        val isStart: Boolean get() = (flags and FLAG_START) != 0
        val isEnd: Boolean get() = (flags and FLAG_END) != 0
        val isIdr: Boolean get() = (flags and FLAG_IDR) != 0
        val isFec: Boolean get() = (flags and FLAG_FEC) != 0
        val totalCount: Int get() = packetCount + parityCount
    }

    data class Decoded(val header: Header, val payload: ByteArray)

    fun encode(header: Header, payload: ByteArray, payloadOffset: Int = 0, payloadSize: Int = payload.size - payloadOffset): ByteArray {
        require(payloadSize >= 0 && payloadSize <= MAX_PAYLOAD)
        require(payloadOffset >= 0 && payloadOffset + payloadSize <= payload.size)
        val buf = ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(MAGIC.toShort())
        buf.put(VERSION.toByte())
        buf.put(header.flags.toByte())
        buf.putInt(header.frameId.toInt())
        buf.putInt(header.seq.toInt())
        buf.putShort(header.packetIndex.toShort())
        buf.put(header.packetCount.toByte())
        buf.put(header.parityCount.toByte())
        buf.putLong(header.ptsUs)
        buf.put(payload, payloadOffset, payloadSize)
        return buf.array()
    }

    /** Returns null for short, corrupt, or foreign datagrams. */
    fun decode(datagram: ByteArray, length: Int = datagram.size): Decoded? {
        if (length < HEADER_SIZE || length > datagram.size) return null
        val buf = ByteBuffer.wrap(datagram, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        if ((buf.short.toInt() and 0xFFFF) != MAGIC) return null
        if ((buf.get().toInt() and 0xFF) != VERSION) return null
        val flags = buf.get().toInt() and 0xFF
        val frameId = buf.int.toLong() and 0xFFFFFFFFL
        val seq = buf.int.toLong() and 0xFFFFFFFFL
        val packetIndex = buf.short.toInt() and 0xFFFF
        val packetCount = buf.get().toInt() and 0xFF
        val parityCount = buf.get().toInt() and 0xFF
        val ptsUs = buf.long
        val totalCount = packetCount + parityCount
        if (packetCount <= 0) return null
        if (packetIndex >= totalCount) return null
        val payloadSize = length - HEADER_SIZE
        if (payloadSize <= 0 || payloadSize > MAX_PAYLOAD) return null
        val payload = datagram.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadSize)
        return Decoded(
            Header(
                frameId = frameId,
                seq = seq,
                packetIndex = packetIndex,
                packetCount = packetCount,
                ptsUs = ptsUs,
                flags = flags,
                parityCount = parityCount
            ),
            payload
        )
    }
}
