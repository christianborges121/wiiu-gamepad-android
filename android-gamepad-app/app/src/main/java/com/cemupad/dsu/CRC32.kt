package com.cemupad.dsu

import java.util.zip.CRC32 as JavaCRC32

/**
 * Utility for computing and verifying CRC32 checksums for DSU (CemuHook) packets.
 *
 * DSU uses standard IEEE 802.3 CRC32 (polynomial 0xEDB88320).
 * Checksum is calculated over the entire message with the CRC field (bytes 8..11) zeroed out.
 */
object CRC32 {
    /**
     * Calculates the CRC32 checksum of the provided buffer.
     * Bytes [crcOffset] until [crcOffset + 4] are treated as 0 for computation.
     */
    fun compute(buffer: ByteArray, length: Int = buffer.size, crcOffset: Int = 8): Long {
        val crc = JavaCRC32()
        if (crcOffset in 0 until length) {
            // Update bytes before CRC field
            if (crcOffset > 0) {
                crc.update(buffer, 0, crcOffset)
            }
            // Update 4 zero bytes in place of the CRC field
            crc.update(byteArrayOf(0, 0, 0, 0), 0, 4)
            // Update remaining bytes after CRC field
            val remainderOffset = crcOffset + 4
            if (remainderOffset < length) {
                crc.update(buffer, remainderOffset, length - remainderOffset)
            }
        } else {
            crc.update(buffer, 0, length)
        }
        return crc.value
    }

    /**
     * Computes the CRC32 and writes it directly into bytes [crcOffset..crcOffset+3] of [buffer] (little-endian).
     */
    fun finalizePacket(buffer: ByteArray, length: Int = buffer.size, crcOffset: Int = 8) {
        val crcValue = compute(buffer, length, crcOffset)
        buffer[crcOffset] = (crcValue and 0xFF).toByte()
        buffer[crcOffset + 1] = ((crcValue shr 8) and 0xFF).toByte()
        buffer[crcOffset + 2] = ((crcValue shr 16) and 0xFF).toByte()
        buffer[crcOffset + 3] = ((crcValue shr 24) and 0xFF).toByte()
    }

    /**
     * Verifies that the packet's embedded CRC32 matches the computed CRC32.
     */
    fun verify(buffer: ByteArray, length: Int = buffer.size, crcOffset: Int = 8): Boolean {
        if (length < crcOffset + 4) return false
        val expectedCrc = (buffer[crcOffset].toLong() and 0xFF) or
                ((buffer[crcOffset + 1].toLong() and 0xFF) shl 8) or
                ((buffer[crcOffset + 2].toLong() and 0xFF) shl 16) or
                ((buffer[crcOffset + 3].toLong() and 0xFF) shl 24)
        val computedCrc = compute(buffer, length, crcOffset)
        return expectedCrc == computedCrc
    }
}
