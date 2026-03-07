package com.sohan.diutransportschedule

import android.media.Ringtone
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import android.Manifest
import androidx.core.content.ContextCompat
import java.util.Locale

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.sohan.diutransportschedule.MainActivity.Companion.ACTION_STOP_SCHEDULE_ALARM
import com.sohan.diutransportschedule.MainActivity.Companion.ALARM_REQ_CODE
import com.sohan.diutransportschedule.MainActivity.Companion.EXTRA_TEXT
import com.sohan.diutransportschedule.MainActivity.Companion.EXTRA_TITLE
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_SOUND_ONLY
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_SOUND_VIB
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_SILENT
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_VIB_ONLY
import android.util.Log

private const val ACTION_STOP_RUNNING_ALERT_INTERNAL = "com.sohan.diutransportschedule.ACTION_STOP_RUNNING_ALERT_INTERNAL"
private const val ALERT_STOP_REQ_CODE = 9077

const val EXTRA_AT_MS = "com.sohan.diutransportschedule.EXTRA_AT_MS"
private const val DEDUPE_PREFS = "alarm_dedupe_prefs"
private const val KEY_LAST_AT_MS = "last_at_ms"
private const val KEY_LAST_WALL_MS = "last_wall_ms"
private const val KEY_LAST_FP = "last_fp"
private const val KEY_LAST_FP_WALL_MS = "last_fp_wall_ms"
private const val NOTICE_ALERT_PREFS = "notice_alert_prefs"
private const val KEY_ALARM_SOUND_5M = "alarm_sound_5m"
private const val KEY_ALARM_VIBRATE_5M = "alarm_vibrate_5m"
private const val KEY_CUSTOM_RINGTONE_URI = "custom_ringtone_uri"
private const val KEY_CUSTOM_RINGTONE_NAME = "custom_ringtone_name"

private object RunningAlertController {
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrating = false

    fun stop(context: Context) {
        try {
            ringtone?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        ringtone = null

        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
        vibrating = false

        // Cancel auto-stop alarm if any
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_STOP_RUNNING_ALERT_INTERNAL
                data = Uri.parse("diu://stop_running_alert")
            }
            val pi = PendingIntent.getBroadcast(
                context,
                ALERT_STOP_REQ_CODE,
                i,
                PendingIntent.FLAG_NO_CREATE or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        } catch (_: Throwable) {
        }
    }

