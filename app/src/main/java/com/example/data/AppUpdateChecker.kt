package com.example.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String,
    val apkSizeBytes: Long
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpdateAvailable(val release: GitHubReleaseInfo) : AppUpdateState
    data class UpToDate(val currentVersion: String) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    const val REPO_SLUG = "Pro-mwas1234/WaddleUp"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_SLUG/releases/latest"
    const val PAGES_SNAPSHOT_URL = "https://pro-mwas1234.github.io/WaddleUp/releases.json"
    const val WEBSITE_URL = "https://pro-mwas1234.github.io/WaddleUp/"
    const val PERMANENT_LATEST_APK_URL =
        "https://github.com/$REPO_SLUG/releases/latest/download/waddleup-latest.apk"

    private const val PREFS_NAME = "waddle_update_prefs"
    private const val KEY_SKIPPED_TAG = "skipped_release_tag"

    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val localVersion = normalizeVersionString(currentVersionName)
        try {
            val latestRelease = fetchLatestRelease(localVersion)
                ?: return@withContext AppUpdateState.UpToDate(localVersion)

            if (isNewerVersion(latestRelease.tagName, localVersion)) {
                AppUpdateState.UpdateAvailable(latestRelease)
            } else {
                AppUpdateState.UpToDate(localVersion)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check GitHub for updates", e)
            AppUpdateState.Error(e.message ?: "Could not reach GitHub Releases.")
        }
    }

    private fun fetchLatestRelease(userAgentVersion: String): GitHubReleaseInfo? {
        val primaryJson = httpGetString(
            urlString = LATEST_RELEASE_API,
            userAgent = "WaddleUp-Android/$userAgentVersion"
        )
        if (primaryJson != null) {
            return parseReleaseObject(JSONObject(primaryJson))
        }

        // Fallback to the unthrottled GitHub Pages release snapshot if GitHub API rate-limits or 404s
        val snapshotJson = httpGetString(
            urlString = PAGES_SNAPSHOT_URL,
            userAgent = "WaddleUp-Android/$userAgentVersion"
        ) ?: return null

        val root = JSONObject(snapshotJson)
        val releases = root.optJSONArray("releases") ?: return null
        return pickLatestFromReleasesArray(releases)
    }

    internal fun pickLatestFromReleasesArray(releases: JSONArray): GitHubReleaseInfo? {
        var firstValid: GitHubReleaseInfo? = null
        for (i in 0 until releases.length()) {
            val obj = releases.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val parsed = parseReleaseObject(obj) ?: continue
            if (!obj.optBoolean("prerelease", false)) {
                return parsed
            }
            if (firstValid == null) {
                firstValid = parsed
            }
        }
        return firstValid
    }

    internal fun parseReleaseObject(json: JSONObject): GitHubReleaseInfo? {
        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null

        val assets = json.optJSONArray("assets")
        var versionedApkUrl: String? = null
        var versionedApkSize = 0L
        var fallbackApkUrl: String? = null
        var fallbackApkSize = 0L

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    if (!name.equals("waddleup-latest.apk", ignoreCase = true)) {
                        versionedApkUrl = url
                        versionedApkSize = size
                        break
                    } else if (fallbackApkUrl == null) {
                        fallbackApkUrl = url
                        fallbackApkSize = size
                    }
                }
            }
        }

        val releasePageUrl = json.optString(
            "html_url",
            "https://github.com/$REPO_SLUG/releases/tag/$tagName"
        ).ifBlank { "https://github.com/$REPO_SLUG/releases/latest" }

        val finalApkUrl = versionedApkUrl ?: fallbackApkUrl ?: PERMANENT_LATEST_APK_URL
        val finalApkSize = if (versionedApkUrl != null) versionedApkSize else fallbackApkSize

        val title = json.optString("name", "").ifBlank { "WaddleUp $tagName" }
        val body = json.optString("body", "").trim()

        return GitHubReleaseInfo(
            tagName = tagName,
            versionName = normalizeVersionString(tagName),
            releaseTitle = title,
            releaseNotes = body,
            apkDownloadUrl = finalApkUrl,
            releasePageUrl = releasePageUrl,
            apkSizeBytes = finalApkSize
        )
    }

    fun normalizeVersionString(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    fun parseNumericVersion(raw: String): List<Int>? {
        val cleaned = normalizeVersionString(raw)
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (cleaned.isEmpty() || !cleaned.first().isDigit()) return null
        val parts = cleaned.split('.')
        val numbers = parts.map { segment -> segment.toIntOrNull() ?: return null }
        return if (numbers.isEmpty()) null else numbers
    }

    fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remoteParts = parseNumericVersion(remoteTag) ?: return false
        val localParts = parseNumericVersion(localVersion) ?: return false
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val remoteVal = remoteParts.getOrElse(i) { 0 }
            val localVal = localParts.getOrElse(i) { 0 }
            if (remoteVal != localVal) {
                return remoteVal > localVal
            }
        }
        return false
    }

    fun formatApkSize(bytes: Long): String {
        if (bytes <= 0L) return "Direct APK"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format("%.1f MB", mb)
        } else {
            val kb = bytes.toDouble() / 1024.0
            String.format("%.0f KB", kb)
        }
    }

    fun isTagSkipped(context: Context, tagName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SKIPPED_TAG, null) == tagName
    }

    fun markTagSkipped(context: Context, tagName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SKIPPED_TAG, tagName)
            .apply()
    }

    fun clearSkippedTag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SKIPPED_TAG)
            .apply()
    }

    fun launchExternalUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app available to open $url", e)
            false
        }
    }

    private fun httpGetString(urlString: String, userAgent: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", userAgent)
            }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.d(TAG, "GET $urlString returned HTTP $code")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "GET $urlString failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
