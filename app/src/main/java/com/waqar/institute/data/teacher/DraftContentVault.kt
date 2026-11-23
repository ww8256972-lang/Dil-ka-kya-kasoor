package com.waqar.institute.data.teacher

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class CourseDraft(
    val draftId: String,
    val campusId: String = "WCI_UNNAO_MAIN",
    val authorTeacherUid: String,
    val courseId: String,
    val title: String,
    val markdownContent: String,
    val videoResourceUrl: String = "",
    val codingExercisePrompt: String = "",
    val lastAutoSavedMs: Long = System.currentTimeMillis()
)

/**
 * DraftContentVault (F-234 & Section 3.2 Module 2.6)
 * Continuously auto-saves in-progress course notes, video links,
 * and coding challenges locally for seamless continuation across sessions.
 */
object DraftContentVault {

    private val drafts = ConcurrentHashMap<String, CourseDraft>()

    fun saveDraft(draft: CourseDraft) {
        drafts[draft.draftId] = draft.copy(lastAutoSavedMs = System.currentTimeMillis())
    }

    fun getDraft(draftId: String): CourseDraft? = drafts[draftId]

    fun getAllDraftsForTeacher(teacherUid: String): List<CourseDraft> {
        return drafts.values.filter { it.authorTeacherUid == teacherUid }
    }

    fun deleteDraft(draftId: String) {
        drafts.remove(draftId)
    }
}
