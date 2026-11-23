package com.wci.core.crypto.vault

import com.wci.nativecxx.WciNativeSentinel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class AnswerCommitment(
    val questionIndex: Int,
    val questionId: String,
    val commitmentHex: String,
    val blindingFactorHex: String
)

@Serializable
data class ExamSessionCommitmentManifest(
    val campusId: String = "WCI_UNNAO_MAIN",
    val studentUid: String,
    val examId: String,
    val commitments: List<AnswerCommitment>,
    val aggregateMerkleRootHex: String,
    val committedTimestampMs: Long
)

/**
 * HomomorphicAnswerVault (S-49)
 * Generates cryptographic answer commitments and Merkle roots to ensure tamper-proof
 * answer integrity between local device and ConfidentialGrader.ts in Cloud Functions.
 */
class HomomorphicAnswerVault(
    private val studentUid: String,
    private val examId: String
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val secureRandom = SecureRandom()

    /**
     * Commits a selected option for a given question index.
     */
    fun createCommitment(questionIndex: Int, questionId: String, selectedOption: String): AnswerCommitment {
        val blinding = ByteArray(32)
        secureRandom.nextBytes(blinding)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(blinding, "HmacSHA256"))

        val dataToCommit = "WCI_UNNAO_MAIN:$studentUid:$examId:$questionId:$selectedOption"
        val commitmentBytes = mac.doFinal(dataToCommit.toByteArray(StandardCharsets.UTF_8))

        return AnswerCommitment(
            questionIndex = questionIndex,
            questionId = questionId,
            commitmentHex = bytesToHex(commitmentBytes),
            blindingFactorHex = bytesToHex(blinding)
        )
    }

    /**
     * Builds the aggregate Merkle Root across all question commitments.
     */
    fun computeMerkleRoot(commitments: List<AnswerCommitment>): String {
        if (commitments.isEmpty()) {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest("EMPTY_MANIFEST".toByteArray()))
        }

        var currentLevel = commitments.map { hexToBytes(it.commitmentHex) }
        val md = MessageDigest.getInstance("SHA-256")

        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<ByteArray>()
            for (i in currentLevel.indices step 2) {
                val left = currentLevel[i]
                val right = if (i + 1 < currentLevel.size) currentLevel[i + 1] else left
                md.update(left)
                md.update(right)
                nextLevel.add(md.digest())
            }
            currentLevel = nextLevel
        }

        return bytesToHex(currentLevel.first())
    }

    /**
     * Compiles complete sealed exam session commitment manifest.
     */
    fun buildSessionManifest(commitments: List<AnswerCommitment>): ExamSessionCommitmentManifest {
        val merkleRoot = computeMerkleRoot(commitments)
        return ExamSessionCommitmentManifest(
            campusId = "WCI_UNNAO_MAIN",
            studentUid = studentUid,
            examId = examId,
            commitments = commitments,
            aggregateMerkleRootHex = merkleRoot,
            committedTimestampMs = System.currentTimeMillis()
        )
    }

    fun serializeManifest(manifest: ExamSessionCommitmentManifest): String {
        return json.encodeToString(manifest)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef".toCharArray()
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val octet = b.toInt() and 0xFF
            result.append(hexChars[octet ushr 4])
            result.append(hexChars[octet and 0x0F])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
