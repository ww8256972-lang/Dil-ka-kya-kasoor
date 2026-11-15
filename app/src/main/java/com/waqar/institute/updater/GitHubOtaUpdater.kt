package com.waqar.institute.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Standalone GitHub Releases Update Checker & Play-Policy-Compliant Updater (`GitHubOtaUpdater.kt`).
 *
 * Capabilities:
 * 1. Queries the GitHub Releases API (`https://api.github.com/repos/{owner}/{repo}/releases/latest`)
 *    asynchronously on `Dispatchers.IO` using `OkHttpClient`.
 * 2. Normalizes and compares semantic version tags (e.g. `"v1.2.0"` vs `BuildConfig.VERSION_NAME` `"1.0"`).
 * 3. Extracts release metadata (`tagName`, `releaseTitle`, `releaseNotes`, `htmlUrl`, `downloadUrl`).
 * 4. Launches the official GitHub Release update page via standard `Intent.ACTION_VIEW` without
 *    requiring restricted `REQUEST_INSTALL_PACKAGES` manifest permissions.
 */
object GitHubOtaUpdater {

    const val DEFAULT_GITHUB_OWNER: String = "waqar-institute"
    const val DEFAULT_GITHUB_REPO: String = "waqar-computer-institute-erp"

    data class GitHubReleaseInfo(
        val tagName: String,
        val normalizedVersion: String,
        val currentVersion: String,
        val releaseTitle: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val assetUrl: String
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks GitHub Releases API on `Dispatchers.IO` and returns a `GitHubReleaseInfo` if and only if
     * a newer semantic version than `currentVersionName` is published.
     */
    suspend fun checkForUpdate(
        owner: String = DEFAULT_GITHUB_OWNER,
        repo: String = DEFAULT_GITHUB_REPO,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): GitHubReleaseInfo? = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank()) return@withContext null
        val apiUrl = "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/releases/latest"

        runCatching {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "WaqarComputerInstitute-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) return@use null
                parseReleaseJsonIfNewer(bodyStr, currentVersionName)
            }
        }.getOrNull()
    }

    /**
     * Parses a GitHub Releases `/releases/latest` JSON payload and returns `GitHubReleaseInfo`
     * if `tag_name` is strictly newer than `currentVersionName`.
     */
    fun parseReleaseJsonIfNewer(
        rawJson: String,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): GitHubReleaseInfo? {
        return runCatching {
            val json = JSONObject(rawJson)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                return null
            }
            val rawTag = json.optString("tag_name").trim()
            if (rawTag.isBlank()) return null

            if (!isNewerVersion(remoteTag = rawTag, currentVersion = currentVersionName)) {
                return null
            }

            val htmlUrl = json.optString("html_url").trim()
            val title = json.optString("name").trim().ifBlank { "Version $rawTag" }
            val notes = json.optString("body").trim().ifBlank {
                "New stability, security, and academic portal enhancements are available."
            }

            var detectedAssetUrl = htmlUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val assetObj = assets.optJSONObject(i) ?: continue
                    val downloadUrl = assetObj.optString("browser_download_url").trim()
                    if (downloadUrl.isNotBlank()) {
                        detectedAssetUrl = downloadUrl
                        break
                    }
                }
            }

            GitHubReleaseInfo(
                tagName = rawTag,
                normalizedVersion = normalizeVersionString(rawTag),
                currentVersion = normalizeVersionString(currentVersionName),
                releaseTitle = title,
                releaseNotes = notes,
                htmlUrl = htmlUrl.ifBlank { detectedAssetUrl },
                assetUrl = detectedAssetUrl.ifBlank { htmlUrl }
            )
        }.getOrNull()
    }

    /**
     * Compares two semantic version strings (e.g. `"v1.2.1"` vs `"1.0"`).
     * Returns `true` if `remoteTag` is strictly greater than `currentVersion`.
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteTag)
        val currentParts = parseVersionParts(currentVersion)
        if (remoteParts.isEmpty()) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun normalizeVersionString(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .trim()
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val clean = normalizeVersionString(raw)
        if (clean.isBlank()) return emptyList()
        return clean.split(".")
            .mapNotNull { segment ->
                segment.filter { it.isDigit() }.toIntOrNull()
            }
    }

    /**
     * Launches the official release update URL via `Intent.ACTION_VIEW`.
     */
    fun launchOfficialReleaseUpdate(context: Context, releaseInfo: GitHubReleaseInfo): Boolean {
        val targetUrl = releaseInfo.htmlUrl.ifBlank { releaseInfo.assetUrl }
        if (targetUrl.isBlank()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
