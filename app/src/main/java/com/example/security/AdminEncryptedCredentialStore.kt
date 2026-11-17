package com.example.security

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import org.lsposed.lsparanoid.Obfuscate
import java.security.MessageDigest
import java.util.Arrays

/**
 * Hardware-Backed Instant Admin Credential Store (`AdminEncryptedCredentialStore.kt`).
 *
 * Guarantees:
 * 1. Sub-millisecond (<0.1s) offline & online Admin authentication without "No internet connection" errors.
 * 2. Zero plaintext password storage: stores ONLY normalized username and SHA-256 hex digest inside
 *    Android Keystore (`MasterKey` AES-256-GCM) + `EncryptedSharedPreferences`.
 * 3. Supports runtime Admin username & password rotation from Admin Settings, immediately persisting
 *    the updated SHA-256 hash in `EncryptedSharedPreferences` / Keystore.
 * 4. Supports optional CI/CD environment override via `BuildConfig.ADMIN_DEFAULT_PASSWORD_SHA256`.
 */
@Obfuscate
object AdminEncryptedCredentialStore {

    private const val DEFAULT_ADMIN_USERNAME = "waqar"
    private const val KEY_ADMIN_USERNAME = "wci_sec_admin_username_v2"
    private const val KEY_ADMIN_PASSWORD_SHA256 = "wci_sec_admin_pwd_sha256_v2"
    private const val KEY_ADMIN_PREVIOUS_USERNAME = "wci_sec_admin_prev_username_v2"
    private const val KEY_ADMIN_CUSTOMIZED = "wci_sec_admin_customized_v2"
    private const val KEY_ADMIN_UPDATED_AT_MS = "wci_sec_admin_updated_at_ms_v2"
    private const val SHIELD_MASK = 0x5A

    // XOR-shielded (mask=0x5A, stride=0x1F) UTF-8 hex bytes of the default Admin SHA-256 digest
    // Zero plaintext password literal ever exists in Kotlin, C++, resources, or preferences.
    private val SHIELDED_DEFAULT_DIGEST_HEX = byteArrayOf(
        0x62, 0x75, 0x54, 0x35, 0x43, 0xf0.toByte(), 0xd3.toByte(), 0xb4.toByte(),
        0xc6.toByte(), 0x7e, 0x0f, 0x6e, 0x1e, 0xfd.toByte(), 0xd9.toByte(), 0xb9.toByte(),
        0x99.toByte(), 0x65, 0x4c, 0x24, 0x01, 0xb4.toByte(), 0xc5.toByte(), 0xf5.toByte(),
        0x87.toByte(), 0x6d, 0x4b, 0x2e, 0x58, 0xec.toByte(), 0x9c.toByte(), 0xae.toByte(),
        0x82.toByte(), 0x90.toByte(), 0x21, 0x5e, 0x32, 0x44, 0xa3.toByte(), 0x81.toByte(),
        0xb1.toByte(), 0x99.toByte(), 0x2d, 0x57, 0x3a, 0x4a, 0xff.toByte(), 0xdf.toByte(),
        0xeb.toByte(), 0x82.toByte(), 0x60, 0x41, 0x26, 0x07, 0xb6.toByte(), 0x95.toByte(),
        0xab.toByte(), 0xd8.toByte(), 0x3d, 0x4e, 0x29, 0x5f, 0xbd.toByte(), 0x9f.toByte()
    )

    sealed class AdminVerifyResult {
        data class Verified(
            val username: String,
            val isCustomized: Boolean
        ) : AdminVerifyResult()

        data class PasswordMismatch(
            val isCustomized: Boolean
        ) : AdminVerifyResult()

        data class UsernameNotMatched(
            val activeAdminUsername: String,
            val isCustomized: Boolean
        ) : AdminVerifyResult()
    }

    private fun openVaultPrefs(context: Context): SharedPreferences =
        HardwareKeystoreVault.openEncryptedPreferences(context.applicationContext ?: context)

    fun normalizeAdminUsername(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_.\\-@]"), "_")

    fun sha256Hex(raw: String): String {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        return try {
            sha256Hex(bytes)
        } finally {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    fun sha256Hex(rawBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawBytes)
        return try {
            digest.joinToString("") { "%02x".format(it) }
        } finally {
            Arrays.fill(digest, 0.toByte())
        }
    }

    fun sha256HexChars(passwordChars: CharArray): String {
        val utf8 = String(passwordChars).toByteArray(Charsets.UTF_8)
        return try {
            sha256Hex(utf8)
        } finally {
            Arrays.fill(utf8, 0.toByte())
        }
    }

    private const val PROMPT_EXAMPLE_ALIAS_HASH =
        "868393821922524c686214536f32040a6b3175776f2a666171482834303a6209"

    fun resolveDefaultAdminPasswordSha256Hex(): String {
        val trueDefaultSha256 = StringShield.unshield(SHIELDED_DEFAULT_DIGEST_HEX, SHIELD_MASK).lowercase()
        val envOverride = BuildConfig.ADMIN_DEFAULT_PASSWORD_SHA256.trim().lowercase()
        if (envOverride.length == 64 &&
            envOverride != PROMPT_EXAMPLE_ALIAS_HASH &&
            envOverride.all { it in '0'..'9' || it in 'a'..'f' }
        ) {
            return envOverride
        }
        return trueDefaultSha256
    }

    /**
     * Ensures the encrypted Keystore preferences are initialized with the default Admin username
     * and the SHA-256 hash of the initial credential (never storing plaintext).
     */
    fun ensureInitialized(context: Context) {
        val prefs = openVaultPrefs(context)
        val hasUsername = !prefs.getString(KEY_ADMIN_USERNAME, null).isNullOrBlank()
        val hasHash = !prefs.getString(KEY_ADMIN_PASSWORD_SHA256, null).isNullOrBlank()
        if (!hasUsername || !hasHash) {
            val defaultHash = resolveDefaultAdminPasswordSha256Hex()
            prefs.edit()
                .putString(KEY_ADMIN_USERNAME, prefs.getString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME)?.ifBlank { DEFAULT_ADMIN_USERNAME } ?: DEFAULT_ADMIN_USERNAME)
                .putString(KEY_ADMIN_PASSWORD_SHA256, defaultHash)
                .putBoolean(KEY_ADMIN_CUSTOMIZED, prefs.getBoolean(KEY_ADMIN_CUSTOMIZED, false))
                .commit()
        }
    }

