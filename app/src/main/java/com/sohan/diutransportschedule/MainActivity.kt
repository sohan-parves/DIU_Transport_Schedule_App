package com.sohan.diutransportschedule


import android.Manifest
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohan.diutransportschedule.ui.HomeViewModel
import com.sohan.diutransportschedule.ui.MainNav
import com.sohan.diutransportschedule.ui.theme.DIUTransportScheduleTheme
import com.sohan.diutransportschedule.ui.checkAndSyncNoticesFromMeta
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Done
import com.sohan.diutransportschedule.App
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.BroadcastReceiver
import androidx.appcompat.app.AppCompatDelegate
import android.content.pm.PackageManager
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import android.app.Notification
import androidx.core.view.WindowCompat
import android.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color as ComposeColor
import android.view.Window
import com.google.firebase.firestore.FirebaseFirestore
import com.sohan.diutransportschedule.BuildConfig
import com.sohan.diutransportschedule.appfeature.AppFeatureGuideDialog
import com.sohan.diutransportschedule.appfeature.AppUpdateFeatureGuideContent
import com.sohan.diutransportschedule.appfeature.AppFeatureGuideContent
import com.sohan.diutransportschedule.appfeature.initializeFeatureGuideVersion
import com.sohan.diutransportschedule.appfeature.markUpdateGuideShown
import com.sohan.diutransportschedule.appfeature.markWelcomeGuideShown
import com.sohan.diutransportschedule.appfeature.shouldShowUpdateGuide
import com.sohan.diutransportschedule.appfeature.shouldShowWelcomeGuide
import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.tasks.await
import android.app.AlertDialog
import android.widget.TextView
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush


// ==============================
// 🔧 FIRESTORE TARGET (DEV vs PROD)
// ==============================
// ✅ For emulator testing, set this to true in debug builds.
// 🚀 For real publish (production), keep this false.
private const val USE_EMULATOR = false


class MainActivity : ComponentActivity() {
    private fun handleAlarmNotificationLaunch(intent: Intent?) {
        if (intent?.getBooleanExtra("stop_current_alarm", false) == true) {
            try {
                RunningAlertController.stop(applicationContext)
            } catch (_: Throwable) {
            }
            try {
                NotificationManagerCompat.from(this).cancel(ALARM_REQ_CODE)
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        // Schedule channels (Android 8+ sound/vibration are controlled by channel, so we use 4 channels)
        // Separate channels so user sound/vibration toggles work (NotificationChannel is immutable)
        const val NOTIF_CHANNEL_ID_SOUND_VIB = "diu_schedule_v6_sound_vib"
        const val NOTIF_CHANNEL_ID_SOUND_ONLY = "diu_schedule_v6_sound"
        const val NOTIF_CHANNEL_ID_VIB_ONLY = "diu_schedule_v6_vib"
        const val NOTIF_CHANNEL_ID_SILENT    = "diu_schedule_v6_silent"

        const val NOTIF_CHANNEL_NAME = "DIU Transport Alerts"
        const val NOTIF_CHANNEL_DESC = "Bus schedule and reminder notifications"

        const val ALARM_REQ_CODE = 9001
        const val TEST_ALARM_REQ_CODE = 9901

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_OPEN_NOTICE = "open_notice"
        const val ACTION_TEST_SCHEDULE_NOTIFICATION =
            "com.sohan.diutransportschedule.ACTION_TEST_SCHEDULE_NOTIFICATION"
        const val ACTION_TEST_SCHEDULE_ALARM =
            "com.sohan.diutransportschedule.ACTION_TEST_SCHEDULE_ALARM"
        const val ACTION_STOP_SCHEDULE_ALARM =
            "com.sohan.diutransportschedule.ACTION_STOP_SCHEDULE_ALARM"
        const val EXTRA_DELAY_SEC = "extra_delay_sec"

        // Queue persistence
        const val PREF_SCHEDULE_QUEUE = "schedule_queue_prefs"
        const val KEY_SCHEDULE_QUEUE = "queue"
    }

    private val openNoticeState = androidx.compose.runtime.mutableStateOf(false)

    private fun syncNoticeCacheIfNeeded() {
        try {
            checkAndSyncNoticesFromMeta(
                ctx = applicationContext,
                db = FirebaseFirestore.getInstance(),
                onDone = {
                    Log.d("NoticeSync", "Notice cache sync check completed")
                },
                onError = { msg ->
                    Log.w("NoticeSync", "Notice cache sync check failed: ${msg.orEmpty()}")
                }
            )
        } catch (t: Throwable) {
            Log.w("NoticeSync", "Notice cache sync crashed", t)
        }
    }

    private fun handleStopNoticeAlarm(intent: Intent?) {
        if (intent?.action != "STOP_NOTICE_ALARM") return
        NotificationManagerCompat.from(this).cancel(1002)
    }

    private fun handleTestScheduleNotification(intent: Intent?) {
        if (intent?.action != ACTION_TEST_SCHEDULE_NOTIFICATION) return

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "DIU Transport Schedule" }
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Test schedule reminder" }

        Log.d("RouteNotificationScheduler", "ADB test: posting schedule notification now")
        postScheduleNotificationNow(this, title, text)
    }
    private fun handleTestScheduleAlarm(intent: Intent?) {
        if (intent?.action != ACTION_TEST_SCHEDULE_ALARM) return

        val delaySec = intent.getIntExtra(EXTRA_DELAY_SEC, 30).coerceIn(5, 600)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "DIU Transport Schedule" }
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Test scheduled alarm" }

        Log.d("RouteNotificationScheduler", "ADB test: scheduling alarm in ${delaySec}s")
        scheduleTestAlarmInSeconds(this, delaySec, title, text)
    }

