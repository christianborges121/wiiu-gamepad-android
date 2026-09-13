package com.cemupad.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryStaleTest {

    @Test
    fun testFreshResponseNotStale() {
        assertFalse(DiscoveryClient.isStale(1000L, 2000L, 7000L))
    }

    @Test
    fun testBoundaryNotStale() {
        assertFalse(DiscoveryClient.isStale(0L, 7000L, 7000L))
    }

    @Test
    fun testMissedWindowsStale() {
        assertTrue(DiscoveryClient.isStale(0L, 7001L, 7000L))
        assertTrue(DiscoveryClient.isStale(1000L, 60000L, 7000L))
    }

    @Test
    fun testNeverSeenStale() {
        assertTrue(DiscoveryClient.isStale(0L, Long.MAX_VALUE / 2, 7000L))
    }

    @Test
    fun testPhoneRepliesIgnored() {
        assertTrue(
            DiscoveryClient.shouldIgnoreResponse(
                "192.168.68.114", setOf("192.168.68.107"), isPhone = true
            )
        )
    }

    @Test
    fun testOwnBroadcastLoopbackIgnored() {
        assertTrue(
            DiscoveryClient.shouldIgnoreResponse(
                "192.168.68.107", setOf("192.168.68.107"), isPhone = false
            )
        )
    }

    @Test
    fun testRealCemuAccepted() {
        assertFalse(
            DiscoveryClient.shouldIgnoreResponse(
                "192.168.68.92", setOf("192.168.68.107"), isPhone = false
            )
        )
    }

    @Test
    fun testEmptySenderIgnored() {
        assertFalse(
            DiscoveryClient.shouldIgnoreResponse(
                "192.168.68.92", emptySet(), isPhone = false
            )
        )
        assertTrue(DiscoveryClient.shouldIgnoreResponse("", emptySet(), isPhone = false))
    }
}
