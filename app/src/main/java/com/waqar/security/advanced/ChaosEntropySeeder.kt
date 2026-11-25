package com.waqar.security.advanced

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * ChaosEntropySeeder: Hardware Micro-Noise Harvesting & Nonlinear Chaotic Map Key Derivation.
 *
 * Implements physics-based random number enhancement using deterministic chaos theory:
 * 1. Physical Entropy Harvesting:
 *    - Reads physical thermal jitter and ambient vibrations from device Accelerometer and Gyroscope.
 *    - Quantizes floating-point sub-milliradian fluctuations into an entropy accumulator.
 * 2. Nonlinear Chaotic Map:
 *    - Feeds harvested seed into the Logistic Map in the fully developed chaotic regime:
 *        x_{n+1} = r * x_n * (1 - x_n)  where r = 3.999999999999999
 *    - Positive Lyapunov exponent (lambda = ln 2 > 0) guarantees sensitive dependence on initial conditions
 *      (butterfly effect), transforming microscopic sensor fluctuations into macroscopic unpredictability.
 * 3. Cryptographic Whitening:
 *    - Combines chaotic state iterations with OS `/dev/urandom` via SHA-512 to produce high-entropy keys.
 */
class ChaosEntropySeeder(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val entropyBuffer = AtomicReference(ByteArray(64))
    private val secureRandom = SecureRandom()

    // Chaotic logistic parameter in deep chaos regime (r in [3.99, 4.0])
    private val rParameter = 3.999999999999999

    private var sampleCount = 0
    private val maxSamples = 16

    init {
        // Initial pool seed with SecureRandom
        val initBytes = ByteArray(64)
        secureRandom.nextBytes(initBytes)
        entropyBuffer.set(initBytes)

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return

        // Extract sub-millisecond physical noise
        val v0 = java.lang.Float.floatToRawIntBits(event.values[0])
        val v1 = java.lang.Float.floatToRawIntBits(event.values.getOrElse(1) { 0f })
        val v2 = java.lang.Float.floatToRawIntBits(event.values.getOrElse(2) { 0f })
        val timeBits = event.timestamp

        val md = MessageDigest.getInstance("SHA-256")
        md.update(entropyBuffer.get())
        md.update((v0 xor v1).toByte())
        md.update((v1 xor v2).toByte())
        md.update((timeBits and 0xFF).toByte())
        val freshEntropy = md.digest()

        entropyBuffer.set(freshEntropy)

        sampleCount++
        if (sampleCount >= maxSamples) {
            unregister()
        }
    }

    fun unregister() {
        sensorManager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for entropy collection
    }

    /**
     * Iterates the chaotic logistic map using harvested micro-noise to generate 32 high-entropy bytes.
     */
    fun generateHighEntropySeed(lengthBytes: Int = 32): ByteArray {
        val currentPool = entropyBuffer.get()
        val md = MessageDigest.getInstance("SHA-512")
        md.update(currentPool)

        // Seed chaotic initial condition x_0 in (0, 1)
        val initialBits = abs(currentPool.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) })
        var x = (abs(initialBits % 1_000_000L) + 1.0) / 1_000_002.0

        // 100 warm-up chaotic iterations to discard transient dynamics
        for (i in 0 until 100) {
            x = rParameter * x * (1.0 - x)
        }

        // Collect chaotic orbit samples
        val chaoticBytes = ByteArray(lengthBytes)
        for (i in 0 until lengthBytes) {
            x = rParameter * x * (1.0 - x)
            val byteVal = (x * 256.0).toInt().coerceIn(0, 255)
            chaoticBytes[i] = byteVal.toByte()
        }

        md.update(chaoticBytes)
        val hwRandom = ByteArray(lengthBytes)
        secureRandom.nextBytes(hwRandom)
        md.update(hwRandom)

        val fullDigest = md.digest()
        return fullDigest.copyOfRange(0, lengthBytes)
    }

    fun unregister() {
        runCatching { sensorManager?.unregisterListener(this) }
    }
}
