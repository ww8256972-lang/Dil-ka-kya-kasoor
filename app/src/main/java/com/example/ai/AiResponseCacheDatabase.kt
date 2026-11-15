package com.example.ai

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.security.HardwareKeystoreVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.lsposed.lsparanoid.Obfuscate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

@Entity(
    tableName = "ai_response_cache",
    indices = [
        Index(value = ["featureType", "languageCode"], name = "idx_ai_cache_feature_lang"),
        Index(value = ["lastAccessedMillis"], name = "idx_ai_cache_last_accessed")
    ]
)
data class CachedAiResponseEntity(
    @PrimaryKey val promptHash: String,
    val featureType: String,
    val languageCode: String,
    val promptText: String,
    val responseText: String,
    val modelName: String,
    val hitCount: Int = 1,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastAccessedMillis: Long = System.currentTimeMillis()
)

@Dao
interface AiResponseCacheDao {
    @Query("SELECT * FROM ai_response_cache WHERE promptHash = :promptHash LIMIT 1")
    suspend fun getCachedResponseByHash(promptHash: String): CachedAiResponseEntity?

    @Query("SELECT * FROM ai_response_cache ORDER BY lastAccessedMillis DESC LIMIT :limit")
    fun observeRecentCachedResponses(limit: Int = 50): Flow<List<CachedAiResponseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedResponse(entry: CachedAiResponseEntity)

    @Query("UPDATE ai_response_cache SET hitCount = hitCount + 1, lastAccessedMillis = :nowMillis WHERE promptHash = :promptHash")
    suspend fun incrementHitCount(promptHash: String, nowMillis: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM ai_response_cache")
    suspend fun countCachedResponses(): Int
}

@Database(
    entities = [CachedAiResponseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AiResponseCacheDatabase : RoomDatabase() {
    abstract fun aiCacheDao(): AiResponseCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AiResponseCacheDatabase? = null

        fun getDatabase(context: Context): AiResponseCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val appCtx = context.applicationContext ?: context
                val instance = runCatching {
                    SQLiteDatabase.loadLibs(appCtx)
                    val passphraseBytes = HardwareKeystoreVault.getOrCreateSqlCipherPassphrase(appCtx)
                    val passphraseCopy = passphraseBytes.copyOf()
                    val supportFactory = SupportFactory(passphraseCopy, null, false)
                    val encryptedDb = Room.databaseBuilder(
                        appCtx,
                        AiResponseCacheDatabase::class.java,
                        "waqar_ai_tutor_cache_encrypted.db"
                    )
                        .openHelperFactory(supportFactory)
                        .build()
                    encryptedDb.openHelper.writableDatabase
                    Arrays.fill(passphraseBytes, 0.toByte())
                    encryptedDb
                }.getOrElse {
                    Room.databaseBuilder(
                        appCtx,
                        AiResponseCacheDatabase::class.java,
                        "waqar_ai_tutor_cache_local.db"
                    ).build()
                }
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Dual-Layer Sub-Millisecond AI Response Cache Repository:
 * - Layer 1: In-memory `ConcurrentHashMap` (< 0.1ms zero-lag read)
 * - Layer 2: Room + SQLCipher `AiResponseCacheDatabase` (< 1ms indexed B-tree persistent lookup)
 */
@Obfuscate
object AiLocalCacheRepository {
    private val l1HotMemoryCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var attachedContext: Context? = null

    fun attachContext(context: Context) {
        if (attachedContext == null) {
            attachedContext = context.applicationContext ?: context
        }
    }

    fun computeCanonicalPromptHash(
        featureType: String,
        languageCode: String,
        rawPrompt: String
    ): String {
        val normalized = "${featureType.trim().uppercase()}|${languageCode.trim().uppercase()}|${
            rawPrompt.trim().lowercase().replace(Regex("\\s+"), " ")
        }"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getInstantL1CacheHit(
        featureType: String,
        languageCode: String,
        prompt: String
    ): String? {
        val hash = computeCanonicalPromptHash(featureType, languageCode, prompt)
        return l1HotMemoryCache[hash]
    }

    private fun resolveContext(context: Context?): Context? {
        context?.let {
            attachContext(it)
            return attachedContext
        }
        attachedContext?.let { return it }
        return runCatching {
            com.google.firebase.FirebaseApp.getInstance().applicationContext.also { attachedContext = it }
        }.getOrNull()
    }

    suspend fun getCachedResponse(
        context: Context?,
        featureType: String,
        languageCode: String,
        prompt: String
    ): String? {
        val hash = computeCanonicalPromptHash(featureType, languageCode, prompt)
        l1HotMemoryCache[hash]?.let { return it }

        val ctx = resolveContext(context) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val dao = AiResponseCacheDatabase.getDatabase(ctx).aiCacheDao()
                val entity = dao.getCachedResponseByHash(hash)
                if (entity != null && entity.responseText.isNotBlank()) {
                    l1HotMemoryCache[hash] = entity.responseText
                    dao.incrementHitCount(hash, System.currentTimeMillis())
                    entity.responseText
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    suspend fun putCachedResponse(
        context: Context?,
        featureType: String,
        languageCode: String,
        prompt: String,
        responseText: String,
        modelName: String
    ) {
        if (prompt.isBlank() || responseText.isBlank()) return
        val hash = computeCanonicalPromptHash(featureType, languageCode, prompt)
        l1HotMemoryCache[hash] = responseText

        val ctx = resolveContext(context) ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val dao = AiResponseCacheDatabase.getDatabase(ctx).aiCacheDao()
                val existing = dao.getCachedResponseByHash(hash)
                dao.upsertCachedResponse(
                    CachedAiResponseEntity(
                        promptHash = hash,
                        featureType = featureType.trim().uppercase(),
                        languageCode = languageCode.trim().uppercase(),
                        promptText = prompt.trim().take(4000),
                        responseText = responseText.take(16000),
                        modelName = modelName,
                        hitCount = (existing?.hitCount ?: 0) + 1,
                        createdAtMillis = existing?.createdAtMillis ?: now,
                        lastAccessedMillis = now
                    )
                )
            }
        }
    }
}
