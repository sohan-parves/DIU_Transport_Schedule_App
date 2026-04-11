package com.sohan.diutransportschedule

import android.media.Ringtone
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationAttributes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import android.Manifest
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
import com.sohan.diutransportschedule.MainActivity.Companion.ACTION_STOP_SCHEDULE_ALARM
import com.sohan.diutransportschedule.MainActivity.Companion.ALARM_REQ_CODE
import com.sohan.diutransportschedule.MainActivity.Companion.EXTRA_TEXT
import com.sohan.diutransportschedule.MainActivity.Companion.EXTRA_TITLE
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_SOUND_ONLY
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_SOUND_VIB
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_SILENT
import com.sohan.diutransportschedule.MainActivity.Companion.NOTIF_CHANNEL_ID_VIB_ONLY
import android.util.Log
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import android.database.ContentObserver
import android.media.AudioManager

private const val ACTION_STOP_RUNNING_ALERT_INTERNAL = "com.sohan.diutransportschedule.ACTION_STOP_RUNNING_ALERT_INTERNAL"
private const val ACTION_TAP_OPEN_AND_STOP = "com.sohan.diutransportschedule.ACTION_TAP_OPEN_AND_STOP"
private const val ACTION_AUTO_OPEN_APP_AFTER_TIMEOUT = "com.sohan.diutransportschedule.ACTION_AUTO_OPEN_APP_AFTER_TIMEOUT"
private const val ACTION_SCREEN_OFF_STOP_ALERT = "com.sohan.diutransportschedule.ACTION_SCREEN_OFF_STOP_ALERT"
private const val ALERT_STOP_REQ_CODE = 9077
private const val ALERT_AUTO_OPEN_REQ_CODE = 9078

const val EXTRA_AT_MS = "com.sohan.diutransportschedule.EXTRA_AT_MS"
const val EXTRA_EXPLICIT_MIDNIGHT = "com.sohan.diutransportschedule.EXTRA_EXPLICIT_MIDNIGHT"
const val EXTRA_ALARM_FINGERPRINT = "com.sohan.diutransportschedule.EXTRA_ALARM_FINGERPRINT"
const val EXTRA_SOURCE_TOKEN = "com.sohan.diutransportschedule.EXTRA_SOURCE_TOKEN"
private const val DEDUPE_PREFS = "alarm_dedupe_prefs"
private const val KEY_LAST_AT_MS = "last_at_ms"
private const val KEY_LAST_WALL_MS = "last_wall_ms"
private const val KEY_LAST_FP = "last_fp"
private const val KEY_LAST_FP_WALL_MS = "last_fp_wall_ms"
private const val NOTICE_ALERT_PREFS = "notice_alert_prefs"
private const val KEY_ALARM_SOUND_5M = "alarm_sound_5m"
private const val KEY_ALARM_VIBRATE_5M = "alarm_vibrate_5m"
private const val KEY_CUSTOM_RINGTONE_URI = "custom_ringtone_uri"
private const val KEY_ALARM_SOUND_DURATION_MS = "alarm_sound_duration_ms"
private const val KEY_ALARM_VIBRATE_DURATION_MS = "alarm_vibrate_duration_ms"
private const val KEY_CUSTOM_RINGTONE_NAME = "custom_ringtone_name"
private const val KEY_CUSTOM_VIBRATION_PATTERN = "custom_vibration_pattern"
private const val ALARM_ROUTE_GUARD_PREFS = "alarm_route_guard_prefs"
private const val KEY_SELECTED_ROUTE_GUARD = "selected_route"
private fun extractDisplayTime(text: String): String {
    return text.substringAfter(" at ", missingDelimiterValue = "").substringBefore("(").trim()
}

private fun isSuspiciousReceiverMidnight(text: String, explicitMidnight: Boolean): Boolean {
    val displayTime = extractDisplayTime(text)
    return displayTime.equals("12:00 AM", ignoreCase = true) && !explicitMidnight
}

private fun extractMeridiemFromReceiverSourceToken(rawToken: String): String? {
    val token = rawToken.uppercase(Locale.ENGLISH)
    return when {
        token.contains("AM") -> "AM"
        token.contains("PM") -> "PM"
        else -> null
    }
}

private fun extractMeridiemFromDisplayTime(displayTime: String): String? {
    val token = displayTime.uppercase(Locale.ENGLISH)
    return when {
        token.contains("AM") -> "AM"
        token.contains("PM") -> "PM"
        else -> null
    }
}

private fun isReceiverDisplayConsistentWithSource(sourceToken: String, displayTime: String): Boolean {
    val sourceMeridiem = extractMeridiemFromReceiverSourceToken(sourceToken) ?: return true
    val displayMeridiem = extractMeridiemFromDisplayTime(displayTime) ?: return true
    return sourceMeridiem == displayMeridiem
}

