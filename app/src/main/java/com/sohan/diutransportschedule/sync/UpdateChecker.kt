package com.sohan.diutransportschedule.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * FULL-APK only updater.
 * Source of truth: GitHub Releases -> latest.
 * No update.json needed.
 */
data class FullApk(
    val url: String,
    val size: Long
)

data class UpdateManifest(
    val latestVersionName: String,
    val fullApk: FullApk
)

sealed class ResolvedUpdate {
    data class Full(val toVersionName: String, val full: FullApk) : ResolvedUpdate()
}

object UpdateChecker {

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/sohan-parves/DIUtransportschedule/releases/latest"

    /** IO thread এ চালাবে */
    suspend fun fetchManifest(): UpdateManifest? = withContext(Dispatchers.IO) {
        val conn = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "DIUTransportSchedule")
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) return@withContext null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tag = json.optString("tag_name", "").trim()
            if (tag.isBlank()) return@withContext null

            val versionName = tag.removePrefix("v").removePrefix("V").trim()
            if (versionName.isBlank()) return@withContext null

            val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            var apkSize = -1L

            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                if (!name.endsWith(".apk", ignoreCase = true)) continue

                val url = a.optString("browser_download_url", "")
                val size = a.optLong("size", -1)
                if (url.isBlank() || size <= 0) continue

                apkUrl = url
                apkSize = size

                if (name.equals("DIUTransportSchedule.apk", ignoreCase = true)) break
            }

            if (apkUrl.isBlank() || apkSize <= 0) return@withContext null

            UpdateManifest(
                latestVersionName = versionName,
                fullApk = FullApk(url = apkUrl, size = apkSize)
            )
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun resolveFor(currentVersionName: String): ResolvedUpdate? {
        val m = fetchManifest() ?: return null
        if (compareSemver(currentVersionName, m.latestVersionName) >= 0) return null
        return ResolvedUpdate.Full(m.latestVersionName, m.fullApk)
    }

    private fun compareSemver(a: String, b: String): Int {
        fun parts(s: String): List<Int> {
            val clean = s.trim().removePrefix("v").removePrefix("V")
            val p = clean.split(".").map {
                it.filter(Char::isDigit).toIntOrNull() ?: 0
            }
            return when (p.size) {
                0 -> listOf(0, 0, 0)
                1 -> listOf(p[0], 0, 0)
                2 -> listOf(p[0], p[1], 0)
                else -> p.take(3)
            }
        }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0..2) {
            if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        }
        return 0
    }
}