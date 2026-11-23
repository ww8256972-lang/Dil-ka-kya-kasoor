package com.wci.core.crypto.biometric

import android.util.Base64
import androidx.biometric.BiometricPrompt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyStore

@Serializable
data class BiometricChallengeProof(
    val campusId: String = "WCI_UNNAO_MAIN",
    val uid: String,
    val action: String,
    val serverNonce: String,
    val timestampMs: Long,
    val signatureBase64: String,
    val publicKeyDerBase64: String
)

/**
 * BioChallengeEngine (S-36, S-50)
 * Binds single-use server nonces to biometric-authenticated CryptoObject signatures.
 */
object BioChallengeEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Signs the server challenge using the successfully unlocked BiometricPrompt.CryptoObject.
     */
    fun signChallenge(
        cryptoObject: BiometricPrompt.CryptoObject,
        uid: String,
        action: String,
        serverNonce: String,
        alias: String = BiometricEnclaveBridge.BIOMETRIC_AUTH_ALIAS
    ): BiometricChallengeProof {
        val signature = cryptoObject.signature
            ?: throw IllegalStateException("CryptoObject does not contain an initialized Signature")

        val timestamp = System.currentTimeMillis()
        val dataToSign = "WCI_UNNAO_MAIN:$uid:$action:$serverNonce:$timestamp".toByteArray(StandardCharsets.UTF_8)
        signature.update(dataToSign)
        val signedBytes = signature.sign()

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = keyStore.getCertificate(alias)
            ?: throw IllegalStateException("Biometric certificate not found for alias: $alias")

        return BiometricChallengeProof(
            campusId = "WCI_UNNAO_MAIN",
            uid = uid,
            action = action,
            serverNonce = serverNonce,
            timestampMs = timestamp,
            signatureBase64 = Base64.encodeToString(signedBytes, Base64.NO_WRAP),
            publicKeyDerBase64 = Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        )
    }

    fun serializeProof(proof: BiometricChallengeProof): String {
        return json.encodeToString(proof)
    }
}