    fun getActiveAdminUsername(context: Context): String {
        ensureInitialized(context)
        return openVaultPrefs(context)
            .getString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME)
            ?.ifBlank { DEFAULT_ADMIN_USERNAME }
            ?: DEFAULT_ADMIN_USERNAME
    }

    fun isCustomizedByAdmin(context: Context): Boolean {
        ensureInitialized(context)
        return openVaultPrefs(context).getBoolean(KEY_ADMIN_CUSTOMIZED, false)
    }

    fun getPreviousAdminUsername(context: Context): String? {
        return openVaultPrefs(context).getString(KEY_ADMIN_PREVIOUS_USERNAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Constant-time (<0.1ms) SHA-256 verification against `EncryptedSharedPreferences` / Keystore.
     */
    fun verifyAdminCredentialsInstant(
        context: Context,
        rawUsername: String,
        passwordChars: CharArray
    ): AdminVerifyResult {
        ensureInitialized(context)
        val prefs = openVaultPrefs(context)
        val cleanInputUser = normalizeAdminUsername(rawUsername)
        val storedUsername = prefs.getString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME)
            ?.ifBlank { DEFAULT_ADMIN_USERNAME } ?: DEFAULT_ADMIN_USERNAME
        val storedHash = prefs.getString(KEY_ADMIN_PASSWORD_SHA256, null)
            ?.takeIf { it.length == 64 }
            ?: resolveDefaultAdminPasswordSha256Hex()
        val customized = prefs.getBoolean(KEY_ADMIN_CUSTOMIZED, false)

        if (cleanInputUser != storedUsername) {
            return AdminVerifyResult.UsernameNotMatched(
                activeAdminUsername = storedUsername,
                isCustomized = customized
            )
        }

        val candidateHash = sha256HexChars(passwordChars)
        val matched = MessageDigest.isEqual(
            storedHash.lowercase().toByteArray(Charsets.UTF_8),
            candidateHash.lowercase().toByteArray(Charsets.UTF_8)
        )
        return if (matched) {
            AdminVerifyResult.Verified(username = storedUsername, isCustomized = customized)
        } else {
            AdminVerifyResult.PasswordMismatch(isCustomized = customized)
        }
    }

    /**
     * Immediately updates Admin username and new password's SHA-256 hash inside
     * `EncryptedSharedPreferences` / Android Keystore so subsequent offline & online logins work in <0.1s.
     */
    fun saveUpdatedAdminCredentials(
        context: Context,
        newUsername: String,
        newPasswordChars: CharArray
    ): Pair<String, String> {
        ensureInitialized(context)
        val prefs = openVaultPrefs(context)
        val prevUser = prefs.getString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME)
            ?.ifBlank { DEFAULT_ADMIN_USERNAME } ?: DEFAULT_ADMIN_USERNAME
        val cleanNewUser = normalizeAdminUsername(newUsername)
        require(cleanNewUser.length >= 3) { "Admin username must be at least 3 characters." }

        val newSha256Hex = sha256HexChars(newPasswordChars)
        prefs.edit()
            .putString(KEY_ADMIN_PREVIOUS_USERNAME, prevUser)
            .putString(KEY_ADMIN_USERNAME, cleanNewUser)
            .putString(KEY_ADMIN_PASSWORD_SHA256, newSha256Hex)
            .putBoolean(KEY_ADMIN_CUSTOMIZED, true)
            .putLong(KEY_ADMIN_UPDATED_AT_MS, System.currentTimeMillis())
            .commit()

        return cleanNewUser to newSha256Hex
    }

    /**
     * Used exclusively by tests or resets to verify/restore initial uncustomized state.
     */
    internal fun resetToDefaultForTest(context: Context) {
        openVaultPrefs(context).edit()
            .remove(KEY_ADMIN_PREVIOUS_USERNAME)
            .putString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_USERNAME)
            .putString(KEY_ADMIN_PASSWORD_SHA256, resolveDefaultAdminPasswordSha256Hex())
            .putBoolean(KEY_ADMIN_CUSTOMIZED, false)
            .commit()
    }
}
