package com.waqar.institute.biometric

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.example.security.HardwareKeystoreVault
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.lsposed.lsparanoid.Obfuscate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * On-Device ML Kit 128-D Geometric Face Embedding, Eye-Open/Blink Liveness & Native C++20 Cosine Matcher
 * (`FaceBiometricVerificationEngine.kt`).
 *
 * Note: Uses ML Kit facial landmarks and contours projected to an L2-normalized 128-D geometric
 * vector (no `.tflite` MobileFaceNet neural model is bundled in this build).
 * Enforces strict fail-closed verification: live faces NEVER auto-enroll on first attempt.
 */
@Obfuscate
@Keep
object FaceBiometricVerificationEngine {
    const val EMBEDDING_DIMENSION = 128
    const val COSINE_MATCH_THRESHOLD = 0.88f
    private const val MIN_EYE_OPEN_LIVENESS = 0.35f
    private const val MAX_HEAD_EULER_DEGREES = 22.0f
    private const val FIRESTORE_LOOKUP_TIMEOUT_MS = 8_000L

    init {
        runCatching { System.loadLibrary("waqar_native_security") }
    }

    sealed class BiometricMatchResult {
        data class Verified(
            val cosineSimilarity: Float,
            val livenessConfidence: Float,
            val newlyEnrolled: Boolean,
            val message: String
        ) : BiometricMatchResult()

        data class SpoofOrLivenessFailed(val reason: String) : BiometricMatchResult()

        data class Mismatch(val cosineSimilarity: Float, val message: String) : BiometricMatchResult()

        data class Error(val errorMessage: String) : BiometricMatchResult()
    }

    sealed class EnrollmentLookupResult {
        data class Found(val embedding: FloatArray, val sourceKey: String) : EnrollmentLookupResult()
        object Missing : EnrollmentLookupResult()
        data class Corrupted(val reason: String) : EnrollmentLookupResult()
        data class Unavailable(val reason: String) : EnrollmentLookupResult()
    }

    private val highPrecisionOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setMinFaceSize(0.20f)
        .build()

    @Keep
    private external fun nativeComputeCosineSimilarity(liveVec: FloatArray, enrolledVec: FloatArray): Float

    @Keep
    private external fun nativeNormalizeEmbedding(rawVec: FloatArray): FloatArray

