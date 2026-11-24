package com.waqar.institute

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.BuildConfig
import com.waqar.institute.di.PerformanceModule
import com.waqar.institute.universal.WaqarUniversalAppBootstrapper
import com.waqar.institute.workers.StorageCleanupWorker
import java.util.concurrent.TimeUnit

/**
 * InstituteApp: Enterprise Root Application Class.
 *
 * Extends [WaqarUniversalAppBootstrapper] and implements [Configuration.Provider]
 * for WorkManager background execution and performance optimization:
 * 1. Installs high-efficiency Coil ImageLoader with 15% RAM and 50MB disk cache.
 * 2. Schedules [StorageCleanupWorker] periodically every 24 hours (with battery-not-low constraint).
 * 3. Configures WorkManager with optimized logging levels.
 */
class InstituteApp : WaqarUniversalAppBootstrapper(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        val appCtx = applicationContext ?: this

        val vmName = System.getProperty("java.vm.name").orEmpty()
        val isAndroidRuntime = vmName.contains("Dalvik", ignoreCase = true) || vmName.contains("ART", ignoreCase = true)

        if (isAndroidRuntime) {
            // 1. Install Performance Module Coil ImageLoader
            runCatching {
                PerformanceModule.installCoilImageLoader(appCtx)
            }.onFailure { err ->
                Log.w(TAG, "PerformanceModule Coil setup notice: ${err.localizedMessage}")
            }

            // 2. Schedule 24h Periodic Storage Cleanup Work
            scheduleStorageCleanupWork(appCtx)
        }

        // 3. Initialize Advanced Mathematical Security & Performance Integrity Orchestrators
        com.waqar.security.advanced.AdvancedSecurityOrchestrator.initialize(appCtx)
        com.waqar.performance.PerformanceIntegrityOrchestrator.initialize(appCtx)
    }

    private fun scheduleStorageCleanupWork(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val cleanupRequest = PeriodicWorkRequestBuilder<StorageCleanupWorker>(
                24, TimeUnit.HOURS,
                6, TimeUnit.HOURS // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "WciStorageCleanupWork",
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )
        }.onFailure { err ->
            Log.w(TAG, "StorageCleanupWorker scheduling notice: ${err.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "InstituteApp"
    }
}
