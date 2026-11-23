package com.wci.exam.engine.student

import com.wci.core.crypto.ratchet.DoubleRatchetEngine
import com.wci.core.crypto.ratchet.RatchetEncryptedMessage
import java.nio.charset.StandardCharsets

/**
 * EncryptedAnswerSaver (F-333 & S-35)
 * Streams option selections as forward-secret Double-Ratchet encrypted deltas to server.
 */
class EncryptedAnswerSaver(
    private val ratchetEngine: DoubleRatchetEngine
) {
    fun encryptAnswerDelta(
        questionId: String,
        selectedOption: String,
        timestampMs: Long = System.currentTimeMillis()
    ): RatchetEncryptedMessage {
        val payload = "$questionId:$selectedOption:$timestampMs".toByteArray(StandardCharsets.UTF_8)
        return ratchetEngine.encrypt(payload)
    }
}
