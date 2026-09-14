package com.cemupad.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReedSolomonDecoderTest {

    @Test
    fun `gfMul and inversion identities hold`() {
        // Multiplicative inverse property: for any a != 0, a * inv(a) == 1
        for (a in 1..255) {
            val inv = ReedSolomonDecoder.GF2_8_INV[a]
            assertTrue("Inverse of $a must be non-zero", inv != 0)
            val prod = ReedSolomonDecoder.gfMul(a, inv)
            assertTrue("Expected $a * $inv == 1, got $prod", prod == 1)
        }

        // Multiplication by zero is zero
        for (a in 0..255) {
            assertTrue(ReedSolomonDecoder.gfMul(a, 0) == 0)
            assertTrue(ReedSolomonDecoder.gfMul(0, a) == 0)
        }
    }

    @Test
    fun `encode and decode recovers single dropped data shard`() {
        val dataCount = 5
        val parityCount = 2
        val blockSize = 1362

        val random = Random(42)
        val originalData = Array(dataCount) { i ->
            ByteArray(blockSize) { random.nextInt().toByte() }
        }
        val parityShards = Array(parityCount) { ByteArray(blockSize) }

        // Encode parity
        ReedSolomonDecoder.encode(originalData, parityShards, blockSize)

        // Drop data shard 2
        val testShards = arrayOfNulls<ByteArray>(dataCount + parityCount)
        for (i in 0 until dataCount) {
            if (i != 2) testShards[i] = originalData[i].clone()
        }
        testShards[dataCount] = parityShards[0].clone()
        testShards[dataCount + 1] = parityShards[1].clone()

        assertTrue(testShards[2] == null)

        val success = ReedSolomonDecoder.decode(testShards, dataCount, parityCount, blockSize)
        assertTrue("Reconstruction should succeed", success)
        assertTrue("Dropped shard 2 must be reconstructed", testShards[2] != null)
        assertArrayEquals("Reconstructed shard must match original exactly", originalData[2], testShards[2])
    }

    @Test
    fun `encode and decode recovers multiple dropped data shards`() {
        val dataCount = 8
        val parityCount = 3
        val blockSize = 1362

        val random = Random(12345)
        val originalData = Array(dataCount) {
            ByteArray(blockSize) { random.nextInt().toByte() }
        }
        val parityShards = Array(parityCount) { ByteArray(blockSize) }

        // Encode
        ReedSolomonDecoder.encode(originalData, parityShards, blockSize)

        // Drop data shards 0, 3, and 7 (3 erasures)
        val testShards = arrayOfNulls<ByteArray>(dataCount + parityCount)
        for (i in 0 until dataCount) {
            if (i != 0 && i != 3 && i != 7) {
                testShards[i] = originalData[i].clone()
            }
        }
        // Provide all 3 parity shards
        for (p in 0 until parityCount) {
            testShards[dataCount + p] = parityShards[p].clone()
        }

        val success = ReedSolomonDecoder.decode(testShards, dataCount, parityCount, blockSize)
        assertTrue("Reconstruction with 3 erasures and 3 parity shards must succeed", success)
        assertArrayEquals(originalData[0], testShards[0])
        assertArrayEquals(originalData[3], testShards[3])
        assertArrayEquals(originalData[7], testShards[7])
    }

    @Test
    fun `decode with some dropped parity shards still recovers missing data`() {
        val dataCount = 6
        val parityCount = 3
        val blockSize = 1362

        val random = Random(999)
        val originalData = Array(dataCount) {
            ByteArray(blockSize) { random.nextInt().toByte() }
        }
        val parityShards = Array(parityCount) { ByteArray(blockSize) }

        ReedSolomonDecoder.encode(originalData, parityShards, blockSize)

        // Drop data shard 1, drop data shard 4 (2 data drops)
        // ALSO drop parity shard 0! (1 parity drop)
        // Parity shards 1 and 2 survived (2 parity shards for 2 data drops = sufficient!)
        val testShards = arrayOfNulls<ByteArray>(dataCount + parityCount)
        for (i in 0 until dataCount) {
            if (i != 1 && i != 4) testShards[i] = originalData[i].clone()
        }
        testShards[dataCount + 1] = parityShards[1].clone()
        testShards[dataCount + 2] = parityShards[2].clone()

        val success = ReedSolomonDecoder.decode(testShards, dataCount, parityCount, blockSize)
        assertTrue("Reconstruction with surviving parity shards must succeed", success)
        assertArrayEquals(originalData[1], testShards[1])
        assertArrayEquals(originalData[4], testShards[4])
    }

    @Test
    fun `decode fails gracefully when erasures exceed available parity`() {
        val dataCount = 5
        val parityCount = 2
        val blockSize = 512

        val originalData = Array(dataCount) { ByteArray(blockSize) { 0x42 } }
        val parityShards = Array(parityCount) { ByteArray(blockSize) }
        ReedSolomonDecoder.encode(originalData, parityShards, blockSize)

        // Drop 3 data shards when only 2 parity shards exist
        val testShards = arrayOfNulls<ByteArray>(dataCount + parityCount)
        testShards[0] = originalData[0]
        testShards[1] = originalData[1]
        // 2, 3, 4 missing
        testShards[dataCount] = parityShards[0]
        testShards[dataCount + 1] = parityShards[1]

        val success = ReedSolomonDecoder.decode(testShards, dataCount, parityCount, blockSize)
        assertFalse("Must fail when erasures > parityCount", success)
    }
}
