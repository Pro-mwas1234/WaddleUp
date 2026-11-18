package com.example.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String,
    val apkSizeBytes: Long,
    val expectedSha256: String? = null
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpdateAvailable(val release: GitHubReleaseInfo) : AppUpdateState
    data class Downloading(
        val release: GitHubReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateState
    data class ReadyToInstall(
        val release: GitHubReleaseInfo,
        val apkFilePath: String,
        val needsInstallPermission: Boolean = false
    ) : AppUpdateState
    data class UpToDate(val currentVersion: String) : AppUpdateState
    data class Error(
        val message: String,
        val failedRelease: GitHubReleaseInfo? = null
    ) : AppUpdateState
}

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    const val REPO_SLUG = "Pro-mwas1234/WaddleUp"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_SLUG/releases/latest"
    const val PAGES_SNAPSHOT_URL = "https://pro-mwas1234.github.io/WaddleUp/releases.json"
    const val PERMANENT_LATEST_APK_URL =
        "https://github.com/$REPO_SLUG/releases/latest/download/waddleup-latest.apk"

    private const val PREFS_NAME = "waddle_update_prefs"
    private const val KEY_SKIPPED_TAG = "skipped_release_tag"
    private const val MAX_REDIRECTS = 5

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

    suspend fun downloadApk(
        release: GitHubReleaseInfo,
        updatesDir: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        val safeTag = release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetFile = File(updatesDir, "waddleup-$safeTag.apk")
        val tempFile = File(updatesDir, "waddleup-$safeTag.apk.tmp")

        // Reuse already downloaded valid APK if present
        if (targetFile.exists() && targetFile.length() > 0L) {
            val expected = release.expectedSha256
            if (expected.isNullOrBlank() || computeSha256(targetFile).equals(expected, ignoreCase = true)) {
                onProgress(targetFile.length(), targetFile.length())
                return@withContext targetFile
            }
            targetFile.delete()
        }

        // Remove older cached APKs/temp files so cache doesn't grow
        updatesDir.listFiles()?.forEach { old ->
            if (old.absolutePath != targetFile.absolutePath) {
                old.delete()
            }
        }

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(
                urlString = release.apkDownloadUrl,
                userAgent = "WaddleUp-Android/${BuildConfig.VERSION_NAME}",
                acceptHeader = "application/octet-stream"
            )
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download failed (HTTP $code)")
            }

            val totalBytes = connection.contentLengthLong.let { len ->
                if (len > 0L) len else release.apkSizeBytes
            }
            var downloaded = 0L
            onProgress(0L, totalBytes)

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var lastReportTime = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 100L) {
                            lastReportTime = now
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded <= 0L) {
                throw IllegalStateException("Downloaded APK file was empty.")
            }

            val expectedSha = release.expectedSha256
            if (!expectedSha.isNullOrBlank()) {
                val actualSha = computeSha256(tempFile)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    tempFile.delete()
                    throw IllegalStateException("APK checksum verification failed.")
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(downloaded, maxOf(totalBytes, downloaded))
            targetFile
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            connection?.disconnect()
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openUnknownSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Unable to open unknown app sources settings", e)
            false
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0L) return false
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch APK installer", e)
            false
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
        var versionedSha256: String? = null
        var fallbackApkUrl: String? = null
        var fallbackApkSize = 0L
        var fallbackSha256: String? = null

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)
                val digest = asset.optString("digest", "")
                    .removePrefix("sha256:")
                    .trim()
                    .ifBlank { null }
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    if (!name.equals("waddleup-latest.apk", ignoreCase = true)) {
                        versionedApkUrl = url
                        versionedApkSize = size
                        versionedSha256 = digest
                        break
                    } else if (fallbackApkUrl == null) {
                        fallbackApkUrl = url
                        fallbackApkSize = size
                        fallbackSha256 = digest
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
        val rootSha256 = json.optString("apkSha256", "")
            .removePrefix("sha256:")
            .trim()
            .ifBlank { null }
        val finalSha256 = versionedSha256 ?: fallbackSha256 ?: rootSha256

        val title = json.optString("name", "").ifBlank { "WaddleUp $tagName" }
        val body = json.optString("body", "").trim()

        return GitHubReleaseInfo(
            tagName = tagName,
            versionName = normalizeVersionString(tagName),
            releaseTitle = title,
            releaseNotes = body,
            apkDownloadUrl = finalApkUrl,
            releasePageUrl = releasePageUrl,
            apkSizeBytes = finalApkSize,
            expectedSha256 = finalSha256
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

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun openFollowingRedirects(
        urlString: String,
        userAgent: String,
        acceptHeader: String
    ): HttpURLConnection {
        var currentUrl = URL(urlString)
        for (attempt in 0..MAX_REDIRECTS) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 20000
                setRequestProperty("User-Agent", userAgent)
                if (attempt == 0) {
                    setRequestProperty("Accept", acceptHeader)
                }
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect missing Location header")
                }
                currentUrl = URL(currentUrl, location)
                continue
            }
            return conn
        }
        throw IllegalStateException("Too many redirects while downloading update")
    }

    private fun httpGetString(urlString: String, userAgent: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = openFollowingRedirects(
                urlString = urlString,
                userAgent = userAgent,
                acceptHeader = "application/vnd.github+json"
            )
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