private fun resolveAlarmVibrationPattern(context: Context): LongArray {
    val prefs = context.getSharedPreferences(NOTICE_ALERT_PREFS, Context.MODE_PRIVATE)
    return when (prefs.getString(KEY_CUSTOM_VIBRATION_PATTERN, "Default vibration").orEmpty()) {
        "Soft vibration" -> longArrayOf(0, 140, 90, 140)
        "Strong vibration" -> longArrayOf(0, 320, 120, 420)
        "Pulse vibration" -> longArrayOf(0, 120, 80, 120, 80, 260)
        else -> longArrayOf(0, 220, 120, 220)
    }
}

private fun buildLoopingAlarmVibrationPattern(basePattern: LongArray): LongArray {
    if (basePattern.isEmpty()) return longArrayOf(0, 220, 120, 220)
    if (basePattern.size == 1) return longArrayOf(basePattern[0], 220, 120, 220)

    val repeatCount = 6
    val result = LongArray(basePattern.size * repeatCount)
    repeat(repeatCount) { blockIndex ->
        basePattern.copyInto(
            destination = result,
            destinationOffset = blockIndex * basePattern.size,
            startIndex = 0,
            endIndex = basePattern.size
        )
    }
    return result
}
private fun openMainActivityFromAlarm(context: Context, fromTimeout: Boolean) {
    val launch = Intent(context, MainActivity::class.java).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        putExtra("from_alarm_notification", true)
        putExtra("from_alarm_timeout_auto_open", fromTimeout)
    }

    try {
        context.startActivity(launch)
    } catch (t: Throwable) {
        Log.e("ScheduleAlarmReceiver", "Failed to open app from alarm action", t)
        val fallbackLaunch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("from_alarm_notification", true)
                putExtra("from_alarm_timeout_auto_open", fromTimeout)
            }
        if (fallbackLaunch != null) {
            try {
                context.startActivity(fallbackLaunch)
            } catch (_: Throwable) {
            }
        }
    }
}
fun resetAlarmStateForNotifyTimeChange(context: Context) {
    try {
        RunningAlertController.stop(context.applicationContext)
    } catch (_: Throwable) {
    }

    try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, ScheduleAlarmReceiver::class.java)
        val alarmPi = PendingIntent.getBroadcast(
            context,
            MainActivity.ALARM_REQ_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_IMMUTABLE else 0)
        )
        alarmManager.cancel(alarmPi)
        alarmPi.cancel()
    } catch (_: Throwable) {
    }

    try {
        NotificationManagerCompat.from(context).cancel(MainActivity.ALARM_REQ_CODE)
    } catch (_: Throwable) {
    }

    try {
        context.getSharedPreferences(
            MainActivity.PREF_SCHEDULE_QUEUE,
            Context.MODE_PRIVATE
        ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, "").apply()
    } catch (_: Throwable) {
    }
}

object RunningAlertController {
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrating = false
    private var isAlertRunning = false
    private var lastStartElapsedMs = 0L
    private var currentSessionId = 0L
    private var lastAlertKey = ""
    private var lastAlertKeyWallMs = 0L
    private const val START_GUARD_WINDOW_MS = 7000L
    private const val SAME_ALERT_KEY_WINDOW_MS = 30000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var localStopRunnable: Runnable? = null
    private var localSoundStopRunnable: Runnable? = null
    private var localVibrationStopRunnable: Runnable? = null
    private var screenOffReceiverRegistered = false
    private var volumeObserverRegistered = false
    private var volumeContentObserver: ContentObserver? = null
    private var volumePollRunnable: Runnable? = null
    private const val VOLUME_POLL_INTERVAL_MS = 350L
    private var lastAlarmVolume = -1
    private var lastRingVolume = -1
    private var lastMusicVolume = -1
    private var lastNotificationVolume = -1
    private var lastSystemVolume = -1

