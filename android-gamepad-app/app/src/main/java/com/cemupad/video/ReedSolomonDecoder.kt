package com.cemupad.video

/**
 * Pure Kotlin Cauchy Reed-Solomon erasure codec over GF(2^8).
 *
 * Implements the exact same Cauchy generator matrix and Galois field tables
 * as `nanors` (used by Apollo/Sunshine and Moonlight).
 *
 * Generator matrix: P[j, i] = 1 / ((ps + i) ^ j) over GF(2^8), polynomial 285 (0x11D).
 * Given N data shards and K parity shards, any N received shards out of N + K
 * can mathematically reconstruct all missing data shards in < 50 µs.
 */
object ReedSolomonDecoder {

    val GF2_8_LOG = intArrayOf(
        255, 0, 1, 25, 2, 50, 26, 198, 3, 223, 51, 238, 27, 104, 199, 75,
        4, 100, 224, 14, 52, 141, 239, 129, 28, 193, 105, 248, 200, 8, 76, 113,
        5, 138, 101, 47, 225, 36, 15, 33, 53, 147, 142, 218, 240, 18, 130, 69,
        29, 181, 194, 125, 106, 39, 249, 185, 201, 154, 9, 120, 77, 228, 114, 166,
        6, 191, 139, 98, 102, 221, 48, 253, 226, 152, 37, 179, 16, 145, 34, 136,
        54, 208, 148, 206, 143, 150, 219, 189, 241, 210, 19, 92, 131, 56, 70, 64,
        30, 66, 182, 163, 195, 72, 126, 110, 107, 58, 40, 84, 250, 133, 186, 61,
        202, 94, 155, 159, 10, 21, 121, 43, 78, 212, 229, 172, 115, 243, 167, 87,
        7, 112, 192, 247, 140, 128, 99, 13, 103, 74, 222, 237, 49, 197, 254, 24,
        227, 165, 153, 119, 38, 184, 180, 124, 17, 68, 146, 217, 35, 32, 137, 46,
        55, 63, 209, 91, 149, 188, 207, 205, 144, 135, 151, 178, 220, 252, 190, 97,
        242, 86, 211, 171, 20, 42, 93, 158, 132, 60, 57, 83, 71, 109, 65, 162,
        31, 45, 67, 216, 183, 123, 164, 118, 196, 23, 73, 236, 127, 12, 111, 246,
        108, 161, 59, 82, 41, 157, 85, 170, 251, 96, 134, 177, 187, 204, 62, 90,
        203, 89, 95, 176, 156, 169, 160, 81, 11, 245, 22, 235, 122, 117, 44, 215,
        79, 174, 213, 233, 230, 231, 173, 232, 116, 214, 244, 234, 168, 80, 88, 175
    )

    val GF2_8_EXP = intArrayOf(
        1, 2, 4, 8, 16, 32, 64, 128, 29, 58, 116, 232, 205, 135, 19, 38,
        76, 152, 45, 90, 180, 117, 234, 201, 143, 3, 6, 12, 24, 48, 96, 192,
        157, 39, 78, 156, 37, 74, 148, 53, 106, 212, 181, 119, 238, 193, 159, 35,
        70, 140, 5, 10, 20, 40, 80, 160, 93, 186, 105, 210, 185, 111, 222, 161,
        95, 190, 97, 194, 153, 47, 94, 188, 101, 202, 137, 15, 30, 60, 120, 240,
        253, 231, 211, 187, 107, 214, 177, 127, 254, 225, 223, 163, 91, 182, 113, 226,
        217, 175, 67, 134, 17, 34, 68, 136, 13, 26, 52, 104, 208, 189, 103, 206,
        129, 31, 62, 124, 248, 237, 199, 147, 59, 118, 236, 197, 151, 51, 102, 204,
        133, 23, 46, 92, 184, 109, 218, 169, 79, 158, 33, 66, 132, 21, 42, 84,
        168, 77, 154, 41, 82, 164, 85, 170, 73, 146, 57, 114, 228, 213, 183, 115,
        230, 209, 191, 99, 198, 145, 63, 126, 252, 229, 215, 179, 123, 246, 241, 255,
        227, 219, 171, 75, 150, 49, 98, 196, 149, 55, 110, 220, 165, 87, 174, 65,
        130, 25, 50, 100, 200, 141, 7, 14, 28, 56, 112, 224, 221, 167, 83, 166,
        81, 162, 89, 178, 121, 242, 249, 239, 195, 155, 43, 86, 172, 69, 138, 9,
        18, 36, 72, 144, 61, 122, 244, 245, 247, 243, 251, 235, 203, 139, 11, 22,
        44, 88, 176, 125, 250, 233, 207, 131, 27, 54, 108, 216, 173, 71, 142, 1,
        2, 4, 8, 16, 32, 64, 128, 29, 58, 116, 232, 205, 135, 19, 38, 76,
        152, 45, 90, 180, 117, 234, 201, 143, 3, 6, 12, 24, 48, 96, 192, 157,
        39, 78, 156, 37, 74, 148, 53, 106, 212, 181, 119, 238, 193, 159, 35, 70,
        140, 5, 10, 20, 40, 80, 160, 93, 186, 105, 210, 185, 111, 222, 161, 95,
        190, 97, 194, 153, 47, 94, 188, 101, 202, 137, 15, 30, 60, 120, 240, 253,
        231, 211, 187, 107, 214, 177, 127, 254, 225, 223, 163, 91, 182, 113, 226, 217,
        175, 67, 134, 17, 34, 68, 136, 13, 26, 52, 104, 208, 189, 103, 206, 129,
        31, 62, 124, 248, 237, 199, 147, 59, 118, 236, 197, 151, 51, 102, 204, 133,
        23, 46, 92, 184, 109, 218, 169, 79, 158, 33, 66, 132, 21, 42, 84, 168,
        77, 154, 41, 82, 164, 85, 170, 73, 146, 57, 114, 228, 213, 183, 115, 230,
        209, 191, 99, 198, 145, 63, 126, 252, 229, 215, 179, 123, 246, 241, 255, 227,
        219, 171, 75, 150, 49, 98, 196, 149, 55, 110, 220, 165, 87, 174, 65, 130,
        25, 50, 100, 200, 141, 7, 14, 28, 56, 112, 224, 221, 167, 83, 166, 81,
        162, 89, 178, 121, 242, 249, 239, 195, 155, 43, 86, 172, 69, 138, 9, 18,
        36, 72, 144, 61, 122, 244, 245, 247, 243, 251, 235, 203, 139, 11, 22, 44,
        88, 176, 125, 250, 233, 207, 131, 27, 54, 108, 216, 173, 71, 142
    )

