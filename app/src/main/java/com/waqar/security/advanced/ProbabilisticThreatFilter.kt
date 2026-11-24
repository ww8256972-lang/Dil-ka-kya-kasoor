package com.waqar.security.advanced

import java.security.MessageDigest
import java.util.BitSet

/**
 * ProbabilisticThreatFilter: Space-Efficient BitSet Bloom Filter with Double Hashing.
 *
 * Implements Kirsch-Mitzenmacher double-hashing Bloom Filter for O(1) ultra-fast lookups
 * of blacklisted app certificates, compromised student IDs, tampered file hashes, or banned APK signatures
 * using only a few kilobytes of device RAM:
 *
 * Mathematics:
 * - Bit vector of size m bits and k independent hash functions.
 * - Optimal hash count: k = (m / n) * ln(2).
 * - Kirsch-Mitzenmacher optimization:
 *     g_i(x) = (h1(x) + i * h2(x)) mod m   for i in 0 until k
 *   Allows simulating k independent hash functions using only 2 hash calculations (SHA-256 halves).
 * - False positive probability: P_fp = (1 - exp(-k * n / m))^k.
 */
class ProbabilisticThreatFilter(
    val bitSize: Int = 16384, // 16,384 bits = 2 KB of RAM
    val numHashes: Int = 7
) {
    private val bitSet = BitSet(bitSize)

    /**
     * Adds an item's identifier or hash to the threat filter.
     */
    fun insert(identifier: String) {
        val (h1, h2) = computeDoubleHash(identifier)
        for (i in 0 until numHashes) {
            val combined = (h1 + i.toLong() * h2).mod(bitSize.toLong()).toInt()
            val idx = (combined + bitSize) % bitSize
            bitSet.set(idx, true)
        }
    }

    /**
     * Tests whether an identifier is probably in the threat filter.
     * Returns true if present (threat detected), false if definitely absent (clean).
     */
    fun contains(identifier: String): Boolean {
        val (h1, h2) = computeDoubleHash(identifier)
        for (i in 0 until numHashes) {
            val combined = (h1 + i.toLong() * h2).mod(bitSize.toLong()).toInt()
            val idx = (combined + bitSize) % bitSize
            if (!bitSet.get(idx)) {
                return false // Definite negative
            }
        }
        return true // Probable positive
    }

    private fun computeDoubleHash(input: String): Pair<Long, Long> {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))

        var h1 = 0L
        for (i in 0 until 8) {
            h1 = (h1 shl 8) or (digest[i].toLong() and 0xFF)
        }

        var h2 = 0L
        for (i in 8 until 16) {
            h2 = (h2 shl 8) or (digest[i].toLong() and 0xFF)
        }

        return Pair(h1, h2)
    }
}