    fun start(context: Context, soundOn: Boolean, vibrateOn: Boolean, durationMs: Long) {
        // Ensure only one running alert at a time
        stop(context)

        if (soundOn) {
            try {
                val prefs = context.getSharedPreferences(NOTICE_ALERT_PREFS, Context.MODE_PRIVATE)
                val customUri = prefs.getString(KEY_CUSTOM_RINGTONE_URI, null)
                if (!customUri.isNullOrBlank()) {
                    val mp = MediaPlayer().apply {
                        setDataSource(context.applicationContext, Uri.parse(customUri))
                        isLooping = true
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        prepare()
                        start()
                    }
                    mediaPlayer = mp
                } else {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val r = RingtoneManager.getRingtone(context.applicationContext, uri)
                    ringtone = r
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        r.isLooping = true
                    }
                    r.play()
                }
            } catch (t: Throwable) {
                Log.e("ScheduleAlarmReceiver", "Failed to play ringtone", t)
                try {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val r = RingtoneManager.getRingtone(context.applicationContext, uri)
                    ringtone = r
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        r.isLooping = true
                    }
                    r.play()
                } catch (fallback: Throwable) {
                    Log.e("ScheduleAlarmReceiver", "Fallback default ringtone also failed", fallback)
                }
            }
        }

        if (vibrateOn) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                val pattern = longArrayOf(0, 500, 250, 900, 250, 500, 400, 1200)
                val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect.createWaveform(pattern, 0) // loop
                } else {
                    null
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }

                vibrating = true
            } catch (t: Throwable) {
                Log.e("ScheduleAlarmReceiver", "Failed to vibrate", t)
            }
        }

        // Auto-stop after duration
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val stopAt = System.currentTimeMillis() + durationMs
            val i = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_STOP_RUNNING_ALERT_INTERNAL
                data = Uri.parse("diu://stop_running_alert")
            }
            val pi = PendingIntent.getBroadcast(
                context,
                ALERT_STOP_REQ_CODE,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, stopAt, pi)
            }
        } catch (t: Throwable) {
            Log.e("ScheduleAlarmReceiver", "Failed to schedule auto-stop", t)
        }
    }
}

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Stop action: stop ONLY the currently running sound/vibration + hide current notification
        // (do NOT cancel future alarms)
        if (intent.action == ACTION_STOP_SCHEDULE_ALARM) {
            RunningAlertController.stop(context)
            NotificationManagerCompat.from(context).cancel(ALARM_REQ_CODE)

            // If user tapped the notification body, we still want to open the app
            // but stop ONLY this running alert.
            if (intent.getBooleanExtra("open_app", false)) {
                val launch = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                if (launch != null) {
                    try { context.startActivity(launch) } catch (_: Throwable) {}
                }
            }
            return
        }

        // Internal auto-stop after 5 minutes
        if (intent.action == ACTION_STOP_RUNNING_ALERT_INTERNAL) {
            RunningAlertController.stop(context)
            return
        }
        Log.d("ScheduleAlarmReceiver", "onReceive action=${intent.action} title=${intent.getStringExtra(EXTRA_TITLE)} text=${intent.getStringExtra(EXTRA_TEXT)}")

        // --- De-dupe: some devices/OS versions may deliver the same alarm twice ---
        // We tag each scheduled alarm with its atMs and ignore repeats within a short window.
        val firedAtMs = intent.getLongExtra(EXTRA_AT_MS, -1L)
        if (firedAtMs > 0L) {
            val nowWall = System.currentTimeMillis()
            val dp = context.getSharedPreferences(DEDUPE_PREFS, Context.MODE_PRIVATE)
            val lastAt = dp.getLong(KEY_LAST_AT_MS, -1L)
            val lastWall = dp.getLong(KEY_LAST_WALL_MS, 0L)
            if (lastAt == firedAtMs && (nowWall - lastWall) < 15_000L) {
                Log.w("ScheduleAlarmReceiver", "Duplicate alarm delivery ignored atMs=$firedAtMs")
                return
            }
            dp.edit()
                .putLong(KEY_LAST_AT_MS, firedAtMs)
                .putLong(KEY_LAST_WALL_MS, nowWall)
                .apply()
        }

        val rawTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "DIU Bus Reminder" }
        val rawText = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Bus reminder" }
        Log.d("ScheduleAlarmReceiver", "Alarm fired title=$rawTitle text=$rawText")
        val nowWall2 = System.currentTimeMillis()
        val fp = "$rawTitle|$rawText"
        val fpPrefs = context.getSharedPreferences(DEDUPE_PREFS, Context.MODE_PRIVATE)
        val lastFp = fpPrefs.getString(KEY_LAST_FP, null)
        val lastFpWall = fpPrefs.getLong(KEY_LAST_FP_WALL_MS, 0L)
        if (lastFp == fp && (nowWall2 - lastFpWall) < 15_000L) {
            Log.w("ScheduleAlarmReceiver", "Duplicate alarm content ignored fp=$fp")
            return
        }
        fpPrefs.edit()
            .putString(KEY_LAST_FP, fp)
            .putLong(KEY_LAST_FP_WALL_MS, nowWall2)
            .apply()

        // User toggles (Profile screen writes these)
        val prefs = context.getSharedPreferences(NOTICE_ALERT_PREFS, Context.MODE_PRIVATE)
        val soundOn = prefs.getBoolean(KEY_ALARM_SOUND_5M, true)
        val vibrateOn = prefs.getBoolean(KEY_ALARM_VIBRATE_5M, true)

        // ✅ pick channel based on toggles (Android 8+ channel overrides notification sound/vib)
        val channelId = when {
            soundOn && vibrateOn -> NOTIF_CHANNEL_ID_SOUND_VIB
            soundOn -> NOTIF_CHANNEL_ID_SOUND_ONLY
            vibrateOn -> NOTIF_CHANNEL_ID_VIB_ONLY
            else -> NOTIF_CHANNEL_ID_SILENT
        }

        Log.d(
            "ScheduleAlarmReceiver",
            "toggles soundOn=$soundOn vibrateOn=$vibrateOn channelId=$channelId"
        )

        // ✅ ensure all 4 channels exist
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_SOUND_VIB, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_SOUND_ONLY, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_VIB_ONLY, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_SILENT,    MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)

        // 1) Title line: "DIU Bus Reminder • {RoadNo}"
        val routeLabel = rawTitle.substringAfter('•', missingDelimiterValue = "").trim()
        val titleLine = if (routeLabel.isNotBlank()) "DIU Bus Reminder • $routeLabel" else "DIU Bus Reminder"

        // 2) Destination line: first before first "<>" and last after last "<>"
        val routeName = rawText.substringBefore(" at ").trim().ifBlank { rawText.trim() }
        val hasSep = routeName.contains("<>")
        val firstDest = if (hasSep) routeName.substringBefore("<>").trim() else routeName
        val lastDest = if (hasSep) routeName.substringAfterLast("<>").trim() else ""
        val destLine = if (hasSep && lastDest.isNotBlank() && lastDest != firstDest) {
            "$firstDest ↔ $lastDest"
        } else {
            firstDest
        }

        // 3) Time line from text: "... at 9:30 PM (lead 5m)"
        val timeToken = rawText.substringAfter(" at ", missingDelimiterValue = "").trim()
            .substringBefore("(").trim()
        val timeLine = if (timeToken.isNotBlank()) "Time: $timeToken" else ""

        val collapsedTitle = if (timeToken.isNotBlank()) "DIU Bus Reminder • $timeToken" else "DIU Bus Reminder"

        // Tap on the notification body: stop ONLY this running alert + open the app.
        // We route the tap through this receiver so we can stop sound/vibration reliably.
        val tapIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_STOP_SCHEDULE_ALARM
            data = Uri.parse("diu://tap_stop_open")
            putExtra("open_app", true)
        }

        val contentPi = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE + 21,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Stop action
        val stopIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_STOP_SCHEDULE_ALARM
            data = Uri.parse("diu://stop_schedule_alarm")
        }
        val stopPi = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE + 20,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            "Stop",
            stopPi
        ).build()

        // ✅ Left side: DIU logo (large icon)
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.diu_logo)

        // Fixed colors so system dark mode cannot change notification appearance
        val COLOR_DEEP_BLUE = 0xFF0B3D91.toInt()
        val COLOR_GREEN = 0xFF2ECC71.toInt()
        val COLOR_WHITE = 0xFFFFFFFF.toInt()

        val bigBody = buildString {
            append(destLine)
            if (timeToken.isNotBlank()) {
                append("\n")
                append("Time: ")
                append(timeToken)
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            // ✅ Right side logo remove: small icon generic (MIUI header এ DIU logo দেখাবে না)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColorized(true)
            .setColor(COLOR_DEEP_BLUE)   // deep blue base
            .setLargeIcon(largeLogo)          // ✅ left logo
            .setContentTitle(titleLine)
            // Collapsed view
            .setContentTitle(collapsedTitle)
            .setContentText(if (timeToken.isNotBlank()) "Time: $timeToken" else destLine)
            // Expanded view
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0)
            )
            .setSubText(destLine)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)                // ✅ time hide হবে না
            .addAction(stopAction)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        builder.setContentIntent(contentPi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(contentPi, true)
        }

        // NOTE: Don't let the Notification itself play sound/vibration.
        // We always drive sound/vibration via RunningAlertController so the user toggles work
        // AND so we don't get double-ringtone (channel sound + manual ringtone).

        NotificationManagerCompat.from(context).notify(ALARM_REQ_CODE, builder.build())

        // Force 5-minute ring/vibration (independent of Android 8+ channel sound lock)
        // This makes the Profile ON/OFF toggles always work.
        val fiveMinMs = 5 * 60 * 1000L
        if (soundOn || vibrateOn) {
            RunningAlertController.start(context, soundOn = soundOn, vibrateOn = vibrateOn, durationMs = fiveMinMs)
        } else {
            RunningAlertController.stop(context)
        }

        // ✅ After firing this alarm, schedule the next one from the saved queue
        try {
            scheduleNextFromStoredQueue(context)
        } catch (t: Throwable) {
            Log.e("ScheduleAlarmReceiver", "Failed to schedule next alarm from queue", t)
        }
    }
}


