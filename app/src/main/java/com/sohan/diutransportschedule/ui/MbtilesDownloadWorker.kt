package com.sohan.diutransportschedule.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MbtilesDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val routeId = inputData.getString("route_id") ?: return@withContext Result.failure()
        if (routeId.isBlank()) return@withContext Result.failure()
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val style = inputData.getString("style") ?: "light"

        val outDir = File(applicationContext.filesDir, "offline").apply { mkdirs() }
        val tmp = File(outDir, "route_${routeId}_${style}.mbtiles.part")
        val dst = File(outDir, "route_${routeId}_${style}.mbtiles")

        runCatching { setForeground(createFg(-1)) }

        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
        return@withContext try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use Result.retry()
                val body = resp.body ?: return@use Result.retry()

                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                var done = 0L

// Throttle UI updates (less overhead => faster)
                var lastUiUpdateAt = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024) // bigger buffer => faster
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            out.write(buf, 0, r)
                            done += r

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdateAt >= 500) {
                                lastUiUpdateAt = now

                                if (total > 0) {
                                    val p = ((done * 100) / total).toInt().coerceIn(0, 100)
                                    setProgress(workDataOf("progress" to p, "done_bytes" to done, "total_bytes" to total))
                                    runCatching { setForeground(createFg(p)) }
                                } else {
                                    // Unknown total => UI shows indeterminate progress instead of 0%
                                    setProgress(workDataOf("progress" to -1, "done_bytes" to done, "total_bytes" to -1L))
                                    runCatching { setForeground(createFg(-1)) }
                                }
                            }
                        }
                    }
                }

// Final progress
                if (total > 0) {
                    setProgress(workDataOf("progress" to 100, "done_bytes" to done, "total_bytes" to total))
                    runCatching { setForeground(createFg(100)) }
                } else {
                    setProgress(workDataOf("progress" to -1, "done_bytes" to done, "total_bytes" to -1L))
                }
            }

            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) return@withContext Result.retry()

            Result.success(workDataOf("file_path" to dst.absolutePath))
        } catch (e: Exception) {
            if (tmp.exists()) tmp.delete()
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private fun createFg(progress: Int): ForegroundInfo {
        val channelId = "offline_downloads"
        ensureChannel(channelId)

        val indeterminate = progress < 0
        val safeProgress = progress.coerceIn(0, 100)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading offline map")
            .setContentText(if (indeterminate) "Preparing download..." else "$safeProgress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, safeProgress, indeterminate)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(1001, notification)
        }
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Offline Downloads", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}