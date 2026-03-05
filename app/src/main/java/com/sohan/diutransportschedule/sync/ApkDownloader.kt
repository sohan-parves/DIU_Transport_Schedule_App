package com.sohan.diutransportschedule.sync

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val downloaded = prefs.getString(KEY_DOWNLOADED_TO_VERSION_NAME, "") ?: ""
        if (downloaded.equals(toVersionName, ignoreCase = true)) {
            return prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        }

        val enqueued = prefs.getString(KEY_ENQUEUED_TO_VERSION_NAME, "") ?: ""
        if (enqueued.equals(toVersionName, ignoreCase = true)) {
            return prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        }

        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("DIU Transport Schedule")
            setDescription("Downloading update…")
            setMimeType("application/vnd.android.package-archive")
            addRequestHeader("User-Agent", "DIUTransportSchedule")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setVisibleInDownloadsUi(true)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(req)

        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_EXPECTED_SHA256, expectedSha256)
            .putString(KEY_TO_VERSION_NAME, toVersionName)
            .putString(KEY_ENQUEUED_TO_VERSION_NAME, toVersionName)
            .apply()

        return id
    }
}