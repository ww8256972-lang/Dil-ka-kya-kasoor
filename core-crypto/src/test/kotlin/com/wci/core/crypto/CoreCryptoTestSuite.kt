package com.wci.core.crypto

import com.wci.core.crypto.hash.Argon2idHasher
import com.wci.core.crypto.jwt.DpopTokenBinder
import com.wci.core.crypto.jwt.EphemeralTokenRotator
import com.wci.core.crypto.pqc.HybridPqcKem
import com.wci.core.crypto.qr.Ed25519QrSigner
import com.wci.core.crypto.ratchet.DoubleRatchetEngine
import com.wci.core.crypto.vault.HomomorphicAnswerVault
import com.wci.nativecxx.WciNativeSentinel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class CoreCryptoTestSuite {

    @Test
    fun testWciNativeSentinelMemoryScrubbingAndUnmask() {
        val testBuffer = byteArrayOf(1, 2, 3, 4, 5)
        WciNativeSentinel.scrubMemory(testBuffer)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), testBuffer)

        val key = "SECRET_KEY_1234".toByteArray(StandardCharsets.UTF_8)
        val mask = "MASK".toByteArray(StandardCharsets.UTF_8)
        val masked = ByteArray(key.size) { i -> (key[i].toInt() xor mask[i % mask.size].toInt()).toByte() }

        val unmasked = WciNativeSentinel.unmaskKey(masked, mask)
        assertEquals("SECRET_KEY_1234", String(unmasked, StandardCharsets.UTF_8))
    }

    @Test
    fun testDpopTokenBinderRfc9449Compliance() {
        val binder = DpopTokenBinder()
        val proof = binder.generateDpopProof(
            httpMethod = "POST",
            httpUri = "https://identity.waqarinstitute.in/api/v1/auth/session",
            accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummy_access_token",
            nonce = "server_nonce_98765"
        )

        assertNotNull(proof)
        assertTrue(proof.split(".").size == 3) // Valid JWS compact serialization

        val thumbprint = binder.getJwkThumbprint()
        assertNotNull(thumbprint)
        assertTrue(thumbprint.isNotEmpty())

        val rotator = EphemeralTokenRotator()
        val rotatedProof = rotator.createProof("GET", "https://identity.waqarinstitute.in/api/v1/exams")
        assertNotNull(rotatedProof)
    }

    @Test
    fun testArgon2idHasher() {
        val passwordChars = "WCI_Secure_Admin#2026".toCharArray()
        val result = Argon2idHasher.hashPassword(passwordChars)
        assertNotNull(result.encodedHash)
        assertNotNull(result.saltHex)

        val verifyOk = Argon2idHasher.verifyPassword(
            "WCI_Secure_Admin#2026".toCharArray(),
            result.saltHex,
            result.encodedHash
        )
        assertTrue(verifyOk)

        val verifyFail = Argon2idHasher.verifyPassword(
            "WrongPassword#123".toCharArray(),
            result.saltHex,
            result.encodedHash
        )
        assertFalse(verifyFail)
    }

    @Test
    fun testEd25519QrSigner() {
        val keyPair = Ed25519QrSigner.generateKeyPair()
        val payload = Ed25519QrSigner.buildFeeReceiptPayload(
            receiptNo = "WCI-REC-2026-00042",
            studentRollNo = "WCI-2026-10492",
            amountPaisa = 1500000L, // Rs. 15,000
            timestampMs = 1774320000000L
        )

        val sig = Ed25519QrSigner.sign(payload, keyPair.privateKeyBase64)
        val valid = Ed25519QrSigner.verify(payload, sig, keyPair.publicKeyBase64)
        assertTrue(valid)

        val tamperedPayload = payload.replace("1500000", "500000")
        val invalid = Ed25519QrSigner.verify(tamperedPayload, sig, keyPair.publicKeyBase64)
        assertFalse(invalid)
    }

    @Test
    fun testDoubleRatchetEngine() {
        val rootSecret = ByteArray(32) { (it * 7).toByte() }
        val alice = DoubleRatchetEngine(rootSecret, isInitiator = true)
        val bob = DoubleRatchetEngine(rootSecret, isInitiator = false)

        val secretMessage = "Exam Answer Tick: Question 4 = B".toByteArray(StandardCharsets.UTF_8)
        val encryptedMsg = alice.encrypt(secretMessage)

        val decryptedMsg = bob.decrypt(encryptedMsg)
        assertEquals(String(secretMessage, StandardCharsets.UTF_8), String(decryptedMsg, StandardCharsets.UTF_8))
    }

    @Test
    fun testHomomorphicAnswerVaultAndMerkleRoot() {
        val vault = HomomorphicAnswerVault(studentUid = "student_test_001", examId = "exam_cca_2026")
        val commitments = listOf(
            vault.createCommitment(0, "q_001", "Option A"),
            vault.createCommitment(1, "q_002", "Option C"),
            vault.createCommitment(2, "q_003", "Option D")
        )

        assertEquals(3, commitments.size)
        val merkleRoot = vault.computeMerkleRoot(commitments)
        assertNotNull(merkleRoot)
        assertEquals(64, merkleRoot.length) // SHA-256 hex string

        val manifest = vault.buildSessionManifest(commitments)
        assertEquals("WCI_UNNAO_MAIN", manifest.campusId)
        assertEquals(merkleRoot, manifest.aggregateMerkleRootHex)
    }

    @Test
    fun testHybridPqcKem() {
        val keyPair = HybridPqcKem.generateKeyPair()
        assertNotNull(keyPair.publicKeyBase64)
        assertNotNull(keyPair.privateKeyBase64)

        val enc = HybridPqcKem.encapsulate(keyPair.publicKeyBase64)
        val decSecret = HybridPqcKem.decapsulate(enc.ciphertextBase64, keyPair.privateKeyBase64)

        val originalSecretBytes = java.util.Base64.getDecoder().decode(enc.sharedSecretBase64)
        assertArrayEquals(originalSecretBytes, decSecret)

        val secretPlaintext = "CONFIDENTIAL_EXAM_PAPER_UNNAO".toByteArray(StandardCharsets.UTF_8)
        val envelope = HybridPqcKem.sealEnvelope(secretPlaintext, decSecret)
        assertTrue(envelope.size > secretPlaintext.size)
    }
}
