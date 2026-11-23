package com.wci.core.crypto.attest

import android.content.Context
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class DeviceAttestationEnvelope(
    val campusId: String = "WCI_UNNAO_MAIN",
    val deviceModel: String,
    val androidApiLevel: Int,
    val isStrongBoxBacked: Boolean,
    val publicKeyDerBase64: String,
    val certificateChain: List<String>,
    val signedChallengeHex: String,
    val timestampMs: Long
)

/**
 * TeeAttestationClient (S-06, S-26, S-47)
 * Builds hardware attestation payloads to be verified against AttestVerifier.ts in Cloud Functions.
 */
class TeeAttestationClient(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /**
     * Executes hardware attestation against server challenge.
     */
    fun buildAttestationEnvelope(challengeNonce: String): DeviceAttestationEnvelope {
        val challengeBytes = challengeNonce.toByteArray(StandardCharsets.UTF_8)
        val result = StrongBoxAttestor.generateAttestedKeypair(
            context = context,
            alias = StrongBoxAttestor.DEVICE_ATTESTATION_ALIAS,
            challenge = challengeBytes
        )

        val timestamp = System.currentTimeMillis()
        val payloadToSign = "WCI_UNNAO_MAIN:$challengeNonce:$timestamp".toByteArray(StandardCharsets.UTF_8)
        val signatureBytes = StrongBoxAttestor.sign(
            alias = StrongBoxAttestor.DEVICE_ATTESTATION_ALIAS,
            payload = payloadToSign
        )

        val hexChars = "0123456789abcdef".toCharArray()
        val signatureHex = buildString {
            for (b in signatureBytes) {
                val octet = b.toInt() and 0xFF
                append(hexChars[octet ushr 4])
                append(hexChars[octet and 0x0F])
            }
        }

        return DeviceAttestationEnvelope(
            campusId = "WCI_UNNAO_MAIN",
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            androidApiLevel = android.os.Build.VERSION.SDK_INT,
            isStrongBoxBacked = result.isStrongBox,
            publicKeyDerBase64 = result.publicKeyDerBase64,
            certificateChain = result.certificateChainBase64,
            signedChallengeHex = signatureHex,
            timestampMs = timestamp
        )
    }

    fun serializeEnvelope(envelope: DeviceAttestationEnvelope): String {
        return json.encodeToString(envelope)
    }
}
