package com.sohan.diutransportschedule.sync

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object ApkDownloader {

    fun enqueue(context: Context, url: String, fileName: String = "DIUTransportSchedule-latest.apk"): Long {
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("DIU Transport Schedule")
            setDescription("Downloading update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)

            // ✅ app-specific external files dir (no storage permission needed)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }
}