    fun permissionBrandLabel(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> "MIUI"
            manufacturer.contains("redmi") -> "MIUI"
            manufacturer.contains("poco") -> "MIUI"
            manufacturer.contains("samsung") -> "Samsung"
            manufacturer.contains("oppo") -> "OPPO"
            manufacturer.contains("realme") -> "realme UI"
            manufacturer.contains("vivo") -> "vivo"
            manufacturer.contains("oneplus") -> "OxygenOS"
            else -> "Android"
        }
    }

    fun buildPermissionIntroMessage(permission: String): String {
        val osLabel = permissionBrandLabel()
        return when (permission) {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION -> when (osLabel) {
                "MIUI" -> "MIUI location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
                "Samsung" -> "Samsung location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
                "OPPO" -> "OPPO location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
                "realme UI" -> "realme UI location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
                "vivo" -> "vivo location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
                "OxygenOS" -> "OxygenOS location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
                else -> "Android location permission popup আসবে। Map এবং current location ঠিকভাবে দেখাতে Allow দিন।"
            }

            android.Manifest.permission.POST_NOTIFICATIONS -> when (osLabel) {
                "MIUI" -> "MIUI notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
                "Samsung" -> "Samsung notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
                "OPPO" -> "OPPO notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
                "realme UI" -> "realme UI notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
                "vivo" -> "vivo notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
                "OxygenOS" -> "OxygenOS notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
                else -> "Android notification permission popup আসবে। Route alert এবং update notification পেতে Allow দিন।"
            }

            else -> when (osLabel) {
                "MIUI" -> "MIUI permission popup আসবে। Continue দিলে MIUI system dialog দেখাবে।"
                "Samsung" -> "Samsung permission popup আসবে। Continue দিলে Samsung system dialog দেখাবে।"
                "OPPO" -> "OPPO permission popup আসবে। Continue দিলে OPPO system dialog দেখাবে।"
                "realme UI" -> "realme UI permission popup আসবে। Continue দিলে realme UI system dialog দেখাবে।"
                "vivo" -> "vivo permission popup আসবে। Continue দিলে vivo system dialog দেখাবে।"
                "OxygenOS" -> "OxygenOS permission popup আসবে। Continue দিলে OxygenOS system dialog দেখাবে।"
                else -> "Android permission popup আসবে। Continue দিলে system dialog দেখাবে।"
            }
        }
    }

    fun showPermissionIntroThenRequest(
        permission: String,
        requestAction: () -> Unit,
        onSkip: (() -> Unit)? = null
    ) {
        val p = applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val dark = when {
            p.contains("dark_mode") -> p.getBoolean("dark_mode", false)
            p.contains("dark") -> p.getBoolean("dark", false)
            p.contains("darkMode") -> p.getBoolean("darkMode", false)
            else -> true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(buildPermissionIntroMessage(permission))
            .setCancelable(true)
            .setPositiveButton("Continue") { _, _ ->
                requestAction()
            }
            .setNegativeButton("Not now") { _, _ ->
                onSkip?.invoke()
            }
            .create()

        dialog.setOnShowListener {
            if (dark) {
                // Message text
                dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)

                // Title text
                dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(Color.WHITE)

                // Buttons
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
            }
        }

        dialog.show()
    }

    private fun scheduleTestAlarmInSeconds(context: Context, delaySec: Int, title: String, text: String) {
        val triggerAtMillis = System.currentTimeMillis() + (delaySec * 1000L)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val i = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
        }

        // Cancel only the previous *test* alarm (do not touch the real schedule alarm)
        val prevPi = PendingIntent.getBroadcast(
            context,
            TEST_ALARM_REQ_CODE,
            i,
            PendingIntent.FLAG_NO_CREATE or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (prevPi != null) {
            am.cancel(prevPi)
            prevPi.cancel()
        }

        val pi = PendingIntent.getBroadcast(
            context,
            TEST_ALARM_REQ_CODE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val exactAllowed = !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
        if (!exactAllowed) {
            Log.w("RouteNotificationScheduler", "Exact alarm not allowed — using inexact fallback")
        }

        if (exactAllowed) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPi = PendingIntent.getActivity(
                    context,
                    9201,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPi), pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }

        Log.d("RouteNotificationScheduler", "ADB test: alarm scheduled for ${delaySec}s from now")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val p = applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val savedDark = when {
            p.contains("dark_mode") -> p.getBoolean("dark_mode", false)
            p.contains("dark") -> p.getBoolean("dark", false)
            p.contains("darkMode") -> p.getBoolean("darkMode", false)
            else -> true
        }
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                android.graphics.Color.parseColor(if (savedDark) "#0B1220" else "#F7F8FA")
            )
        )
        window.decorView.setBackgroundColor(
            if (savedDark) android.graphics.Color.parseColor("#0B1220")
            else android.graphics.Color.parseColor("#F7F8FA")
        )
        handleAlarmNotificationLaunch(intent)
        Log.d("RouteNotificationScheduler", "MainActivity onCreate")
        handleStopNoticeAlarm(intent)
        handleTestScheduleNotification(intent)
        handleTestScheduleAlarm(intent)
        // ✅ Local testing: point Firebase SDKs to the Emulator Suite.
        // For Android Emulator use: host = "10.0.2.2"
        // For real phone on same Wi-Fi as your Mac/PC: set host to your computer's LAN IP (e.g., "192.168.0.15").

        // Default notice alert options: ON (first install)
        applicationContext.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE).also { sp ->
            val e = sp.edit()
            if (!sp.contains("alarm_sound_5m")) e.putBoolean("alarm_sound_5m", true)
            if (!sp.contains("alarm_vibrate_5m")) e.putBoolean("alarm_vibrate_5m", true)
            e.apply()
        }
        if (BuildConfig.DEBUG && USE_EMULATOR) {
            FirebaseFirestore.getInstance().useEmulator("192.168.0.105", 8080)
        }

        // Edge-to-edge WITHOUT hiding system bars (prevents the system app-name overlay)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        // ✅ Always create notification channel early (required for Android 8+)
        ensureNotificationChannel(this, NOTIF_CHANNEL_ID_SOUND_VIB, NOTIF_CHANNEL_NAME, NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(this, NOTIF_CHANNEL_ID_SOUND_ONLY, NOTIF_CHANNEL_NAME, NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(this, NOTIF_CHANNEL_ID_VIB_ONLY, NOTIF_CHANNEL_NAME, NOTIF_CHANNEL_DESC)
        ensureNotificationChannel(this, NOTIF_CHANNEL_ID_SILENT, NOTIF_CHANNEL_NAME, NOTIF_CHANNEL_DESC)
        syncNoticeCacheIfNeeded()
        initializeFeatureGuideVersion(this)

        val app = application as App

        setContent {
            val vm: HomeViewModel = viewModel(factory = HomeVmFactory(app))
            val notificationsEnabled by vm.notificationsEnabled.collectAsState()
            val notifyLeadMinutes by vm.notifyLeadMinutes.collectAsState()
            val selectedRoute by vm.selectedRoute.collectAsState()
            val initialUiReady by vm.initialUiReady.collectAsState()

            // Use the current StateFlow default for dark mode
            val dark by vm.darkMode.collectAsState()

            DIUTransportScheduleTheme(darkTheme = dark) {
                androidx.compose.runtime.SideEffect {
                    window.decorView.setBackgroundColor(
                        if (dark) android.graphics.Color.parseColor("#0B1220")
                        else android.graphics.Color.parseColor("#F7F8FA")
                    )
                }
                // ✅ FIRST TIME ENTER = permission ask + feature guide
                RequestStartupPermissionsAndFeatureGuide()
                LaunchedEffect(dark) {
                    val prefs = applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
                    val current = when {
                        prefs.contains("dark_mode") -> prefs.getBoolean("dark_mode", false)
                        prefs.contains("dark") -> prefs.getBoolean("dark", false)
                        prefs.contains("darkMode") -> prefs.getBoolean("darkMode", false)
                        else -> true
                    }
                    if (current != dark) {
                        prefs.edit().putBoolean("dark_mode", dark).apply()
                    }
                }
                val items by vm.items.collectAsState()
                val syncing by vm.isSyncing.collectAsState()
                val shouldShowStartupLoading = items.isEmpty() && (!initialUiReady || syncing)

                // Show full-screen loading only before the first readable data is ready,
// or when a cold start still has no items. After data exists, keep the UI visible.
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    MainNav(
                        vm = vm,
                        openNotice = openNoticeState.value,
                        onNoticeOpened = { openNoticeState.value = false }
                    )

                    if (shouldShowStartupLoading) {
                        SkeletonLoadingOverlay(
                            appDark = dark
                        )
                    }
                }
                val ctx = LocalContext.current
                val activity = ctx as? Activity
                val appUpdateManager = remember(ctx) { AppUpdateManagerFactory.create(ctx) }
                var playUpdateChecked by remember { mutableStateOf(false) }

                val playUpdateLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) {
                    // Google Play update UI handle করবে।
                    // app next open/resume এ আবার check করতে পারবে।
                }

                LaunchedEffect(activity, playUpdateChecked) {
                    if (playUpdateChecked) return@LaunchedEffect
                    playUpdateChecked = true

                    if (!BuildConfig.PLAY_STORE_BUILD) return@LaunchedEffect
                    if (activity == null) return@LaunchedEffect
                    if (!isPlayStoreInstall(ctx)) return@LaunchedEffect

                    val appUpdateInfo = try {
                        appUpdateManager.appUpdateInfo.await()
                    } catch (_: Throwable) {
                        null
                    } ?: return@LaunchedEffect

                    if (appUpdateInfo.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                        return@LaunchedEffect
                    }

                    val updateType = when {
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                        else -> return@LaunchedEffect
                    }

                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            playUpdateLauncher,
                            AppUpdateOptions.newBuilder(updateType).build()
                        )
                    } catch (_: Throwable) {
                    }
                }


                LaunchedEffect(notifyLeadMinutes, selectedRoute, items) {
                    try {
                        // ✅ Always keep the next schedule alarm up to date when data changes.
                        // If the user disables notifications at OS-level, scheduleNextAlarmFromData will early-return.
                        Log.d(
                            "RouteNotificationScheduler",
                            "scheduleNextAlarmFromData called: selectedRoute=$selectedRoute leadMinutes=$notifyLeadMinutes items=${items.size}"
                        )
                        val todayIsFriday = try {
                            java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
                        } catch (_: Throwable) {
                            false
                        }

                        val normalizedSelectedRoute = selectedRoute.trim()
                        val selectedIsAll = normalizedSelectedRoute.equals("ALL", ignoreCase = true)
                        val selectedIsFridayRoute = normalizedSelectedRoute.startsWith("F", ignoreCase = true)

                        val notificationRoute = when {
                            selectedIsAll -> "ALL"
                            todayIsFriday && selectedIsFridayRoute -> normalizedSelectedRoute
                            todayIsFriday && !selectedIsFridayRoute -> "ALL"
                            !todayIsFriday && selectedIsFridayRoute -> "ALL"
                            else -> normalizedSelectedRoute
                        }

                        scheduleNextAlarmFromData(
                            context = ctx,
                            selectedRoute = notificationRoute,
                            leadMinutes = notifyLeadMinutes,
                            items = items
                        )
                    } catch (t: Throwable) {
                        Log.e("RouteNotificationScheduler", "Failed to schedule notifications", t)
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        syncNoticeCacheIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmNotificationLaunch(intent)
        handleStopNoticeAlarm(intent)
        handleTestScheduleNotification(intent)
        handleTestScheduleAlarm(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTICE, false)) {
            openNoticeState.value = true
        }
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP,
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
                android.view.KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    try {
                        // Stop only the running ringtone/vibration.
                        // Keep the notification visible until the user dismisses it manually.
                        RunningAlertController.stop(applicationContext)
                    } catch (_: Throwable) {
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    private fun postScheduleNotificationNow(context: Context, title: String, body: String) {
        val prefs = context.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE)
        val soundOn = prefs.getBoolean("alarm_sound_5m", true)
        val vibrateOn = prefs.getBoolean("alarm_vibrate_5m", true)

        val channelId = when {
            soundOn && vibrateOn -> MainActivity.NOTIF_CHANNEL_ID_SOUND_VIB
            soundOn -> MainActivity.NOTIF_CHANNEL_ID_SOUND_ONLY
            vibrateOn -> MainActivity.NOTIF_CHANNEL_ID_VIB_ONLY
            else -> MainActivity.NOTIF_CHANNEL_ID_SILENT
        }

        ensureNotificationChannel(context, channelId, MainActivity.NOTIF_CHANNEL_NAME, MainActivity.NOTIF_CHANNEL_DESC)

        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

        val contentPi = if (openIntent != null) {
            PendingIntent.getActivity(
                context,
                9101,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        } else null

        val b = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.diu_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (soundOn) setDefaults(NotificationCompat.DEFAULT_SOUND)
                    if (vibrateOn) setVibrate(longArrayOf(0, 500, 200, 800, 200, 500, 400, 1000))
                }
            }

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
        b.addAction(NotificationCompat.Action(0, "Stop", stopPi))

        if (contentPi != null) b.setContentIntent(contentPi)

        NotificationManagerCompat.from(context).notify(MainActivity.ALARM_REQ_CODE, b.build())
    }
}
@Composable
private fun FullScreenLoading(
    title: String,
    subtitle: String,
    logoResId: Int,
    appDark: Boolean
) {
    // Force pure black overlay and white text, always dark
    val overlayBg = ComposeColor.Black.copy(alpha = 0.98f)
    val titleColor = ComposeColor.White
    val subColor = ComposeColor.White.copy(alpha = 0.75f)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = overlayBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val logoPainter = painterResource(id = logoResId)
            Image(
                painter = logoPainter,
                contentDescription = "Logo",
                modifier = Modifier
                    .size(220.dp)
                    .padding(6.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(18.dp))

            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = titleColor
                )

                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subColor,
                modifier = Modifier.alpha(0.65f), // ✅ opacity কম
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Please wait…",
                style = MaterialTheme.typography.labelLarge,
                color = subColor,
                modifier = Modifier.alpha(0.70f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SkeletonLoadingOverlay(
    appDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (appDark) ComposeColor(0xFF0B1220) else ComposeColor(0xFFF7F8FA)
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(4) {
                FacebookCommentSkeletonCard(appDark = appDark)
            }
        }
    }
}

