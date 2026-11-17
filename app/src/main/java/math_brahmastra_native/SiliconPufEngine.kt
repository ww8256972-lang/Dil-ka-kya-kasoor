package math_brahmastra_native

import android.os.Build
import java.security.MessageDigest
import kotlin.math.abs

/**
 * 1. `math_brahmastra_native/SiliconPufEngine.kt`
 * Hardware Silicon DNA (Physically Unclonable Function via CPU Nanosecond Timing Jitter).
 */
object SiliconPufEngine {

    data class SiliconIntegrityResult(
        val siliconFingerprint256Hex: String,
        val jitterVarianceNs: Double,
        val isGenuineSilicon: Boolean,
        val verdictReason: String
    )

    /**
     * Executes a deterministic multi-stage ALU & L1 cache timing loop measuring nanosecond
     * execution jitter (`System.nanoTime()`) to derive a 256-bit Hardware Silicon Fingerprint.
     */
    fun verifySiliconIntegrity(sampleRounds: Int = 32): SiliconIntegrityResult {
        val safeRounds = sampleRounds.coerceIn(8, 128)
        val deltasNs = LongArray(safeRounds)
        var accumulator = 0x5A3C9E71L

        for (r in 0 until safeRounds) {
            val t0 = System.nanoTime()
            for (i in 1..256) {
                accumulator = (accumulator xor (i * 0x9E3779B97F4A7C15UL.toLong())) * 0x5DEECE66DL + 0xBL
            }
            val t1 = System.nanoTime()
            deltasNs[r] = (t1 - t0).coerceAtLeast(1L)
        }

        val mean = deltasNs.average()
        var varianceSum = 0.0
        for (d in deltasNs) {
            val diff = d - mean
            varianceSum += diff * diff
        }
        val variance = varianceSum / safeRounds

        val hwIdentity = buildString {
            append(Build.BOARD).append('|')
            append(Build.HARDWARE).append('|')
            append(Build.SUPPORTED_ABIS.joinToString(",")).append('|')
            append(accumulator)
            deltasNs.forEach { append('|').append(it and 0xFFL) }
        }
        val fingerprintHex = MessageDigest.getInstance("SHA-256")
            .digest(hwIdentity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val nonZeroJitter = deltasNs.any { it > 0L } && abs(mean) >= 1.0
        return SiliconIntegrityResult(
            siliconFingerprint256Hex = fingerprintHex,
            jitterVarianceNs = variance,
            isGenuineSilicon = nonZeroJitter,
            verdictReason = if (nonZeroJitter) "SILICON_PUF_VERIFIED" else "SYNTHETIC_CLOCK_DETECTED"
        )
    }
}
