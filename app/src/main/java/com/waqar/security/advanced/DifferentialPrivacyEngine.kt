package com.waqar.security.advanced

import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign

/**
 * DifferentialPrivacyEngine: Differential Privacy via the Laplace Mechanism (Dwork et al.).
 *
 * Sanitizes numeric telemetry, usage statistics, attendance counters, and exam averages before
 * they are synced to cloud storage or exported to reports:
 *
 * Mathematical Foundations:
 * 1. Differential Privacy Definition: An algorithm M satisfies epsilon-differential privacy if
 *    for all neighboring datasets D1, D2 differing by at most 1 individual and all S:
 *      Pr[M(D1) in S] <= exp(epsilon) * Pr[M(D2) in S]
 * 2. Laplace Mechanism:
 *      M(x) = f(x) + Lap(scale = Delta f / epsilon)
 *    where:
 *      - Delta f is the global sensitivity: max || f(D1) - f(D2) ||
 *      - epsilon is the privacy loss budget (e.g., epsilon = 1.0)
 * 3. Inverse Transform Sampling:
 *      Lap(0, b) = -sgn(u) * b * ln(1 - 2 * |u|)  where u ~ Uniform(-0.5, 0.5)
 */
object DifferentialPrivacyEngine {

    private val secureRandom = SecureRandom()

    /**
     * Generates a zero-mean Laplace random variate with scale parameter b = sensitivity / epsilon.
     */
    fun sampleLaplaceNoise(sensitivity: Double, epsilon: Double): Double {
        require(sensitivity > 0.0) { "Sensitivity Delta f must be strictly positive" }
        require(epsilon > 0.0) { "Privacy budget epsilon must be strictly positive" }

        val scale = sensitivity / epsilon
        // u uniformly distributed in (-0.5, 0.5)
        val u = secureRandom.nextDouble() - 0.5
        val sign = sign(u).takeIf { it != 0.0 } ?: 1.0
        return -sign * scale * ln(1.0 - 2.0 * abs(u))
    }

    /**
     * Sanitizes a single count metric (e.g. daily student attendance count) with Delta f = 1.0.
     */
    fun sanitizeCount(realCount: Int, epsilon: Double = 1.0): Int {
        val noise = sampleLaplaceNoise(sensitivity = 1.0, epsilon = epsilon)
        val sanitized = (realCount + noise).toInt()
        return maxOf(0, sanitized)
    }

    /**
     * Sanitizes a floating percentage metric (e.g. syllabus completion % or average exam score).
     */
    fun sanitizePercentage(realPercentage: Double, studentCount: Int, epsilon: Double = 1.0): Double {
        // Sensitivity of mean percentage in [0, 100] across N students is 100.0 / N
        val sensitivity = 100.0 / maxOf(1, studentCount)
        val noise = sampleLaplaceNoise(sensitivity = sensitivity, epsilon = epsilon)
        val sanitized = realPercentage + noise
        return sanitized.coerceIn(0.0, 100.0)
    }
}