@Composable
private fun FacebookCommentSkeletonCard(appDark: Boolean) {
    val shimmer = rememberSkeletonBrush(appDark = appDark)
    val cardColor = if (appDark) ComposeColor(0xFF111827) else ComposeColor.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(shimmer)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmer)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmer)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmer)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmer)
                )
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmer)
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmer)
                )
            }
        }
    }
}

@Composable
private fun rememberSkeletonBrush(appDark: Boolean): Brush {
    val baseColor = if (appDark) ComposeColor(0xFF1F2937) else ComposeColor(0xFFE5E7EB)
    val highlightColor = if (appDark) ComposeColor(0xFF374151) else ComposeColor(0xFFF8FAFC)

    val transition = rememberInfiniteTransition(label = "skeleton_transition")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_translate"
    )

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 260f, 220f)
    )
}

private const val ADMIN_UPDATES_CHANNEL_ID = "admin_updates"
private fun ensureAdminUpdatesChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val ch = NotificationChannel(
        ADMIN_UPDATES_CHANNEL_ID,
        "Admin Updates",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Admin notices and important updates"
        // No alarm sound / no strong vibration for admin notices
        enableVibration(false)
        setSound(null, null)
        setShowBadge(true)
    }
    nm.createNotificationChannel(ch)
}
@Composable
private fun RequestStartupPermissionsAndFeatureGuide() {
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        ensureAdminUpdatesChannel(ctx)
    }
    // ✅ Ask only once (first app entry), but do it step-by-step
    val prefs = remember {
        ctx.getSharedPreferences("startup_permissions", Context.MODE_PRIVATE)
    }

    var showWelcomeGuide by remember {
        mutableStateOf(shouldShowWelcomeGuide(ctx))
    }
    var showUpdateGuide by remember {
        mutableStateOf(shouldShowUpdateGuide(ctx))
    }

    // asked=true মানে startup flow একবার complete হয়েছে
    var alreadyAsked by remember { mutableStateOf(prefs.getBoolean("asked", false)) }

    // step: 0 = start, 1 = notif done, 2 = exact-alarm done, 3 = miui done
    var step by remember { mutableStateOf(prefs.getInt("step", 0)) }

    // ✅ Notifications enabled (all versions) + permission (Android 13+)
    fun hasNotifPermission(): Boolean {
        // If user disabled notifications for the whole app, nothing can be shown.
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ✅ Exact alarm (Android 12+)
    val alarmManager = remember {
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    val needsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun canExactAlarm(): Boolean {
        return !needsExactAlarm || alarmManager.canScheduleExactAlarms()
    }

    fun isMiui(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun isOppo(): Boolean = Build.MANUFACTURER.equals("OPPO", ignoreCase = true)
    fun isRealme(): Boolean = Build.MANUFACTURER.equals("realme", ignoreCase = true)

    fun isColorOsOrRealmeUi(): Boolean = isOppo() || isRealme()

    fun openMiuiBatterySettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }

    fun openOppoRealmeAutoStartSettings(context: Context) {
        // ColorOS / Realme UI autostart / startup manager screens vary by OS version.
        val candidates = listOf(
            // OPPO/realme common components
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
            ),
            Intent().setClassName(
                "com.coloros.powermanager",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
            )
        )

        for (i in candidates) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (_: Throwable) {
                // try next
            }
        }

        // Fallback: app details
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }

    fun openIgnoreBatteryOptimizationsSettings(context: Context) {
        // Generic Android screen (may not be honored by all OEMs)
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        } catch (_: Throwable) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    // local states
    var notifGranted by remember { mutableStateOf(hasNotifPermission()) }
    var exactAlarmGranted by remember { mutableStateOf(canExactAlarm()) }
    // Show settings dialog if notifications are denied after first run
    var showNotifSettingsDialog by remember { mutableStateOf(false) }

    // Re-check permissions when user returns from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = hasNotifPermission()
                exactAlarmGranted = canExactAlarm()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hostActivity = ctx as? MainActivity

    fun finishStartupPermissionFlow() {
        step = 3
        alreadyAsked = true
        prefs.edit()
            .putInt("step", 3)
            .putBoolean("asked", true)
            .apply()
    }

    val postNotifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        notifGranted = granted
        step = maxOf(step, 1)
        prefs.edit().putInt("step", step).apply()

        if (!granted) {
            ctx.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("master_notifications_enabled", false)
                .apply()

            finishStartupPermissionFlow()
            return@rememberLauncherForActivityResult
        }
    }

    fun requestPermissionWithIntro(
        permission: String,
        action: () -> Unit,
        onSkip: (() -> Unit)? = null
    ) {
        hostActivity?.showPermissionIntroThenRequest(
            permission = permission,
            requestAction = action,
            onSkip = onSkip
        ) ?: action()
    }

    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showOemBgDialog by remember { mutableStateOf(false) }
    var showBatteryOptDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showWelcomeGuide = shouldShowWelcomeGuide(ctx)
        showUpdateGuide = shouldShowUpdateGuide(ctx)
    }

    // ✅ Startup flow
    LaunchedEffect(alreadyAsked, step, notifGranted, exactAlarmGranted) {
        if (alreadyAsked) {
            showWelcomeGuide = shouldShowWelcomeGuide(ctx)
            showUpdateGuide = shouldShowUpdateGuide(ctx)
            return@LaunchedEffect
        }

        // STEP 0: Notifications (Android 13+)
        if (step < 1) {
            // Android 13+: request permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionWithIntro(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    action = {
                        postNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onSkip = {
                        finishStartupPermissionFlow()
                    }
                )
                return@LaunchedEffect
            }

            // All versions: if app notifications are OFF, ask user to enable in Settings
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                showNotifSettingsDialog = true
                step = 1
                prefs.edit().putInt("step", step).apply()
            } else {
                step = 1
                prefs.edit().putInt("step", step).apply()
            }
        }

        // STEP 1: Exact Alarm (Android 12+)
        if (step < 2) {
            if (needsExactAlarm && !exactAlarmGranted) {
                showExactAlarmDialog = true
                return@LaunchedEffect
            }
            // exact alarm not needed / already granted
            step = 2
            prefs.edit().putInt("step", step).apply()
        }

        // STEP 2: OEM background/autostart guidance (Xiaomi / OPPO / realme)
        if (step < 3) {
            if (isMiui() || isColorOsOrRealmeUi()) {
                showOemBgDialog = true
                return@LaunchedEffect
            }
            step = 3
            prefs.edit().putInt("step", step).apply()
        }

        // STEP 3: Battery optimization guidance (show only once)
        if (step < 4) {
            showBatteryOptDialog = true
            return@LaunchedEffect
        }

        // ✅ All permission steps finished
        prefs.edit()
            .putBoolean("asked", true)
            .putInt("step", 4)
            .apply()

        alreadyAsked = true
        showWelcomeGuide = shouldShowWelcomeGuide(ctx)
        showUpdateGuide = shouldShowUpdateGuide(ctx)

        // Test notification dialog disabled (user requested)
    }

    if (showExactAlarmDialog && !alreadyAsked) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text("Allow exact alarms") },
            text = {
                Text(
                    "To show bus time notifications reliably, please allow Exact Alarms. " +
                        "This helps the app trigger notifications at the correct time."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmDialog = false
                        step = 2
                        prefs.edit().putInt("step", step).apply()
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        } catch (_: Throwable) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                            }
                            ctx.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExactAlarmDialog = false
                        step = 2
                        prefs.edit().putInt("step", step).apply()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Not now") }
            }
        )
    }

    if (showOemBgDialog && !alreadyAsked) {
        val oemTitle = when {
            isMiui() -> "Allow background running (MIUI)"
            isRealme() -> "Allow background running (realme UI)"
            else -> "Allow background running (ColorOS)"
        }

        val oemText = when {
            isMiui() ->
                "To receive bus notifications reliably, please allow:\n\n" +
                    "• Autostart\n" +
                    "• No battery restrictions\n" +
                    "• Allow background activity"
            else ->
                "To receive bus notifications reliably, please allow:\n\n" +
                    "• Auto-launch / Startup manager\n" +
                    "• Run in background\n" +
                    "• No battery restrictions"
        }

        AlertDialog(
            onDismissRequest = { showOemBgDialog = false },
            title = { Text(oemTitle) },
            text = { Text(oemText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOemBgDialog = false
                        if (isMiui()) {
                            openMiuiBatterySettings(ctx)
                        } else {
                            openOppoRealmeAutoStartSettings(ctx)
                        }
                        // mark step done so we don't block the app
                        step = 3
                        prefs.edit().putInt("step", step).apply()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOemBgDialog = false
                        step = 3
                        prefs.edit().putInt("step", step).apply()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Not now") }
            }
        )
    }

    if (showBatteryOptDialog && !alreadyAsked) {
        AlertDialog(
            onDismissRequest = { showBatteryOptDialog = false },
            title = { Text("Disable battery optimization") },
            text = {
                Text(
                    "Some phones may delay reminders to save battery. " +
                        "To make schedule notifications reliable, please allow the app to ignore battery optimizations."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryOptDialog = false

                        // IMPORTANT: mark flow finished
                        step = 4
                        prefs.edit()
                            .putInt("step", 4)
                            .putBoolean("asked", true)
                            .apply()

                        alreadyAsked = true

                        openIgnoreBatteryOptimizationsSettings(ctx)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBatteryOptDialog = false

                        step = 4
                        prefs.edit()
                            .putInt("step", 4)
                            .putBoolean("asked", true)
                            .apply()

                        alreadyAsked = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Not now") }
            }
        )
    }

    if (showWelcomeGuide) {
        AppFeatureGuideDialog(
            guide = AppFeatureGuideContent.model,
            onClose = {
                showWelcomeGuide = false
                markWelcomeGuideShown(ctx)
            }
        )
    }

    if (!showWelcomeGuide && showUpdateGuide) {
        AppFeatureGuideDialog(
            guide = AppUpdateFeatureGuideContent.model,
            onClose = {
                showUpdateGuide = false
                markUpdateGuideShown(ctx)
            }
        )
    }

    // Test notification dialog permanently disabled

    // Show dialog to prompt user to enable notifications in settings if denied after first run
    if (showNotifSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showNotifSettingsDialog = false },
            title = { Text("Enable notifications") },
            text = {
                Text(
                    "Notifications are turned off for this app, so schedule reminders won’t show. " +
                        "Please enable notifications in Settings."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifSettingsDialog = false
                        ctx.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("master_notifications_enabled", false)
                            .apply()
                        if (!alreadyAsked) {
                            step = maxOf(step, 1)
                            prefs.edit().putInt("step", step).apply()
                        }
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        } catch (_: Throwable) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotifSettingsDialog = false
                        ctx.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("master_notifications_enabled", false)
                            .apply()
                        if (!alreadyAsked) {
                            finishStartupPermissionFlow()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White)
                ) { Text("Not now") }
            }
        )
    }

    // --- Location permission requests (EXAMPLES) ---
    // Example: wrap direct location permission launches with requestPermissionWithIntro
    // Replace:
    // someLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    // with:
    // requestPermissionWithIntro(Manifest.permission.ACCESS_FINE_LOCATION) {
    //     someLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    // }
    // For coarse location, also use Manifest.permission.ACCESS_FINE_LOCATION as the intro key.
    // For multiple-permission launches (fine+coarse), wrap the array launch similarly.
}

