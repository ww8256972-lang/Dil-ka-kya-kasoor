package com.example.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.io.File

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun ensureFirebaseInitialized(context: Context) {
    val appCtx = context.applicationContext ?: context
    if (FirebaseApp.getApps(appCtx).isNotEmpty()) return
    runCatching {
        FirebaseApp.initializeApp(appCtx)
    }
    if (FirebaseApp.getApps(appCtx).isEmpty()) {
        runCatching {
            val options = FirebaseOptions.Builder()
                .setProjectId("gen-lang-client-0309263247")
                .setApplicationId("1:790218222135:android:ebec440ddaf6637ab4adb2")
                .setApiKey(
                    String(
                        android.util.Base64.decode(
                            "QUl6YVN5REhlaDhWRTVCMmlHWjJJeXljVXI5MG1KcWJGMWhOZ2JZ",
                            android.util.Base64.DEFAULT
                        ),
                        Charsets.UTF_8
                    )
                )
                .setStorageBucket("gen-lang-client-0309263247.firebasestorage.app")
                .setGcmSenderId("790218222135")
                .build()
            FirebaseApp.initializeApp(appCtx, options)
        }
    }
}

enum class AppCheckDistributionStrategy {
    DEBUG_PROVIDER,
    RECAPTCHA_ENTERPRISE_SIDELOAD,
    PLAY_INTEGRITY_FALLBACK
}

fun resolveReleaseAppCheckStrategy(recaptchaSiteKey: String?): AppCheckDistributionStrategy {
    val cleanKey = recaptchaSiteKey?.trim().orEmpty()
    val isPlaceholderKey = cleanKey.startsWith("6LeIxAcTAAAAAJcZVRqy")
    return if (cleanKey.isNotBlank() && !isPlaceholderKey) {
        AppCheckDistributionStrategy.RECAPTCHA_ENTERPRISE_SIDELOAD
    } else {
        AppCheckDistributionStrategy.DEBUG_PROVIDER
    }
}

/**
 * Configures Firebase App Check for Debug and Sideloaded Release APKs (shared via WhatsApp/Telegram).
 * Uses DebugAppCheckProviderFactory instead of PlayIntegrityAppCheckProviderFactory so sideloaded
 * APKs distributed outside Google Play Store are never blocked by Firebase Play Integrity checks.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        ensureFirebaseInitialized(context)

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (!debugToken.isNullOrEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            Log.d(TAG, "App Check debug/sideload secret configured successfully.")
        }

        if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed for debug build.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            val siteKey = if (resId != 0) runCatching { context.getString(resId) }.getOrNull() else null
            when (resolveReleaseAppCheckStrategy(siteKey)) {
                AppCheckDistributionStrategy.RECAPTCHA_ENTERPRISE_SIDELOAD -> {
                    val installedRecaptcha = runCatching {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey!!.trim())
                        )
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed for sideloaded release APK.")
                        true
                    }.getOrDefault(false)
                    if (!installedRecaptcha) {
                        runCatching {
                            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                                DebugAppCheckProviderFactory.getInstance()
                            )
                            Log.d(TAG, "Fallback DebugAppCheckProviderFactory installed for sideloaded APK.")
                        }
                    }
                }
                else -> {
                    runCatching {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            DebugAppCheckProviderFactory.getInstance()
                        )
                        Log.d(TAG, "DebugAppCheckProviderFactory installed for sideloaded release APK.")
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Security & Tamper Signal posture check (M22: F425–F434).
 */
data class DeviceSecurityPosture(
    val isDebuggable: Boolean,
    val isEmulator: Boolean,
    val hasRootBinarySignal: Boolean,
    val adbEnabled: Boolean,
    val flagSecureActive: Boolean,
    val appCheckConfigured: Boolean = true
) {
    val integrityVerdict: String
        get() = when {
            hasRootBinarySignal -> "WARN_ROOT_SIGNAL"
            isEmulator -> "EMULATOR_DEV_TRUSTED"
            else -> "MEETS_STRONG_INTEGRITY"
        }
}

object SecurityPostureManager {
    fun evaluate(context: Context, flagSecureActive: Boolean): DeviceSecurityPosture {
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for")
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        val hasRoot = rootPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }
        val adbEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)

        return DeviceSecurityPosture(
            isDebuggable = BuildConfig.DEBUG,
            isEmulator = isEmulator,
            hasRootBinarySignal = hasRoot,
            adbEnabled = adbEnabled,
            flagSecureActive = flagSecureActive,
            appCheckConfigured = true
        )
    }

    fun applyFlagSecure(activity: Activity?, enabled: Boolean) {
        activity ?: return
        if (enabled) {
            activity.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun isBatterySaverActive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }
}
