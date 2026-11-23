package com.wci.core.crypto.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabaseHook
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
    }

    init {
        SQLiteDatabase.loadLibs(context)
    }

    /**
     * Creates SQLCipher database hook enforcing S-27 PRAGMA hardening parameters.
     */
    fun createSecurityHook(): SQLiteDatabaseHook {
        return object : SQLiteDatabaseHook {
            override fun preKey(database: SQLiteDatabase?) {
                // Pre-key hook if needed
            }

            override fun postKey(database: SQLiteDatabase?) {
                database?.let { db ->
                    db.rawExecSQL("PRAGMA cipher_memory_security = ON;")
                    db.rawExecSQL("PRAGMA secure_delete = ON;")
                    db.rawExecSQL("PRAGMA kdf_iter = $KDF_ITERATIONS;")
                    db.rawExecSQL("PRAGMA cipher_page_size = 4096;")
                }
            }
        }
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

        val storedHex = prefs.getString(KEY_SQLCIPHER_PASSPHRASE, null)
        if (storedHex != null) {
            return hexToBytes(storedHex)
        }

        // Generate 32 bytes (256-bit) CSPRNG key
        val secureRandom = SecureRandom()
        val newKey = ByteArray(32)
        secureRandom.nextBytes(newKey)

        val newHex = bytesToHex(newKey)
        prefs.edit().putString(KEY_SQLCIPHER_PASSPHRASE, newHex).commit()
        return newKey
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef".toCharArray()
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val octet = b.toInt() and 0xFF
            result.append(hexChars[octet ushr 4])
            result.append(hexChars[octet and 0x0F])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