private data class QueueItem(val atMs: Long, val title: String, val text: String)

private fun parseQueue(raw: String): List<QueueItem> {
    if (raw.isBlank()) return emptyList()
    return raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains('|') }
        .mapNotNull { line ->
            val p1 = line.indexOf('|')
            val p2 = if (p1 >= 0) line.indexOf('|', p1 + 1) else -1
            if (p1 <= 0 || p2 <= p1) return@mapNotNull null
            val ms = line.substring(0, p1).toLongOrNull() ?: return@mapNotNull null
            val title = line.substring(p1 + 1, p2)
            val text = line.substring(p2 + 1)
            QueueItem(ms, title, text)
        }
        .toList()
}

private fun scheduleNextFromStoredQueue(context: Context) {
    val prefs = context.getSharedPreferences(MainActivity.PREF_SCHEDULE_QUEUE, Context.MODE_PRIVATE)
    val raw = prefs.getString(MainActivity.KEY_SCHEDULE_QUEUE, "").orEmpty()

    val nowMs = System.currentTimeMillis()
    val all = parseQueue(raw)
        .filter { it.atMs > nowMs - 2_000L } // keep near-future items; tolerate small clock drift
        .distinctBy { "${it.atMs}|${it.title}|${it.text}" }
        .sortedBy { it.atMs }

    if (all.isEmpty()) {
        // Nothing left
        Log.d("ScheduleAlarmReceiver", "Queue empty after alarm fired — nothing to schedule")
        return
    }

    // The first item is the one that just fired (or already due). Drop all <= now.
    val remaining = all.filter { it.atMs > nowMs + 1_000L }

    // Persist remaining queue
    val newRaw = remaining.joinToString("\n") { "${it.atMs}|${it.title.replace("|", " ")}|${it.text.replace("|", " ")}" }
    prefs.edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, newRaw).apply()

    val next = remaining.firstOrNull() ?: run {
        Log.d("ScheduleAlarmReceiver", "No future items left in queue")
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
        putExtra(MainActivity.EXTRA_TITLE, next.title)
        putExtra(MainActivity.EXTRA_TEXT, next.text)
        putExtra(EXTRA_AT_MS, next.atMs)
    }

    val pi = PendingIntent.getBroadcast(
        context,
        MainActivity.ALARM_REQ_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    // Schedule exact if allowed; else fallback.
    val exactAllowed = !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms())

    if (exactAllowed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                ?: Intent(context, MainActivity::class.java)

            val showPi = PendingIntent.getActivity(
                context,
                9002,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(next.atMs, showPi),
                pi
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMs, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.atMs, pi)
        }
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMs, pi)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, next.atMs, pi)
        }
    }

    Log.d("ScheduleAlarmReceiver", "Scheduled NEXT alarm from queue atMs=${next.atMs} title=${next.title}")
}