package com.wci.core.crypto.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.wci.nativecxx.WciNativeSentinel

/**
 * SqlCipherSanitizer (S-11, S-27)
 * Hardens SQLite databases via SQLCipher pragmas, executes cryptographic database sanitization,
 * and securely scrubs sensitive passphrases from memory via WciNativeSentinel.
 */
object SqlCipherSanitizer {

    /**
     * Applies mandatory security PRAGMAs to an active SQLCipher database instance:
     * - PRAGMA cipher_memory_security = ON
     * - PRAGMA secure_delete = ON
     * - PRAGMA cipher_page_size = 4096
     */
    fun applyHardenedPragmas(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA cipher_memory_security = ON;")
        db.execSQL("PRAGMA secure_delete = ON;")
        db.execSQL("PRAGMA cipher_page_size = 4096;")
    }

    /**
     * Executes PRAGMA vacuum to physically purge and defragment deleted records.
     */
    fun sanitizeAndVacuum(db: SupportSQLiteDatabase) {
        applyHardenedPragmas(db)
        db.execSQL("VACUUM;")
    }

    /**
     * Zeroes out sensitive passphrase byte arrays in memory immediately after connection open.
     */
    fun wipePassphrase(passphrase: ByteArray) {
        WciNativeSentinel.scrubMemory(passphrase)
    }

    /**
     * Zeroes out sensitive CharArray passphrase.
     */
    fun wipePassphrase(passphrase: CharArray) {
        passphrase.fill('\u0000')
    }
}
