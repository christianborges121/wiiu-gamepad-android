package com.cemupad.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryResponderTest {

    @Test
    fun testProbeRecognition() {
        assertTrue(DiscoveryResponder.isProbePacket("CEMU_DISCOVER"))
        assertTrue(DiscoveryResponder.isProbePacket("CEMUPAD_DISCOVER"))
        assertTrue(DiscoveryResponder.isProbePacket("CEMU_DISCOVER\n"))
        assertFalse(DiscoveryResponder.isProbePacket("CEMUPAD_HERE:PC:26760:26761:26762"))
        assertFalse(DiscoveryResponder.isProbePacket(""))
        assertFalse(DiscoveryResponder.isProbePacket("HELLO"))
    }

    @Test
    fun testHereResponseFormat() {
        val response = DiscoveryResponder.buildHereResponse("Galaxy-S23-FE")
        assertEquals("CEMUPAD_HERE:Galaxy-S23-FE:26760:26761:26762", response)
    }

    @Test
    fun testDeviceNameSanitization() {
        assertEquals("Pixel-8", DiscoveryResponder.sanitizeDeviceName("Pixel:8"))
        assertEquals("My Phone", DiscoveryResponder.sanitizeDeviceName("My\nPhone\r"))
        assertEquals("CemuPad", DiscoveryResponder.sanitizeDeviceName(""))
        assertEquals("CemuPad", DiscoveryResponder.sanitizeDeviceName("   "))
        // Colons must never leak into the wire format (field separator)
        val response = DiscoveryResponder.buildHereResponse("A:B:C")
        assertEquals(5, response.split(":").size)
        assertEquals("CEMUPAD_HERE:A-B-C:26760:26761:26762", response)
    }

    @Test
    fun testPortConstants() {
        assertEquals(26763, DiscoveryResponder.DISCOVERY_PORT)
        assertEquals(DiscoveryClient.DISCOVERY_PORT, DiscoveryResponder.DISCOVERY_PORT)
        assertEquals(26760, DiscoveryResponder.DSU_PORT)
        assertEquals(26761, DiscoveryResponder.VIDEO_PORT)
        assertEquals(26762, DiscoveryResponder.AUDIO_PORT)
    }
}