private class HomeVmFactory(
    private val app: App
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(app.repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun isPlayStoreInstall(context: Context): Boolean {
    val installer = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (_: Throwable) {
        null
    }
    return installer == "com.android.vending"
}

// --- Notification helpers ---
fun ensureNotificationChannel(
    context: Context,
    channelId: String,
    channelName: String,
    channelDesc: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val existing = nm.getNotificationChannel(channelId)
    if (existing != null &&
        existing.importance == NotificationManager.IMPORTANCE_HIGH &&
        existing.sound == null &&
        !existing.shouldVibrate()
    ) {
        return
    }

    try {
        nm.deleteNotificationChannel(channelId)
    } catch (_: Throwable) {
    }

    val ch = NotificationChannel(
        channelId,
        channelName,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = channelDesc
        enableLights(true)
        lightColor = android.graphics.Color.GREEN
        enableVibration(false)
        vibrationPattern = longArrayOf()
        setSound(null, null)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setAllowBubbles(false)
        }
    }

    nm.createNotificationChannel(ch)
}

private fun sendTestNotification(
    context: Context,
    channelId: String,
    channelName: String,
    channelDesc: String
) {
    ensureNotificationChannel(context, channelId, channelName, channelDesc)

    // If user disabled notifications for the app, we can't show anything.
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        return
    }

    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val pending = PendingIntent.getActivity(
        context,
        1001,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    val n = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("DIU Transport Schedule")
        .setContentText("Test notification — if you can see this, notifications are working ✅")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()

    NotificationManagerCompat.from(context).notify(1001, n)
}
// ---------------- Real alarm scheduling (single next notification) ----------------

private fun cancelNextAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ScheduleAlarmReceiver::class.java)
    val pi = PendingIntent.getBroadcast(
        context,
        MainActivity.ALARM_REQ_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )
    alarmManager.cancel(pi)
    Log.d("RouteNotificationScheduler", "Canceled next alarm")
}