    private val screenOffStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (
                intent.action == Intent.ACTION_SCREEN_OFF ||
                intent.action == Intent.ACTION_SCREEN_ON ||
                intent.action == Intent.ACTION_USER_PRESENT
            ) {
                try {
                    // Any power-button interaction that turns the screen OFF / ON,
                    // or unlocks the device, should stop only the running alert.
                    // Keep the notification visible for manual dismissal.
                    RunningAlertController.stop(context.applicationContext)
                } catch (_: Throwable) {
                }
            }
        }
    }
    private fun captureCurrentVolumes(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            lastAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            lastRingVolume = am.getStreamVolume(AudioManager.STREAM_RING)
            lastMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            lastNotificationVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            lastSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
        } catch (_: Throwable) {
            lastAlarmVolume = -1
            lastRingVolume = -1
            lastMusicVolume = -1
            lastNotificationVolume = -1
            lastSystemVolume = -1
        }
    }

    private fun hasAnyTrackedVolumeChanged(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarm = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val ring = am.getStreamVolume(AudioManager.STREAM_RING)
            val music = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val notification = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val system = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            alarm != lastAlarmVolume ||
                    ring != lastRingVolume ||
                    music != lastMusicVolume ||
                    notification != lastNotificationVolume ||
                    system != lastSystemVolume
        } catch (_: Throwable) {
            false
        }
    }

    private fun registerVolumeStopObserver(context: Context) {
        try {
            if (volumeObserverRegistered) return
            captureCurrentVolumes(context.applicationContext)

            val observer = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)

                    if (!isAlertRunning) return
                    if (!hasAnyTrackedVolumeChanged(context.applicationContext)) {
                        captureCurrentVolumes(context.applicationContext)
                        return
                    }

                    try {
                        // Any volume-button interaction should stop only the running alert.
                        // Keep the notification visible so the user can dismiss it manually.
                        RunningAlertController.stop(context.applicationContext)
                    } catch (_: Throwable) {
                    }
                }
            }

            context.applicationContext.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
            volumeContentObserver = observer
            volumeObserverRegistered = true

            // Fallback for devices where Settings observer is unreliable while locked/screen-off.
            // If any volume stream changes during alert, stop the running alert immediately.
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!isAlertRunning) return
                    try {
                        if (hasAnyTrackedVolumeChanged(context.applicationContext)) {
                            RunningAlertController.stop(context.applicationContext)
                            return
                        }
                        captureCurrentVolumes(context.applicationContext)
                    } catch (_: Throwable) {
                    }
                    try {
                        mainHandler.postDelayed(this, VOLUME_POLL_INTERVAL_MS)
                    } catch (_: Throwable) {
                    }
                }
            }
            volumePollRunnable = pollRunnable
            mainHandler.postDelayed(pollRunnable, VOLUME_POLL_INTERVAL_MS)
        } catch (_: Throwable) {
            volumeContentObserver = null
            volumeObserverRegistered = false
            volumePollRunnable = null
        }
    }

    private fun unregisterVolumeStopObserver(context: Context) {
        try {
            volumeContentObserver?.let {
                context.applicationContext.contentResolver.unregisterContentObserver(it)
            }
        } catch (_: Throwable) {
        }

        volumeContentObserver = null
        volumeObserverRegistered = false
        try {
            volumePollRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {
        }
        volumePollRunnable = null
        lastAlarmVolume = -1
        lastRingVolume = -1
        lastMusicVolume = -1
        lastNotificationVolume = -1
        lastSystemVolume = -1
    }
    fun isRunning(): Boolean = isAlertRunning

    private fun stopSoundOnly() {
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
    }

    private fun stopVibrationOnly() {
        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
        vibrator = null
        vibrating = false
    }

    fun stop(context: Context) {
        try {
            localStopRunnable?.let { mainHandler.removeCallbacks(it) }
            localSoundStopRunnable?.let { mainHandler.removeCallbacks(it) }
            localVibrationStopRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {
        }
        localStopRunnable = null
        localSoundStopRunnable = null
        localVibrationStopRunnable = null

        // 🔥 Instant cleanup for volume/power button stop
        unregisterVolumeStopObserver(context.applicationContext)

        try {
            if (screenOffReceiverRegistered) {
                context.applicationContext.unregisterReceiver(screenOffStopReceiver)
                screenOffReceiverRegistered = false
            }
        } catch (_: Throwable) {
            screenOffReceiverRegistered = false
        }

        stopSoundOnly()
        stopVibrationOnly()
        isAlertRunning = false
        lastStartElapsedMs = 0L
        currentSessionId = 0L
        mainHandler.postDelayed({
            lastAlertKey = ""
            lastAlertKeyWallMs = 0L
        }, SAME_ALERT_KEY_WINDOW_MS)

        // Cancel auto-stop / auto-open alarms if any
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val stopIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_STOP_RUNNING_ALERT_INTERNAL
                data = Uri.parse("diu://stop_running_alert")
            }
            val stopPi = PendingIntent.getBroadcast(
                context,
                ALERT_STOP_REQ_CODE,
                stopIntent,
                PendingIntent.FLAG_NO_CREATE or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (stopPi != null) {
                am.cancel(stopPi)
                stopPi.cancel()
            }

            val autoOpenIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_AUTO_OPEN_APP_AFTER_TIMEOUT
                data = Uri.parse("diu://auto_open_after_timeout")
            }
            val autoOpenPi = PendingIntent.getBroadcast(
                context,
                ALERT_AUTO_OPEN_REQ_CODE,
                autoOpenIntent,
                PendingIntent.FLAG_NO_CREATE or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (autoOpenPi != null) {
                am.cancel(autoOpenPi)
                autoOpenPi.cancel()
            }
        } catch (_: Throwable) {
        }
    }

    fun start(
        context: Context,
        soundOn: Boolean,
        vibrateOn: Boolean,
        soundDurationMs: Long,
        vibrateDurationMs: Long,
        alertKey: String
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val normalizedAlertKey = alertKey.trim()

        val resolvedSoundDurationMs = soundDurationMs.coerceIn(5_000L, 5 * 60 * 1000L)
        val resolvedVibrateDurationMs = vibrateDurationMs.coerceIn(5_000L, 5 * 60 * 1000L)
        val overallDurationMs = maxOf(
            if (soundOn) resolvedSoundDurationMs else 0L,
            if (vibrateOn) resolvedVibrateDurationMs else 0L
        ).coerceAtLeast(5_000L)

        // Stronger guard: if the same logical alert arrives again shortly after the first one,
        // never start sound/vibration a second time.
        if (
            normalizedAlertKey.isNotBlank() &&
            lastAlertKey == normalizedAlertKey &&
            (nowWall - lastAlertKeyWallMs) < SAME_ALERT_KEY_WINDOW_MS
        ) {
            Log.w(
                "ScheduleAlarmReceiver",
                "Ignoring duplicate RunningAlertController.start for same alertKey within ${SAME_ALERT_KEY_WINDOW_MS}ms key=$normalizedAlertKey"
            )
            return
        }

        // Hard guard: if the same alert flow calls start twice within a few seconds,
        // ignore the second start so ringtone/vibration cannot double-trigger.
        if (isAlertRunning && (nowElapsed - lastStartElapsedMs) < START_GUARD_WINDOW_MS) {
            Log.w(
                "ScheduleAlarmReceiver",
                "Ignoring duplicate RunningAlertController.start within ${START_GUARD_WINDOW_MS}ms"
            )
            return
        }

        // Ensure only one running alert at a time when this is a genuine new alert.
        stop(context)
        lastStartElapsedMs = nowElapsed
        if (normalizedAlertKey.isNotBlank()) {
            lastAlertKey = normalizedAlertKey
            lastAlertKeyWallMs = nowWall
        }
        val newSessionId = System.currentTimeMillis()
        currentSessionId = newSessionId
        try {
            if (!screenOffReceiverRegistered) {
                val filter = android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                context.applicationContext.registerReceiver(screenOffStopReceiver, filter)
                screenOffReceiverRegistered = true
            }
        } catch (_: Throwable) {
        }
        // 🔥 Enable instant stop via volume buttons (even on lock screen)
        registerVolumeStopObserver(context.applicationContext)
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
        }else {
            try {
                mediaPlayer?.release()
            } catch (_: Throwable) {
            }
            mediaPlayer = null
            ringtone = null
        }

        isAlertRunning = soundOn || vibrateOn

        // Local in-process fallback timeout handling.
        // Sound and vibration can stop independently, with a final cleanup fallback.
        if (soundOn) {
            localSoundStopRunnable = Runnable {
                if (currentSessionId == newSessionId) {
                    try {
                        stopSoundOnly()
                        isAlertRunning = mediaPlayer != null || ringtone != null || vibrating
                    } catch (_: Throwable) {
                    }
                }
            }
            try {
                mainHandler.postDelayed(localSoundStopRunnable!!, resolvedSoundDurationMs)
            } catch (_: Throwable) {
            }
        }

        if (vibrateOn) {
            localVibrationStopRunnable = Runnable {
                if (currentSessionId == newSessionId) {
                    try {
                        stopVibrationOnly()
                        isAlertRunning = mediaPlayer != null || ringtone != null || vibrating
                    } catch (_: Throwable) {
                    }
                }
            }
            try {
                mainHandler.postDelayed(localVibrationStopRunnable!!, resolvedVibrateDurationMs)
            } catch (_: Throwable) {
            }
        }

        localStopRunnable = Runnable {
            if (currentSessionId == newSessionId) {
                try {
                    stop(context.applicationContext)
                } catch (_: Throwable) {
                }
            }
        }
        try {
            mainHandler.postDelayed(localStopRunnable!!, overallDurationMs)
        } catch (_: Throwable) {
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

                val savedPattern = resolveAlarmVibrationPattern(context)
                val loopingPattern = buildLoopingAlarmVibrationPattern(savedPattern)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val effect = VibrationEffect.createWaveform(loopingPattern, 0) // loop
                    val vibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                    vibrator?.vibrate(effect, vibrationAttributes)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(loopingPattern, 0) // loop
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(
                        effect,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(loopingPattern, 0)
                }

                vibrating = true
            } catch (t: Throwable) {
                Log.e("ScheduleAlarmReceiver", "Failed to vibrate", t)
            }
        } else {
            try {
                vibrator?.cancel()
            } catch (_: Throwable) {
            }
            vibrator = null
            vibrating = false
        }

        // Auto-timeout after duration: stop only the running sound/vibration.
        // Keep the notification visible so the user must dismiss it manually.
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val timeoutAt = System.currentTimeMillis() + overallDurationMs

            val timeoutIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_AUTO_OPEN_APP_AFTER_TIMEOUT
                data = Uri.parse("diu://auto_open_after_timeout")
            }
            val timeoutPi = PendingIntent.getBroadcast(
                context,
                ALERT_AUTO_OPEN_REQ_CODE,
                timeoutIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeoutAt, timeoutPi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, timeoutAt, timeoutPi)
            }
        } catch (t: Throwable) {
            Log.e("ScheduleAlarmReceiver", "Failed to schedule timeout stop-only action", t)
        }
    }
}

