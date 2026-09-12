package com.cemupad.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDecoderTest {
    @Test
    fun normalizeAvcBitstream_convertsAvccToAnnexB() {
        val avcc = byteArrayOf(
            0x00, 0x00, 0x00, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05,
            0x00, 0x00, 0x00, 0x02, 0x06, 0x07,
            0x00, 0x00, 0x00, 0x00
        )

        val normalized = VideoDecoder.normalizeAvcBitstream(avcc)

        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x01, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x00, 0x00, 0x00, 0x01, 0x06, 0x07
            ),
            normalized
        )
    }

    @Test
    fun normalizeAvcBitstream_keepsAnnexBUnchanged() {
        val annexB = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, 0x41, 0x42,
            0x00, 0x00, 0x01, 0x43, 0x44
        )

        assertArrayEquals(annexB, VideoDecoder.normalizeAvcBitstream(annexB))
    }

    @Test
    fun normalizeAvcBitstream_handlesSingleNalWithoutPrefix() {
        val singleNal = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val normalized = VideoDecoder.normalizeAvcBitstream(singleNal)

        assertEquals(8, normalized.size)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x01, 0x02, 0x03, 0x04), normalized)
    }
}
