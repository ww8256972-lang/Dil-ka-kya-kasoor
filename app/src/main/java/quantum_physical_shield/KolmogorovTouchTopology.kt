package quantum_physical_shield

import kotlin.math.abs

/**
 * 5. `quantum_physical_shield/KolmogorovTouchTopology.kt`
 * Kolmogorov-Arnold Univariate B-Spline Superposition Touch & Micro-Tilt Authenticator:
 *   f(x_1, x_2, x_3) = sum_{q=0..2n} Phi_q( sum_{p=1..n} phi_{q,p}(x_p) )
 *
 * Continuously evaluates touch contact radius, pressure derivative `dP/dt`, and gyroscope
 * micro-tilt during exams to block synthetic ADB `input tap` and robotic macro injectors.
 */
object KolmogorovTouchTopology {

    data class TouchKinematicSample(
        val contactRadiusPx: Float,
        val pressure: Float,
        val prevPressure: Float,
        val gyroMicroTiltRad: Float,
        val deltaTimeMs: Long = 16L
    )

    data class KolmogorovVerdict(
        val splineActivationScore: Double,
        val pressureDerivative: Double,
        val isGenuineBiomechanicalTouch: Boolean
    )

    fun evaluateTouchSample(sample: TouchKinematicSample): KolmogorovVerdict {
        val dtSec = (sample.deltaTimeMs.coerceAtLeast(1L)) / 1000.0
        val dPdt = abs((sample.pressure - sample.prevPressure) / dtSec)

        // Normalize features into [0, 1] domain for univariate cubic B-spline basis evaluation
        val x1 = (sample.contactRadiusPx / 64.0).coerceIn(0.0, 1.0)
        val x2 = (dPdt / 10.0).coerceIn(0.0, 1.0)
        val x3 = (abs(sample.gyroMicroTiltRad) * 50.0).coerceIn(0.0, 1.0)

        var outerSum = 0.0
        for (q in 0..6) {
            val shift = q * 0.12
            val inner = cubicBSpline((x1 + shift) % 1.0) * 0.45 +
                cubicBSpline((x2 + shift) % 1.0) * 0.35 +
                cubicBSpline((x3 + shift) % 1.0) * 0.20
            outerSum += cubicBSpline(inner.coerceIn(0.0, 1.0))
        }

        // Synthetic ADB taps have constant pressure (dP/dt == 0), zero gyro micro-tilt, and fixed radius
        val hasOrganicVariation = sample.contactRadiusPx > 0.5f &&
            (dPdt > 1e-4 || abs(sample.gyroMicroTiltRad) > 1e-5f) &&
            outerSum > 0.1
        return KolmogorovVerdict(
            splineActivationScore = outerSum,
            pressureDerivative = dPdt,
            isGenuineBiomechanicalTouch = hasOrganicVariation
        )
    }

    private fun cubicBSpline(u: Double): Double {
        val t = (u * 2.0).coerceIn(0.0, 2.0)
        return when {
            t < 1.0 -> (1.0 / 6.0) * (4.0 - 6.0 * t * t + 3.0 * t * t * t)
            else -> {
                val s = 2.0 - t
                (1.0 / 6.0) * s * s * s
            }
        }
    }
}
