package com.wci.core.crypto.ratchet

import com.wci.nativecxx.WciNativeSentinel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class RatchetEncryptedMessage(
    val senderEphemeralPubBase64: String,
    val messageSequence: Int,
    val previousChainLength: Int,
    val ivBase64: String,
    val ciphertextBase64: String
)

/**
 * DoubleRatchetEngine (S-35)
 * Forward-Secret messaging and exam answer delta synchronization engine
 * based on Diffie-Hellman Ratchet + Symmetric KDF Chain Ratchet.
 */
class DoubleRatchetEngine(
    initialRootKey: ByteArray,
    private val isInitiator: Boolean,
    remoteDhPublicKey: PublicKey? = null
) {
    private var rootKey: ByteArray = initialRootKey.copyOf()
    private var localDhKeyPair: KeyPair = generateEcKeyPair()
    private var remoteDhPublicKey: PublicKey? = remoteDhPublicKey

    private var sendingChainKey: ByteArray? = null
    private var receivingChainKey: ByteArray? = null

    private var sendSequence: Int = 0
    private var receiveSequence: Int = 0
    private var previousChainLength: Int = 0

    init {
        if (isInitiator && remoteDhPublicKey != null) {
            val dhSecret = computeDh(localDhKeyPair.private, remoteDhPublicKey)
            val kdfResult = kdfRootKey(rootKey, dhSecret)
            rootKey = kdfResult.first
            sendingChainKey = kdfResult.second
        }
    }

    /**
     * Encrypts plaintext payload, advancing the sending ratchet chain.
     */
    @Synchronized
    fun encrypt(plaintext: ByteArray): RatchetEncryptedMessage {
        val currentSendChain = sendingChainKey ?: run {
            val newKeyPair = generateEcKeyPair()
            localDhKeyPair = newKeyPair
            val dhSecret = computeDh(localDhKeyPair.private, remoteDhPublicKey!!)
            val kdfResult = kdfRootKey(rootKey, dhSecret)
            rootKey = kdfResult.first
            sendingChainKey = kdfResult.second
            kdfResult.second
        }

        val (nextChain, messageKey) = kdfChainKey(currentSendChain)
        sendingChainKey = nextChain

        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(messageKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)

        WciNativeSentinel.scrubMemory(messageKey)

        val pubKeyBase64 = Base64.getEncoder().encodeToString(localDhKeyPair.public.encoded)
        val message = RatchetEncryptedMessage(
            senderEphemeralPubBase64 = pubKeyBase64,
            messageSequence = sendSequence++,
            previousChainLength = previousChainLength,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)
        )

        return message
    }

    /**
     * Decrypts received ratchet message, advancing receiving ratchet chain.
     */
    @Synchronized
    fun decrypt(message: RatchetEncryptedMessage): ByteArray {
        val senderPub = decodePublicKey(message.senderEphemeralPubBase64)

        // Check if DH ratchet must turn
        if (remoteDhPublicKey == null || !remoteDhPublicKey!!.encoded.contentEquals(senderPub.encoded)) {
            previousChainLength = sendSequence
            sendSequence = 0
            receiveSequence = 0
            remoteDhPublicKey = senderPub

            val dhReceive = computeDh(localDhKeyPair.private, senderPub)
            val kdfRecv = kdfRootKey(rootKey, dhReceive)
            rootKey = kdfRecv.first
            receivingChainKey = kdfRecv.second

            localDhKeyPair = generateEcKeyPair()
            val dhSend = computeDh(localDhKeyPair.private, senderPub)
            val kdfSend = kdfRootKey(rootKey, dhSend)
            rootKey = kdfSend.first
            sendingChainKey = kdfSend.second
        }

        val currentRecvChain = receivingChainKey
            ?: throw IllegalStateException("Receiving chain key uninitialized")

        val (nextChain, messageKey) = kdfChainKey(currentRecvChain)
        receivingChainKey = nextChain
        receiveSequence++

        val iv = Base64.getDecoder().decode(message.ivBase64)
        val ciphertext = Base64.getDecoder().decode(message.ciphertextBase64)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(messageKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = cipher.doFinal(ciphertext)

        WciNativeSentinel.scrubMemory(messageKey)
        return decrypted
    }

    private fun kdfRootKey(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(rk, "HmacSHA256"))
        }
        val master = mac.doFinal(dhOut)

        val nextRkMac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(master, "HmacSHA256"))
        }
        val nextRk = nextRkMac.doFinal("WCI_RATCHET_ROOT_SALT".toByteArray(StandardCharsets.UTF_8))

        val nextCkMac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(master, "HmacSHA256"))
        }
        val nextCk = nextCkMac.doFinal("WCI_RATCHET_CHAIN_SALT".toByteArray(StandardCharsets.UTF_8))

        return Pair(nextRk, nextCk)
    }

    private fun kdfChainKey(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val mac1 = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ck, "HmacSHA256")) }
        val nextChain = mac1.doFinal(byteArrayOf(0x01))

        val mac2 = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ck, "HmacSHA256")) }
        val messageKey = mac2.doFinal(byteArrayOf(0x02))

        return Pair(nextChain, messageKey)
    }

    private fun computeDh(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    private fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    private fun decodePublicKey(pubBase64: String): PublicKey {
        val bytes = Base64.getDecoder().decode(pubBase64)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(bytes))
    }
}
