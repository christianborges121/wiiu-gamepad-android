package com.cemupad.dsu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DSUServerLifecycleTest {

    private fun waitFor(condition: () -> Boolean, timeoutMs: Long = 3000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    @Test
    fun `start stop start keeps worker liveness accurate`() {
        // Ephemeral-adjacent high port to avoid clashing with a live server.
        val server = DSUServer(port = 26999)
        try {
            assertTrue(server.start())
            assertTrue(waitFor({ server.areWorkersAlive() }))

            server.stop()
            assertTrue(waitFor({ !server.areWorkersAlive() }))

            assertTrue(server.start())
            assertTrue(waitFor({ server.areWorkersAlive() }))
        } finally {
            server.stop()
        }
        assertTrue(waitFor({ !server.areWorkersAlive() }))
    }

    @Test
    fun `ensureThreads is a no-op when healthy and idle when stopped`() {
        val server = DSUServer(port = 26998)
        try {
            assertTrue(server.start())
            assertTrue(waitFor({ server.areWorkersAlive() }))
            assertFalse(server.ensureThreads())

            server.stop()
            assertTrue(waitFor({ !server.areWorkersAlive() }))
            assertFalse(server.ensureThreads())
        } finally {
            server.stop()
        }
    }
}
