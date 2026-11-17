package quantum_physical_shield

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.tanh

/**
 * 4. `quantum_physical_shield/PoincareHyperbolicVault.kt`
 * Non-Euclidean Poincaré Disk Coordinate Space Vault with Dynamic Möbius Transformations:
 *   f(z) = (a * z + b) / (c * z + d), where ad - bc != 0 and |z| < 1.
 *
 * Encodes sensitive session keys and biometric hashes into hyperbolic disk coordinates so
 * raw memory dumps only observe transformed non-Euclidean complex trajectories.
 */
object PoincareHyperbolicVault {

    data class ComplexPoint(val re: Double, val im: Double) {
        fun magnitude(): Double = hypot(re, im)

        operator fun plus(other: ComplexPoint) = ComplexPoint(re + other.re, im + other.im)
        operator fun minus(other: ComplexPoint) = ComplexPoint(re - other.re, im - other.im)
        operator fun times(other: ComplexPoint) = ComplexPoint(
            re = this.re * other.re - this.im * other.im,
            im = this.re * other.im + this.im * other.re
        )
        operator fun div(other: ComplexPoint): ComplexPoint {
            val denom = (other.re * other.re + other.im * other.im).coerceAtLeast(1e-15)
            return ComplexPoint(
                re = (this.re * other.re + this.im * other.im) / denom,
                im = (this.im * other.re - this.re * other.im) / denom
            )
        }
        fun conjugate() = ComplexPoint(re, -im)
    }

    /**
     * Automorphism of the Poincaré unit disk:
     *   M(z) = (z - w0) / (1 - conj(w0) * z), with |w0| < 1
     * Inverse automorphism:
     *   M^-1(u) = (u + w0) / (1 + conj(w0) * u)
     */
    data class MobiusDiskKey(val w0: ComplexPoint) {
        init {
            require(w0.magnitude() < 0.99) { "Poincaré disk anchor |w0| must be strictly < 1." }
        }
    }

    data class HyperbolicVaultEntry(
        val keyAlias: String,
        val transformedPoints: List<ComplexPoint>,
        val originalByteLength: Int
    )

    private val vaultStorage = ConcurrentHashMap<String, HyperbolicVaultEntry>()

    fun applyMobiusTransform(z: ComplexPoint, key: MobiusDiskKey): ComplexPoint {
        val one = ComplexPoint(1.0, 0.0)
        val numerator = z - key.w0
        val denominator = one - (key.w0.conjugate() * z)
        return numerator / denominator
    }

    fun invertMobiusTransform(u: ComplexPoint, key: MobiusDiskKey): ComplexPoint {
        val one = ComplexPoint(1.0, 0.0)
        val numerator = u + key.w0
        val denominator = one + (key.w0.conjugate() * u)
        return numerator / denominator
    }

    fun sealSecretInPoincareDisk(
        keyAlias: String,
        secretText: String,
        mobiusKey: MobiusDiskKey = MobiusDiskKey(ComplexPoint(0.35, -0.28))
    ): HyperbolicVaultEntry {
        val bytes = secretText.toByteArray(Charsets.UTF_8)
        val points = bytes.mapIndexed { idx, b ->
            val unsigned = b.toInt() and 0xFF
            // Map byte [0..255] inside open unit disk |z| < 0.85
            val r = (unsigned.toDouble() - 128.0) / 256.0
            val theta = ((idx % 16) - 8) / 32.0
            val z = ComplexPoint(tanh(r) * 0.75, tanh(theta) * 0.35)
            applyMobiusTransform(z, mobiusKey)
        }
        val entry = HyperbolicVaultEntry(
            keyAlias = keyAlias,
            transformedPoints = points,
            originalByteLength = bytes.size
        )
        vaultStorage[keyAlias] = entry
        return entry
    }

    fun unsealSecretFromPoincareDisk(
        entry: HyperbolicVaultEntry,
        mobiusKey: MobiusDiskKey = MobiusDiskKey(ComplexPoint(0.35, -0.28))
    ): String {
        val out = ByteArray(entry.originalByteLength)
        for (i in 0 until entry.originalByteLength) {
            val z = invertMobiusTransform(entry.transformedPoints[i], mobiusKey)
            val atanhVal = 0.5 * kotlin.math.ln((1.0 + (z.re / 0.75).coerceIn(-0.999, 0.999)) / (1.0 - (z.re / 0.75).coerceIn(-0.999, 0.999)))
            val recovered = kotlin.math.round(atanhVal * 256.0 + 128.0).toInt().coerceIn(0, 255)
            out[i] = recovered.toByte()
        }
        return String(out, Charsets.UTF_8)
    }
}
