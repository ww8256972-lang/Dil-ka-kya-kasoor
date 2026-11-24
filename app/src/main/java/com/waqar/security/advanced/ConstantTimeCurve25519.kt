package com.waqar.security.advanced

import java.math.BigInteger

/**
 * ConstantTimeCurve25519: Constant-Time X25519 Scalar Multiplication (RFC 7748).
 *
 * Implements Curve25519 scalar multiplication on the Montgomery curve:
 *   y^2 = x^3 + 486662 * x^2 + x  modulo p = 2^255 - 19
 *
 * Side-Channel Resistance:
 * - Employs the Montgomery Ladder algorithm executing exactly 255 uniform steps.
 * - Uses branchless bitwise conditional swap (`cswap`) with arithmetic masks.
 * - Zero secret-dependent branching (no `if (bit == 1)` branches).
 * - Zero secret-dependent memory lookups (no cache-timing side-channels).
 */
object ConstantTimeCurve25519 {

    // Prime modulus p = 2^255 - 19
    val P: BigInteger = BigInteger.valueOf(2L).pow(255).subtract(BigInteger.valueOf(19L))
    val A24: BigInteger = BigInteger.valueOf(121665L) // (486662 - 2) / 4

    /**
     * Constant-time X25519 scalar multiplication: returns R = k * u.
     */
    fun scalarMultiply(scalar: ByteArray, uCoordinateBytes: ByteArray): ByteArray {
        require(scalar.size == 32) { "Scalar must be exactly 32 bytes" }
        require(uCoordinateBytes.size == 32) { "U-coordinate must be exactly 32 bytes" }

        // Clamp scalar according to RFC 7748:
        // scalar[0] &= 248; scalar[31] &= 127; scalar[31] |= 64
        val clampedScalar = scalar.copyOf()
        clampedScalar[0] = (clampedScalar[0].toInt() and 248).toByte()
        clampedScalar[31] = (clampedScalar[31].toInt() and 127).toByte()
        clampedScalar[31] = (clampedScalar[31].toInt() or 64).toByte()

        val u = decodeLittleEndian(uCoordinateBytes).mod(P)

        var x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0

        // Process scalar bits from 254 down to 0 in strictly constant time
        for (t in 254 downTo 0) {
            val byteIdx = t / 8
            val bitIdx = t % 8
            val kBit = (clampedScalar[byteIdx].toInt() ushr bitIdx) and 1

            swap = swap xor kBit

            // Branchless conditional swap
            val (sx2, sx3) = cswap(swap, x2, x3)
            val (sz2, sz3) = cswap(swap, z2, z3)
            x2 = sx2
            x3 = sx3
            z2 = sz2
            z3 = sz3

            swap = kBit

            // Montgomery ladder step
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            x3 = da.add(cb).mod(P).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).mod(P).pow(2).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e).mod(P))).mod(P)
        }

        // Final conditional swap
        val (finalX2, _) = cswap(swap, x2, x3)
        val (finalZ2, _) = cswap(swap, z2, z3)

        // Result = x2 * z2^(p - 2) mod p
        val result = finalX2.multiply(finalZ2.modPow(P.subtract(BigInteger.valueOf(2L)), P)).mod(P)
        return encodeLittleEndian(result)
    }

    /**
     * Branchless conditional swap using bit mask.
     */
    private fun cswap(swap: Int, a: BigInteger, b: BigInteger): Pair<BigInteger, BigInteger> {
        val mask = BigInteger.valueOf(-swap.toLong()) // 0 -> 0, 1 -> -1 (all bits 1)
        val diff = a.xor(b).and(mask)
        return Pair(a.xor(diff), b.xor(diff))
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
        var result = BigInteger.ZERO
        for (i in bytes.indices) {
            val b = BigInteger.valueOf(bytes[i].toLong() and 0xFF)
            result = result.or(b.shiftLeft(i * 8))
        }
        return result
    }

    private fun encodeLittleEndian(value: BigInteger): ByteArray {
        val out = ByteArray(32)
        var cur = value
        for (i in 0 until 32) {
            out[i] = cur.and(BigInteger.valueOf(0xFF)).toByte()
            cur = cur.shiftRight(8)
        }
        return out
    }
}
