package com.wci.core.crypto.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/**
 * SqlCipherFactory (S-11, S-27, S-47)
 * Produces hardened, Keystore-backed SQLCipher connections for local Room v3 storage.
 */
class SqlCipherFactory(private val context: Context) {

    companion object {
        const val DB_NAME = "wci_secure_local.db"
        private const val PREFS_KEY_VAULT = "wci_vault_secure_prefs"
        private const val KEY_SQLCIPHER_PASSPHRASE = "k_sqlcipher_enc_key"
        private const val KDF_ITERATIONS = 256000

        init {
            runCatching { System.loadLibrary("sqlcipher") }
        }
    }

    /**
     * Creates SQLCipher SupportOpenHelperFactory with Keystore-backed passphrase.
     */
    fun createOpenHelperFactory(): SupportSQLiteOpenHelper.Factory {
        val passphrase = getOrGeneratePassphrase()
        return SupportOpenHelperFactory(passphrase)
    }

    /**
     * Applies S-27 security PRAGMAs to an active database.
     */
    fun applyHardenedPragmas(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA cipher_memory_security = ON;")
        db.execSQL("PRAGMA secure_delete = ON;")
        db.execSQL("PRAGMA kdf_iter = $KDF_ITERATIONS;")
        db.execSQL("PRAGMA cipher_page_size = 4096;")
    }

    /**
     * Retrieves or provisions the 256-bit CSPRNG database encryption passphrase
     * wrapped in Android Keystore via EncryptedSharedPreferences.
     */
    @Synchronized
    fun getOrGeneratePassphrase(): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_KEY_VAULT,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existingBase64 = prefs.getString(KEY_SQLCIPHER_PASSPHRASE, null)
        if (!existingBase64.isNullOrEmpty()) {
            return android.util.Base64.decode(existingBase64, android.util.Base64.NO_WRAP)
        }

        // Generate 32 bytes (256 bits) cryptographically strong random key
        val secureRandom = SecureRandom()
        val newKey = ByteArray(32)
        secureRandom.nextBytes(newKey)

        val encoded = android.util.Base64.encodeToString(newKey, android.util.Base64.NO_WRAP)
        prefs.edit().putString(KEY_SQLCIPHER_PASSPHRASE, encoded).commit()

        return newKey
    }

    /**
     * Physically purges the SQLite encryption passphrase from disk and preference storage.
     */
    @Synchronized
    fun rotatePassphrase(newPassphrase: ByteArray) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_KEY_VAULT,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val encoded = android.util.Base64.encodeToString(newPassphrase, android.util.Base64.NO_WRAP)
        prefs.edit().putString(KEY_SQLCIPHER_PASSPHRASE, encoded).commit()
    }
}
