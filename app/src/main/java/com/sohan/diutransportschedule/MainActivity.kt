package com.sohan.diutransportschedule

import com.google.firebase.messaging.FirebaseMessaging

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.shape.RoundedCornerShape
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

import androidx.core.view.WindowCompat
import android.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color as ComposeColor
import android.view.Window
import com.google.firebase.firestore.FirebaseFirestore
import com.sohan.diutransportschedule.BuildConfig
import com.sohan.diutransportschedule.sync.ApkDownloader
import com.sohan.diutransportschedule.sync.ResolvedUpdate
import com.sohan.diutransportschedule.sync.UpdateChecker
// ==============================
// 🔧 FIRESTORE TARGET (DEV vs PROD)
// ==============================
// ✅ For emulator testing, set this to true in debug builds.
// 🚀 For real publish (production), keep this false.
private const val USE_EMULATOR = false

class MainActivity : ComponentActivity() {

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
        // ✅ Apply saved dark mode BEFORE first draw to avoid light flash
        val p = applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val savedDark = when {
            p.contains("dark_mode") -> p.getBoolean("dark_mode", false)
            p.contains("dark") -> p.getBoolean("dark", false)
            p.contains("darkMode") -> p.getBoolean("darkMode", false)
            else -> false
        }
        AppCompatDelegate.setDefaultNightMode(
            if (savedDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
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

        val app = application as App

        // ✅ Subscribe every install to admin topic so admin push works even when app is closed
        FirebaseMessaging.getInstance().subscribeToTopic("diu_admin")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to topic: diu_admin")
                } else {
                    Log.e("FCM", "Topic subscribe failed", task.exception)
                }
            }

        setContent {
            val vm: HomeViewModel = viewModel(factory = HomeVmFactory(app))
            val notificationsEnabled by vm.notificationsEnabled.collectAsState()
            val notifyLeadMinutes by vm.notifyLeadMinutes.collectAsState()
            val selectedRoute by vm.selectedRoute.collectAsState()

            // ✅ FIRST TIME ENTER = permission ask
            RequestStartupPermissions()

            // Use savedDark as the initial value for dark mode
            val dark by vm.darkMode.collectAsState(initial = savedDark)

            DIUTransportScheduleTheme(darkTheme = dark) {
                LaunchedEffect(dark) {
                    applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("dark_mode", dark)
                        .apply()
                }
                val items by vm.items.collectAsState()
                val syncing by vm.isSyncing.collectAsState()

                // Always render the app, but blur + show full-screen overlay while syncing
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (syncing) Modifier.blur(16.dp) else Modifier)
                    ) {
                        // Keep app running underneath so user sees it's loading
                        MainNav(
                            vm = vm,
                            openNotice = openNoticeState.value,
                            onNoticeOpened = { openNoticeState.value = false }
                        )
                    }

