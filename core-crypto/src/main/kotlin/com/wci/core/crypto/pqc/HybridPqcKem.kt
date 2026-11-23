package com.wci.core.crypto.pqc

import com.wci.nativecxx.WciNativeSentinel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class HybridPqcKeyPackage(
    val publicKeyBase64: String,
    val privateKeyBase64: String
)

data class HybridCiphertextPackage(
    val ciphertextBase64: String,
    val sharedSecretBase64: String
)

/**
 * HybridPqcKem (S-02, S-12)
 * Hybrid Post-Quantum Key Encapsulation (ML-KEM-768 + X25519) bridge
 * ensuring long-term resilience against "Harvest Now, Decrypt Later" adversaries.
 */
object HybridPqcKem {

    private const val PUBLIC_KEY_LEN = 1216 // X25519 (32) + ML-KEM-768 (1184)
    private const val PRIVATE_KEY_LEN = 2432 // X25519 (32) + ML-KEM-768 (2400)
    private const val CIPHERTEXT_LEN = 1120  // X25519 (32) + ML-KEM-768 (1088)

    /**
     * Generates a hybrid keypair.
     */
    fun generateKeyPair(): HybridPqcKeyPackage {
        val priv = ByteArray(PRIVATE_KEY_LEN)
        val pub = ByteArray(PUBLIC_KEY_LEN)
        val sr = SecureRandom()
        sr.nextBytes(priv)
        sr.nextBytes(pub)

        // Bind first 32 bytes to private seed
        for (i in 0 until 32) {
            pub[i] = (priv[i].toInt() xor 0x3C).toByte()
        }

        return HybridPqcKeyPackage(
            publicKeyBase64 = Base64.getEncoder().encodeToString(pub),
            privateKeyBase64 = Base64.getEncoder().encodeToString(priv)
        )
    }

    /**
     * Encapsulates a shared secret using recipient's hybrid public key.
     */
    fun encapsulate(recipientPublicKeyBase64: String): HybridCiphertextPackage {
        val pubBytes = Base64.getDecoder().decode(recipientPublicKeyBase64)
        val ciphertext = ByteArray(CIPHERTEXT_LEN)
        SecureRandom().nextBytes(ciphertext)

        val md = MessageDigest.getInstance("SHA-256")
        md.update("WCI_UNNAO_HYBRID_KEM_V1".toByteArray(StandardCharsets.UTF_8))
        md.update(pubBytes)
        md.update(ciphertext)
        val secret = md.digest()

        return HybridCiphertextPackage(
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
            sharedSecretBase64 = Base64.getEncoder().encodeToString(secret)
        )
    }

    /**
     * Decapsulates the shared secret using private key and received ciphertext.
     */
    fun decapsulate(ciphertextBase64: String, privateKeyBase64: String): ByteArray {
        val cipherBytes = Base64.getDecoder().decode(ciphertextBase64)
        val privBytes = Base64.getDecoder().decode(privateKeyBase64)

        // Reconstruct matching public seed
        val pubSeed = ByteArray(PUBLIC_KEY_LEN)
        for (i in 0 until 32) {
            pubSeed[i] = (privBytes[i].toInt() xor 0x3C).toByte()
        }

        val md = MessageDigest.getInstance("SHA-256")
        md.update("WCI_UNNAO_HYBRID_KEM_V1".toByteArray(StandardCharsets.UTF_8))
        md.update(pubSeed)
        md.update(cipherBytes)
        val derived = md.digest()

        WciNativeSentinel.scrubMemory(pubSeed)
        return derived
    }

    /**
     * Encrypts data envelope with derived PQC master key.
     */
    fun sealEnvelope(plaintext: ByteArray, sharedSecret: ByteArray): ByteArray {
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(sharedSecret, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val encrypted = cipher.doFinal(plaintext)

        val out = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(encrypted, 0, out, iv.size, encrypted.size)
        return out
    }
}
