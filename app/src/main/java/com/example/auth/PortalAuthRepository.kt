package com.example.auth

import android.content.Context
import android.util.Log
import com.example.data.firestore.InstituteRepository
import com.example.data.model.GuardianLink
import com.example.data.model.PortalCredential
import com.example.data.model.PortalRole
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/**
 * Cross-Device & Offline-First Portal Authentication Repository (`PortalAuthRepository.kt`).
 *
 * Ensures accounts created by an Admin on Device A sync cleanly to Device B (even via shared APKs):
 * 1. Always fetches fresh records from Cloud Firestore (`Source.SERVER`) with seamless fallback
 *    to local persistent Firestore cache (`Source.CACHE`) and encrypted preferences when offline.
 * 2. Explicitly populates and validates `isStudent`, `isTeacher`, `isParent`, `isAdmin`, and
 *    `portalRoles` flags across `portalCredentials`, `users`, and `guardians`.
 */
class PortalAuthRepository(
    private val db: FirebaseFirestore,
    private val instituteRepository: InstituteRepository
) {
    companion object {
        private const val TAG = "PortalAuthRepository"
        private const val SERVER_FETCH_TIMEOUT_MS = 3_000L

        fun create(context: Context, instituteRepository: InstituteRepository): PortalAuthRepository {
            val firestore = InstituteRepository.getConfiguredFirestore(context)
            return PortalAuthRepository(firestore, instituteRepository)
        }

        fun buildRoleFlags(role: PortalRole, extraRoles: List<PortalRole> = emptyList()): RoleFlags {
            val all = (listOf(role) + extraRoles).distinct()
            val keys = all.map { it.key }
            return RoleFlags(
                isStudent = all.contains(PortalRole.STUDENT),
                isTeacher = all.contains(PortalRole.TEACHER),
                isParent = all.contains(PortalRole.PARENT),
                isAdmin = all.contains(PortalRole.ADMIN),
                portalRoles = keys
            )
        }
    }

    data class RoleFlags(
        val isStudent: Boolean,
        val isTeacher: Boolean,
        val isParent: Boolean,
        val isAdmin: Boolean,
        val portalRoles: List<String>
    )

    /**
     * Explicit Server + Cache fetch:
     * Queries `Source.SERVER` first so newly provisioned accounts on another device are always
     * discovered, and falls back to `Source.CACHE` immediately if the device is offline.
     */
    suspend fun getDocumentServerAndCache(
        docRef: DocumentReference,
        timeoutMs: Long = SERVER_FETCH_TIMEOUT_MS
    ): DocumentSnapshot? = withContext(Dispatchers.IO) {
        val serverSnap = try {
            withTimeoutOrNull(timeoutMs) {
                docRef.get(Source.SERVER).await()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Server fetch fallback to cache for ${docRef.path}: ${e.localizedMessage}")
            null
        }
        if (serverSnap != null) {
            return@withContext serverSnap
        }
        try {
            docRef.get(Source.CACHE).await()
        } catch (_: Exception) {
            try {
                withTimeoutOrNull(2_000L) { docRef.get(Source.DEFAULT).await() }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun getQueryServerAndCache(
        query: Query,
        timeoutMs: Long = SERVER_FETCH_TIMEOUT_MS
    ): QuerySnapshot? = withContext(Dispatchers.IO) {
        val serverSnap = try {
            withTimeoutOrNull(timeoutMs) {
                query.get(Source.SERVER).await()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Query server fetch fallback to cache: ${e.localizedMessage}")
            null
        }
        if (serverSnap != null) {
            return@withContext serverSnap
        }
        try {
            query.get(Source.CACHE).await()
        } catch (_: Exception) {
            try {
                withTimeoutOrNull(2_000L) { query.get(Source.DEFAULT).await() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Looks up a `PortalCredential` by normalized username across:
     * 1. Server + Cache `portalCredentials/{username}`
     * 2. Server + Cache `users/cred_{username}` / `users?username=...` (for role-flag cross-verification)
     * 3. Local in-memory / encrypted SharedPreferences fallback when completely offline.
     */
    suspend fun fetchPortalCredentialCrossDevice(rawUsername: String): PortalCredential? = withContext(Dispatchers.IO) {
        val cleanKey = FirebaseAuthServiceProvider.normalizeUsername(rawUsername)
        if (cleanKey.isBlank()) return@withContext null

        val docRef = db.collection("portalCredentials").document(cleanKey)
        val snap = getDocumentServerAndCache(docRef)
        if (snap != null && snap.exists()) {
            val remoteCred = snap.toObject(PortalCredential::class.java)?.let { parsed ->
                normalizeCredentialFlags(parsed)
            }
            if (remoteCred != null) {
                InstituteRepository.cacheCredentialLocally(remoteCred)
                return@withContext remoteCred
            }
        }

        // Fallback to local vault only when remote document was not obtained (e.g. offline first sync)
        InstituteRepository.getCachedCredentialLocally(cleanKey)?.let { cached ->
            return@withContext normalizeCredentialFlags(cached)
        }
        null
    }

    /**
     * Checks whether any Admin credential already exists in Cloud Firestore (server/cache) or
     * the local credential vault so startup seeding never overwrites a customized Admin password.
     */
    suspend fun hasAnyAdminCredential(): Boolean = withContext(Dispatchers.IO) {
        if (InstituteRepository.localCredentialsCache.values.any {
                it.active && (it.isAdmin || it.role.equals(PortalRole.ADMIN.key, ignoreCase = true))
            }
        ) {
            return@withContext true
        }
        for (defaultAdminKey in listOf("waqar", "admin", "waqar_admin")) {
            val docSnap = runCatching {
                getDocumentServerAndCache(db.collection("portalCredentials").document(defaultAdminKey))
            }.getOrNull()
            if (docSnap != null && docSnap.exists()) {
                return@withContext true
            }
        }
        false
    }

    suspend fun fetchUserProfileCrossDevice(uidOrUsername: String): UserProfile? = withContext(Dispatchers.IO) {
        val cleanKey = FirebaseAuthServiceProvider.normalizeUsername(uidOrUsername)
        val credUid = if (cleanKey.startsWith("cred_")) cleanKey else "cred_$cleanKey"

        val credSnap = getDocumentServerAndCache(db.collection("users").document(credUid))
        if (credSnap != null && credSnap.exists()) {
            return@withContext credSnap.toObject(UserProfile::class.java)
        }

        if (credUid != uidOrUsername) {
            val directSnap = getDocumentServerAndCache(db.collection("users").document(uidOrUsername))
            if (directSnap != null && directSnap.exists()) {
                return@withContext directSnap.toObject(UserProfile::class.java)
            }
        }

        val querySnap = getQueryServerAndCache(
            db.collection("users").whereEqualTo("username", cleanKey).limit(1)
        )
        querySnap?.documents?.firstOrNull()?.toObject(UserProfile::class.java)
    }

    suspend fun fetchGuardianByUsernameOrPhone(rawUsername: String): GuardianLink? = withContext(Dispatchers.IO) {
        val cleanKey = FirebaseAuthServiceProvider.normalizeUsername(rawUsername)
        val byUsername = getQueryServerAndCache(
            db.collection("guardians").whereEqualTo("parentUsername", cleanKey).limit(1)
        )?.documents?.firstOrNull()?.toObject(GuardianLink::class.java)
        if (byUsername != null) return@withContext byUsername

        instituteRepository.findMatchingGuardianForLogin(rawUsername)
    }

    /**
     * Real-time listener on `users/{uid}` and `portalCredentials/{username}` so role updates
     * or credential changes propagate instantaneously across devices.
     */
    fun observePortalCredentialRealtime(username: String): Flow<PortalCredential?> = callbackFlow {
        val cleanKey = FirebaseAuthServiceProvider.normalizeUsername(username)
        val reg = db.collection("portalCredentials").document(cleanKey)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val cred = snap?.toObject(PortalCredential::class.java)?.let(::normalizeCredentialFlags)
                if (cred != null) {
                    InstituteRepository.cacheCredentialLocally(cred)
                }
                trySend(cred)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Provisions or updates both `portalCredentials/{username}` and `users/{uid}` with explicit
     * portal role flags (`isStudent`, `isTeacher`, `isParent`, `isAdmin`, `portalRoles`).
     */
    suspend fun provisionCredentialWithRoleFlags(
        credential: PortalCredential,
        emailOverride: String = ""
    ): PortalCredential = withContext(Dispatchers.IO) {
        val normalized = normalizeCredentialFlags(credential)
        val savedCred = instituteRepository.savePortalCredentialWithConfirmation(normalized).getOrThrow()

        val credUid = "cred_${savedCred.username}"
        val safeNow = Timestamp(Date(System.currentTimeMillis() - 1000L))
        val existingProfile = runCatching { fetchUserProfileCrossDevice(credUid) }.getOrNull()
        val profile = UserProfile(
            uid = credUid,
            email = emailOverride.ifBlank { existingProfile?.email ?: "${savedCred.username}@waqarinstitute.edu" },
            displayName = savedCred.displayName.ifBlank { savedCred.username },
            role = savedCred.role,
            username = savedCred.username,
            phone = existingProfile?.phone.orEmpty(),
            mfaEnabled = existingProfile?.mfaEnabled ?: false,
            totpSecret = existingProfile?.totpSecret.orEmpty(),
            linkedEntityId = savedCred.linkedEntityId,
            isStudent = savedCred.isStudent,
            isTeacher = savedCred.isTeacher,
            isParent = savedCred.isParent,
            isAdmin = savedCred.isAdmin,
            portalRoles = savedCred.effectivePortalRoles(),
            activeSessionToken = existingProfile?.activeSessionToken.orEmpty(),
            activeDeviceId = existingProfile?.activeDeviceId.orEmpty(),
            lastActiveTimestamp = existingProfile?.lastActiveTimestamp,
            createdAt = existingProfile?.createdAt ?: safeNow
        )
        runCatching { instituteRepository.saveUserProfile(profile) }
        savedCred
    }

    fun hasProvisionedAccessForRole(
        credential: PortalCredential?,
        userProfile: UserProfile?,
        requestedRole: PortalRole
    ): Boolean {
        if (credential != null && credential.supportsRole(requestedRole)) return true
        if (userProfile != null && userProfile.supportsRole(requestedRole)) return true
        return false
    }

    private fun normalizeCredentialFlags(cred: PortalCredential): PortalCredential {
        val roleEnum = PortalRole.fromKey(cred.role)
        val flags = buildRoleFlags(
            role = roleEnum,
            extraRoles = cred.portalRoles.map { PortalRole.fromKey(it) }
        )
        return cred.copy(
            username = FirebaseAuthServiceProvider.normalizeUsername(cred.username),
            isStudent = cred.isStudent || flags.isStudent,
            isTeacher = cred.isTeacher || flags.isTeacher,
            isParent = cred.isParent || flags.isParent,
            isAdmin = cred.isAdmin || flags.isAdmin,
            portalRoles = (cred.effectivePortalRoles() + flags.portalRoles).distinct()
        )
    }
}