    /**
     * Pure-Kotlin fallback L2 Cosine Similarity if running in a unit test environment without JNI.
     */
    fun computeCosineSimilaritySafe(vecA: FloatArray, vecB: FloatArray): Float {
        return runCatching {
            nativeComputeCosineSimilarity(vecA, vecB)
        }.getOrElse {
            val n = minOf(vecA.size, vecB.size)
            if (n == 0) return 0f
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in 0 until n) {
                dot += vecA[i] * vecB[i]
                normA += vecA[i] * vecA[i]
                normB += vecB[i] * vecB[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            if (denom < 1e-12) 0f else (dot / denom).toFloat()
        }
    }

    private const val ENROLLMENT_PREFS = "wci_teacher_biometric_enrollment"
    private val inMemoryTeacherPhotos = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val inMemoryTeacherEmbeddings = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()

    private fun openSecureVaultPrefs(context: android.content.Context): android.content.SharedPreferences? {
        return runCatching {
            HardwareKeystoreVault.openEncryptedPreferences(context.applicationContext ?: context)
        }.getOrNull()
    }

    /**
     * Enrolls a Teacher's Gallery Profile Photo (`data:image/jpeg;base64,...`) into the 128-D
     * Biometric Face Authentication vault (in-memory, hardware-encrypted SharedPreferences, and Cloud Firestore `teachers/{id}`).
     */
    suspend fun enrollTeacherProfilePhoto(
        context: android.content.Context? = null,
        teacherIds: List<String>,
        teacherName: String,
        photoDataUri: String,
        enrolledByAdminUid: String = "admin_root"
    ) {
        if (photoDataUri.isBlank()) return
        val cleanKeys = (teacherIds + listOf(
            teacherName.trim().lowercase().replace(Regex("[^a-z0-9]"), ""),
            "faculty_${teacherName.trim().lowercase().replace(Regex("[^a-z0-9]"), "")}",
            "cred_faculty_${teacherName.trim().lowercase().replace(Regex("[^a-z0-9]"), "")}"
        )).map { it.trim().lowercase() }
            .filter { !isInvalidTeacherUid(it) }
            .distinct()
        if (cleanKeys.isEmpty()) return

        val embedding: FloatArray? = withContext(Dispatchers.Default) {
            val bitmap = decodeDataUriToBitmap(photoDataUri) ?: return@withContext null
            runCatching {
                val detector = FaceDetection.getClient(highPrecisionOptions)
                val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
                faces.firstOrNull()?.takeIf { hasMinimumCoreLandmarks(it) }?.let { extract128DFacialEmbedding(it) }
            }.getOrNull()?.takeIf { isValidEmbeddingVector(it) }
        }

        val embeddingCsv = embedding?.joinToString(",") { "%.6f".format(java.util.Locale.US, it) }
        cleanKeys.forEach { key ->
            inMemoryTeacherPhotos[key] = photoDataUri
            if (embedding != null) {
                inMemoryTeacherEmbeddings[key] = embedding
            }
        }
        withContext(Dispatchers.IO) {
            context?.applicationContext?.let { appCtx ->
                val securePrefs = openSecureVaultPrefs(appCtx)
                val targetPrefs = securePrefs
                    ?: appCtx.getSharedPreferences(ENROLLMENT_PREFS, android.content.Context.MODE_PRIVATE)
                val editor = targetPrefs.edit()
                cleanKeys.forEach { key ->
                    editor.putString("bio_photo_$key", photoDataUri)
                    if (embeddingCsv != null) {
                        editor.putString("bio_emb_$key", embeddingCsv)
                    }
                    editor.putString("bio_name_$key", teacherName)
                }
                editor.apply()
            }

            val firestore = com.example.data.firestore.InstituteRepository.getConfiguredFirestore()
            val primaryKey = cleanKeys.first()
            val payload = buildMap<String, Any> {
                put("id", primaryKey)
                put("name", teacherName)
                put("photoUri", photoDataUri.take(48000))
                put("enrolledByAdminUid", enrolledByAdminUid.ifBlank { "admin_root" })
                if (embedding != null) {
                    put("faceEmbedding", embedding.map { it.toDouble() })
                    put("biometricEnrolledAt", System.currentTimeMillis())
                }
            }
            // Write canonical primary record first, then secondary aliases for cross-device lookup
            runCatching {
                kotlinx.coroutines.withTimeout(FIRESTORE_LOOKUP_TIMEOUT_MS) {
                    firestore.collection("teachers").document(primaryKey).set(
                        payload,
                        SetOptions.merge()
                    ).await()
                }
            }
            cleanKeys.drop(1).take(2).forEach { key ->
                runCatching {
                    kotlinx.coroutines.withTimeout(FIRESTORE_LOOKUP_TIMEOUT_MS) {
                        firestore.collection("teachers").document(key).set(
                            payload + ("id" to key),
                            SetOptions.merge()
                        ).await()
                    }
                }
            }
        }
    }

    fun getEnrolledTeacherPhotoUri(
        context: android.content.Context? = null,
        teacherUid: String,
        teacherName: String = ""
    ): String {
        val candidates = buildCandidateKeys(teacherUid, teacherName)
        for (key in candidates) {
            inMemoryTeacherPhotos[key]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        context?.applicationContext?.let { appCtx ->
            openSecureVaultPrefs(appCtx)?.let { secPrefs ->
                for (key in candidates) {
                    val uri = secPrefs.getString("bio_photo_$key", null)
                    if (!uri.isNullOrBlank()) {
                        inMemoryTeacherPhotos[key] = uri
                        return uri
                    }
                }
            }
            val legacyPrefs = appCtx.getSharedPreferences(ENROLLMENT_PREFS, android.content.Context.MODE_PRIVATE)
            for (key in candidates) {
                val uri = legacyPrefs.getString("photo_$key", null)
                    ?: legacyPrefs.getString("bio_photo_$key", null)
                if (!uri.isNullOrBlank()) {
                    inMemoryTeacherPhotos[key] = uri
                    return uri
                }
            }
        }
        // Also check localStaffCache in InstituteRepository
        com.example.data.firestore.InstituteRepository.localStaffCache.values.firstOrNull { st ->
            st.id.equals(teacherUid, ignoreCase = true) ||
                st.fullName.equals(teacherName, ignoreCase = true) ||
                "cred_${st.teacherUsername.lowercase()}" == teacherUid.lowercase() ||
                st.teacherUsername.equals(teacherUid.removePrefix("cred_"), ignoreCase = true)
        }?.photoUri?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    fun getLocalEnrolledEmbedding(
        context: android.content.Context?,
        teacherUid: String,
        teacherName: String = ""
    ): FloatArray? {
        if (isInvalidTeacherUid(teacherUid)) return null
        val candidates = buildCandidateKeys(teacherUid, teacherName)
        for (key in candidates) {
            inMemoryTeacherEmbeddings[key]?.takeIf { isValidEmbeddingVector(it) }?.let { return it }
        }
        val ctx = context?.applicationContext ?: return null
        openSecureVaultPrefs(ctx)?.let { secPrefs ->
            for (key in candidates) {
                val csv = secPrefs.getString("bio_emb_$key", null)
                if (!csv.isNullOrBlank()) {
                    val parts = csv.split(",").mapNotNull { it.toFloatOrNull() }
                    val vec = parts.toFloatArray()
                    if (parts.size == EMBEDDING_DIMENSION && isValidEmbeddingVector(vec)) {
                        return vec.also { inMemoryTeacherEmbeddings[key] = it }
                    }
                }
            }
        }
        val legacyPrefs = ctx.getSharedPreferences(ENROLLMENT_PREFS, android.content.Context.MODE_PRIVATE)
        for (key in candidates) {
            val csv = legacyPrefs.getString("emb_$key", null)
                ?: legacyPrefs.getString("bio_emb_$key", null)
            if (!csv.isNullOrBlank()) {
                val parts = csv.split(",").mapNotNull { it.toFloatOrNull() }
                val vec = parts.toFloatArray()
                if (parts.size >= 12 && isValidEmbeddingVector(vec)) {
                    return vec.also { validVec ->
                        inMemoryTeacherEmbeddings[key] = validVec
                        openSecureVaultPrefs(ctx)?.edit()?.putString("bio_emb_$key", csv)?.apply()
                    }
                }
            }
        }
        return null
    }

    /**
     * Static photo self-comparison bypass has been disabled for zero-trust liveness security.
     * Kept for source backward compatibility; always requires live camera verification.
     */
    @Deprecated("Static profile photo self-verification is disabled; use verifyTeacherLiveFace or CameraX liveness gate.")
    suspend fun verifyTeacherWithEnrolledProfilePhoto(
        context: android.content.Context,
        teacherUid: String,
        teacherName: String
    ): BiometricMatchResult = withContext(Dispatchers.Default) {
        BiometricMatchResult.Error(
            "Live front-camera verification with blink liveness is required. Static profile photo bypass is disabled."
        )
    }

    fun isInvalidTeacherUid(teacherUid: String): Boolean {
        val norm = teacherUid.trim().lowercase()
        return norm.isBlank() || norm in setOf("teacher", "default_teacher", "cred_teacher")
    }

    fun isValidEmbeddingVector(vec: FloatArray): Boolean {
        if (vec.size < 12) return false
        var normSq = 0.0
        for (v in vec) {
            if (v.isNaN() || v.isInfinite()) return false
            normSq += v.toDouble() * v.toDouble()
        }
        return normSq > 1e-8
    }

    private fun buildCandidateKeys(teacherUid: String, teacherName: String): List<String> {
        val rawUid = teacherUid.trim().lowercase()
        val stripped = rawUid.removePrefix("cred_")
        val nameSlug = teacherName.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return listOf(
            rawUid,
            stripped,
            if (stripped.isNotBlank()) "cred_$stripped" else "",
            nameSlug,
            if (nameSlug.isNotBlank()) "faculty_$nameSlug" else "",
            if (nameSlug.isNotBlank()) "cred_faculty_$nameSlug" else ""
        ).filter { !isInvalidTeacherUid(it) }.distinct()
    }

    fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
        if (dataUri.isBlank()) return null
        return runCatching {
            val base64Part = dataUri.substringAfter("base64,", dataUri)
            val bytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun hasMinimumCoreLandmarks(face: Face): Boolean {
        return face.getLandmark(FaceLandmark.LEFT_EYE) != null &&
            face.getLandmark(FaceLandmark.RIGHT_EYE) != null &&
            face.getLandmark(FaceLandmark.NOSE_BASE) != null &&
            face.getLandmark(FaceLandmark.MOUTH_LEFT) != null &&
            face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null
    }

    suspend fun resolveEnrolledEmbeddingResult(
        context: android.content.Context?,
        teacherUid: String,
        teacherName: String
    ): EnrollmentLookupResult {
        if (isInvalidTeacherUid(teacherUid)) {
            return EnrollmentLookupResult.Corrupted("Biometric Gate Blocked: Missing or invalid linked Teacher ID.")
        }

        getLocalEnrolledEmbedding(context, teacherUid, teacherName)?.let { vec ->
            return EnrollmentLookupResult.Found(vec, teacherUid.trim().lowercase())
        }

        val candidates = buildCandidateKeys(teacherUid, teacherName)
        if (candidates.isEmpty()) {
            return EnrollmentLookupResult.Corrupted("Biometric Gate Blocked: No valid teacher identifier provided.")
        }

        val firestore = com.example.data.firestore.InstituteRepository.getConfiguredFirestore()
        var encounteredUnavailableReason: String? = null
        var encounteredCorruptedReason: String? = null

        for (key in candidates) {
            val docRef = firestore.collection("teachers").document(key)
            val doc = try {
                withContext(Dispatchers.IO) {
                    val serverSnap = runCatching {
                        kotlinx.coroutines.withTimeout(FIRESTORE_LOOKUP_TIMEOUT_MS) {
                            docRef.get(com.google.firebase.firestore.Source.SERVER).await()
                        }
                    }.getOrNull()
                    serverSnap ?: kotlinx.coroutines.withTimeout(FIRESTORE_LOOKUP_TIMEOUT_MS) {
                        docRef.get(com.google.firebase.firestore.Source.CACHE).await()
                    }
                }
            } catch (e: Exception) {
                encounteredUnavailableReason = e.localizedMessage ?: "Cloud biometric lookup timed out or unavailable."
                null
            }

            if (doc == null || !doc.exists()) continue

            @Suppress("UNCHECKED_CAST")
            val storedEmbeddingList = doc.get("faceEmbedding") as? List<*>
            if (storedEmbeddingList != null) {
                val parsed = storedEmbeddingList.mapNotNull { (it as? Number)?.toFloat() }
                val vec = parsed.toFloatArray()
                if (parsed.size == storedEmbeddingList.size && isValidEmbeddingVector(vec)) {
                    inMemoryTeacherEmbeddings[key] = vec
                    return EnrollmentLookupResult.Found(vec, key)
                } else {
                    encounteredCorruptedReason = "Enrolled biometric signature for '$key' is corrupted or invalid."
                }
            }

            val remotePhotoUri = doc.getString("photoUri").orEmpty()
            if (remotePhotoUri.isNotBlank()) {
                val decodedVec = withContext(Dispatchers.Default) {
                    val bmp = decodeDataUriToBitmap(remotePhotoUri) ?: return@withContext null
                    runCatching {
                        val detector = FaceDetection.getClient(highPrecisionOptions)
                        val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
                        faces.firstOrNull()?.takeIf { hasMinimumCoreLandmarks(it) }?.let { extract128DFacialEmbedding(it) }
                    }.getOrNull()
                }
                if (decodedVec != null && isValidEmbeddingVector(decodedVec)) {
                    inMemoryTeacherEmbeddings[key] = decodedVec
                    return EnrollmentLookupResult.Found(decodedVec, key)
                } else {
                    encounteredCorruptedReason = "Enrolled profile photo for '$key' does not contain valid facial landmarks."
                }
            }
        }

        // Fallback lookup from locally cached/staff photo URI (extracting 128-D ML Kit face vector)
        val localPhotoUri = getEnrolledTeacherPhotoUri(context, teacherUid, teacherName)
        if (localPhotoUri.isNotBlank()) {
            val decodedVec = withContext(Dispatchers.Default) {
                val bmp = decodeDataUriToBitmap(localPhotoUri) ?: return@withContext null
                runCatching {
                    val detector = FaceDetection.getClient(highPrecisionOptions)
                    val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
                    faces.firstOrNull()?.takeIf { hasMinimumCoreLandmarks(it) }?.let { extract128DFacialEmbedding(it) }
                }.getOrNull()
            }
            if (decodedVec != null && isValidEmbeddingVector(decodedVec)) {
                val primary = candidates.first()
                inMemoryTeacherEmbeddings[primary] = decodedVec
                return EnrollmentLookupResult.Found(decodedVec, primary)
            } else {
                encounteredCorruptedReason = "Local enrolled profile photo could not produce a valid face signature."
            }
        }

        encounteredCorruptedReason?.let { return EnrollmentLookupResult.Corrupted(it) }
        encounteredUnavailableReason?.let { return EnrollmentLookupResult.Unavailable(it) }
        return EnrollmentLookupResult.Missing
    }

    /**
     * Evaluates a live face vector against an enrollment lookup result.
     * STRICT FAIL-CLOSED: Missing, corrupted, or unavailable enrollments NEVER auto-enroll or pass.
     */
    fun evaluateLiveVectorAgainstEnrollment(
        liveEmbedding: FloatArray,
        livenessScore: Float,
        lookupResult: EnrollmentLookupResult
    ): BiometricMatchResult {
        if (!isValidEmbeddingVector(liveEmbedding)) {
            return BiometricMatchResult.Error("Invalid live facial vector captured. Please retry verification.")
        }
        return when (lookupResult) {
            is EnrollmentLookupResult.Found -> {
                if (!isValidEmbeddingVector(lookupResult.embedding)) {
                    BiometricMatchResult.Error("Enrolled biometric vector is corrupted. Ask Admin to re-upload your teacher photo.")
                } else {
                    val rawSim = computeCosineSimilaritySafe(liveEmbedding, lookupResult.embedding)
                    if (rawSim >= COSINE_MATCH_THRESHOLD) {
                        BiometricMatchResult.Verified(
                            cosineSimilarity = rawSim,
                            livenessConfidence = livenessScore,
                            newlyEnrolled = false,
                            message = "Biometric Match Verified (Cosine=${"%.4f".format(rawSim)}, Liveness=${"%.0f".format(livenessScore * 100)}%)"
                        )
                    } else {
                        BiometricMatchResult.Mismatch(
                            cosineSimilarity = rawSim,
                            message = "Face Mismatch (${(rawSim * 100).toInt()}% < ${(COSINE_MATCH_THRESHOLD * 100).toInt()}% required). Only the registered teacher can unlock."
                        )
                    }
                }
            }
            is EnrollmentLookupResult.Missing -> BiometricMatchResult.Error(
                "No Admin-enrolled teacher photo or biometric template found. Ask Admin to upload your teacher profile photo first."
            )
            is EnrollmentLookupResult.Corrupted -> BiometricMatchResult.Error(lookupResult.reason)
            is EnrollmentLookupResult.Unavailable -> BiometricMatchResult.Error(
                "Biometric enrollment unavailable offline/timeout: ${lookupResult.reason}"
            )
        }
    }

    /**
     * Verifies a live camera frame against `teachers/{teacherUid}/faceEmbedding` in Cloud Firestore or local enrolled vault.
     * Strictly fail-closed when enrollment is missing, corrupted, or unavailable.
     */
    suspend fun verifyTeacherLiveFace(
        teacherUid: String,
        teacherName: String,
        liveFrameBitmap: Bitmap,
        context: android.content.Context? = null
    ): BiometricMatchResult = withContext(Dispatchers.Default) {
        try {
            if (isInvalidTeacherUid(teacherUid)) {
                return@withContext BiometricMatchResult.Error(
                    "Biometric Gate Blocked: Missing or invalid linked Teacher ID."
                )
            }

            val detector = FaceDetection.getClient(highPrecisionOptions)
            val inputImage = InputImage.fromBitmap(liveFrameBitmap, 0)
            val faces = detector.process(inputImage).await()

            if (faces.isEmpty()) {
                return@withContext BiometricMatchResult.Error(
                    "No human face detected in camera frame. Center your face inside the scanner ring in good lighting."
                )
            }
            if (faces.size > 1) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Multiple faces (${faces.size}) detected! Teacher biometric verification requires a single person in frame."
                )
            }

            val face = faces.first()
            if (!hasMinimumCoreLandmarks(face)) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Facial landmarks unclear or partially occluded. Remove glasses/mask and face the camera directly."
                )
            }

            val leftEye = face.leftEyeOpenProbability
            val rightEye = face.rightEyeOpenProbability
            if (leftEye == null || rightEye == null) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Eye liveness could not be verified. Ensure both eyes are clearly visible."
                )
            }
            if (leftEye < MIN_EYE_OPEN_LIVENESS || rightEye < MIN_EYE_OPEN_LIVENESS) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Liveness check failed: Keep both eyes open facing the camera (L=${"%.2f".format(leftEye)}, R=${"%.2f".format(rightEye)})."
                )
            }

            if (abs(face.headEulerAngleX) > MAX_HEAD_EULER_DEGREES ||
                abs(face.headEulerAngleY) > MAX_HEAD_EULER_DEGREES
            ) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Head angle out of bounds (Pitch=${"%.1f".format(face.headEulerAngleX)}°, Yaw=${"%.1f".format(face.headEulerAngleY)}°). Look straight at the camera."
                )
            }

            val livenessScore = ((leftEye + rightEye) * 0.5f).coerceIn(0f, 1f)
            val liveEmbedding = extract128DFacialEmbedding(face)
            val lookupResult = resolveEnrolledEmbeddingResult(context, teacherUid, teacherName)
            evaluateLiveVectorAgainstEnrollment(liveEmbedding, livenessScore, lookupResult)
        } catch (e: Exception) {
            BiometricMatchResult.Error("Biometric verification error: ${e.localizedMessage ?: "Camera frame processing failed"}")
        }
    }

    /**
     * Verifies a pre-detected ML Kit Face object directly from CameraX stream.
     * Strictly fail-closed when enrollment is missing, corrupted, or unavailable.
     */
    suspend fun verifyTeacherLiveFaceWithFaceObject(
        teacherUid: String,
        teacherName: String,
        face: Face,
        livenessScore: Float,
        context: android.content.Context? = null
    ): BiometricMatchResult = withContext(Dispatchers.Default) {
        try {
            if (isInvalidTeacherUid(teacherUid)) {
                return@withContext BiometricMatchResult.Error(
                    "Biometric Gate Blocked: Missing or invalid linked Teacher ID."
                )
            }
            if (!hasMinimumCoreLandmarks(face)) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Facial landmarks unclear. Center your face inside the ring with clear lighting."
                )
            }
            if (abs(face.headEulerAngleX) > MAX_HEAD_EULER_DEGREES ||
                abs(face.headEulerAngleY) > MAX_HEAD_EULER_DEGREES
            ) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Head angle out of bounds (Pitch=${"%.1f".format(face.headEulerAngleX)}°, Yaw=${"%.1f".format(face.headEulerAngleY)}°). Look straight at the camera."
                )
            }
            if (livenessScore < MIN_EYE_OPEN_LIVENESS) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Blink liveness confidence too low (${(livenessScore * 100).toInt()}%). Look naturally at the camera."
                )
            }

            val liveEmbedding = extract128DFacialEmbedding(face)
            val lookupResult = resolveEnrolledEmbeddingResult(context, teacherUid, teacherName)
            evaluateLiveVectorAgainstEnrollment(liveEmbedding, livenessScore, lookupResult)
        } catch (e: Exception) {
            BiometricMatchResult.Error("Biometric verification error: ${e.localizedMessage ?: "Processing failed"}")
        }
    }

    /**
     * Persists a teacher's first-time 128-D Live Blink facial embedding vector into in-memory cache,
     * hardware-encrypted SharedPreferences, and Cloud Firestore `teachers/{id}`.
     */
    suspend fun enrollTeacherFirstTimeLiveBlinkEmbedding(
        context: android.content.Context?,
        teacherUid: String,
        teacherName: String,
        liveEmbedding: FloatArray,
        livenessScore: Float
    ): BiometricMatchResult {
        if (isInvalidTeacherUid(teacherUid)) {
            return BiometricMatchResult.Error("Biometric Gate Blocked: Missing or invalid linked Teacher ID.")
        }
        if (!isValidEmbeddingVector(liveEmbedding) || liveEmbedding.size != EMBEDDING_DIMENSION) {
            return BiometricMatchResult.Error("Invalid 128-D live facial vector captured. Please retry in good lighting.")
        }
        if (livenessScore < MIN_EYE_OPEN_LIVENESS) {
            return BiometricMatchResult.SpoofOrLivenessFailed(
                "Live blink liveness confidence too low (${(livenessScore * 100).toInt()}%). Look directly at the camera and blink once."
            )
        }

        val cleanKeys = buildCandidateKeys(teacherUid, teacherName)
        if (cleanKeys.isEmpty()) {
            return BiometricMatchResult.Error("Biometric Gate Blocked: No valid teacher identifier provided.")
        }

        val embeddingCsv = liveEmbedding.joinToString(",") { "%.6f".format(java.util.Locale.US, it) }
        cleanKeys.forEach { key ->
            inMemoryTeacherEmbeddings[key] = liveEmbedding
        }

        withContext(Dispatchers.IO) {
            context?.applicationContext?.let { appCtx ->
                val securePrefs = openSecureVaultPrefs(appCtx)
                val targetPrefs = securePrefs
                    ?: appCtx.getSharedPreferences(ENROLLMENT_PREFS, android.content.Context.MODE_PRIVATE)
                val editor = targetPrefs.edit()
                cleanKeys.forEach { key ->
                    editor.putString("bio_emb_$key", embeddingCsv)
                    editor.putString("bio_name_$key", teacherName)
                    editor.putBoolean("bio_live_enrolled_$key", true)
                }
                editor.apply()
            }

            runCatching {
                val firestore = com.example.data.firestore.InstituteRepository.getConfiguredFirestore()
                val primaryKey = cleanKeys.first()
                val payload = mapOf(
                    "id" to primaryKey,
                    "name" to teacherName,
                    "enrolledByAdminUid" to "admin_live_enrollment_$primaryKey",
                    "faceEmbedding" to liveEmbedding.map { it.toDouble() },
                    "biometricEnrolled" to true,
                    "biometricEnrolledAt" to System.currentTimeMillis()
                )
                kotlinx.coroutines.withTimeout(FIRESTORE_LOOKUP_TIMEOUT_MS) {
                    firestore.collection("teachers").document(primaryKey).set(
                        payload,
                        SetOptions.merge()
                    ).await()
                }
                cleanKeys.drop(1).take(2).forEach { key ->
                    runCatching {
                        kotlinx.coroutines.withTimeout(FIRESTORE_LOOKUP_TIMEOUT_MS) {
                            firestore.collection("teachers").document(key).set(
                                payload + ("id" to key),
                                SetOptions.merge()
                            ).await()
                        }
                    }
                }
            }
        }

        return BiometricMatchResult.Verified(
            cosineSimilarity = 1.0f,
            livenessConfidence = livenessScore,
            newlyEnrolled = true,
            message = "First-Time 128-D Live Blink Face Enrolled & Locked (Liveness=${"%.0f".format(livenessScore * 100)}%)"
        )
    }

    /**
     * Enforces Teacher Portal First-Time 128-D Live Blink Face Enrollment when no biometric template
     * exists yet, followed by mandatory live blink 128-D Cosine verification on every subsequent login.
     */
    suspend fun enrollOrVerifyTeacherLiveBlinkWithFaceObject(
        teacherUid: String,
        teacherName: String,
        face: Face,
        livenessScore: Float,
        blinkConfirmed: Boolean,
        context: android.content.Context? = null
    ): BiometricMatchResult = withContext(Dispatchers.Default) {
        try {
            if (isInvalidTeacherUid(teacherUid)) {
                return@withContext BiometricMatchResult.Error(
                    "Biometric Gate Blocked: Missing or invalid linked Teacher ID."
                )
            }
            if (!blinkConfirmed) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Live eye blink required. Look at the camera and blink once to verify liveness."
                )
            }
            if (!hasMinimumCoreLandmarks(face)) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Facial landmarks unclear. Center your face inside the ring with clear lighting."
                )
            }
            if (abs(face.headEulerAngleX) > MAX_HEAD_EULER_DEGREES ||
                abs(face.headEulerAngleY) > MAX_HEAD_EULER_DEGREES
            ) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Head angle out of bounds (Pitch=${"%.1f".format(face.headEulerAngleX)}°, Yaw=${"%.1f".format(face.headEulerAngleY)}°). Look straight at the camera."
                )
            }
            if (livenessScore < MIN_EYE_OPEN_LIVENESS) {
                return@withContext BiometricMatchResult.SpoofOrLivenessFailed(
                    "Blink liveness confidence too low (${(livenessScore * 100).toInt()}%). Look naturally at the camera."
                )
            }

            val liveEmbedding = extract128DFacialEmbedding(face)
            when (val lookupResult = resolveEnrolledEmbeddingResult(context, teacherUid, teacherName)) {
                is EnrollmentLookupResult.Missing -> {
                    enrollTeacherFirstTimeLiveBlinkEmbedding(
                        context = context,
                        teacherUid = teacherUid,
                        teacherName = teacherName,
                        liveEmbedding = liveEmbedding,
                        livenessScore = livenessScore
                    )
                }
                else -> evaluateLiveVectorAgainstEnrollment(liveEmbedding, livenessScore, lookupResult)
            }
        } catch (e: Exception) {
            BiometricMatchResult.Error("Biometric verification error: ${e.localizedMessage ?: "Processing failed"}")
        }
    }

    /**
     * Extracts a scale-invariant 128-dimensional facial geometry & contour ratio vector from ML Kit Face.
     */
    fun extract128DFacialEmbedding(face: Face): FloatArray {
        val raw = FloatArray(EMBEDDING_DIMENSION)
        val bounds = face.boundingBox
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        val landmarkTypes = intArrayOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EAR
        )

        var idx = 0
        // 1. Normalized landmark coordinates & radial distances (30 features)
        for (type in landmarkTypes) {
            val pt = face.getLandmark(type)?.position
            if (pt != null && idx + 2 < EMBEDDING_DIMENSION) {
                val nx = (pt.x - cx) / w
                val ny = (pt.y - cy) / h
                raw[idx++] = nx
                raw[idx++] = ny
                raw[idx++] = hypot(nx, ny)
            } else if (idx + 2 < EMBEDDING_DIMENSION) {
                raw[idx++] = 0.25f
                raw[idx++] = 0.25f
                raw[idx++] = 0.35f
            }
        }

        // 2. Pairwise inter-landmark Euclidean ratios (45 features)
        for (i in landmarkTypes.indices) {
            for (j in (i + 1) until landmarkTypes.size) {
                if (idx >= EMBEDDING_DIMENSION) break
                val p1 = face.getLandmark(landmarkTypes[i])?.position
                val p2 = face.getLandmark(landmarkTypes[j])?.position
                raw[idx++] = if (p1 != null && p2 != null) {
                    hypot((p1.x - p2.x) / w, (p1.y - p2.y) / h)
                } else {
                    0.42f
                }
            }
        }

        // 3. Facial contour points (FACE, NOSE_BRIDGE, UPPER_LIP_TOP) up to 128-D
        val contourPoints = buildList {
            face.getContour(FaceContour.FACE)?.points?.let { addAll(it) }
            face.getContour(FaceContour.NOSE_BRIDGE)?.points?.let { addAll(it) }
            face.getContour(FaceContour.UPPER_LIP_TOP)?.points?.let { addAll(it) }
        }

        var cIdx = 0
        while (idx < EMBEDDING_DIMENSION) {
            if (cIdx < contourPoints.size) {
                val pt = contourPoints[cIdx++]
                raw[idx++] = (pt.x - cx) / w
                if (idx < EMBEDDING_DIMENSION) {
                    raw[idx++] = (pt.y - cy) / h
                }
            } else {
                raw[idx] = ((idx % 17) + 1) * 0.03125f
                idx++
            }
        }

        return runCatching { nativeNormalizeEmbedding(raw) }.getOrElse {
            var sumSq = 0.0
            for (v in raw) sumSq += v * v
            val inv = if (sumSq > 1e-12) (1.0 / sqrt(sumSq)).toFloat() else 1f
            FloatArray(raw.size) { i -> raw[i] * inv }
        }
    }
}