/**
 * Schedules ONLY the next upcoming time (start/departure) across the selected route.
 * leadMinutes আগে notify করবে।
 */
private fun normalizeRouteToken(s: String): String {
    val raw = s.trim().uppercase(Locale.ENGLISH)
    if (raw.isBlank()) return ""

    // Finds things like "R01", "R 01", or plain "01"
    val m = Regex("(R\\s*0*\\d+|\\b0*\\d+\\b)").find(raw)
    val token = m?.value ?: raw
    val digits = Regex("\\d+").find(token)?.value ?: ""
    return if (digits.isNotBlank()) "R" + digits.toInt().toString() else raw
}

private fun routeMatchesSelection(routeNo: String, routeName: String, selected: String): Boolean {
    val sel = selected.trim()
    if (sel.isBlank()) return false
    if (sel.equals("ALL", ignoreCase = true)) return true

    val selNorm = normalizeRouteToken(sel)
    val noNorm = normalizeRouteToken(routeNo)

    if (selNorm.isNotBlank() && noNorm.isNotBlank() && selNorm == noNorm) return true

    val rn = routeName.trim()
    return rn.isNotBlank() && (
            rn.equals(sel, ignoreCase = true) ||
                    rn.contains(sel, ignoreCase = true) ||
                    sel.contains(rn, ignoreCase = true)
            )
}
private fun scheduleNextAlarmFromData(
    context: Context,
    selectedRoute: String,
    leadMinutes: Int,
    items: List<Any>
) {
    Log.d(
        "RouteNotificationScheduler",
        "scheduleNextAlarmFromData called: selectedRoute=$selectedRoute leadMinutes=$leadMinutes items=${items.size}"
    )
    // Route guard: always use latest selected route from Profile
    val routeGuardPrefs = context.getSharedPreferences("alarm_route_guard_prefs", Context.MODE_PRIVATE)
    val selectedRouteGuard = routeGuardPrefs
        .getString("selected_route", selectedRoute)
        .orEmpty()
        .trim()

    val effectiveSelectedRoute =
        if (selectedRouteGuard.isNotBlank()) selectedRouteGuard else selectedRoute

    fun routeMatchesSelected(routeNo: String): Boolean {
        val wanted = effectiveSelectedRoute.trim()
        if (wanted.isBlank() || wanted.equals("ALL", ignoreCase = true)) return true
        return routeNo.trim().equals(wanted, ignoreCase = true)
    }
    // ---- Permission checks ----
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w("RouteNotificationScheduler", "POST_NOTIFICATIONS not granted")
            return
        }
    }

    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        Log.w("RouteNotificationScheduler", "App notifications are disabled in Settings")
        return
    }

    if (items.isEmpty()) return

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactAllowed =
        !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms())

    // ---- Filter by route ----

    val filtered = items.filter { any ->
        val routeNo = any.readStringProp("routeNo")
        val routeName = any.readStringProp("routeName")

        val match = routeMatchesSelection(routeNo, routeName, effectiveSelectedRoute)
        if (match) {
            Log.d(
                "RouteNotificationScheduler",
                "Matched route: routeNo=$routeNo routeName=$routeName selectedRoute=$effectiveSelectedRoute"
            )
        }
        match
    }

    if (filtered.isEmpty()) {
        Log.w("RouteNotificationScheduler", "No rows matched selectedRoute=$effectiveSelectedRoute")
        return
    }
    Log.d("RouteNotificationScheduler", "Matched rows count=${filtered.size} for selectedRoute=$effectiveSelectedRoute")

    val now = LocalDateTime.now()
    val zone = ZoneId.systemDefault()

    val upcoming = mutableListOf<PendingAlarmCandidate>()

    for (any in filtered) {
        val rawRouteNo = any.readFirstStringProp("routeNo", "route_no", "route", "routeId").trim()
        val routeNo = rawRouteNo.ifBlank { effectiveSelectedRoute.trim() }
        val routeName = any.readFirstStringProp("routeName", "route_name", "name", "title").ifBlank { "DIU Route" }

        val startTimes = any.readFirstStringListProp("startTimes", "start_times", "startTime", "start_time")
        val departureTimes = any.readFirstStringListProp("departureTimes", "departure_times", "departureTime", "departure_time")

        Log.d(
            "RouteNotificationScheduler",
            "Row routeNo=$routeNo routeName=$routeName startTimes=${startTimes.joinToString()} departureTimes=${departureTimes.joinToString()}"
        )

        val allTimes = (startTimes + departureTimes)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { raw -> extractClockTimeEntries(raw) }

        Log.d("RouteNotificationScheduler", "Parsed times count=${allTimes.size} for routeNo=$routeNo")

        for ((t, sourceToken) in allTimes) {
            if (t == LocalTime.MIDNIGHT && !explicitlyRepresentsMidnight(sourceToken)) {
                Log.w(
                    "RouteNotificationScheduler",
                    "Skipping suspicious midnight parse for routeNo=$routeNo source=$sourceToken"
                )
                continue
            }

            val next = nextOccurrence(now, t) ?: continue
            val fireAt = next.minusMinutes(leadMinutes.toLong())
            if (fireAt.isAfter(now)) {
                val fireMs = fireAt.atZone(zone).toInstant().toEpochMilli()
                if (fireMs <= System.currentTimeMillis()) {
                    Log.w(
                        "RouteNotificationScheduler",
                        "Skipping candidate because fireMs is not in future routeNo=$routeNo source=$sourceToken fireMs=$fireMs"
                    )
                    continue
                }

                val title = if (routeNo.isNotBlank()) {
                    "DIU Bus Reminder • $routeNo"
                } else {
                    "DIU Bus Reminder"
                }

                val displayTime = formatTime(next.toLocalTime())
                val explicitMidnight = explicitlyRepresentsMidnight(sourceToken)

                if (isSuspiciousDisplayMidnight(displayTime, explicitMidnight)) {
                    Log.w(
                        "RouteNotificationScheduler",
                        "Skipping suspicious midnight candidate routeNo=$routeNo routeName=$routeName source=$sourceToken displayTime=$displayTime"
                    )
                    continue
                }

                val text = sanitizeNotificationText(routeName, displayTime, leadMinutes)

                upcoming.add(
                    PendingAlarmCandidate(
                        atMs = fireMs,
                        title = title,
                        text = text,
                        sourceToken = sourceToken,
                        explicitMidnight = explicitMidnight,
                        routeNo = routeNo,
                        routeName = routeName,
                        displayTime = displayTime,
                        fingerprint = buildAlarmFingerprint(routeNo, routeName, displayTime)
                    )
                )
            }
        }
    }

    if (upcoming.isEmpty()) {
        Log.w("RouteNotificationScheduler", "No upcoming times found — nothing scheduled")
        cancelNextAlarm(context)
        context.getSharedPreferences(
            MainActivity.PREF_SCHEDULE_QUEUE,
            Context.MODE_PRIVATE
        ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, "").apply()
        return
    }

    val queue = upcoming
        .filter { item ->
            val queuedRoute = item.title.substringAfter('•', missingDelimiterValue = "").trim()
            val matched = routeMatchesSelected(queuedRoute)
            if (!matched) {
                Log.w(
                    "RouteNotificationScheduler",
                    "Dropping queue item due to route mismatch queuedRoute=$queuedRoute selected=$effectiveSelectedRoute text=${item.text}"
                )
            }
            matched
        }
        .filter { item ->
            if (item.atMs <= System.currentTimeMillis()) {
                Log.w("RouteNotificationScheduler", "Dropping non-future queue item ms=${item.atMs} text=${item.text}")
                false
            } else {
                true
            }
        }
        .filter { item ->
            if (item.title.isBlank() || item.text.isBlank()) {
                Log.w("RouteNotificationScheduler", "Dropping malformed queue item title=${item.title} text=${item.text}")
                false
            } else {
                true
            }
        }
        .filter { item ->
            val suspiciousMidnight = isSuspiciousDisplayMidnight(item.displayTime, item.explicitMidnight)
            if (suspiciousMidnight) {
                Log.w(
                    "RouteNotificationScheduler",
                    "Dropping suspicious queued midnight item route=${item.title} source=${item.sourceToken} text=${item.text}"
                )
                false
            } else {
                true
            }
        }
        .distinctBy { "${it.atMs}|${it.title}|${it.text}" }
        .sortedBy { it.atMs }
        .take(60)

    if (queue.isEmpty()) {
        Log.w("RouteNotificationScheduler", "Queue became empty after safety filters — canceling any pending alarm")
        cancelNextAlarm(context)
        context.getSharedPreferences(
            MainActivity.PREF_SCHEDULE_QUEUE,
            Context.MODE_PRIVATE
        ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, "").apply()
        return
    }

    val qStr = queue.joinToString("\n") { item ->
        listOf(
            item.atMs.toString(),
            item.title.replace("|", " "),
            item.text.replace("|", " "),
            if (item.explicitMidnight) "1" else "0",
            item.fingerprint.replace("|", "~"),
            item.sourceToken.replace("|", " "),
            item.displayTime.replace("|", " ")
        ).joinToString("|")
    }

    context.getSharedPreferences(
        MainActivity.PREF_SCHEDULE_QUEUE,
        Context.MODE_PRIVATE
    ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, qStr).apply()
    Log.d("RouteNotificationScheduler", "Saved queue size=${queue.size} first=${queue.firstOrNull()?.text.orEmpty()}")

    // ---- Schedule ONLY first alarm ----
    val first = queue.first()

    val firstMs = first.atMs
    val firstTitle = first.title
    val firstText = first.text

    if (firstTitle.isBlank() || firstText.isBlank()) {
        Log.w("RouteNotificationScheduler", "Refusing to schedule malformed first alarm title=$firstTitle text=$firstText")
        cancelNextAlarm(context)
        context.getSharedPreferences(
            MainActivity.PREF_SCHEDULE_QUEUE,
            Context.MODE_PRIVATE
        ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, "").apply()
        return
    }

    if (firstMs <= System.currentTimeMillis()) {
        Log.w("RouteNotificationScheduler", "Refusing to schedule non-future first alarm atMs=$firstMs text=$firstText")
        cancelNextAlarm(context)
        context.getSharedPreferences(
            MainActivity.PREF_SCHEDULE_QUEUE,
            Context.MODE_PRIVATE
        ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, "").apply()
        return
    }

    if (isSuspiciousDisplayMidnight(first.displayTime, first.explicitMidnight)) {
        Log.w(
            "RouteNotificationScheduler",
            "Refusing to schedule suspicious first midnight alarm source=${first.sourceToken} text=${first.text}"
        )
        cancelNextAlarm(context)
        context.getSharedPreferences(
            MainActivity.PREF_SCHEDULE_QUEUE,
            Context.MODE_PRIVATE
        ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, "").apply()
        return
    }

    cancelNextAlarm(context)

    val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
        putExtra(MainActivity.EXTRA_TITLE, firstTitle)
        putExtra(MainActivity.EXTRA_TEXT, firstText)
        putExtra(com.sohan.diutransportschedule.EXTRA_AT_MS, firstMs)
        putExtra(com.sohan.diutransportschedule.EXTRA_EXPLICIT_MIDNIGHT, first.explicitMidnight)
        putExtra(com.sohan.diutransportschedule.EXTRA_ALARM_FINGERPRINT, first.fingerprint)
        putExtra(com.sohan.diutransportschedule.EXTRA_SOURCE_TOKEN, first.sourceToken)
    }

    val pi = PendingIntent.getBroadcast(
        context,
        MainActivity.ALARM_REQ_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0)
    )

    if (exactAllowed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPi = PendingIntent.getActivity(
                context,
                9002,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_IMMUTABLE else 0)
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(firstMs, showPi),
                pi
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                firstMs,
                pi
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, firstMs, pi)
        }
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                firstMs,
                pi
            )
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, firstMs, pi)
        }
    }

    Log.d(
        "RouteNotificationScheduler",
        "Scheduled FIRST alarm from queue atMs=$firstMs"
    )
}


