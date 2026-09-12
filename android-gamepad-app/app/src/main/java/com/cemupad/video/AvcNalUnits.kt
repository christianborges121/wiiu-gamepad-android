package com.cemupad.video

/**
 * Minimal Annex B H.264 NAL unit scanner for stream-health checks.
 *
 * Splits a payload on 3-byte (`00 00 01`) and 4-byte (`00 00 00 01`)
 * start codes and reports each unit's type (`byteAfterStart & 0x1F`).
 * Emulation-prevention bytes (`00 00 03`) guarantee no false `00 00 01`
 * pattern can appear inside a unit, so a start-code scan is sufficient
 * for type detection without a full bitstream parser.
 */
object AvcNalUnits {
    const val TYPE_NON_IDR = 1
    const val TYPE_IDR = 5
    const val TYPE_SEI = 6
    const val TYPE_SPS = 7
    const val TYPE_PPS = 8

    data class NalUnit(val type: Int, val offset: Int, val size: Int)

    data class StreamParameters(
        val hasSps: Boolean,
        val hasPps: Boolean,
        val hasIdr: Boolean
    ) {
        val hasParameterSets: Boolean get() = hasSps && hasPps
    }

    fun parseAnnexB(data: ByteArray): List<NalUnit> {
        val units = mutableListOf<NalUnit>()
        var pos = 0
        while (pos < data.size) {
            val start = findStartCode(data, pos) ?: break
            val nalStart = start.first + start.second
            val next = findStartCode(data, nalStart)
            val end = next?.first ?: data.size
            // Trim trailing zero padding before the next start code.
            var nalEnd = end
            while (nalEnd > nalStart && data[nalEnd - 1] == 0.toByte()) {
                nalEnd--
            }
            if (nalEnd > nalStart) {
                units.add(
                    NalUnit(
                        type = data[nalStart].toInt() and 0x1F,
                        offset = nalStart,
                        size = nalEnd - nalStart
                    )
                )
            }
            pos = end
        }
        return units
    }

    fun describe(units: List<NalUnit>): StreamParameters {
        var sps = false
        var pps = false
        var idr = false
        for (unit in units) {
            when (unit.type) {
                TYPE_SPS -> sps = true
                TYPE_PPS -> pps = true
                TYPE_IDR -> idr = true
            }
        }
        return StreamParameters(hasSps = sps, hasPps = pps, hasIdr = idr)
    }

    /**
     * Returns (startCodeOffset, startCodeLength) for the first start code
     * at or after [from], or null when there is none. Prefers the 4-byte
     * form so `00 00 00 01` is consumed as one code.
     */
    private fun findStartCode(data: ByteArray, from: Int): Pair<Int, Int>? {
        var i = from
        while (i + 2 < data.size) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    return Pair(i, 3)
                }
                if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    return Pair(i, 4)
                }
            }
            i++
        }
        return null
    }
}
