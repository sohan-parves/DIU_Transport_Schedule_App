package com.sohan.diutransportschedule.sync

import android.content.Context

object ApkDownloader {
    const val PREFS = "apk_update"
    const val KEY_DOWNLOAD_ID = "download_id"
    const val KEY_EXPECTED_SHA256 = "expected_sha256"
    const val KEY_TO_VERSION_NAME = "to_version_name"
    const val KEY_ENQUEUED_TO_VERSION_NAME = "enqueued_to_version_name"
    const val KEY_DOWNLOADED_TO_VERSION_NAME = "downloaded_to_version_name"

    fun enqueueFull(
        context: Context,
        url: String,
        expectedSha256: String,
        toVersionName: String,
        fileName: String = "DIUTransportSchedule-${toVersionName}.apk"
    ): Long {
        return -1L
    }
}