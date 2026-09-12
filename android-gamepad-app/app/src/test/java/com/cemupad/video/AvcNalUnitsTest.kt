package com.cemupad.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvcNalUnitsTest {

    private fun annexB(vararg nalTypes: Int, fourByteStart: Boolean = true): ByteArray {
        val out = mutableListOf<Byte>()
        for (type in nalTypes) {
            if (fourByteStart) {
                out.addAll(listOf(0, 0, 0, 1).map { it.toByte() })
            } else {
                out.addAll(listOf(0, 0, 1).map { it.toByte() })
            }
            // Fake NAL header byte: forbidden_zero(0) | nal_ref_idc(11) | type.
            out.add((0x60 or type).toByte())
            out.addAll(listOf(0x11, 0x22).map { it.toByte() })
        }
        return out.toByteArray()
    }

    @Test
    fun `parses sps pps and idr with four byte start codes`() {
        val units = AvcNalUnits.parseAnnexB(
            annexB(AvcNalUnits.TYPE_SPS, AvcNalUnits.TYPE_PPS, AvcNalUnits.TYPE_IDR)
        )

        assertEquals(listOf(7, 8, 5), units.map { it.type })
        val params = AvcNalUnits.describe(units)
        assertTrue(params.hasSps)
        assertTrue(params.hasPps)
        assertTrue(params.hasIdr)
        assertTrue(params.hasParameterSets)
    }

    @Test
    fun `parses three byte start codes`() {
        val units = AvcNalUnits.parseAnnexB(
            annexB(AvcNalUnits.TYPE_NON_IDR, fourByteStart = false)
        )

        assertEquals(1, units.size)
        assertEquals(AvcNalUnits.TYPE_NON_IDR, units[0].type)
        val params = AvcNalUnits.describe(units)
        assertFalse(params.hasParameterSets)
        assertFalse(params.hasIdr)
    }

    @Test
    fun `mixed start code lengths stay in sync`() {
        val payload = annexB(AvcNalUnits.TYPE_SPS) +
            annexB(AvcNalUnits.TYPE_PPS, fourByteStart = false) +
            annexB(AvcNalUnits.TYPE_NON_IDR)

        val units = AvcNalUnits.parseAnnexB(payload)

        assertEquals(listOf(7, 8, 1), units.map { it.type })
    }

    @Test
    fun `empty and garbage payloads yield no units`() {
        assertTrue(AvcNalUnits.parseAnnexB(ByteArray(0)).isEmpty())
        assertTrue(AvcNalUnits.parseAnnexB(byteArrayOf(0x11, 0x22, 0x33)).isEmpty())
        // Lone start code with no header byte is not a unit.
        assertTrue(AvcNalUnits.parseAnnexB(byteArrayOf(0, 0, 0, 1)).isEmpty())
    }

    @Test
    fun `trailing zero padding is not a unit`() {
        val payload = annexB(AvcNalUnits.TYPE_IDR) + byteArrayOf(0, 0, 0)

        val units = AvcNalUnits.parseAnnexB(payload)

        assertEquals(1, units.size)
        assertEquals(AvcNalUnits.TYPE_IDR, units[0].type)
    }
}
