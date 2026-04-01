package com.sohan.diutransportschedule.sync


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

    /**
     * GitHub updater permanently disabled.
     * Play Store build should use Google Play In-App Updates only.
     */
    suspend fun fetchManifest(): UpdateManifest? {
        return null
    }

    suspend fun resolveFor(currentVersionName: String): ResolvedUpdate? {
        return null
    }
}