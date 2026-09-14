package com.cemupad.config

import android.view.KeyEvent
import com.cemupad.input.ControllerProfile
import com.cemupad.video.VideoStreamClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InputMappingPushTest {

    @Test
    fun `push packet layout count and LE pairs`() {
        val entries = listOf(1 to 14, 2 to 13, 3 to 15)
        val pkt = VideoStreamClient.buildPushMappingsPacket(entries)
        assertEquals(1 + 1 + 3 * 8, pkt.size)
        assertEquals(VideoStreamClient.OPCODE_PUSH_MAPPINGS.toByte(), pkt[0])
        assertEquals(3.toByte(), pkt[1])
        val buf = ByteBuffer.wrap(pkt, 2, 8).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buf.int)
        assertEquals(14, buf.int)
    }

    @Test
    fun `set mapping packet layout`() {
        val pkt = VideoStreamClient.buildSetMappingPacket(5, 10, true)
        assertEquals(1 + 4 + 4 + 1, pkt.size)
        assertEquals(VideoStreamClient.OPCODE_SET_MAPPING.toByte(), pkt[0])
        val buf = ByteBuffer.wrap(pkt).order(ByteOrder.LITTLE_ENDIAN)
        buf.get() // opcode
        assertEquals(5, buf.int)
        assertEquals(10, buf.int)
        assertEquals(1.toByte(), buf.get())
    }

    @Test
    fun `profile to vpad entries default has 16 mappings`() {
        val entries = InputMappingCodec.toVpadEntries(ControllerProfile.DEFAULT)
        assertEquals(16, entries.size)
        // A->Cross(14), B->Circle(13)
        assertTrue(entries.contains(1 to 14))
        assertTrue(entries.contains(2 to 13))
    }

    @Test
    fun `nintendo layout swaps A B`() {
        val nintendo = ControllerProfile.DEFAULT.copy(keyA = KeyEvent.KEYCODE_BUTTON_B, keyB = KeyEvent.KEYCODE_BUTTON_A)
        val entries = InputMappingCodec.toVpadEntries(nintendo)
        assertTrue(entries.contains(1 to 13))
        assertTrue(entries.contains(2 to 14))
    }

    @Test
    fun `push packet rejects empty and too many`() {
        try {
            VideoStreamClient.buildPushMappingsPacket(emptyList())
            assertTrue("should throw", false)
        } catch (_: IllegalArgumentException) {
        }
        try {
            VideoStreamClient.buildPushMappingsPacket(List(33) { it to it })
            assertTrue("should throw", false)
        } catch (_: IllegalArgumentException) {
        }
    }
}