class ScheduleAlarmReceiver : BroadcastReceiver() {
    companion object {
        private val lastHandledElapsedMs = AtomicLong(0L)
        private const val DUPLICATE_GUARD_WINDOW_MS = 1500L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Never block user stop/open actions with the duplicate guard.
        // Otherwise, if the user taps the notification immediately after it appears,
        // some devices may ignore the tap because it lands inside the guard window.
        if (
            action != ACTION_STOP_SCHEDULE_ALARM &&
            action != ACTION_STOP_RUNNING_ALERT_INTERNAL &&
            action != ACTION_TAP_OPEN_AND_STOP &&
            action != ACTION_AUTO_OPEN_APP_AFTER_TIMEOUT &&
            action != ACTION_SCREEN_OFF_STOP_ALERT
        ) {
            // --- Hard duplicate guard: some devices fire the same broadcast twice within milliseconds ---
            val nowElapsed = SystemClock.elapsedRealtime()
            while (true) {
                val previousElapsed = lastHandledElapsedMs.get()
                if (nowElapsed - previousElapsed < DUPLICATE_GUARD_WINDOW_MS) {
                    Log.w(
                        "ScheduleAlarmReceiver",
                        "Duplicate alarm broadcast ignored within ${DUPLICATE_GUARD_WINDOW_MS}ms window"
                    )
                    return
                }
                if (lastHandledElapsedMs.compareAndSet(previousElapsed, nowElapsed)) {
                    break
                }
            }
        }
        // Stop button action: stop ONLY the currently running sound/vibration + hide current notification.
        // Do NOT open the app and do NOT cancel future alarms.
        if (action == ACTION_STOP_SCHEDULE_ALARM) {
            RunningAlertController.stop(context.applicationContext)
            try {
                NotificationManagerCompat.from(context).cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    NotificationManagerCompat.from(context.applicationContext).cancel(ALARM_REQ_CODE)
                } catch (_: Throwable) {
                }
                try {
                    val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(ALARM_REQ_CODE)
                } catch (_: Throwable) {
                }
            }, 250L)
            return
        }
        if (action == ACTION_SCREEN_OFF_STOP_ALERT) {
            RunningAlertController.stop(context.applicationContext)
            return
        }

        if (action == ACTION_TAP_OPEN_AND_STOP) {
            RunningAlertController.stop(context.applicationContext)
            try {
                NotificationManagerCompat.from(context).cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
            openMainActivityFromAlarm(context, fromTimeout = false)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    NotificationManagerCompat.from(context.applicationContext).cancel(ALARM_REQ_CODE)
                } catch (_: Throwable) {
                }
                try {
                    val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(ALARM_REQ_CODE)
                } catch (_: Throwable) {
                }
            }, 250L)
            return
        }

        // Internal auto-stop after 5 minutes
        if (action == ACTION_STOP_RUNNING_ALERT_INTERNAL) {
            RunningAlertController.stop(context.applicationContext)
            try {
                NotificationManagerCompat.from(context).cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
            return
        }
        Log.d("ScheduleAlarmReceiver", "onReceive action=${intent.action} title=${intent.getStringExtra(EXTRA_TITLE)} text=${intent.getStringExtra(EXTRA_TEXT)}")
        if (action == ACTION_AUTO_OPEN_APP_AFTER_TIMEOUT) {
            // Stop only the running ringtone/vibration after 5 minutes.
            // Keep the notification in the panel until the user dismisses it manually.
            RunningAlertController.stop(context.applicationContext)
            return
        }
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
        val explicitMidnight = intent.getBooleanExtra(EXTRA_EXPLICIT_MIDNIGHT, false)
        val alarmFingerprint = intent.getStringExtra(EXTRA_ALARM_FINGERPRINT).orEmpty()
        val sourceToken = intent.getStringExtra(EXTRA_SOURCE_TOKEN).orEmpty()

        if (isSuspiciousReceiverMidnight(rawText, explicitMidnight)) {
            Log.w("ScheduleAlarmReceiver", "Ignoring suspicious midnight alarm payload title=$rawTitle text=$rawText")
            try {
                scheduleNextFromStoredQueue(context)
            } catch (t: Throwable) {
                Log.e("ScheduleAlarmReceiver", "Failed to schedule next alarm after suspicious-midnight ignore", t)
            }
            return
        }

        val displayTimeFromText = extractDisplayTime(rawText)
        if (!isReceiverDisplayConsistentWithSource(sourceToken, displayTimeFromText)) {
            Log.w(
                "ScheduleAlarmReceiver",
                "Ignoring alarm because display AM/PM does not match source token source=$sourceToken displayTime=$displayTimeFromText title=$rawTitle text=$rawText"
            )
            try {
                scheduleNextFromStoredQueue(context)
            } catch (t: Throwable) {
                Log.e("ScheduleAlarmReceiver", "Failed to schedule next alarm after AM/PM mismatch ignore", t)
            }
            return
        }

        Log.d("ScheduleAlarmReceiver", "Alarm fired title=$rawTitle text=$rawText")
        val nowWall2 = System.currentTimeMillis()
        val fp = alarmFingerprint.ifBlank { "$rawTitle|$rawText" }
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
        val masterNotificationsEnabled = prefs.getBoolean("master_notifications_enabled", true)

        // If master notifications are OFF, completely ignore this alarm
        if (!masterNotificationsEnabled) {
            Log.d("ScheduleAlarmReceiver", "Master notifications OFF — ignoring alarm completely")
            return
        }

        val soundOn = masterNotificationsEnabled && prefs.getBoolean(KEY_ALARM_SOUND_5M, true)
        val vibrateOn = masterNotificationsEnabled && prefs.getBoolean(KEY_ALARM_VIBRATE_5M, true)
        val soundDurationMs = prefs.getLong(KEY_ALARM_SOUND_DURATION_MS, 5_000L)
            .coerceIn(5_000L, 5 * 60 * 1000L)
        val vibrateDurationMs = prefs.getLong(KEY_ALARM_VIBRATE_DURATION_MS, 5_000L)
            .coerceIn(5_000L, 5 * 60 * 1000L)

        // Use a HIGH-importance channel based on the current toggle combination.
        // The channels themselves are kept silent in ensureNotificationChannel(),
        // so heads-up can appear without causing double ringtone/vibration.
        val channelId = when {
            soundOn && vibrateOn -> NOTIF_CHANNEL_ID_SOUND_VIB
            soundOn -> NOTIF_CHANNEL_ID_SOUND_ONLY
            vibrateOn -> NOTIF_CHANNEL_ID_VIB_ONLY
            else -> NOTIF_CHANNEL_ID_SILENT
        }

        Log.d(
            "ScheduleAlarmReceiver",
            "toggles soundOn=$soundOn vibrateOn=$vibrateOn soundDurationMs=$soundDurationMs vibrateDurationMs=$vibrateDurationMs manualAlert=true channelId=$channelId"
        )

        val alertKey = buildString {
            append(rawTitle.trim())
            append("|")
            append(rawText.trim())
            append("|")
            append(sourceToken.trim())
        }

        // ✅ ensure all 4 channels exist
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_SOUND_VIB, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_SOUND_ONLY, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_VIB_ONLY, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(context, NOTIF_CHANNEL_ID_SILENT,    MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)

        // 1) Title line: "DIU Bus Reminder • {RoadNo}"
        val routeLabel = rawTitle.substringAfter('•', missingDelimiterValue = "").trim()

        val selectedRouteGuard = context
            .getSharedPreferences(ALARM_ROUTE_GUARD_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_ROUTE_GUARD, "ALL")
            .orEmpty()
            .trim()

        if (
            selectedRouteGuard.isNotBlank() &&
            !selectedRouteGuard.equals("ALL", ignoreCase = true) &&
            routeLabel.isNotBlank() &&
            !routeLabel.equals(selectedRouteGuard, ignoreCase = true)
        ) {
            Log.w(
                "ScheduleAlarmReceiver",
                "Ignoring stale alarm for route=$routeLabel because current selected route=$selectedRouteGuard"
            )
            try {
                scheduleNextFromStoredQueue(context)
            } catch (t: Throwable) {
                Log.e("ScheduleAlarmReceiver", "Failed to schedule next alarm after stale-route ignore", t)
            }
            return
        }

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
        val timeToken = extractDisplayTime(rawText)
        val timeLine = if (timeToken.isNotBlank()) "Time: $timeToken" else ""
        val routeLine = if (routeLabel.isNotBlank()) "Route: $routeLabel" else ""

        val collapsedTitle = if (routeLabel.isNotBlank()) {
            "DIU Bus Reminder • $routeLabel"
        } else {
            "DIU Bus Reminder"
        }

        val collapsedText = when {
            destLine.isNotBlank() && timeToken.isNotBlank() -> "$destLine • $timeToken"
            destLine.isNotBlank() -> destLine
            timeToken.isNotBlank() -> timeLine
            else -> rawText
        }

        val expandedBody = buildString {
            if (destLine.isNotBlank()) append(destLine)
            if (routeLine.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(routeLine)
            }
            if (timeLine.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(timeLine)
            }
        }.ifBlank { rawText }


        // Tap on the notification body: stop current alert first, then open the app.
        val tapIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("stop_current_alarm", true)
                data = Uri.parse("diu://tap_open_and_stop/${System.currentTimeMillis()}")
            }

        val contentPi = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Stop action
        val stopIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            setAction(ACTION_STOP_SCHEDULE_ALARM)
            data = Uri.parse("diu://stop_schedule_alarm")
        }
        val stopPi = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE + 20,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val iconBitmap = BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_media_pause)
        val icon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(iconBitmap)

        val stopAction = NotificationCompat.Action.Builder(
            icon,
            "Stop",
            stopPi
        ).build()

        // ✅ Left side: DIU logo (large icon)
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.diu_logo)

        // Fixed color so system dark mode cannot change notification appearance
        val COLOR_DEEP_BLUE = 0xFF0B3D91.toInt()


        val builder = NotificationCompat.Builder(context, channelId)
            // ✅ Right side logo remove: small icon generic (MIUI header এ DIU logo দেখাবে না)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColorized(true)
            .setColor(COLOR_DEEP_BLUE)   // deep blue base
            .setLargeIcon(largeLogo)          // ✅ left logo
            // Collapsed view
            .setContentTitle(collapsedTitle)
            .setContentText(collapsedText)
            // Expanded view
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(collapsedTitle)
                    .bigText(expandedBody)
                    .setSummaryText(if (routeLabel.isNotBlank()) "DIU Bus Reminder • $routeLabel" else "DIU Bus Reminder")
            )
            .setSubText(if (timeToken.isNotBlank()) timeToken else null)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)                // ✅ time hide হবে না
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setDefaults(0)

        if (soundOn || vibrateOn) {
            builder.addAction(stopAction)
        }

        builder.setContentIntent(contentPi)
        builder.setDeleteIntent(stopPi)
        // Do not use a full-screen intent here, otherwise some devices auto-open the app.
        // Heads-up popup will still depend on HIGH/MAX notification importance + priority.
        builder.setTicker(collapsedTitle)
        builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // NOTE: Don't let the Notification itself play sound/vibration.
        // We always drive sound/vibration via RunningAlertController so the user toggles work
        // AND so we don't get double-ringtone (channel sound + manual ringtone).

        NotificationManagerCompat.from(context).notify(ALARM_REQ_CODE, builder.build())

        // Force 5-minute ring/vibration (independent of Android 8+ channel sound lock)
        // This makes the Profile ON/OFF toggles always work.
        val fiveMinMs = 5 * 60 * 1000L
        val alertAlreadyRunning = intent.getBooleanExtra("alert_already_running", false)
        if (soundOn || vibrateOn) {
            if (!alertAlreadyRunning && !RunningAlertController.isRunning()) {
                RunningAlertController.start(
                    context.applicationContext,
                    soundOn = soundOn,
                    vibrateOn = vibrateOn,
                    soundDurationMs = soundDurationMs,
                    vibrateDurationMs = vibrateDurationMs,
                    alertKey = alertKey
                )
            } else {
                Log.d("ScheduleAlarmReceiver", "Skipping RunningAlertController.start because alert is already running")
            }
        } else {
            RunningAlertController.stop(context.applicationContext)
        }

        // ✅ After firing this alarm, schedule the next one from the saved queue
        try {
            scheduleNextFromStoredQueue(context)
        } catch (t: Throwable) {
            Log.e("ScheduleAlarmReceiver", "Failed to schedule next alarm from queue", t)
        }
    }
}


