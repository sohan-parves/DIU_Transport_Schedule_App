package com.sohan.diutransportschedule.sync

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String
)

object UpdateChecker {

    // ✅ GitHub API endpoint (JSON)
    private const val LATEST_URL =
        "https://api.github.com/repos/sohan-parves/DIUtransportschedule/releases/latest"

    fun fetchLatest(): UpdateInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "DIUTransportSchedule")
        }

        return try {
            val code = conn.responseCode
            if (code !in 200..299) return null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tag = json.optString("tag_name") // e.g. v1.0.2
            val assets = json.optJSONArray("assets") ?: return null

            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isNullOrBlank() || tag.isBlank()) return null
            UpdateInfo(versionName = tag.removePrefix("v"), apkUrl = apkUrl)
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }
}