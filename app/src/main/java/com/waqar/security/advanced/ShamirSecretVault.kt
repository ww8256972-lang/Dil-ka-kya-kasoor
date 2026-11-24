package com.waqar.security.advanced

import java.security.SecureRandom

/**
 * ShamirSecretVault: Shamir's Secret Sharing Scheme over Galois Field GF(256).
 *
 * Implements rigorous finite field arithmetic over GF(2^8) modulo the Rijndael irreducible
 * polynomial m(x) = x^8 + x^4 + x^3 + x + 1 (0x11B) with generator alpha = 0x03.
 *
 * Mathematics:
 * - Master key S of length L bytes is split into N shares: { (x_1, y_1), ..., (x_N, y_N) }.
 * - Threshold K (K <= N) defines the exact minimum shares required to reconstruct S.
 * - For each byte s_i in S, an independent random polynomial P_i(x) of degree (K - 1) is generated:
 *     P_i(x) = s_i + a_1 x + a_2 x^2 + ... + a_{K-1} x^{K-1} in GF(256)
 * - Evaluation points x_j in {1, ..., N} are non-zero.
 * - Lagrange Polynomial Interpolation recovers P(0) in GF(256):
 *     S = P(0) = sum_{j=1}^K y_j * l_j(0)
 *     l_j(0) = prod_{m != j} (0 - x_m) / (x_j - x_m) = prod_{m != j} x_m / (x_j XOR x_m)
 * - Information-Theoretic Security: Having K-1 or fewer shares reveals ZERO bits about S.
 */
object ShamirSecretVault {

    private val EXP_TABLE = IntArray(512)
    private val LOG_TABLE = IntArray(256)
    private val secureRandom = SecureRandom()

    init {
        // Precompute GF(256) log and exp tables using generator 3 with poly 0x11B
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            EXP_TABLE[i + 255] = x
            LOG_TABLE[x] = i
            x = x shl 1
            if (x >= 256) {
                x = x xor 0x11B
            }
        }
        LOG_TABLE[0] = 0 // By convention
    }

    /**
     * GF(256) addition / subtraction (identical in characteristic 2).
     */
    private fun gfAdd(a: Int, b: Int): Int = a xor b

    /**
     * GF(256) multiplication using precomputed log/exp tables.
     */
    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        val logSum = LOG_TABLE[a] + LOG_TABLE[b]
        return EXP_TABLE[logSum]
    }

    /**
     * GF(256) division: a / b. Throws ArithmeticException on b == 0.
     */
    private fun gfDiv(a: Int, b: Int): Int {
        if (b == 0) throw ArithmeticException("Division by zero in GF(256)")
        if (a == 0) return 0
        var logDiff = LOG_TABLE[a] - LOG_TABLE[b]
        if (logDiff < 0) logDiff += 255
        return EXP_TABLE[logDiff]
    }

    data class SecretShare(
        val shareIndex: Int, // x-coordinate (1 <= shareIndex <= 255)
        val data: ByteArray // y-coordinates for each byte of the secret
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SecretShare) return false
            return shareIndex == other.shareIndex && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * shareIndex + data.contentHashCode()
    }

    /**
     * Splits masterKey into totalShares (N) with threshold (K).
     */
    fun splitKey(masterKey: ByteArray, totalShares: Int, threshold: Int): List<SecretShare> {
        require(threshold in 2..totalShares) { "Threshold must be between 2 and totalShares" }
        require(totalShares <= 255) { "Maximum 255 shares supported in GF(256)" }
        require(masterKey.isNotEmpty()) { "Master key must not be empty" }

        val sharesData = Array(totalShares) { ByteArray(masterKey.size) }

        // For each byte of the secret, build random polynomial P(x) = s + a_1*x + ... + a_{k-1}*x^{k-1}
        for (byteIdx in masterKey.indices) {
            val secretByte = masterKey[byteIdx].toInt() and 0xFF
            val poly = IntArray(threshold)
            poly[0] = secretByte
            for (coeffIdx in 1 until threshold) {
                poly[coeffIdx] = secureRandom.nextInt(256)
            }

            // Evaluate P(x) at x = 1..totalShares
            for (shareIdx in 0 until totalShares) {
                val x = shareIdx + 1
                var y = 0
                var xPower = 1
                for (coeff in poly) {
                    y = gfAdd(y, gfMul(coeff, xPower))
                    xPower = gfMul(xPower, x)
                }
                sharesData[shareIdx][byteIdx] = y.toByte()
            }
        }

        return (0 until totalShares).map { idx ->
            SecretShare(shareIndex = idx + 1, data = sharesData[idx])
        }
    }

    /**
     * Reconstructs the original master key from any M (>= threshold) shares using Lagrange interpolation.
     */
    fun reconstructKey(shares: List<SecretShare>, threshold: Int): ByteArray {
        require(shares.size >= threshold) { "At least $threshold shares required to reconstruct key" }
        val distinctShares = shares.distinctBy { it.shareIndex }.take(threshold)
        require(distinctShares.size == threshold) { "Shares must have distinct index identifiers" }

        val keyLength = distinctShares.first().data.size
        require(distinctShares.all { it.data.size == keyLength }) { "All shares must have identical length" }

        val reconstructed = ByteArray(keyLength)

        for (byteIdx in 0 until keyLength) {
            var secretByte = 0

            for (j in distinctShares.indices) {
                val xj = distinctShares[j].shareIndex
                val yj = distinctShares[j].data[byteIdx].toInt() and 0xFF

                // Compute Lagrange basis polynomial l_j(0) = prod_{m != j} (0 - x_m) / (x_j - x_m)
                var basisAtZero = 1
                for (m in distinctShares.indices) {
                    if (m != j) {
                        val xm = distinctShares[m].shareIndex
                        // In GF(256), 0 - xm is xm, and xj - xm is (xj xor xm)
                        val num = xm
                        val den = gfAdd(xj, xm)
                        val factor = gfDiv(num, den)
                        basisAtZero = gfMul(basisAtZero, factor)
                    }
                }

                secretByte = gfAdd(secretByte, gfMul(yj, basisAtZero))
            }

            reconstructed[byteIdx] = secretByte.toByte()
        }

        return reconstructed
    }
}
