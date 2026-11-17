package internal_compose_brahmastra

import java.math.BigInteger
import java.security.SecureRandom

/**
 * 1. `internal_compose_brahmastra/HomomorphicLedgerGuard.kt`
 * Additive & Affine Homomorphic Masking (`E(x) = (a * x + b) mod p`) for In-Memory UI States
 * (Fees Balances, Exam Scores, Attendance Counts).
 *
 * Ensures raw financial or academic integers never sit in plain RAM where Frida/GameGuardian
 * memory scanners can locate and patch them.
 */
class HomomorphicLedgerGuard(
    seedBytes: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
) {
    // 127-bit Mersenne Prime p = 2^127 - 1
    private val primeP: BigInteger = BigInteger.TWO.pow(127).subtract(BigInteger.ONE)
    private val multiplierA: BigInteger
    private val invA: BigInteger

    init {
        val rawA = BigInteger(1, seedBytes).mod(primeP.subtract(BigInteger.TWO)).add(BigInteger.TWO)
        multiplierA = rawA
        invA = multiplierA.modInverse(primeP)
    }

    data class MaskedValue(
        val cipherResidueHex: String,
        val blindingTagHex: String
    )

    /**
     * Masks an integer `x`:
     *   `E(x) = (a * x + b) mod p`
     */
    fun maskInteger(plainValue: Long, blindingNonce: Long = 0L): MaskedValue {
        val x = BigInteger.valueOf(plainValue).mod(primeP)
        val b = BigInteger.valueOf(blindingNonce).multiply(multiplierA).mod(primeP)
        val c = multiplierA.multiply(x).add(b).mod(primeP)
        return MaskedValue(
            cipherResidueHex = c.toString(16),
            blindingTagHex = b.toString(16)
        )
    }

    /**
     * Unmasks a [MaskedValue] back to `Long`:
     *   `x = a^-1 * (E(x) - b) mod p`
     */
    fun unmaskInteger(masked: MaskedValue): Long {
        val c = BigInteger(masked.cipherResidueHex, 16)
        val b = BigInteger(masked.blindingTagHex, 16)
        val unmasked = c.subtract(b).mod(primeP).multiply(invA).mod(primeP)
        val halfP = primeP.shiftRight(1)
        val signed = if (unmasked > halfP) unmasked.subtract(primeP) else unmasked
        return signed.toLong()
    }

    /**
     * Homomorphically adds two masked integers `E(x1)` and `E(x2)` in encrypted space without
     * decrypting either operand in RAM:
     *   `E(x1 + x2) = (E(x1) + E(x2)) mod p`
     */
    fun addHomomorphically(m1: MaskedValue, m2: MaskedValue): MaskedValue {
        val c1 = BigInteger(m1.cipherResidueHex, 16)
        val c2 = BigInteger(m2.cipherResidueHex, 16)
        val b1 = BigInteger(m1.blindingTagHex, 16)
        val b2 = BigInteger(m2.blindingTagHex, 16)
        return MaskedValue(
            cipherResidueHex = c1.add(c2).mod(primeP).toString(16),
            blindingTagHex = b1.add(b2).mod(primeP).toString(16)
        )
    }
}
