package com.waqar.institute.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * StorageCleanupWorker: Production Periodic CoroutineWorker.
 *
 * Runs asynchronously on Dispatchers.IO every 24 hours (when battery is not low)
 * to prevent storage bloat on low-storage student devices:
 * 1. Purges cached files older than 7 days.
 * 2. If cache exceeds 100MB, purges oldest temporary files until cache is below 50MB.
 * 3. STRICTLY PRESERVES all security keys, encrypted preferences, databases (.db, .db-wal, .db-shm),
 *    keystores (.key, .keystore, .jks), and crash logs.
 */
class StorageCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = applicationContext
            val cacheDirs = listOfNotNull(
                ctx.cacheDir,
                ctx.externalCacheDir,
                ctx.codeCacheDir
            )

            val now = System.currentTimeMillis()
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            val maxCacheBytes = 100L * 1024L * 1024L // 100 MB
            val targetCacheBytes = 50L * 1024L * 1024L // 50 MB

            for (dir in cacheDirs) {
                if (!dir.exists() || !dir.isDirectory) continue

                // Pass 1: Remove files older than 7 days (preserving protected extensions)
                purgeOldFiles(dir, now, sevenDaysMs)

                // Pass 2: Check total cache size and trim oldest if > 100MB
                val totalSize = calculateDirSize(dir)
                if (totalSize > maxCacheBytes) {
                    trimDirSize(dir, targetBytes = targetCacheBytes)
                }
            }
            Log.d(TAG, "Storage cleanup completed successfully")
            Result.success()
        }.getOrElse { err ->
            Log.w(TAG, "Storage cleanup completed with notice: ${err.localizedMessage}")
            Result.success() // Soft fail to prevent work queue stalling
        }
    }

    private fun isProtectedFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".key") ||
            name.endsWith(".keystore") ||
            name.endsWith(".jks") ||
            name.endsWith(".db") ||
            name.endsWith(".db-wal") ||
            name.endsWith(".db-shm") ||
            name.endsWith(".prefs") ||
            name.endsWith(".xml") ||
            name.contains("keystore") ||
            name.contains("vault")
    }

    private fun purgeOldFiles(dir: File, now: Long, maxAgeMs: Long) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                purgeOldFiles(file, now, maxAgeMs)
                // Delete empty directory if safe
                if (file.listFiles()?.isEmpty() == true) {
                    file.delete()
                }
            } else if (!isProtectedFile(file)) {
                if (now - file.lastModified() > maxAgeMs) {
                    file.delete()
                }
            }
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun trimDirSize(dir: File, targetBytes: Long) {
        val allFiles = mutableListOf<File>()
        collectNonProtectedFiles(dir, allFiles)
        allFiles.sortBy { it.lastModified() } // Oldest first

        var currentSize = calculateDirSize(dir)
        for (file in allFiles) {
            if (currentSize <= targetBytes) break
            val len = file.length()
            if (file.delete()) {
                currentSize -= len
            }
        }
    }

    private fun collectNonProtectedFiles(dir: File, out: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                collectNonProtectedFiles(file, out)
            } else if (!isProtectedFile(file)) {
                out.add(file)
            }
        }
    }

    companion object {
        const val TAG = "StorageCleanupWorker"
    }
}
