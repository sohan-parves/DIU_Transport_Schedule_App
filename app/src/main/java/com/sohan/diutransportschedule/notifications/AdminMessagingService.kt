package com.sohan.diutransportschedule.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sohan.diutransportschedule.MainActivity
import com.sohan.diutransportschedule.R
import com.sohan.diutransportschedule.ui.notice.cacheNoticeFromPush
import com.sohan.diutransportschedule.ui.notice.registerNoticePushForHomePopup
import android.net.Uri
const val ACTION_NEW_NOTICE = "com.sohan.diutransportschedule.ACTION_NEW_NOTICE"

class AdminMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        ensureAdminNotificationChannel(applicationContext)
        FirebaseMessaging.getInstance().subscribeToTopic("diu_admin")
        FirebaseMessaging.getInstance().subscribeToTopic("diu_transport")
    }
    // onMessageReceived(remoteMessage: RemoteMessage) -- removed duplicate, logic merged below

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Keep topic subscription after token refresh
        FirebaseMessaging.getInstance()
            .subscribeToTopic("diu_admin")
            .addOnFailureListener {
                Log.w(
                    "FCM",
                    "Failed to subscribe diu_admin after token refresh",
                    it
                )
            }
        FirebaseMessaging.getInstance()
            .subscribeToTopic("diu_transport")
            .addOnFailureListener {
                Log.w(
                    "FCM",
                    "Failed to subscribe diu_transport after token refresh",
                    it
                )
            }
        ensureAdminNotificationChannel(applicationContext)
        Log.d("FCM", "New FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Support both Data and Notification payloads
        val rawType = message.data["type"]
            ?: message.data["messageType"]
            ?: message.data["category"]
            ?: message.notification?.tag
            ?: ""
        val type = rawType.trim()
        Log.d(
            "FCM",
            "onMessageReceived: type=$type data=${message.data} notifTitle=${message.notification?.title} notifBody=${message.notification?.body}"
        )

        val nowMs = System.currentTimeMillis()

        val looksLikeNotice = isNoticePayload(message, type)

        val title = when {
            looksLikeNotice ->
                firstNonBlank(
                    message.data["title"],
                    message.data["noticeTitle"],
                    message.data["notice_title"],
                    message.notification?.title
                ) ?: "Transport Notice"

            else ->
                firstNonBlank(
                    message.notification?.title,
                    message.data["title"],
                    message.data["noticeTitle"],
                    message.data["notice_title"]
                ) ?: "DIU Transport Schedule"
        }

        val body = when {
            looksLikeNotice ->
                firstNonBlank(
                    message.data["body"],
                    message.data["message"],
                    message.data["noticeBody"],
                    message.data["notice_body"],
                    message.data["content"],
                    message.data["text"],
                    message.notification?.body
                ).orEmpty()

            else ->
                firstNonBlank(
                    message.notification?.body,
                    message.data["body"],
                    message.data["message"],
                    message.data["noticeBody"],
                    message.data["notice_body"],
                    message.data["content"],
                    message.data["text"]
                ).orEmpty()
        }

        val canShowSystemNotification = body.isNotBlank()
        if (!canShowSystemNotification) {
            Log.w("FCM", "FCM received but body is blank; skipping system notification")
        }
        ensureAdminNotificationChannel(applicationContext)


        if (looksLikeNotice && body.isNotBlank()) {
            val id = message.data["id"]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: message.data["noticeId"]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: message.data["notice_id"]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: buildStableNoticeId(title = title, body = body)

            val createdAtMs = message.data["createdAtMs"]?.toLongOrNull()
                ?: message.data["created_at_ms"]?.toLongOrNull()
                ?: message.data["tsMillis"]?.toLongOrNull()
                ?: message.data["timestamp"]?.toLongOrNull()
                ?: nowMs

            val releaseAtMs = message.data["releaseAtMs"]?.toLongOrNull()
                ?: message.data["release_at_ms"]?.toLongOrNull()
                ?: message.data["releaseDateMs"]?.toLongOrNull()
                ?: message.data["publishedAtMs"]?.toLongOrNull()
                ?: message.data["createdAtMs"]?.toLongOrNull()
                ?: createdAtMs

            try {
                cacheNoticeFromPush(
                    ctx = applicationContext,
                    id = id,
                    title = title,
                    body = body,
                    createdAtMs = createdAtMs,
                    releaseAtMs = releaseAtMs
                )
                registerNoticePushForHomePopup(
                    ctx = applicationContext,
                    id = id,
                    title = title,
                    body = body,
                    createdAtMs = createdAtMs,
                    releaseAtMs = releaseAtMs
                )
                try {
                    val noticeIntent = Intent(ACTION_NEW_NOTICE).apply {
                        `package` = applicationContext.packageName
                        putExtra("id", id)
                        putExtra("title", title)
                        putExtra("body", body)
                        putExtra("createdAtMs", createdAtMs)
                        putExtra("releaseAtMs", releaseAtMs)
                    }
                    // UI receivers update Compose state — deliver on main thread.
                    Handler(Looper.getMainLooper()).post {
                        try {
                            applicationContext.sendBroadcast(noticeIntent)
                        } catch (_: Throwable) {
                        }
                    }
                } catch (_: Throwable) {
                }
                Log.d("FCM", "Notice cached from push: id=$id title=$title")
            } catch (t: Throwable) {
                Log.w("FCM", "Failed to cache notice locally", t)
            }
        } else if (body.isNotBlank()) {
            // Persist last admin message so Home screen can show popup
            // even if the message arrives while app is backgrounded.
            try {
                val id = (title.trim() + "|" + body.trim() + "|" + nowMs).hashCode().toString()
                applicationContext
                    .getSharedPreferences(PREF_ADMIN_MESSAGE, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ADMIN_MESSAGE_ID, id)
                    .putString(KEY_ADMIN_MESSAGE_TITLE, title)
                    .putString(KEY_ADMIN_MESSAGE_BODY, body)
                    .putLong(KEY_ADMIN_MESSAGE_TS, nowMs)
                    .apply()
            } catch (_: Throwable) {
            }
            try {
                val msgIntent = Intent(ACTION_NEW_ADMIN_MESSAGE).apply {
                    `package` = applicationContext.packageName
                    putExtra("title", title)
                    putExtra("body", body)
                }
                applicationContext.sendBroadcast(msgIntent)
            } catch (_: Throwable) {
            }
        }

        // Show a system notification for both notice and admin messages.
        // Only notice payloads are cached into the Notice screen.
        if (canShowSystemNotification) {
            val openNoticeScreen = looksLikeNotice
            val nmCompat = NotificationManagerCompat.from(applicationContext)
            if (!nmCompat.areNotificationsEnabled()) {
                Log.w(
                    "FCM",
                    "Notifications are disabled for this app; skipping system notification"
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Log.w("FCM", "POST_NOTIFICATIONS not granted; skipping system notification")
                } else {
                    showAdminNotification(applicationContext, title, body, openNoticeScreen)
                }
            } else {
                showAdminNotification(applicationContext, title, body, openNoticeScreen)
            }

            // 🔔 Use app-selected ringtone/vibration (not system default)
            // Keep channel silent; play a short one-shot alert ourselves.
            try {
                playAppAlertForNonScheduleNotification(applicationContext)
            } catch (_: Throwable) {
            }
        }
    }

    private fun isNoticePayload(message: RemoteMessage, type: String): Boolean {
        val data = message.data

        // DIUTransportAdmin: general pushes use type/category/messageType = admin_message (may target diu_transport).
        if (type.equals("admin_message", ignoreCase = true)) return false
        if (data["type"]?.equals("admin_message", ignoreCase = true) == true) return false
        if (data["category"]?.equals("admin_message", ignoreCase = true) == true) return false
        if (data["messageType"]?.equals("admin_message", ignoreCase = true) == true) return false

        if (type.equals("notice", ignoreCase = true)) return true

        if (data["open_notice"]?.equals("true", ignoreCase = true) == true) return true
        if (data["screen"]?.equals("notice", ignoreCase = true) == true) return true
        if (data["target"]?.equals("notice", ignoreCase = true) == true) return true
        if (data["target"]?.equals("diu_transport", ignoreCase = true) == true) return true
        if (data["topic"]?.equals("diu_transport", ignoreCase = true) == true) return true
        if (data["channel"]?.equals("notice", ignoreCase = true) == true) return true
        if (data.containsKey("noticeId")) return true
        if (data.containsKey("notice_id")) return true
        if (data.containsKey("releaseAtMs")) return true
        if (data.containsKey("release_at_ms")) return true
        if (data.containsKey("releaseDateMs")) return true
        if (data.containsKey("noticeTitle")) return true
        if (data.containsKey("noticeBody")) return true
        if (data.containsKey("notice_title")) return true
        if (data.containsKey("notice_body")) return true
        if (data["category"]?.equals("notice", ignoreCase = true) == true) return true
        if (data["messageType"]?.equals("notice", ignoreCase = true) == true) return true

        // Common backend aliases for type=notice
        for (key in listOf(
            "ntf_type",
            "event",
            "kind",
            "payload_type",
            "notice_type",
            "msg_type"
        )) {
            if (data[key]?.equals("notice", ignoreCase = true) == true) return true
        }

        // Topic sends (Console / Admin SDK) often have no data keys; `from` is like "/topics/diu_admin".
        val from = message.from.orEmpty()
        if (from.contains("diu_admin", ignoreCase = true)) return true
        if (from.contains("diu_transport", ignoreCase = true)) return true

        // Token / direct sends: `from` is often the numeric sender id, not "/topics/...". Firebase Console
        // "notification" campaigns usually ship with an empty data map — still treat as notice for this app.
        if (data.isEmpty() && message.notification != null) {
            val nb = message.notification!!.body
            if (!nb.isNullOrBlank()) {
                val tag = message.notification!!.tag?.trim().orEmpty()
                return !tag.equals("admin_popup_only", ignoreCase = true)
            }
        }

        return false
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) {
            val t = v?.trim().orEmpty()
            if (t.isNotEmpty()) return t
        }
        return null
    }

    private fun buildStableNoticeId(title: String, body: String): String {
        return (title.trim() + "|" + body.trim()).hashCode().toString()
    }

    private fun showAdminNotification(
        context: Context,
        title: String,
        body: String,
        openNoticeScreen: Boolean
    ) {
        val appContext = context.applicationContext

        val notifPrefs = appContext.getSharedPreferences("notice_alert_prefs", MODE_PRIVATE)
        val vibrationEnabled = notifPrefs.getBoolean("master_notifications_enabled", true) &&
                notifPrefs.getBoolean("alarm_vibrate_5m", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(ADMIN_MSG_CHANNEL_ID)

            val needsRecreate = existing != null && (
                    existing.importance < NotificationManager.IMPORTANCE_HIGH ||
                            existing.sound != null ||
                            existing.shouldVibrate() != vibrationEnabled
                    )
            if (needsRecreate) {
                try {
                    nm.deleteNotificationChannel(ADMIN_MSG_CHANNEL_ID)
                } catch (_: Throwable) {
                }
            }

            val channel = NotificationChannel(
                ADMIN_MSG_CHANNEL_ID,
                "Admin messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Transport notices and important admin updates"
                enableVibration(vibrationEnabled)
                if (vibrationEnabled) {
                    vibrationPattern = longArrayOf(0, 250, 180, 250)
                } else {
                    vibrationPattern = longArrayOf(0)
                }
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (openNoticeScreen) {
                    putExtra(MainActivity.EXTRA_OPEN_NOTICE, true)
                }
            }

        val pending = if (openIntent != null) {
            PendingIntent.getActivity(
                appContext,
                4001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        } else null

        val builder = NotificationCompat.Builder(appContext, ADMIN_MSG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fcm_status)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(false)

        if (pending != null) {
            builder.setContentIntent(pending)
            builder.setFullScreenIntent(pending, false)
        }

        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 180, 250))
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setVibrate(longArrayOf(0))
            builder.setDefaults(0)
        }

        NotificationManagerCompat.from(appContext)
            .notify((title + body).hashCode(), builder.build())
    }

    private fun ensureAdminNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val appContext = context.applicationContext
        val notifPrefs = appContext.getSharedPreferences("notice_alert_prefs", MODE_PRIVATE)
        val vibrationEnabled = notifPrefs.getBoolean("master_notifications_enabled", true) &&
                notifPrefs.getBoolean("alarm_vibrate_5m", true)

        val nm = appContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(ADMIN_MSG_CHANNEL_ID)

        val needsRecreate = existing != null && (
                existing.importance < NotificationManager.IMPORTANCE_HIGH ||
                        existing.sound != null ||
                        existing.shouldVibrate() != vibrationEnabled
                )
        if (needsRecreate) {
            try {
                nm.deleteNotificationChannel(ADMIN_MSG_CHANNEL_ID)
            } catch (_: Throwable) {
            }
        }

        if (!needsRecreate && existing != null) return

        val channel = NotificationChannel(
            ADMIN_MSG_CHANNEL_ID,
            "Admin messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Transport notices and important admin updates"
            enableVibration(vibrationEnabled)
            if (vibrationEnabled) {
                vibrationPattern = longArrayOf(0, 250, 180, 250)
            } else {
                vibrationPattern = longArrayOf(0)
            }
            setSound(null, null)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val ADMIN_MSG_CHANNEL_ID = "admin_updates"
        const val ACTION_NEW_ADMIN_MESSAGE =
            "com.sohan.diutransportschedule.ACTION_NEW_ADMIN_MESSAGE"

        private const val PREF_ADMIN_MESSAGE = "admin_message_popup"
        private const val KEY_ADMIN_MESSAGE_ID = "id"
        private const val KEY_ADMIN_MESSAGE_TITLE = "title"
        private const val KEY_ADMIN_MESSAGE_BODY = "body"
        private const val KEY_ADMIN_MESSAGE_TS = "ts"
    }
}