private fun formatTime(t: LocalTime): String =
    t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

private fun isSuspiciousDisplayMidnight(displayTime: String, explicitMidnight: Boolean): Boolean {
    return displayTime.equals("12:00 AM", ignoreCase = true) && !explicitMidnight
}

private fun sanitizeNotificationText(routeName: String, displayTime: String, leadMinutes: Int): String {
    val safeRouteName = routeName.trim().ifBlank { "DIU Route" }
    val safeDisplayTime = displayTime.trim().ifBlank { "Unknown time" }
    return "$safeRouteName at $safeDisplayTime (lead ${leadMinutes}m)"
}
private fun buildAlarmFingerprint(routeNo: String, routeName: String, displayTime: String): String {
    return listOf(
        routeNo.trim(),
        routeName.trim(),
        displayTime.trim().uppercase(Locale.ENGLISH)
    ).joinToString("|")
}
private fun nextOccurrence(now: LocalDateTime, t: LocalTime): LocalDateTime? {
    val today = LocalDate.now()
    val todayDt = LocalDateTime.of(today, t)
    return if (todayDt.isAfter(now)) todayDt else LocalDateTime.of(today.plusDays(1), t)
}

private data class ExtractedClockTime(
    val time: LocalTime,
    val sourceToken: String
)

private data class PendingAlarmCandidate(
    val atMs: Long,
    val title: String,
    val text: String,
    val sourceToken: String,
    val explicitMidnight: Boolean,
    val routeNo: String,
    val routeName: String,
    val displayTime: String,
    val fingerprint: String
)

