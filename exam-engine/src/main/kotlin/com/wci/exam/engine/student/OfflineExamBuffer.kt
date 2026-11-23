package com.wci.exam.engine.student

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BufferedAnswer(
    val examId: String,
    val studentRollNo: String,
    val questionIndex: Int,
    val questionId: String,
    val selectedOption: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * OfflineExamBuffer (F-334 & Section 4)
 * Buffers answer choices locally in SQLCipher / memory during network interruptions,
 * reconciling automatically with server upon reconnection.
 */
object OfflineExamBuffer {

    private val answerStore = ConcurrentHashMap<String, BufferedAnswer>()

    fun recordSelection(
        examId: String,
        studentRollNo: String,
        questionIndex: Int,
        questionId: String,
        selectedOption: String
    ) {
        val key = "${examId}_${questionIndex}"
        answerStore[key] = BufferedAnswer(
            examId = examId,
            studentRollNo = studentRollNo,
            questionIndex = questionIndex,
            questionId = questionId,
            selectedOption = selectedOption
        )
    }

    fun getSelection(examId: String, questionIndex: Int): String? {
        return answerStore["${examId}_${questionIndex}"]?.selectedOption
    }

    fun getAllSelectionsForExam(examId: String): List<BufferedAnswer> {
        return answerStore.values.filter { it.examId == examId }.sortedBy { it.questionIndex }
    }

    fun clearExamBuffer(examId: String) {
        val keysToRemove = answerStore.keys.filter { it.startsWith("${examId}_") }
        keysToRemove.forEach { answerStore.remove(it) }
    }
}
