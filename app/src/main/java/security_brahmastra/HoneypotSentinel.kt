package security_brahmastra

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1. `security_brahmastra/HoneypotSentinel.kt`
 * Cyber-Deception & Silent Shadow-Ban Engine.
 *
 * When tampering, Frida hooks, or biometric spoofing is detected, silently activates
 * `isShadowSession = true` via [triggerShadowMode], serves realistic decoy exam questions
 * and fake submission receipts on the UI, and records an append-only audit entry in
 * `intrusion_honeypot_logs`.
 */
object HoneypotSentinel {

    data class IntrusionHoneypotLog(
        val id: String,
        val reason: String,
        val actorId: String,
        val deviceFingerprint: String,
        val ipAddress: String,
        val snapshotDigestHex: String,
        val metadata: Map<String, String>,
        val timestampEpochMs: Long
    )

    data class DecoyExamQuestion(
        val questionId: String,
        val questionText: String,
        val options: List<String>
    )

    private val shadowSessionActive = AtomicBoolean(false)
    private val appendOnlyHoneypotLogs = CopyOnWriteArrayList<IntrusionHoneypotLog>()

    val isShadowSession: Boolean
        get() = shadowSessionActive.get()

    fun triggerShadowMode(
        reason: String,
        metadata: Map<String, String> = emptyMap()
    ): IntrusionHoneypotLog {
        shadowSessionActive.set(true)
        val now = System.currentTimeMillis()
        val entry = IntrusionHoneypotLog(
            id = "hp_${now}_${appendOnlyHoneypotLogs.size + 1}",
            reason = reason.trim().ifBlank { "UNSPECIFIED_INTRUSION" },
            actorId = metadata["actorId"] ?: "unknown_actor",
            deviceFingerprint = metadata["deviceFingerprint"] ?: "android_hw_fp",
            ipAddress = metadata["ip"] ?: "0.0.0.0",
            snapshotDigestHex = metadata["snapshotDigestHex"] ?: "",
            metadata = metadata.toMap(),
            timestampEpochMs = now
        )
        appendOnlyHoneypotLogs.add(entry)
        return entry
    }

    fun getDecoyExamQuestions(): List<DecoyExamQuestion> = listOf(
        DecoyExamQuestion(
            questionId = "DECOY_Q1",
            questionText = "Which OSI layer handles physical bitstream framing in legacy mainframe topologies?",
            options = listOf("Layer 1 (Physical)", "Layer 2 (Data Link)", "Layer 4 (Transport)", "Layer 7 (Application)")
        ),
        DecoyExamQuestion(
            questionId = "DECOY_Q2",
            questionText = "What is the default administrative port for standard local loopback diagnostics?",
            options = listOf("Port 8080", "Port 443", "Port 22", "Port 3306")
        )
    )

    fun buildFakeShadowSubmissionConfirmation(examId: String, studentId: String): Map<String, Any> = mapOf(
        "status" to "SUBMITTED_OK",
        "examId" to examId,
        "studentId" to studentId,
        "receiptCode" to "SHADOW-RCPT-${System.currentTimeMillis() % 100000}",
        "shadowDeception" to true
    )

    fun getIntrusionHoneypotLogs(): List<IntrusionHoneypotLog> = appendOnlyHoneypotLogs.toList()

    fun resetForTests() {
        shadowSessionActive.set(false)
        appendOnlyHoneypotLogs.clear()
    }
}
