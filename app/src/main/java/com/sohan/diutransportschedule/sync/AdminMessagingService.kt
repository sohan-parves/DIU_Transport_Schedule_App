

package com.sohan.diutransportschedule.sync
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sohan.diutransportschedule.R
import android.app.PendingIntent
import android.content.Intent
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

const val ACTION_NEW_NOTICE = "com.sohan.diutransportschedule.ACTION_NEW_NOTICE"

class AdminMessagingService : FirebaseMessagingService() {
    // onMessageReceived(remoteMessage: RemoteMessage) -- removed duplicate, logic merged below

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Keep topic subscription after token refresh
        FirebaseMessaging.getInstance()
            .subscribeToTopic("diu_admin")
        Log.d("FCM", "New FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Support both Data and Notification payloads
        val type = message.data["type"].orEmpty()
        Log.d("FCM", "onMessageReceived: type=$type data=${message.data} notifTitle=${message.notification?.title} notifBody=${message.notification?.body}")

        val title = when {
            type.equals("notice", ignoreCase = true) ->
                message.data["title"].orEmpty().ifBlank { "Transport Notice" }
            else ->
                message.notification?.title
                    ?: message.data["title"]
                    ?: "DIU Transport Schedule"
        }

        val body = when {
            type.equals("notice", ignoreCase = true) ->
                message.data["body"].orEmpty().ifBlank { message.notification?.body.orEmpty() }
            else ->
                message.notification?.body
                    ?: message.data["body"]
                    ?: message.data["message"]
                    ?: ""
        }

        val canShowSystemNotification = body.isNotBlank()
        if (!canShowSystemNotification) {
            Log.w("FCM", "FCM received but body is blank; skipping system notification")
        }

        // Only show a system notification for published NOTICE messages
        if (type.equals("notice", ignoreCase = true) && canShowSystemNotification) {
            val nmCompat = NotificationManagerCompat.from(applicationContext)
            if (!nmCompat.areNotificationsEnabled()) {
                Log.w("FCM", "Notifications are disabled for this app; skipping system notification")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Log.w("FCM", "POST_NOTIFICATIONS not granted; skipping system notification")
                } else {
                    showAdminNotification(applicationContext, title, body)
                }
            } else {
                showAdminNotification(applicationContext, title, body)
            }
        }

        // Persist into local prefs for in-app Notice list
        try {
            val prefs = applicationContext.getSharedPreferences("admin_notices", Context.MODE_PRIVATE)
            val raw = prefs.getString("json", "[]") ?: "[]"
            val arr = JSONArray(raw)

            val o = JSONObject()
            o.put("id", message.data["id"] ?: UUID.randomUUID().toString())
            o.put("title", title)
            o.put("body", body)
            o.put("tsMillis", System.currentTimeMillis())
            o.put("isRead", false)

            // newest first
            val out = JSONArray()
            out.put(o)
            for (i in 0 until arr.length()) out.put(arr.getJSONObject(i))

            prefs.edit().putString("json", out.toString()).apply()

            // Notify UI (Home popup) when app is open
            if (type.equals("notice", ignoreCase = true) && body.isNotBlank()) {
                val i = Intent(ACTION_NEW_NOTICE).apply {
                    setPackage(applicationContext.packageName)
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("tsMillis", System.currentTimeMillis())
                }
                applicationContext.sendBroadcast(i)
            }
        } catch (_: Throwable) {
        }
    }

    private fun showAdminNotification(context: Context, title: String, body: String) {
        val appContext = context.applicationContext

        // Android 8+ channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ADMIN_MSG_CHANNEL_ID,
                "Admin messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Transport notices and important admin updates"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val openIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)

        if (pending != null) builder.setContentIntent(pending)

        val notif = builder.build()

        NotificationManagerCompat.from(appContext)
            .notify((title + body).hashCode(), notif)
    }

    companion object {
        private const val ADMIN_MSG_CHANNEL_ID = "admin_updates"
    }
}