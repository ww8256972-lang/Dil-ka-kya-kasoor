package com.example

import android.app.Application
import android.content.Context
import com.example.security.ensureFirebaseInitialized
import com.waqar.institute.utils.GlobalExceptionHandler
import kotlinx.coroutines.launch

/**
 * Application entry point for Waqar Computer Institute ERP.
 *
 * Guarantees that `FirebaseApp` and `GlobalExceptionHandler` are initialized
 * before any Activity, WorkManager CoroutineWorker, Service, or BroadcastReceiver starts,
 * even when built via GitHub Actions CI without a bundled `google-services.json`.
 */
class WaqarInstituteApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        base?.let { ctx ->
            runCatching { GlobalExceptionHandler.install() }
            runCatching { ensureFirebaseInitialized(ctx) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { GlobalExceptionHandler.install() }
        runCatching { ensureFirebaseInitialized(this) }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                com.waqar.institute.biometric.AdminFaceConversionAndGateManager.ensureMasterAdminPhotoEnrolled(
                    this@WaqarInstituteApplication
                )
            }
        }
    }
}
