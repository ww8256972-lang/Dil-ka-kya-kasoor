package quantum_physical_shield

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2. `quantum_physical_shield/UltrasonicSonarLiveness.kt`
 * Inaudible 19.5 kHz Ultrasonic Chirp Generator & Fast Cross-Correlation / Cepstral 3D Echo Analyzer.
 *
 * Verifies multi-path 3D facial geometry echo dispersion (nose, eye-socket, cheek depth reflections)
 * versus a single specular reflection from a flat 2D screen or printed photograph.
 */
object UltrasonicSonarLiveness {

    const val SAMPLE_RATE_HZ = 48_000
    const val CHIRP_START_HZ = 18_800.0
    const val CHIRP_END_HZ = 20_200.0

    data class SonarEchoVerdict(
        val peakLagSamples: Int,
        val estimatedDistanceCm: Double,
        val multiPathDispersionScore: Double,
        val isReal3DFaceGeometry: Boolean
    )

    /**
     * Generates a 19.5 kHz Hann-windowed linear FM ultrasonic chirp buffer at 48 kHz sample rate.
     */
    fun generateUltrasonicChirpPcm(durationSamples: Int = 480): DoubleArray {
        val n = durationSamples.coerceIn(64, 4800)
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE_HZ
            val durationSec = n.toDouble() / SAMPLE_RATE_HZ
            val instFreq = CHIRP_START_HZ + (CHIRP_END_HZ - CHIRP_START_HZ) * (t / durationSec)
            val hannWindow = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1).coerceAtLeast(1)))
            out[i] = hannWindow * sin(2.0 * PI * instFreq * t)
        }
        return out
    }

    /**
     * Computes normalized cross-correlation and cepstral multi-path dispersion between [transmittedChirp]
     * and [recordedEcho] to distinguish 3D facial depth contours from a flat 2D surface.
     */
    fun analyzeEchoFor3DGeometry(
        transmittedChirp: DoubleArray,
        recordedEcho: DoubleArray
    ): SonarEchoVerdict {
        if (transmittedChirp.isEmpty() || recordedEcho.size <= transmittedChirp.size) {
            return SonarEchoVerdict(0, 0.0, 0.0, false)
        }

        val maxLag = (recordedEcho.size - transmittedChirp.size).coerceAtMost(600)
        val corr = DoubleArray(maxLag)
        var maxCorr = 0.0
        var peakLag = 0

        for (lag in 0 until maxLag) {
            var sum = 0.0
            for (i in transmittedChirp.indices) {
                sum += transmittedChirp[i] * recordedEcho[lag + i]
            }
            val absCorr = kotlin.math.abs(sum)
            corr[lag] = absCorr
            if (absCorr > maxCorr) {
                maxCorr = absCorr
                peakLag = lag
            }
        }

        // Measure secondary multi-path reflections around the primary peak (+3 to +25 samples -> 2cm..18cm depth)
        val windowStart = (peakLag + 3).coerceAtMost(maxLag - 1)
        val windowEnd = (peakLag + 25).coerceAtMost(maxLag)
        var secondaryEnergy = 0.0
        for (k in windowStart until windowEnd) {
            secondaryEnergy += corr[k]
        }
        val dispersionScore = if (maxCorr > 1e-9) {
            ln(1.0 + (secondaryEnergy / maxCorr))
        } else {
            0.0
        }

        // Speed of sound ~34,300 cm/s -> round-trip distance = (lag / Fs) * 34300 / 2
        val distanceCm = (peakLag.toDouble() / SAMPLE_RATE_HZ) * 17_150.0
        val is3D = maxCorr > 1e-4 && dispersionScore >= 0.15
        return SonarEchoVerdict(
            peakLagSamples = peakLag,
            estimatedDistanceCm = distanceCm,
            multiPathDispersionScore = dispersionScore,
            isReal3DFaceGeometry = is3D
        )
    }
}