private data class QueueItem(
    val atMs: Long,
    val title: String,
    val text: String,
    val explicitMidnight: Boolean,
    val fingerprint: String,
    val sourceToken: String,
    val displayTime: String
)

private fun parseQueue(raw: String): List<QueueItem> {
    if (raw.isBlank()) return emptyList()
    return raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains('|') }
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 3) return@mapNotNull null

            val ms = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val title = parts.getOrNull(1).orEmpty().trim()
            val text = parts.getOrNull(2).orEmpty().trim()
            val explicitMidnight = parts.getOrNull(3).orEmpty().trim() == "1"
            val fingerprint = parts.getOrNull(4).orEmpty().replace("~", "|").trim()
            val sourceToken = parts.getOrNull(5).orEmpty().trim()
            val displayTime = parts.getOrNull(6).orEmpty().trim().ifBlank { extractDisplayTime(text) }

            if (ms <= 0L || title.isBlank() || text.isBlank()) return@mapNotNull null
            if (isSuspiciousReceiverMidnight(text, explicitMidnight)) return@mapNotNull null

            QueueItem(
                atMs = ms,
                title = title,
                text = text,
                explicitMidnight = explicitMidnight,
                fingerprint = fingerprint,
                sourceToken = sourceToken,
                displayTime = displayTime
            )
        }
        .toList()
}