    val GF2_8_INV = intArrayOf(
        0, 1, 142, 244, 71, 167, 122, 186, 173, 157, 221, 152, 61, 170, 93, 150,
        216, 114, 192, 88, 224, 62, 76, 102, 144, 222, 85, 128, 160, 131, 75, 42,
        108, 237, 57, 81, 96, 86, 44, 138, 112, 208, 31, 74, 38, 139, 51, 110,
        72, 137, 111, 46, 164, 195, 64, 94, 80, 34, 207, 169, 171, 12, 21, 225,
        54, 95, 248, 213, 146, 78, 166, 4, 48, 136, 43, 30, 22, 103, 69, 147,
        56, 35, 104, 140, 129, 26, 37, 97, 19, 193, 203, 99, 151, 14, 55, 65,
        36, 87, 202, 91, 185, 196, 23, 77, 82, 141, 239, 179, 32, 236, 47, 50,
        40, 209, 17, 217, 233, 251, 218, 121, 219, 119, 6, 187, 132, 205, 254, 252,
        27, 84, 161, 29, 124, 204, 228, 176, 73, 49, 39, 45, 83, 105, 2, 245,
        24, 223, 68, 79, 155, 188, 15, 92, 11, 220, 189, 148, 172, 9, 199, 162,
        28, 130, 159, 198, 52, 194, 70, 5, 206, 59, 13, 60, 156, 8, 190, 183,
        135, 229, 238, 107, 235, 242, 191, 175, 197, 100, 7, 123, 149, 154, 174, 182,
        18, 89, 165, 53, 101, 184, 163, 158, 210, 247, 98, 90, 133, 125, 168, 58,
        41, 113, 200, 246, 249, 67, 215, 214, 16, 115, 118, 120, 153, 10, 25, 145,
        20, 63, 230, 240, 134, 177, 226, 241, 250, 116, 243, 180, 109, 33, 178, 106,
        227, 231, 181, 234, 3, 143, 211, 201, 66, 212, 232, 117, 127, 255, 126, 253
    )

    fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF2_8_EXP[GF2_8_LOG[a] + GF2_8_LOG[b]]
    }

    fun axpy(dst: ByteArray, src: ByteArray, u: Int, len: Int) {
        if (u == 0) return
        if (u == 1) {
            for (i in 0 until len) {
                dst[i] = (dst[i].toInt() xor src[i].toInt()).toByte()
            }
        } else {
            val logU = GF2_8_LOG[u]
            for (i in 0 until len) {
                val b = src[i].toInt() and 0xFF
                if (b != 0) {
                    val prod = GF2_8_EXP[logU + GF2_8_LOG[b]]
                    dst[i] = (dst[i].toInt() xor prod).toByte()
                }
            }
        }
    }

    fun scal(dst: ByteArray, u: Int, len: Int) {
        if (u <= 1) return
        val logU = GF2_8_LOG[u]
        for (i in 0 until len) {
            val b = dst[i].toInt() and 0xFF
            if (b != 0) {
                dst[i] = GF2_8_EXP[logU + GF2_8_LOG[b]].toByte()
            } else {
                dst[i] = 0
            }
        }
    }

    /**
     * Encodes N data shards into K parity shards using the Cauchy generator matrix.
     * Each shard in [dataShards] and [parityShards] must have length >= [blockSize].
     */
    fun encode(
        dataShards: Array<ByteArray>,
        parityShards: Array<ByteArray>,
        blockSize: Int
    ) {
        val ds = dataShards.size
        val ps = parityShards.size
        for (j in 0 until ps) {
            val row = parityShards[j]
            row.fill(0, 0, blockSize)
            for (i in 0 until ds) {
                val u = GF2_8_INV[(ps + i) xor j]
                axpy(row, dataShards[i], u, blockSize)
            }
        }
    }

    /**
     * Decodes / reconstructs missing data shards in [shards] in-place.
     *
     * [shards]: Array of length `dataCount + parityCount`.
     *   - Indices `0 until dataCount` are the data shards (some may be null).
     *   - Indices `dataCount until dataCount + parityCount` are the parity shards (some may be null).
     *
     * Returns true if all missing data shards were successfully reconstructed in [shards],
     * or false if there are too many erasures to recover.
     */
    fun decode(
        shards: Array<ByteArray?>,
        dataCount: Int,
        parityCount: Int,
        blockSize: Int
    ): Boolean {
        val ds = dataCount
        val ps = parityCount
        val ts = ds + ps

        val erasures = IntArray(ds)
        var gaps = 0
        for (i in 0 until ds) {
            if (shards[i] == null) {
                erasures[gaps++] = i
            }
        }

        // If no data shards are missing, nothing to reconstruct
        if (gaps == 0) return true

        // If more data shards are missing than total parity shards, impossible to recover
        if (gaps > ps) return false

        // Generate Cauchy generator matrix P: size ps x ds
        val P = IntArray(ps * ds)
        for (j in 0 until ps) {
            val rowOffset = j * ds
            for (i in 0 until ds) {
                P[rowOffset + i] = GF2_8_INV[(ps + i) xor j]
            }
        }

        // Column permutation: surviving data shards first, then erased data shards
        val colperm = IntArray(ds)
        var jCol = 0
        for (i in 0 until (ds - gaps)) {
            while (jCol < ds && shards[jCol] == null) {
                jCol++
            }
            colperm[i] = jCol++
        }
        for (i in 0 until gaps) {
            colperm[ds - gaps + i] = erasures[i]
        }

        // Row permutation: assign available parity shards to missing slots
        val rowperm = IntArray(gaps)
        var pIdx = 0
        var paritySlot = ds
        while (pIdx < gaps) {
            while (paritySlot < ts && shards[paritySlot] == null) {
                paritySlot++
            }
            if (paritySlot >= ts) {
                // Not enough parity shards available to cover erasures
                return false
            }
            rowperm[pIdx] = paritySlot - ds
            val erasedIndex = erasures[pIdx]
            shards[erasedIndex] = shards[paritySlot]!!.copyOf(blockSize)
            pIdx++
            paritySlot++
        }

        // Gaussian elimination on Cauchy submatrix:
        val V0b = ds - gaps
        val W = gaps
        val wrk = IntArray(W * W)

        // 1. Fill wrk with submatrix from P
        for (i in 0 until W) {
            val dr = rowperm[i] * ds
            for (k in 0 until W) {
                wrk[i * W + k] = P[dr + colperm[V0b + k]]
            }
        }

        // 2. Eliminate surviving data columns from destination rows
        for (v in V0b until ds) {
            val dr = rowperm[v - V0b] * ds
            val dstShard = shards[colperm[v]]!!
            for (row in 0 until V0b) {
                val u = P[dr + colperm[row]]
                if (u != 0) {
                    axpy(dstShard, shards[colperm[row]]!!, u, blockSize)
                }
            }
        }

        // 3. Forward elimination on wrk and destination rows
        for (x in 0 until W) {
            val pivot = wrk[x * W + x]
            if (pivot == 0) return false
            val u = GF2_8_INV[pivot]

            // Scale row x by pivot inverse
            val rowOffset = x * W
            for (col in x until W) {
                wrk[rowOffset + col] = gfMul(wrk[rowOffset + col], u)
            }
            scal(shards[colperm[V0b + x]]!!, u, blockSize)

            // Eliminate lower rows
            for (row in (x + 1) until W) {
                val lowerRowOffset = row * W
                val factor = wrk[lowerRowOffset + x]
                if (factor != 0) {
                    for (col in x until W) {
                        wrk[lowerRowOffset + col] = wrk[lowerRowOffset + col] xor gfMul(wrk[rowOffset + col], factor)
                    }
                    axpy(shards[colperm[V0b + row]]!!, shards[colperm[V0b + x]]!!, factor, blockSize)
                }
            }
        }

        // 4. Backward substitution
        for (x in (W - 1) downTo 0) {
            val fromShard = shards[colperm[V0b + x]]!!
            for (row in 0 until x) {
                val factor = wrk[row * W + x]
                if (factor != 0) {
                    axpy(shards[colperm[V0b + row]]!!, fromShard, factor, blockSize)
                }
            }
        }

        return true
    }
}
