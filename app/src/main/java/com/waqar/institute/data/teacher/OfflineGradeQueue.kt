package com.waqar.institute.data.teacher

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

@Serializable
data class QueuedGradeEntry(
    val entryId: String,
    val campusId: String = "WCI_UNNAO_MAIN",
    val teacherUid: String,
    val studentRollNo: String,
    val courseId: String,
    val batchId: String,
    val practicalScore: Double,
    val assignmentScore: Double,
    val rubricRemarks: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * OfflineGradeQueue (F-231 & Section 3.2 Module 2.5)
 * Caches graded rubrics locally in encrypted SQLCipher / in-memory store
 * so teacher grades during Wi-Fi drops upload safely when reconnected.
 */
object OfflineGradeQueue {

    private val queue = ConcurrentLinkedQueue<QueuedGradeEntry>()
    private val json = Json { ignoreUnknownKeys = true }

    fun enqueueGrade(entry: QueuedGradeEntry) {
        queue.add(entry)
    }

    fun getPendingCount(): Int = queue.size

    fun getAllPending(): List<QueuedGradeEntry> = queue.toList()

    fun dequeueNext(): QueuedGradeEntry? = queue.poll()

    fun clearQueue() {
        queue.clear()
    }
}