private fun scheduleNextFromStoredQueue(context: Context) {
    val prefs = context.getSharedPreferences(MainActivity.PREF_SCHEDULE_QUEUE, Context.MODE_PRIVATE)
    val raw = prefs.getString(MainActivity.KEY_SCHEDULE_QUEUE, "").orEmpty()

    val nowMs = System.currentTimeMillis()
    val selectedRouteGuard = context
        .getSharedPreferences(ALARM_ROUTE_GUARD_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_SELECTED_ROUTE_GUARD, "ALL")
        .orEmpty()
        .trim()

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nextAlarmIntent = Intent(context, ScheduleAlarmReceiver::class.java)
    val nextAlarmPi = PendingIntent.getBroadcast(
        context,
        MainActivity.ALARM_REQ_CODE,
        nextAlarmIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    val all = parseQueue(raw)
        .filter { it.atMs > nowMs - 2_000L } // keep near-future items; tolerate small clock drift
        .filter { item ->
            if (selectedRouteGuard.isBlank() || selectedRouteGuard.equals("ALL", ignoreCase = true)) {
                true
            } else {
                val queuedRoute = item.title.substringAfter('•', missingDelimiterValue = "").trim()
                queuedRoute.equals(selectedRouteGuard, ignoreCase = true)
            }
        }
        .distinctBy { "${it.atMs}|${it.title}|${it.text}" }
        .sortedBy { it.atMs }

    if (all.isEmpty()) {
        try {
            alarmManager.cancel(nextAlarmPi)
            nextAlarmPi.cancel()
        } catch (_: Throwable) {
        }
        // Nothing left
        Log.d("ScheduleAlarmReceiver", "Queue empty after alarm fired — canceled pending alarm and nothing to schedule")
        return
    }

    // The first item is the one that just fired (or already due). Drop all <= now.
    val remaining = all
        .filter { it.atMs > nowMs + 1_000L }
        .filter { !isSuspiciousReceiverMidnight(it.text, it.explicitMidnight) }

    // Persist remaining queue
    val newRaw = remaining.joinToString("\n") {
        listOf(
            it.atMs.toString(),
            it.title.replace("|", " "),
            it.text.replace("|", " "),
            if (it.explicitMidnight) "1" else "0",
            it.fingerprint.replace("|", "~"),
            it.sourceToken.replace("|", " "),
            it.displayTime.replace("|", " ")
        ).joinToString("|")
    }
    prefs.edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, newRaw).apply()

    val next = remaining.firstOrNull() ?: run {
        try {
            alarmManager.cancel(nextAlarmPi)
            nextAlarmPi.cancel()
        } catch (_: Throwable) {
        }
        Log.d("ScheduleAlarmReceiver", "No future items left in queue — canceled pending alarm")
        return
    }

    if (next.atMs <= System.currentTimeMillis()) {
        Log.w("ScheduleAlarmReceiver", "Refusing to schedule non-future queued alarm atMs=${next.atMs} text=${next.text}")
        return
    }

    if (isSuspiciousReceiverMidnight(next.text, next.explicitMidnight)) {
        Log.w("ScheduleAlarmReceiver", "Refusing to schedule suspicious queued midnight alarm text=${next.text}")
        return
    }

    val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
        putExtra(MainActivity.EXTRA_TITLE, next.title)
        putExtra(MainActivity.EXTRA_TEXT, next.text)
        putExtra(EXTRA_AT_MS, next.atMs)
        putExtra(EXTRA_EXPLICIT_MIDNIGHT, next.explicitMidnight)
        putExtra(EXTRA_ALARM_FINGERPRINT, next.fingerprint)
        putExtra(EXTRA_SOURCE_TOKEN, next.sourceToken)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

    Log.d(
        "ScheduleAlarmReceiver",
        "Scheduled NEXT alarm from queue with exactAllowed=$exactAllowed atMs=${next.atMs} title=${next.title} text=${next.text}"
    )
}