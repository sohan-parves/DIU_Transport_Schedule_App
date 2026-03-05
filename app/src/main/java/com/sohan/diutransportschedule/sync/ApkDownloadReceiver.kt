package com.sohan.diutransportschedule.sync

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ApkDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val prefs = context.getSharedPreferences(ApkDownloader.PREFS, Context.MODE_PRIVATE)
        val expectedId = prefs.getLong(ApkDownloader.KEY_DOWNLOAD_ID, -1L)
        if (expectedId != id) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(id)
        val c: Cursor = dm.query(q) ?: return

        c.use {
            if (!it.moveToFirst()) return

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                // allow retry next time
                prefs.edit().putString(ApkDownloader.KEY_ENQUEUED_TO_VERSION_NAME, "").apply()
                return
            }
            val localUriStr = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (localUriStr.isNullOrBlank()) return

            val localUri = Uri.parse(localUriStr)
            val file = if (localUri.scheme == "file") File(localUri.path!!) else null
            if (file == null || !file.exists()) return

            val expectedSha = prefs.getString(ApkDownloader.KEY_EXPECTED_SHA256, "") ?: ""

            // integrity verify
            if (expectedSha.isNotBlank()) {
                val actual = sha256Hex(file)
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    Log.e("AppUpdate", "SHA256 mismatch; abort install")
                    return
                }
            }

            // Mark downloaded target so we don't enqueue again on next launches
            val toVersionName = prefs.getString(ApkDownloader.KEY_TO_VERSION_NAME, "") ?: ""
            if (toVersionName.isNotBlank()) {
                prefs.edit()
                    .putString(ApkDownloader.KEY_DOWNLOADED_TO_VERSION_NAME, toVersionName)
                    .putString(ApkDownloader.KEY_ENQUEUED_TO_VERSION_NAME, "")
                    .apply()
            }
            // FULL APK install only
            installApk(context, file)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(1024 * 64)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        val digest = md.digest()
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}