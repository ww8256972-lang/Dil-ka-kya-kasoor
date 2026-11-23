package com.wci.core.crypto.qr

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

/**
 * Ed25519QrSigner
 * Cryptographic signer and offline verifier for student QR cards,
 * course completion certificates, and offline fee receipts (Section 3.4 & Module 4).
 */
object Ed25519QrSigner {

    data class Ed25519KeyPairBytes(
        val privateKeyBase64: String,
        val publicKeyBase64: String
    )

    /**
     * Generates a pure Ed25519 keypair.
     */
    fun generateKeyPair(): Ed25519KeyPairBytes {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val pair = gen.generateKeyPair()

        val priv = pair.private as Ed25519PrivateKeyParameters
        val pub = pair.public as Ed25519PublicKeyParameters

        return Ed25519KeyPairBytes(
            privateKeyBase64 = Base64.getEncoder().encodeToString(priv.encoded),
            publicKeyBase64 = Base64.getEncoder().encodeToString(pub.encoded)
        )
    }

    /**
     * Signs data string using Ed25519 private key.
     */
    fun sign(payload: String, privateKeyBase64: String): String {
        val privBytes = Base64.getDecoder().decode(privateKeyBase64)
        val privateKey = Ed25519PrivateKeyParameters(privBytes, 0)

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val data = payload.toByteArray(StandardCharsets.UTF_8)
        signer.update(data, 0, data.size)
        val signature = signer.generateSignature()

        return Base64.getEncoder().encodeToString(signature)
    }

    /**
     * Verifies data string signature using Ed25519 public key.
     */
    fun verify(payload: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val pubBytes = Base64.getDecoder().decode(publicKeyBase64)
            val publicKey = Ed25519PublicKeyParameters(pubBytes, 0)
            val sigBytes = Base64.getDecoder().decode(signatureBase64)

            val verifier = Ed25519Signer()
            verifier.init(false, publicKey)
            val data = payload.toByteArray(StandardCharsets.UTF_8)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Formats an offline fee receipt QR payload string:
     * WCI_UNNAO_MAIN|receiptNo|studentRollNo|amountPaisa|timestamp
     */
    fun buildFeeReceiptPayload(
        receiptNo: String,
        studentRollNo: String,
        amountPaisa: Long,
        timestampMs: Long
    ): String {
        return "WCI_UNNAO_MAIN|$receiptNo|$studentRollNo|$amountPaisa|$timestampMs"
    }
}