                    if (syncing) {
                        FullScreenLoading(
                            title = "",
                            subtitle = if (items.isEmpty()) "Loading schedule…" else "Syncing…",
                            logoResId = com.sohan.diutransportschedule.R.drawable.diu_logo,
                            appDark = dark
                        )
                    }
                }
                val ctx = LocalContext.current

                // ✅ In-app update popup (no auto-download)
                var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
                var pendingUpdate by remember { mutableStateOf<ResolvedUpdate.Full?>(null) }

                // Check once per app launch
                LaunchedEffect(Unit) {
                    try {
                        val u = UpdateChecker.resolveFor(BuildConfig.VERSION_NAME)
                        // Full-only updater: if update exists, show popup.
                        val full = u as? ResolvedUpdate.Full
                        if (full != null) {
                            // ✅ Show every time until the user actually updates.
                            pendingUpdate = full
                            showUpdateDialog = true
                        }
                    } catch (t: Throwable) {
                        Log.e("AppUpdate", "Update check failed", t)
                    }
                }

                if (showUpdateDialog && pendingUpdate != null) {
                    val u = pendingUpdate!!
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = {
                            Text(
                                "Update available",
                                color = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                "A new version (${u.toVersionName}) is available. " +
                                    "Do you want to download and install it now?",
                                color = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    try {
                                        // Allow retry if a previous enqueue/download got stuck
                                        ctx.getSharedPreferences(ApkDownloader.PREFS, Context.MODE_PRIVATE)
                                            .edit()
                                            .putString(ApkDownloader.KEY_ENQUEUED_TO_VERSION_NAME, "")
                                            .apply()
                                        ApkDownloader.enqueueFull(
                                            context = ctx,
                                            url = u.full.url,
                                            expectedSha256 = "",
                                            toVersionName = u.toVersionName,
                                            fileName = "DIUTransportSchedule-${u.toVersionName}.apk"
                                        )
                                    } catch (t: Throwable) {
                                        Log.e("AppUpdate", "Failed to start download", t)
                                    } finally {
                                        showUpdateDialog = false
                                    }
                                }
                            ) {
                                Text(
                                    "Download",
                                    color = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text(
                                    "Later",
                                    color = if (dark) ComposeColor.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }

                LaunchedEffect(notifyLeadMinutes, selectedRoute, items) {
                    try {
                        // ✅ Always keep the next schedule alarm up to date when data changes.
                        // If the user disables notifications at OS-level, scheduleNextAlarmFromData will early-return.
                        Log.d(
                            "RouteNotificationScheduler",
                            "scheduleNextAlarmFromData called: selectedRoute=$selectedRoute leadMinutes=$notifyLeadMinutes items=${items.size}"
                        )
                        scheduleNextAlarmFromData(
                            context = ctx,
                            selectedRoute = selectedRoute,
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStopNoticeAlarm(intent)
        handleTestScheduleNotification(intent)
        handleTestScheduleAlarm(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTICE, false)) {
            openNoticeState.value = true
        }
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
    val overlayBg = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
    val titleColor = MaterialTheme.colorScheme.onBackground
    val subColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
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
private const val ADMIN_UPDATES_CHANNEL_ID = "admin_updates"
private fun ensureAdminUpdatesChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(ADMIN_UPDATES_CHANNEL_ID) != null) return

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
private fun RequestStartupPermissions() {
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        ensureAdminUpdatesChannel(ctx)
    }
    // ✅ Ask only once (first app entry), but do it step-by-step
    val prefs = remember {
        ctx.getSharedPreferences("startup_permissions", Context.MODE_PRIVATE)
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
                // If notifications are disabled (either permission denied or app notifications OFF), prompt Settings
                if (!notifGranted) {
                    showNotifSettingsDialog = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val postNotifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        notifGranted = granted
        if (granted) {
            step = maxOf(step, 1)
            prefs.edit().putInt("step", step).apply()
        }
    }

    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showOemBgDialog by remember { mutableStateOf(false) }
    var showBatteryOptDialog by remember { mutableStateOf(false) }

    // ✅ Startup flow
    LaunchedEffect(alreadyAsked, step, notifGranted, exactAlarmGranted) {
        if (alreadyAsked) {
            // If notifications are disabled (any Android version), show settings prompt
            if (!notifGranted) {
                showNotifSettingsDialog = true
            }
            return@LaunchedEffect
        }

        // STEP 0: Notifications (Android 13+)
        if (step < 1) {
            // Android 13+: request permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                postNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@LaunchedEffect
            }

            // All versions: if app notifications are OFF, ask user to enable in Settings
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                showNotifSettingsDialog = true
                return@LaunchedEffect
            }

            step = 1
            prefs.edit().putInt("step", step).apply()
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
                        step = 4
                        prefs.edit().putInt("step", 4).apply()
                        showExactAlarmDialog = false
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
                        // Step will advance automatically on resume if permission becomes granted
                    }
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        step = 4
                        prefs.edit().putInt("step", 4).apply()
                        showExactAlarmDialog = false
                        // user skipped -> advance step anyway
                        step = 2
                        prefs.edit().putInt("step", step).apply()
                    }
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
                    }
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOemBgDialog = false
                        step = 3
                        prefs.edit().putInt("step", step).apply()
                    }
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
                    }
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
                    }
                ) { Text("Not now") }
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
                    }
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNotifSettingsDialog = false }) { Text("Not now") }
            }
        )
    }
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