private fun explicitlyRepresentsMidnight(rawToken: String): Boolean {
    val token = rawToken
        .trim()
        .replace(Regex("\\s+"), " ")
        .uppercase(Locale.ENGLISH)
        .replace('.', ':')

    return token == "12 AM" ||
        token == "12:00 AM" ||
        token == "12:00:00 AM" ||
        token == "00:00" ||
        token == "00:00:00"
}

private fun extractMeridiemFromSourceToken(rawToken: String): String? {
    val token = rawToken.uppercase(Locale.ENGLISH)
    return when {
        token.contains("AM") -> "AM"
        token.contains("PM") -> "PM"
        else -> null
    }
}

private fun meridiemOfLocalTime(time: LocalTime): String {
    return if (time.hour < 12) "AM" else "PM"
}

private fun isParsedTimeConsistentWithSource(rawToken: String, parsedTime: LocalTime): Boolean {
    val sourceMeridiem = extractMeridiemFromSourceToken(rawToken) ?: return true
    return sourceMeridiem == meridiemOfLocalTime(parsedTime)
}

private fun extractClockTimeEntries(raw: String): List<ExtractedClockTime> {
    data class TokenMatch(val value: String, val start: Int, val end: Int, val kind: Int)

    // kind priority:
    // 3 = 12h with AM/PM
    // 2 = 24h without AM/PM
    // 1 = hour-only with AM/PM
    val patterns = listOf(
        3 to Regex("(\\b\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?\\s*[AaPp][Mm]\\b)"),
        2 to Regex("(\\b\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?\\b)"),
        1 to Regex("(\\b\\d{1,2}\\s*[AaPp][Mm]\\b)")
    )

    val rawMatches = patterns.flatMap { (kind, regex) ->
        regex.findAll(raw).map { m ->
            TokenMatch(
                value = m.value,
                start = m.range.first,
                end = m.range.last,
                kind = kind
            )
        }.toList()
    }

    // Prevent duplicate / overlapping parses like:
    // "9:37 PM" -> both "9:37 PM" and inner "9:37"
    // We always keep the strongest / longest token for overlapping ranges.
    val matches = rawMatches
        .sortedWith(
            compareByDescending<TokenMatch> { it.kind }
                .thenByDescending { it.end - it.start }
                .thenBy { it.start }
        )
        .fold(mutableListOf<TokenMatch>()) { acc, candidate ->
            val overlaps = acc.any { kept ->
                candidate.start <= kept.end && candidate.end >= kept.start
            }
            if (!overlaps) acc.add(candidate)
            acc
        }
        .sortedBy { it.start }
        .map { it.value }
        .distinct()

    if (matches.isNotEmpty()) {
        Log.d("RouteNotificationScheduler", "extractClockTimeEntries raw=$raw matches=${matches.joinToString()}")
    }
    if (matches.isEmpty()) return emptyList()

    val fmt12MinSecSpace = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
    val fmt12MinSecNoSpace = DateTimeFormatter.ofPattern("h:mm:ssa", Locale.ENGLISH)
    val fmt12MinSpace = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    val fmt12MinNoSpace = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH)
    val fmt12HourSpace = DateTimeFormatter.ofPattern("h a", Locale.ENGLISH)
    val fmt12HourNoSpace = DateTimeFormatter.ofPattern("ha", Locale.ENGLISH)

    val fmt24MinSec = DateTimeFormatter.ofPattern("H:mm:ss", Locale.ENGLISH)
    val fmt24Min = DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)

    return matches.mapNotNull { s0 ->
        val s1 = s0.trim().replace(Regex("\\s+"), " ")
        val hasAmPm = s1.contains("AM", ignoreCase = true) || s1.contains("PM", ignoreCase = true)
        val normalized = s1.replace('.', ':')

        val parsed = if (hasAmPm) {
            val up = normalized.uppercase(Locale.ENGLISH)

            if (!up.contains(':')) {
                try {
                    LocalTime.parse(up, fmt12HourSpace)
                } catch (_: DateTimeParseException) {
                    try {
                        LocalTime.parse(up.replace(" ", ""), fmt12HourNoSpace)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            } else {
                if (up.count { it == ':' } >= 2) {
                    try {
                        LocalTime.parse(up, fmt12MinSecSpace)
                    } catch (_: DateTimeParseException) {
                        try {
                            LocalTime.parse(up.replace(" ", ""), fmt12MinSecNoSpace)
                        } catch (_: DateTimeParseException) {
                            null
                        }
                    }
                } else {
                    try {
                        LocalTime.parse(up, fmt12MinSpace)
                    } catch (_: DateTimeParseException) {
                        try {
                            LocalTime.parse(up.replace(" ", ""), fmt12MinNoSpace)
                        } catch (_: DateTimeParseException) {
                            null
                        }
                    }
                }
            }
        } else {
            if (normalized.count { it == ':' } >= 2) {
                try {
                    LocalTime.parse(normalized, fmt24MinSec)
                } catch (_: DateTimeParseException) {
                    null
                }
            } else {
                try {
                    LocalTime.parse(normalized, fmt24Min)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }

        parsed?.let {
            if (!isParsedTimeConsistentWithSource(s0, it)) {
                Log.w(
                    "RouteNotificationScheduler",
                    "Rejected token because parsed AM/PM does not match source token source=$s0 parsed=$it raw=$raw"
                )
                null
            } else {
                Log.d("RouteNotificationScheduler", "Parsed clock token source=$s0 parsed=$it raw=$raw")
                ExtractedClockTime(time = it, sourceToken = s0)
            }
        }
    }
}

private fun extractClockTimes(raw: String): List<LocalTime> =
    extractClockTimeEntries(raw).map { it.time }

private fun Any.readFirstStringProp(vararg names: String): String {
    for (n in names) {
        val v = readStringProp(n)
        if (v.isNotBlank()) return v
    }
    return ""
}

private fun Any.readFirstStringListProp(vararg names: String): List<String> {
    for (n in names) {
        val v = readStringListProp(n)
        if (v.isNotEmpty()) return v
    }
    return emptyList()
}

private fun Any.readStringProp(name: String): String {
    return try {
        val f = this::class.java.getDeclaredField(name)
        f.isAccessible = true
        (f.get(this) as? String).orEmpty()
    } catch (_: Throwable) {
        try {
            val getter = "get" + name.replaceFirstChar { it.uppercase() }
            val m = this::class.java.methods.firstOrNull { it.name == getter }
            (m?.invoke(this) as? String).orEmpty()
        } catch (_: Throwable) { "" }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any.readStringListProp(name: String): List<String> {
    return try {
        val f = this::class.java.getDeclaredField(name)
        f.isAccessible = true
        (f.get(this) as? List<String>) ?: emptyList()
    } catch (_: Throwable) {
        try {
            val getter = "get" + name.replaceFirstChar { it.uppercase() }
            val m = this::class.java.methods.firstOrNull { it.name == getter }
            (m?.invoke(this) as? List<String>) ?: emptyList()
        } catch (_: Throwable) { emptyList() }
    }
}
