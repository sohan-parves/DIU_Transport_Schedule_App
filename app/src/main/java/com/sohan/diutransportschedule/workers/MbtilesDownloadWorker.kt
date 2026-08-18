package com.sohan.diutransportschedule.workers

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MbtilesDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val routeId = inputData.getString("route_id") ?: return@withContext Result.failure()
        if (routeId.isBlank()) return@withContext Result.failure()
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val style = inputData.getString("style") ?: "light"
        val force = inputData.getBoolean("force", false)

        val outDir = File(applicationContext.filesDir, "offline").apply { mkdirs() }
        val tmp = File(outDir, "route_${routeId}_${style}.mbtiles.part")
        val dst = File(outDir, "route_${routeId}_${style}.mbtiles")
        val metaFile = File(outDir, "route_${routeId}_${style}.meta.json")

        runCatching { setForeground(createFg(-1, "Checking map update…")) }

        return@withContext try {
            val remoteMeta = fetchRemoteMeta(url)
            val localMeta = readLocalMeta(metaFile)

            if (!force && dst.exists() && remoteMeta != null && localMeta != null && localMeta.matches(
                    remoteMeta
                )
            ) {
                setProgress(
                    workDataOf(
                        "progress" to 100,
                        "done_bytes" to dst.length(),
                        "total_bytes" to dst.length(),
                        "file_path" to dst.absolutePath,
                        "unchanged" to true,
                        "version_label" to remoteMeta.versionLabel()
                    )
                )
                return@withContext Result.success(
                    workDataOf(
                        "file_path" to dst.absolutePath,
                        "unchanged" to true,
                        "version_label" to remoteMeta.versionLabel()
                    )
                )
            }

            val existingBytes = if (!force && tmp.exists()) tmp.length().coerceAtLeast(0L) else 0L
            if (force && tmp.exists()) tmp.delete()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                doInput = true
                if (existingBytes > 0L) {
                    setRequestProperty("Range", "bytes=${existingBytes}-")
                }
                connect()
            }

            val code = connection.responseCode
            val totalRemoteBytes = remoteMeta?.contentLength
                ?: if (connection.contentLengthLong > 0) connection.contentLengthLong else -1L

            val shouldAppend = existingBytes > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (code !in 200..299 && code != HttpURLConnection.HTTP_PARTIAL) {
                connection.disconnect()
                return@withContext if (runAttemptCount >= 3) Result.failure() else Result.retry()
            }

            if (existingBytes > 0L && !shouldAppend) {
                tmp.delete()
            }

            var done = if (shouldAppend) existingBytes else 0L
            var lastUiUpdateAt = 0L
            val input = connection.inputStream

            runCatching {
                setForeground(
                    createFg(
                        if (totalRemoteBytes > 0L) ((done * 100) / totalRemoteBytes).toInt()
                            .coerceIn(0, 100) else -1,
                        if (shouldAppend) "Resuming offline map download…" else "Downloading offline map…"
                    )
                )
            }

            input.use { stream ->
                FileOutputStream(tmp, shouldAppend).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val r = stream.read(buf)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        done += r

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateAt >= 400) {
                            lastUiUpdateAt = now
                            if (totalRemoteBytes > 0L) {
                                val p = ((done * 100) / totalRemoteBytes).toInt().coerceIn(0, 100)
                                setProgress(
                                    workDataOf(
                                        "progress" to p,
                                        "done_bytes" to done,
                                        "total_bytes" to totalRemoteBytes,
                                        "version_label" to (remoteMeta?.versionLabel() ?: "")
                                    )
                                )
                                runCatching {
                                    setForeground(
                                        createFg(
                                            p,
                                            if (shouldAppend) "Resuming offline map download…" else "Downloading offline map…"
                                        )
                                    )
                                }
                            } else {
                                setProgress(
                                    workDataOf(
                                        "progress" to -1,
                                        "done_bytes" to done,
                                        "total_bytes" to -1L,
                                        "version_label" to (remoteMeta?.versionLabel() ?: "")
                                    )
                                )
                                runCatching {
                                    setForeground(
                                        createFg(
                                            -1,
                                            if (shouldAppend) "Resuming offline map download…" else "Downloading offline map…"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            connection.disconnect()

            if (totalRemoteBytes > 0L && done < totalRemoteBytes) {
                return@withContext if (runAttemptCount >= 3) Result.failure() else Result.retry()
            }

            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) return@withContext if (runAttemptCount >= 3) Result.failure() else Result.retry()

            writeLocalMeta(
                metaFile = metaFile,
                remote = remoteMeta ?: RemoteMapMeta(
                    etag = null,
                    lastModified = null,
                    contentLength = dst.length()
                ),
                fileSize = dst.length()
            )

            if (totalRemoteBytes > 0L) {
                setProgress(
                    workDataOf(
                        "progress" to 100,
                        "done_bytes" to done,
                        "total_bytes" to totalRemoteBytes,
                        "file_path" to dst.absolutePath,
                        "version_label" to (remoteMeta?.versionLabel() ?: "")
                    )
                )
                runCatching { setForeground(createFg(100, "Offline map ready")) }
            }

            Result.success(
                workDataOf(
                    "file_path" to dst.absolutePath,
                    "version_label" to (remoteMeta?.versionLabel() ?: "")
                )
            )
        } catch (_: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private fun fetchRemoteMeta(url: String): RemoteMapMeta? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "HEAD"
                doInput = false
                connect()
            }
            val meta = if (conn.responseCode in 200..299) {
                RemoteMapMeta(
                    etag = conn.getHeaderField("ETag")?.trim()?.trim('"'),
                    lastModified = conn.getHeaderField("Last-Modified")?.trim(),
                    contentLength = conn.contentLengthLong.takeIf { it > 0 }
                )
            } else {
                null
            }
            conn.disconnect()
            meta
        } catch (_: Exception) {
            null
        }
    }

    private fun readLocalMeta(metaFile: File): LocalMapMeta? {
        return try {
            if (!metaFile.exists()) return null
            val json = JSONObject(metaFile.readText())
            LocalMapMeta(
                etag = json.optString("etag", "").ifBlank { null },
                lastModified = json.optString("lastModified", "").ifBlank { null },
                remoteContentLength = json.optLong("remoteContentLength", -1L).takeIf { it > 0 },
                localFileSize = json.optLong("localFileSize", -1L).takeIf { it >= 0 }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeLocalMeta(metaFile: File, remote: RemoteMapMeta, fileSize: Long) {
        val json = JSONObject()
            .put("etag", remote.etag ?: JSONObject.NULL)
            .put("lastModified", remote.lastModified ?: JSONObject.NULL)
            .put("remoteContentLength", remote.contentLength ?: JSONObject.NULL)
            .put("localFileSize", fileSize)
        metaFile.writeText(json.toString())
    }

    private fun createFg(progress: Int, statusText: String): ForegroundInfo {
        val channelId = "offline_downloads"
        ensureChannel(channelId)

        val indeterminate = progress < 0
        val safeProgress = progress.coerceIn(0, 100)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("Offline map sync")
            .setContentText(if (indeterminate) statusText else "$statusText ($safeProgress%)")
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
                    NotificationChannel(
                        channelId,
                        "Offline Downloads",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private data class RemoteMapMeta(
        val etag: String?,
        val lastModified: String?,
        val contentLength: Long?
    ) {
        fun versionLabel(): String {
            return etag ?: lastModified ?: contentLength?.toString() ?: ""
        }
    }

    private data class LocalMapMeta(
        val etag: String?,
        val lastModified: String?,
        val remoteContentLength: Long?,
        val localFileSize: Long?
    ) {
        fun matches(remote: RemoteMapMeta): Boolean {
            if (localFileSize == null || localFileSize <= 0L) return false
            remote.contentLength?.let { if (it != localFileSize) return false }
            if (!etag.isNullOrBlank() && !remote.etag.isNullOrBlank()) {
                return etag == remote.etag
            }
            if (!lastModified.isNullOrBlank() && !remote.lastModified.isNullOrBlank()) {
                return lastModified == remote.lastModified && (remote.contentLength == null || remote.contentLength == localFileSize)
            }
            return remote.contentLength != null && remote.contentLength == localFileSize && remote.contentLength == remoteContentLength
        }
    }
}