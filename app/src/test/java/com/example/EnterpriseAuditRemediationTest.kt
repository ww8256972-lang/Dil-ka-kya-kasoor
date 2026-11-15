package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.FirebaseAuthServiceProvider
import com.example.data.firestore.InstituteRepository
import com.example.data.model.BatchRecord
import com.example.data.model.PortalRole
import com.example.data.model.StudentRecord
import com.example.security.AppCheckDistributionStrategy
import com.example.security.ensureFirebaseInitialized
import com.example.security.resolveReleaseAppCheckStrategy
import com.example.viewmodel.TeacherScopeMatcher
import com.waqar.institute.biometric.FaceBiometricVerificationEngine
import com.waqar.institute.biometric.TeacherAttendanceSyncManager
import com.waqar.institute.biometric.TeacherBiometricGateManager
import com.waqar.institute.session.ConcurrentSessionManager
import com.waqar.institute.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnterpriseAuditRemediationTest {

    @Test
    fun faceBiometricEngine_failsClosedOnMissingCorruptedUnavailableAndMismatch() {
        val liveVec = FloatArray(128) { i -> kotlin.math.sin((i + 1) * 0.19f) }
        val otherVec = FloatArray(128) { i -> kotlin.math.cos((i + 1) * 0.43f) }

        val missingRes = FaceBiometricVerificationEngine.evaluateLiveVectorAgainstEnrollment(
            liveEmbedding = liveVec,
            livenessScore = 0.95f,
            lookupResult = FaceBiometricVerificationEngine.EnrollmentLookupResult.Missing
        )
        assertTrue(
            "Missing enrollment must fail closed with Error, got $missingRes",
            missingRes is FaceBiometricVerificationEngine.BiometricMatchResult.Error
        )

        val unavailableRes = FaceBiometricVerificationEngine.evaluateLiveVectorAgainstEnrollment(
            liveEmbedding = liveVec,
            livenessScore = 0.95f,
            lookupResult = FaceBiometricVerificationEngine.EnrollmentLookupResult.Unavailable("Offline timeout")
        )
        assertTrue(
            "Unavailable enrollment must fail closed with Error, got $unavailableRes",
            unavailableRes is FaceBiometricVerificationEngine.BiometricMatchResult.Error
        )

        val corruptedRes = FaceBiometricVerificationEngine.evaluateLiveVectorAgainstEnrollment(
            liveEmbedding = liveVec,
            livenessScore = 0.95f,
            lookupResult = FaceBiometricVerificationEngine.EnrollmentLookupResult.Corrupted("Invalid vector")
        )
        assertTrue(
            "Corrupted enrollment must fail closed with Error, got $corruptedRes",
            corruptedRes is FaceBiometricVerificationEngine.BiometricMatchResult.Error
        )

        val mismatchRes = FaceBiometricVerificationEngine.evaluateLiveVectorAgainstEnrollment(
            liveEmbedding = liveVec,
            livenessScore = 0.95f,
            lookupResult = FaceBiometricVerificationEngine.EnrollmentLookupResult.Found(otherVec, "stf_real_1")
        )
        assertTrue(
            "Distinct face vector must return Mismatch, got $mismatchRes",
            mismatchRes is FaceBiometricVerificationEngine.BiometricMatchResult.Mismatch
        )

        val verifiedRes = FaceBiometricVerificationEngine.evaluateLiveVectorAgainstEnrollment(
            liveEmbedding = liveVec,
            livenessScore = 0.96f,
            lookupResult = FaceBiometricVerificationEngine.EnrollmentLookupResult.Found(liveVec, "stf_real_1")
        )
        assertTrue(verifiedRes is FaceBiometricVerificationEngine.BiometricMatchResult.Verified)
        assertFalse(
            "Verified result must never claim fail-open newlyEnrolled",
            (verifiedRes as FaceBiometricVerificationEngine.BiometricMatchResult.Verified).newlyEnrolled
        )
    }

    @Test
    fun faceBiometricEngine_rejectsInvalidOrPlaceholderTeacherUids() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (badUid in listOf("", " ", "teacher", "default_teacher", "cred_teacher")) {
            val lookup = FaceBiometricVerificationEngine.resolveEnrolledEmbeddingResult(
                context = context,
                teacherUid = badUid,
                teacherName = "Demo Teacher"
            )
            assertTrue(
                "Invalid teacher UID '$badUid' must be rejected as Corrupted, got $lookup",
                lookup is FaceBiometricVerificationEngine.EnrollmentLookupResult.Corrupted
            )
        }
    }

    @Test
    fun teacherBiometricGateManager_blocksPlaceholderTeacherIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (badId in listOf("", "teacher", "default_teacher", "cred_teacher")) {
            TeacherBiometricGateManager.recordSuccessful24HourVerification(context, badId, 0.99f)
            assertFalse(
                "Placeholder teacher ID '$badId' must never become verified",
                TeacherBiometricGateManager.isTeacherVerifiedWithin24Hours(context, badId)
            )
        }
    }

    @Test
    fun teacherAttendanceSyncManager_rejectsInvalidTeacherUid() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (badId in listOf("", "teacher", "default_teacher")) {
            val res = TeacherAttendanceSyncManager.markTeacherPresentOnBiometricPass(
                context = context,
                teacherUid = badId,
                teacherName = "Test Teacher",
                cosineSimilarity = 0.95f,
                livenessConfidence = 0.95f
            )
            assertTrue(
                "Invalid teacher UID '$badId' must fail attendance sync, got $res",
                res is TeacherAttendanceSyncManager.TeacherAttendanceSyncResult.Failed
            )
        }
    }

    @Test
    fun concurrentSessionManager_returnsFalseWhenNoValidLocalSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SessionManager.clearLocalSession(context)
        ConcurrentSessionManager.stopWatchingSession()
        assertFalse(
            "Unbound/cleared session must return false from verifySubMillisecondSession",
            ConcurrentSessionManager.verifySubMillisecondSession(context, "student_test")
        )
    }

    @Test
    fun appCheckHelper_selectsDebugSideloadProviderOrRecaptchaForSideloadedApk() {
        assertEquals(
            AppCheckDistributionStrategy.RECAPTCHA_ENTERPRISE_SIDELOAD,
            resolveReleaseAppCheckStrategy("6L_REAL_RECAPTCHA_ENTERPRISE_SITE_KEY")
        )
        assertEquals(
            AppCheckDistributionStrategy.DEBUG_PROVIDER,
            resolveReleaseAppCheckStrategy("6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI")
        )
        assertEquals(
            AppCheckDistributionStrategy.DEBUG_PROVIDER,
            resolveReleaseAppCheckStrategy("")
        )
        assertEquals(
            AppCheckDistributionStrategy.DEBUG_PROVIDER,
            resolveReleaseAppCheckStrategy(null)
        )
    }

    @Test
    fun teacherFirstTime128DLiveBlinkEnrollment_and_mandatoryEveryLoginVerification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val teacherId = "stf_live_blink_teacher_01"
        val liveVec = FloatArray(128) { i -> kotlin.math.sin((i + 1) * 0.21f) }

        TeacherBiometricGateManager.clearLoginVerificationOnAuthChange()
        assertFalse(TeacherBiometricGateManager.isTeacherVerifiedForCurrentLogin(teacherId))

        val enrollRes = FaceBiometricVerificationEngine.enrollTeacherFirstTimeLiveBlinkEmbedding(
            context = context,
            teacherUid = teacherId,
            teacherName = "Prof. Salman",
            liveEmbedding = liveVec,
            livenessScore = 0.94f
        )
        assertTrue(
            "First-time 128-D Live Blink enrollment must succeed, got $enrollRes",
            enrollRes is FaceBiometricVerificationEngine.BiometricMatchResult.Verified &&
                enrollRes.newlyEnrolled
        )

        TeacherBiometricGateManager.recordSuccessfulLoginVerification(context, teacherId, 1.0f)
        assertTrue(TeacherBiometricGateManager.isTeacherVerifiedForCurrentLogin(teacherId))

        // On sign-out or next login, per-login verification resets so live blink is mandatory on every login
        TeacherBiometricGateManager.clearLoginVerificationOnAuthChange()
        assertFalse(TeacherBiometricGateManager.isTeacherVerifiedForCurrentLogin(teacherId))

        // Subsequent login finds the enrolled 128-D vector and verifies against it
        val lookup = FaceBiometricVerificationEngine.resolveEnrolledEmbeddingResult(
            context = context,
            teacherUid = teacherId,
            teacherName = "Prof. Salman"
        )
        assertTrue(lookup is FaceBiometricVerificationEngine.EnrollmentLookupResult.Found)
        val verifyRes = FaceBiometricVerificationEngine.evaluateLiveVectorAgainstEnrollment(
            liveEmbedding = liveVec,
            livenessScore = 0.92f,
            lookupResult = lookup
        )
        assertTrue(verifyRes is FaceBiometricVerificationEngine.BiometricMatchResult.Verified)
    }

    @Test
    fun teacherScopeMatcher_isolatesBatchesAndStudentsAcrossCaseWhitespaceAndBatchAdmin() {
        val batches = listOf(
            BatchRecord(
                id = "bat_adca_m",
                code = "ADCA-M",
                name = "ADCA Morning 9AM",
                courseId = "crs_adca",
                courseName = "ADCA",
                teacherId = "stf_ravi_01",
                teacherName = "Ravi Kumar"
            ),
            BatchRecord(
                id = "bat_tally_e",
                code = "TLY-E",
                name = "Tally Evening 5PM",
                courseId = "crs_tally",
                courseName = "Tally Prime",
                teacherId = "",
                teacherName = "  Dr. Ravi  Kumar  (Batch Admin) "
            ),
            BatchRecord(
                id = "bat_python_n",
                code = "PY-N",
                name = "Python Noon",
                courseId = "crs_py",
                courseName = "Python",
                teacherId = "stf_neha_02",
                teacherName = "Neha Verma"
            )
        )

        val scopedBatches = TeacherScopeMatcher.filterBatchesForTeacher(
            batches = batches,
            teacherIdKeys = setOf("stf_ravi_01", "cred_faculty_ravi"),
            teacherNameKeys = setOf("dr. ravi kumar", "faculty_ravi"),
            teacherNameSlugs = setOf("drravikumar", "ravi")
        )
        assertEquals(listOf("bat_adca_m", "bat_tally_e"), scopedBatches.map { it.id })

        val students = listOf(
            StudentRecord(id = "stu_1", fullName = "Aarav", courseName = "ADCA", batchId = "bat_adca_m", batchName = ""),
            StudentRecord(id = "stu_2", fullName = "Priya", courseName = "Tally Prime", batchId = "", batchName = " tally  evening 5pm "),
            StudentRecord(id = "stu_3", fullName = "Kabir", courseName = "ADCA", batchId = "", batchName = ""),
            StudentRecord(id = "stu_4", fullName = "Riya", courseName = "Python", batchId = "bat_python_n", batchName = "Python Noon")
        )

        val scopedStudents = TeacherScopeMatcher.filterStudentsForTeacher(
            students = students,
            teacherBatchIds = scopedBatches.map { TeacherScopeMatcher.normKey(it.id) }.toSet(),
            teacherBatchNames = scopedBatches.map { TeacherScopeMatcher.normKey(it.name) }.toSet(),
            teacherCourseNames = scopedBatches.map { TeacherScopeMatcher.normKey(it.courseName) }.toSet()
        )
        assertEquals(listOf("stu_1", "stu_2", "stu_3"), scopedStudents.map { it.id })
    }

    @Test
    fun credentialProvisioning_rejectsPasswordsShorterThanSixCharacters() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureFirebaseInitialized(context)
        val repo = InstituteRepository.create(context)
        val provider = FirebaseAuthServiceProvider(repo, context = context)

        val shortResult = provider.provisionOrUpdateCredential(
            username = "rohit_wci",
            rawPasswordChars = "12345".toCharArray(),
            role = PortalRole.STUDENT,
            linkedEntityId = "stu_101",
            displayName = "Rohit Verma"
        )
        assertTrue(
            "Passwords shorter than 6 characters must fail provisioning",
            shortResult.isFailure
        )

        val blankResult = provider.provisionOrUpdateCredential(
            username = "rohit_wci",
            rawPasswordChars = "".toCharArray(),
            role = PortalRole.STUDENT,
            linkedEntityId = "stu_101",
            displayName = "Rohit Verma"
        )
        assertTrue(
            "Blank passwords must never default to 123456 and must fail",
            blankResult.isFailure
        )
    }
}
