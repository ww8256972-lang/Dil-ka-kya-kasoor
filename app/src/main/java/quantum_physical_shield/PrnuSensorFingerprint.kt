package quantum_physical_shield

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 1. `quantum_physical_shield/PrnuSensorFingerprint.kt`
 * Camera CMOS Sensor Photo-Response Non-Uniformity (PRNU) Wavelet Noise Extractor.
 *
 * Applies a 2D spatial high-pass Haar/Laplacian wavelet residual filter (`W = I - Denoised(I)`)
 * to extract physical silicon photon-response non-uniformity noise and reject flat synthetic
 * virtual-camera frames or zero-noise deepfake buffers.
 */
object PrnuSensorFingerprint {

    data class PrnuAuditResult(
        val prnuDigestHex: String,
        val highPassResidualVariance: Double,
        val normalizedCorrelation: Double,
        val isAuthenticPhysicalCmos: Boolean
    )

    /**
     * Extracts the 2D high-pass PRNU residual matrix from a grayscale luminance frame (`width x height`)
     * and computes its variance and optional normalized cross-correlation against a reference PRNU pattern.
     */
    fun extractAndVerifyPrnu(
        luminanceFrame: FloatArray,
        width: Int,
        height: Int,
        referencePrnuResidual: FloatArray? = null
    ): PrnuAuditResult {
        if (width < 4 || height < 4 || luminanceFrame.size < width * height) {
            return PrnuAuditResult("", 0.0, 0.0, false)
        }

        val residual = FloatArray(width * height)
        var sum = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = luminanceFrame[idx]
                // 3x3 local wavelet denoised approximation
                val localMean = (
                    luminanceFrame[(y - 1) * width + x] +
                    luminanceFrame[(y + 1) * width + x] +
                    luminanceFrame[y * width + (x - 1)] +
                    luminanceFrame[y * width + (x + 1)]
                ) * 0.25f
                val w = center - localMean
                residual[idx] = w
                sum += w.toDouble()
                count++
            }
        }

        val mean = if (count > 0) sum / count else 0.0
        var sqSum = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val diff = residual[y * width + x] - mean
                sqSum += diff * diff
            }
        }
        val variance = if (count > 0) sqSum / count else 0.0

        val ncc = if (referencePrnuResidual != null && referencePrnuResidual.size == residual.size) {
            computeNormalizedCrossCorrelation(residual, referencePrnuResidual)
        } else {
            1.0
        }

        val md = MessageDigest.getInstance("SHA-256")
        for (i in residual.indices step maxOf(1, residual.size / 64)) {
            val quantized = (residual[i] * 1000f).toInt()
            md.update((quantized and 0xFF).toByte())
        }
        val digestHex = md.digest().joinToString("") { "%02x".format(it) }

        val authentic = variance > 1e-6 && ncc >= 0.65
        return PrnuAuditResult(
            prnuDigestHex = digestHex,
            highPassResidualVariance = variance,
            normalizedCorrelation = ncc,
            isAuthenticPhysicalCmos = authentic
        )
    }

    private fun computeNormalizedCrossCorrelation(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom < 1e-12) 0.0 else abs(dot / denom)
    }
}