// --- Notification helpers ---
fun ensureNotificationChannel(
    context: Context,
    channelId: String,
    channelName: String,
    channelDesc: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val (soundOn, vibrateOn, vibPattern) = when (channelId) {
        MainActivity.NOTIF_CHANNEL_ID_SOUND_VIB ->
            Triple(true, true, longArrayOf(0, 500, 250, 900, 250, 500, 400, 1200))
        MainActivity.NOTIF_CHANNEL_ID_SOUND_ONLY ->
            Triple(true, false, longArrayOf())
        MainActivity.NOTIF_CHANNEL_ID_VIB_ONLY ->
            Triple(false, true, longArrayOf(0, 500, 250, 900, 250, 500, 400, 1200))
        MainActivity.NOTIF_CHANNEL_ID_SILENT ->
            Triple(false, false, longArrayOf())
        else ->
            Triple(false, false, longArrayOf())
    }

    // Channel আগে ভুলভাবে create হলে toggle কাজ করবে না
    // mismatch হলে delete করে recreate করি
    val existing = nm.getNotificationChannel(channelId)
    if (existing != null) {
        val existingHasSound = existing.sound != null
        val existingHasVib = existing.shouldVibrate()

        val needRecreate = (existingHasSound != soundOn) || (existingHasVib != vibrateOn)
        if (needRecreate) {
            nm.deleteNotificationChannel(channelId)
        } else {
            return
        }
    }

    val ch = NotificationChannel(
        channelId,
        channelName,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = channelDesc
        setShowBadge(true)

        if (soundOn) {
            // Alarm tone ব্যবহার করলে Alarm volume follow করবে
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(uri, attrs)
        } else {
            setSound(null, null)
        }

        enableVibration(vibrateOn)
        if (vibrateOn && vibPattern.isNotEmpty()) vibrationPattern = vibPattern
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

        val match = routeMatchesSelection(routeNo, routeName, selectedRoute)
        if (match) {
            Log.d(
                "RouteNotificationScheduler",
                "Matched route: routeNo=$routeNo routeName=$routeName selectedRoute=$selectedRoute"
            )
        }
        match
    }

    if (filtered.isEmpty()) {
        Log.w("RouteNotificationScheduler", "No rows matched selectedRoute=$selectedRoute")
        return
    }
    Log.d("RouteNotificationScheduler", "Matched rows count=${filtered.size} for selectedRoute=$selectedRoute")

    val now = LocalDateTime.now()
    val zone = ZoneId.systemDefault()

    val upcoming = mutableListOf<Triple<Long, String, String>>()

    for (any in filtered) {
        val routeNo = any.readFirstStringProp("routeNo", "route_no", "route", "routeId").ifBlank { selectedRoute }
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
            .flatMap { extractClockTimes(it) }

        Log.d("RouteNotificationScheduler", "Parsed times count=${allTimes.size} for routeNo=$routeNo")

        for (t in allTimes) {
            val next = nextOccurrence(now, t) ?: continue
            val fireAt = next.minusMinutes(leadMinutes.toLong())
            if (fireAt.isAfter(now)) {
                val fireMs = fireAt.atZone(zone).toInstant().toEpochMilli()
                val title = "DIU Bus Reminder • $routeNo"
                val text =
                    "$routeName at ${formatTime(next.toLocalTime())} (lead ${leadMinutes}m)"
                upcoming.add(Triple(fireMs, title, text))
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
        .distinctBy { it.first }
        .sortedBy { it.first }
        .take(60)

    val qStr = queue.joinToString("\n") { (ms, t, x) ->
        "${ms}|${t.replace("|", " ")}|${x.replace("|", " ")}"
    }

    context.getSharedPreferences(
        MainActivity.PREF_SCHEDULE_QUEUE,
        Context.MODE_PRIVATE
    ).edit().putString(MainActivity.KEY_SCHEDULE_QUEUE, qStr).apply()

    // ---- Schedule ONLY first alarm ----
    val (firstMs, firstTitle, firstText) = queue.first()

    cancelNextAlarm(context)

    val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
        putExtra(MainActivity.EXTRA_TITLE, firstTitle)
        putExtra(MainActivity.EXTRA_TEXT, firstText)
        putExtra(com.sohan.diutransportschedule.EXTRA_AT_MS, firstMs)
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

private fun nextOccurrence(now: LocalDateTime, t: LocalTime): LocalDateTime? {
    val today = LocalDate.now()
    val todayDt = LocalDateTime.of(today, t)
    return if (todayDt.isAfter(now)) todayDt else LocalDateTime.of(today.plusDays(1), t)
}

private fun extractClockTimes(raw: String): List<LocalTime> {
    // Support (with or without seconds):
    // - 12h: 7:30 AM, 7:30AM, 7:30:00 PM, 7:30:00PM
    // - 12h hour-only: 7 PM, 7PM
    // - 24h: 07:30, 19:05, 07:30:00, 19:05:00
    // - dot separator: 7.30 PM, 19.05

    // Prefer longest matches first (so "9:37:00 PM" doesn't become "9:37")
    val patterns = listOf(
        // 12-hour with optional seconds + AM/PM
        Regex("(\\b\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?\\s*[AaPp][Mm]\\b)"),
        // 24-hour with optional seconds (no AM/PM)
        Regex("(\\b\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?\\b)"),
        // hour-only AM/PM
        Regex("(\\b\\d{1,2}\\s*[AaPp][Mm]\\b)")
    )

    val matches = patterns
        .flatMap { it.findAll(raw).map { m -> m.value }.toList() }
        .distinct()

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

        if (hasAmPm) {
            val up = normalized.uppercase(Locale.ENGLISH)

            if (!up.contains(':')) {
                // "7 PM"
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
                // "h:mm(:ss) AM/PM"
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
            // 24-hour: "H:mm" or "H:mm:ss"
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
    }
}

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
