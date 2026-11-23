package com.wci.core.crypto.attest

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec

/**
 * StrongBoxAttestor (S-06, S-26, S-47)
 * Generates hardware-isolated ECDSA-P256 keypairs inside Android StrongBox (Titan M2 / Secure Element)
 * or ARM TrustZone TEE with hardware X.509 attestation certificate chains.
 */
object StrongBoxAttestor {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val DEVICE_ATTESTATION_ALIAS = "wci_hw_device_attestation_key"
    const val EXAM_HARDWARE_SEAL_ALIAS = "wci_hw_exam_seal_key"

    data class AttestationResult(
        val alias: String,
        val isStrongBox: Boolean,
        val certificateChainBase64: List<String>,
        val publicKeyDerBase64: String
    )

    /**
     * Checks if device hardware features StrongBox Keymaster.
     */
    fun hasStrongBox(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }

    /**
     * Generates or retrieves an attested ECDSA-P256 keypair with hardware attestation challenge.
     */
    fun generateAttestedKeypair(
        context: Context,
        alias: String = DEVICE_ATTESTATION_ALIAS,
        challenge: ByteArray
    ): AttestationResult {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Remove previous key if re-attesting
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }

        var isStrongBox = false
        val keyPair = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox(context)) {
                try {
                    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                    val spec = KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAttestationChallenge(challenge)
                        .setIsStrongBoxBacked(true)
                        .build()
                    kpg.initialize(spec)
                    val kp = kpg.generateKeyPair()
                    isStrongBox = true
                    kp
                } catch (e: StrongBoxUnavailableException) {
                    generateTeeFallback(alias, challenge)
                } catch (e: Exception) {
                    generateTeeFallback(alias, challenge)
                }
            } else {
                generateTeeFallback(alias, challenge)
            }
        } catch (e: Exception) {
            generateTeeFallback(alias, challenge)
        }

        val chain = keyStore.getCertificateChain(alias) ?: emptyArray()
        val certsBase64 = chain.map { cert ->
            Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        }
        val pubKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        return AttestationResult(
            alias = alias,
            isStrongBox = isStrongBox,
            certificateChainBase64 = certsBase64,
            publicKeyDerBase64 = pubKeyBase64
        )
    }

    private fun generateTeeFallback(alias: String, challenge: ByteArray): KeyPair {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && challenge.isNotEmpty()) {
            builder.setAttestationChallenge(challenge)
        }

        kpg.initialize(builder.build())
        return kpg.generateKeyPair()
    }

    /**
     * Signs data using the hardware-backed private key (S-26).
     */
    fun sign(alias: String, payload: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(payload)
        return signature.sign()
    }

    /**
     * Verifies signature against the alias's public certificate.
     */
    fun verify(alias: String, payload: ByteArray, signatureBytes: ByteArray): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert: Certificate? = keyStore.getCertificate(alias) ?: return false
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(cert.publicKey)
        signature.update(payload)
        return signature.verify(signatureBytes)
    }
}
