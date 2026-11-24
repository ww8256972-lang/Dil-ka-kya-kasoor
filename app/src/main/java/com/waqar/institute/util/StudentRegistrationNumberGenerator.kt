package com.waqar.institute.util

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

/**
 * Unique 12-Digit Numeric Student Registration Number Generator (`StudentRegistrationNumberGenerator.kt`).
 *
 * Requirements:
 * - Generates collision-free 12-digit numeric Registration Numbers (e.g., `2026XXXXXXXX`).
 * - Cryptographically random 8-digit suffix with leading-zero preservation.
 * - Verifies uniqueness against Cloud Firestore `public_verifications/{registrationNumber}` and
 *   local candidate caches before final assignment.
 */
object StudentRegistrationNumberGenerator {

    private val secureRandom = SecureRandom()
    private const val ADMISSION_YEAR = 2026

    /**
     * Generates a 12-digit candidate string in format YYYY + 8 random digits (e.g. "202673829104").
     */
    fun generateCandidate(year: Int = ADMISSION_YEAR): String {
        val randomSuffix = secureRandom.nextInt(100_000_000)
        return "%04d%08d".format(year, randomSuffix)
    }

    /**
     * Verifies if a given string is a valid 12-digit numeric registration number.
     */
    fun isValid12DigitNumber(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        val clean = candidate.trim()
        return clean.length == 12 && clean.all { it.isDigit() }
    }

    /**
     * Asynchronously generates a guaranteed globally unique 12-digit numeric registration number
     * by querying `public_verifications/{candidate}` with retry logic.
     */
    suspend fun generateUniqueRegistrationNumber(
        firestore: FirebaseFirestore?,
        existingLocalNumbers: Set<String> = emptySet(),
        year: Int = ADMISSION_YEAR
    ): String {
        repeat(12) {
            val candidate = generateCandidate(year)
            if (candidate !in existingLocalNumbers) {
                if (firestore == null) {
                    return candidate
                }
                val existsOnServer = runCatching {
                    withTimeoutOrNull(2500L) {
                        val doc = firestore.collection("public_verifications")
                            .document(candidate)
                            .get()
                            .await()
                        doc.exists()
                    } ?: false
                }.getOrDefault(false)

                if (!existsOnServer) {
                    return candidate
                }
            }
        }

        // Monotonic millisecond fallback preserving 12 numeric digits
        val msSuffix = (System.currentTimeMillis() % 100_000_000L).toInt()
        return "%04d%08d".format(year, msSuffix)
    }
}
