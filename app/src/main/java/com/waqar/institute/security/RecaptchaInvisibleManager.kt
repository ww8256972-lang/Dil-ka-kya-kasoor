package com.waqar.institute.security

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Silent Invisible Google reCAPTCHA Enterprise Wrapper.
 *
 * Initializes `Recaptcha.getClient(application, siteKey)` in the background using the
 * site key configured in `AndroidManifest.xml` (`com.waqar.institute.RECAPTCHA_SITE_KEY`)
 * and generates zero-interaction cryptographic risk tokens for `LOGIN`, `SIGNUP`, and
 * sensitive ERP actions without interrupting the user with visual puzzles.
 */
object RecaptchaInvisibleManager {

    private const val META_SITE_KEY = "com.waqar.institute.RECAPTCHA_SITE_KEY"

    private val clientMutex = Mutex()

    @Volatile
    private var cachedClient: RecaptchaClient? = null

    fun resolveConfiguredSiteKey(context: Context): String? {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(META_SITE_KEY)
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.startsWith("6LeIxAcTAAAAAJcZVRqy") }
        }.getOrNull()
    }

    suspend fun getOrInitClient(application: Application): Result<RecaptchaClient> =
        withContext(Dispatchers.IO) {
            cachedClient?.let { return@withContext Result.success(it) }
            clientMutex.withLock {
                cachedClient?.let { return@withContext Result.success(it) }
                val siteKey = resolveConfiguredSiteKey(application)
                    ?: return@withContext Result.failure(
                        IllegalStateException("reCAPTCHA Enterprise site key ($META_SITE_KEY) is not configured.")
                    )
                runCatching {
                    Recaptcha.fetchClient(application, siteKey).also { client ->
                        cachedClient = client
                    }
                }
            }
        }

    /**
     * Silently executes an invisible reCAPTCHA action (`LOGIN` or custom action)
     * and returns the genuine token from Google reCAPTCHA Enterprise SDK or `Result.failure`.
     */
    suspend fun executeInvisibleLoginToken(
        context: Context,
        actionName: String = "login"
    ): Result<String> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? Application
            ?: return@withContext Result.failure(IllegalStateException("Application context unavailable for reCAPTCHA."))
        val client = getOrInitClient(app).getOrElse { err ->
            return@withContext Result.failure(err)
        }
        val action = when (actionName.lowercase()) {
            "login" -> RecaptchaAction.LOGIN
            "signup" -> RecaptchaAction.SIGNUP
            else -> RecaptchaAction.custom(actionName)
        }
        val tokenResult = client.execute(action, 8_000L)
        val token = tokenResult.getOrNull().orEmpty()
        if (tokenResult.isSuccess && token.isNotBlank()) {
            Result.success(token)
        } else {
            Result.failure(
                tokenResult.exceptionOrNull()
                    ?: IllegalStateException("Empty reCAPTCHA token returned for action '$actionName'.")
            )
        }
    }
}
