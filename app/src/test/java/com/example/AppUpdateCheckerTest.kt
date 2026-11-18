package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppUpdateChecker
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateCheckerTest {

    @Test
    fun `isNewerVersion compares semantic version components accurately`() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.0.3", "1.0.2"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.0.10", "1.0.2"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.1.0", "1.0.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.0", "1.9.9"))

        assertFalse(AppUpdateChecker.isNewerVersion("v1.0.2", "1.0.2"))
        assertFalse(AppUpdateChecker.isNewerVersion("v1.0.1", "1.0.2"))
        assertFalse(AppUpdateChecker.isNewerVersion("dev-19", "1.0.2"))
    }

    @Test
    fun `parseReleaseObject prefers versioned apk over latest alias and aab`() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.6",
              "name": "WaddleUp v1.0.6",
              "html_url": "https://github.com/Pro-mwas1234/WaddleUp/releases/tag/v1.0.6",
              "body": "## Changes\n- Added step boost",
              "assets": [
                {
                  "name": "waddleup-v1.0.6.aab",
                  "size": 12000000,
                  "browser_download_url": "https://example.com/waddleup-v1.0.6.aab"
                },
                {
                  "name": "waddleup-latest.apk",
                  "size": 15000000,
                  "browser_download_url": "https://example.com/waddleup-latest.apk"
                },
                {
                  "name": "waddleup-v1.0.6.apk",
                  "size": 15000000,
                  "browser_download_url": "https://example.com/waddleup-v1.0.6.apk"
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = AppUpdateChecker.parseReleaseObject(json)
        assertNotNull(parsed)
        assertEquals("v1.0.6", parsed!!.tagName)
        assertEquals("1.0.6", parsed.versionName)
        assertEquals("https://example.com/waddleup-v1.0.6.apk", parsed.apkDownloadUrl)
        assertEquals(15000000L, parsed.apkSizeBytes)
    }

    @Test
    fun `pickLatestFromReleasesArray skips drafts and prefers stable releases`() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name": "v1.0.9",
                "draft": true,
                "prerelease": false,
                "assets": []
              },
              {
                "tag_name": "v1.0.8-rc1",
                "draft": false,
                "prerelease": true,
                "assets": []
              },
              {
                "tag_name": "v1.0.7",
                "draft": false,
                "prerelease": false,
                "assets": []
              }
            ]
            """.trimIndent()
        )

        val picked = AppUpdateChecker.pickLatestFromReleasesArray(releases)
        assertNotNull(picked)
        assertEquals("v1.0.7", picked!!.tagName)
        assertEquals(AppUpdateChecker.PERMANENT_LATEST_APK_URL, picked.apkDownloadUrl)
    }

    @Test
    fun `skipped tag preferences persist and clear cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppUpdateChecker.clearSkippedTag(context)
        assertFalse(AppUpdateChecker.isTagSkipped(context, "v1.0.7"))

        AppUpdateChecker.markTagSkipped(context, "v1.0.7")
        assertTrue(AppUpdateChecker.isTagSkipped(context, "v1.0.7"))
        assertFalse(AppUpdateChecker.isTagSkipped(context, "v1.0.8"))

        AppUpdateChecker.clearSkippedTag(context)
        assertFalse(AppUpdateChecker.isTagSkipped(context, "v1.0.7"))
    }
}
