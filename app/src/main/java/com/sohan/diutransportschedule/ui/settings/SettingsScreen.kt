package com.sohan.diutransportschedule.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material.icons.filled.GraphicEq
import java.io.File
import java.io.FileOutputStream
import androidx.core.app.NotificationManagerCompat
import com.sohan.diutransportschedule.R
import androidx.compose.material3.ColorScheme
import kotlin.math.ln
import android.Manifest
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Immutable
import androidx.compose.material.icons.outlined.Info
import com.sohan.diutransportschedule.appfeature.AppFeatureGuideDialog
import com.sohan.diutransportschedule.appfeature.AppFeatureGuideContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import com.sohan.diutransportschedule.MainActivity
import com.sohan.diutransportschedule.notifications.resetAlarmStateForNotifyTimeChange
import com.sohan.diutransportschedule.ui.map.SelectedRoadStore
import com.sohan.diutransportschedule.ui.home.HomeViewModel
import com.sohan.diutransportschedule.ui.home.RouteOption
import com.sohan.diutransportschedule.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.material.icons.automirrored.filled.ArrowForward

private fun ColorScheme.surfaceColorAtElevationCompat(elevation: Dp): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1f)) + 2f) / 100f
    return primary.copy(alpha = alpha).compositeOver(surface)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val routePrefs = remember(ctx) {
        ctx.getSharedPreferences("profile_route_prefs", Context.MODE_PRIVATE)
    }

    val dark by vm.darkMode.collectAsState()
    val selectedRoute by vm.selectedRoute.collectAsState()
    val isFriday = LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY
    var selectedFridayRouteNoUi by rememberSaveable {
        mutableStateOf(routePrefs.getString("selected_friday_route", "").orEmpty())
    }

    val normalizedDailyRoute = selectedRoute.trim().ifBlank { "ALL" }
    val normalizedFridayRoute = selectedFridayRouteNoUi.trim().ifBlank { "ALL" }

    // Friday vs weekday: separate rules. Friday = must pick a Friday (F*) route, not ALL.
    // Weekdays = must pick a daily route, not ALL and not an F* route.
    val dailyAllowsMasterNotifications = remember(normalizedDailyRoute) {
        val d = normalizedDailyRoute.trim()
        d.isNotBlank() && !d.equals("ALL", ignoreCase = true) && !d.startsWith(
            "F",
            ignoreCase = true
        )
    }
    val fridayAllowsMasterNotifications = remember(normalizedFridayRoute) {
        val f = normalizedFridayRoute.trim()
        f.isNotBlank() && !f.equals("ALL", ignoreCase = true) && f.startsWith(
            "F",
            ignoreCase = true
        )
    }
    val routeAllowsMasterNotificationsToday =
        if (isFriday) fridayAllowsMasterNotifications else dailyAllowsMasterNotifications
    val notificationsBlockedByRoute = !routeAllowsMasterNotificationsToday

// ✅ Always use FULL route list from VM (not filtered by Home)
    val routeOptions by vm.routeOptions.collectAsState()
    val regularRouteOptions = remember(routeOptions) {
        routeOptions.filterNot {
            it.routeNo.trim().startsWith("F", ignoreCase = true)
        }
    }

    val fridayRouteOptions = remember(routeOptions) {
        routeOptions.filter {
            it.routeNo.trim().startsWith("F", ignoreCase = true)
        }
    }

    val selectedDailyRouteOption = remember(selectedRoute, regularRouteOptions) {
        regularRouteOptions.firstOrNull { it.routeNo.equals(selectedRoute, ignoreCase = true) }
            ?: regularRouteOptions.firstOrNull { it.routeNo.equals("ALL", ignoreCase = true) }
            ?: RouteOption("ALL", "All Routes")
    }

    val selectedFridayRouteOption = remember(selectedRoute, fridayRouteOptions) {
        fridayRouteOptions.firstOrNull { it.routeNo.equals(selectedRoute, ignoreCase = true) }
    }

    val dailyDropdownItems = remember(regularRouteOptions) {
        regularRouteOptions.map {
            RouteDropdownUi(
                routeNo = it.routeNo.trim(),
                label = it.label,
                compactLabel = compactRouteOptionLabel(it.routeNo, it.label)
            )
        }
    }

    val fridayDropdownItems = remember(fridayRouteOptions) {
        fridayRouteOptions.map {
            RouteDropdownUi(
                routeNo = it.routeNo.trim(),
                label = it.label,
                compactLabel = compactRouteOptionLabel(it.routeNo, it.label)
            )
        }
    }

    val selectedRouteLabel by vm.selectedRouteLabel.collectAsState()
    val isSyncing by vm.isSyncing.collectAsState()
    val primaryText = if (dark) CardSurfaceLight else MaterialTheme.colorScheme.onSurface
    val secondaryText =
        if (dark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
    val view = LocalView.current
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val effectiveNotificationsEnabled = notificationsEnabled && routeAllowsMasterNotificationsToday
    val notifyLeadMinutes by vm.notifyLeadMinutes.collectAsState()
    val navBarBottomPad = with(LocalDensity.current) {
        val bottomPx = runCatching {
            ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
        }.getOrNull() ?: 0
        bottomPx.toDp()
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showProfileFeatureGuide by remember { mutableStateOf(false) }

    val showToggleMessage: (String) -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar(it)
        }
    }

    val alertPrefs =
        remember(ctx) { ctx.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE) }
    val hostActivity = ctx as? MainActivity

    var alarmSound5mEnabled by rememberSaveable {
        mutableStateOf(alertPrefs.getBoolean("alarm_sound_5m", false))
    }
    var alarmVibrate5mEnabled by rememberSaveable {
        mutableStateOf(alertPrefs.getBoolean("alarm_vibrate_5m", true))
    }

    fun hasNotificationPermissionNow(): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val latestRouteAllowsMaster by rememberUpdatedState(routeAllowsMasterNotificationsToday)

    fun routeAllowsNotifyTodayForDailyPick(dailyRouteNo: String): Boolean {
        val d = dailyRouteNo.trim()
        return if (isFriday) {
            val f = selectedFridayRouteNoUi.trim().ifBlank { "ALL" }
            f.isNotBlank() && !f.equals("ALL", ignoreCase = true) && f.startsWith(
                "F",
                ignoreCase = true
            )
        } else {
            d.isNotBlank() && !d.equals("ALL", ignoreCase = true) && !d.startsWith(
                "F",
                ignoreCase = true
            )
        }
    }

    fun routeAllowsNotifyTodayForFridayPick(fridayRouteRaw: String): Boolean {
        if (!isFriday) return false
        val f = fridayRouteRaw.trim().ifBlank { "ALL" }
        return f.isNotBlank() && !f.equals("ALL", ignoreCase = true) && f.startsWith(
            "F",
            ignoreCase = true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            if (!latestRouteAllowsMaster) {
                vm.setNotificationsEnabled(false)
                alertPrefs.edit().putBoolean("master_notifications_enabled", false).apply()
                showToggleMessage(
                    if (isFriday) {
                        "Select a Friday route (not ALL) to enable notifications"
                    } else {
                        "Select a daily route (not ALL) to enable notifications"
                    }
                )
            } else {
                vm.setNotificationsEnabled(true)
                alarmVibrate5mEnabled = true
                alertPrefs.edit().putBoolean("master_notifications_enabled", true)
                    .putBoolean("alarm_vibrate_5m", true).apply()
                showToggleMessage("Notifications enabled")
            }
        } else {
            vm.setNotificationsEnabled(false)
            alertPrefs.edit().putBoolean("master_notifications_enabled", false).apply()
            showToggleMessage("Notification permission required")
        }
    }


    val appDefaultRingtoneUri =
        "android.resource://${ctx.packageName}/${R.raw.app_default_ringtone}"
    val appDefaultRingtoneName = "App default ringtone"

    LaunchedEffect(notificationsEnabled, routeAllowsMasterNotificationsToday) {
        val osOk = hasNotificationPermissionNow()
        val persist = notificationsEnabled && osOk && routeAllowsMasterNotificationsToday
        if (notificationsEnabled && (!osOk || !routeAllowsMasterNotificationsToday)) {
            vm.setNotificationsEnabled(false)
        }
        val ed = alertPrefs.edit().putBoolean("master_notifications_enabled", persist)
        if (persist) {
            ed.putBoolean("alarm_vibrate_5m", true)
            alarmVibrate5mEnabled = true
        }
        ed.apply()
    }

    var customRingtoneUri by rememberSaveable {
        mutableStateOf(
            alertPrefs.getString("custom_ringtone_uri", appDefaultRingtoneUri)
                ?: appDefaultRingtoneUri
        )
    }
    var customRingtoneName by rememberSaveable {
        mutableStateOf(
            alertPrefs.getString("custom_ringtone_name", appDefaultRingtoneName)
                ?: appDefaultRingtoneName
        )
    }
    val hasPickedCustomFile =
        !customRingtoneUri.isNullOrBlank() && customRingtoneUri != appDefaultRingtoneUri && customRingtoneName != appDefaultRingtoneName
    var showRingtonePickerPage by rememberSaveable { mutableStateOf(false) }
    var showRouteSelectionPage by rememberSaveable { mutableStateOf(false) }
    var showNotificationSettingsPage by rememberSaveable { mutableStateOf(false) }
    var showNotificationOptimizationPage by rememberSaveable { mutableStateOf(false) }

    val playPreviewVibration: (String) -> Unit = remember(ctx) {
        { patternName ->
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = when (patternName) {
                "Soft vibration" -> longArrayOf(0, 140, 90, 140)
                "Strong vibration" -> longArrayOf(0, 320, 120, 420)
                "Pulse vibration" -> longArrayOf(0, 120, 80, 120, 80, 260)
                else -> longArrayOf(0, 220, 120, 220)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
                }
            } catch (_: Throwable) {
            }
        }
    }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingUri by remember { mutableStateOf<String?>(null) }

    val stopPreviewRingtone: () -> Unit = remember {
        {
            try {
                previewPlayer?.stop()
            } catch (_: Throwable) {
            }
            try {
                previewPlayer?.release()
            } catch (_: Throwable) {
            }
            previewPlayer = null
            previewingUri = null
        }
    }
    var presetBusyName by rememberSaveable { mutableStateOf("") }
    var presetLoadError by rememberSaveable { mutableStateOf("") }
    var presetLoadedCount by rememberSaveable { mutableStateOf(0) }

    suspend fun loadPresetRingtonesFromServer(): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            val candidateUrls = listOf(
                "https://raw.githubusercontent.com/sohan-parves/DIUtransportschedule/master/assets/ringtones/ringtones.json",
                "https://cdn.jsdelivr.net/gh/sohan-parves/DIUtransportschedule@master/assets/ringtones/ringtones.json?ts=${System.currentTimeMillis()}"
            )

            var lastError: Throwable? = null

            for (candidateUrl in candidateUrls) {
                try {
                    val conn = (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        requestMethod = "GET"
                        useCaches = false
                        setRequestProperty("Accept", "application/json,text/plain,*/*")
                        setRequestProperty("Cache-Control", "no-cache")
                        setRequestProperty("Pragma", "no-cache")
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                    }

                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Preset load failed (HTTP $code)")
                    }

                    val raw = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (!raw.startsWith("[")) {
                        throw IllegalStateException("Preset response was not JSON array")
                    }

                    val arr = JSONArray(raw)
                    val parsed = List(arr.length()) { i ->
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name").trim()
                        val url = obj.optString("url").trim()
                        name to url
                    }.filter { it.first.isNotBlank() && it.second.isNotBlank() }

                    return@withContext parsed
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            if (lastError != null) throw lastError
            emptyList()
        }
    }

    fun buildRawGitHubRingtoneUrl(displayName: String, remoteUrl: String): String {
        val ext = remoteUrl.substringAfterLast('/', "").substringAfterLast('.', "mp3")
            .substringBefore('?').ifBlank { "mp3" }

        val fileName = when {
            remoteUrl.contains("/assets/ringtones/", ignoreCase = true) -> {
                remoteUrl.substringAfterLast('/').substringBefore('?').ifBlank {
                    "${displayName.trim().ifBlank { "preset_ringtone" }}.$ext"
                }
            }

            else -> "${displayName.trim().ifBlank { "preset_ringtone" }}.$ext"
        }

        val encodedFileName =
            URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()).replace("+", "%20")

        return "https://raw.githubusercontent.com/sohan-parves/DIUtransportschedule/master/assets/ringtones/$encodedFileName"
    }

    suspend fun downloadPresetRingtoneToCache(remoteUrl: String, displayName: String): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val safeBase = displayName.trim().ifBlank { "preset_ringtone" }
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_")

                val ext = remoteUrl.substringAfterLast('/', "").substringAfterLast('.', "mp3")
                    .substringBefore('?').ifBlank { "mp3" }

                val dir = File(ctx.cacheDir, "preset_ringtones")
                if (!dir.exists()) dir.mkdirs()

                val outFile = File(dir, "$safeBase.$ext")

                if (!outFile.exists() || outFile.length() <= 0L) {
                    val candidateUrls = listOf(
                        remoteUrl, buildRawGitHubRingtoneUrl(displayName, remoteUrl)
                    ).distinct()

                    var copied = false
                    var lastError: Throwable? = null

                    for (candidateUrl in candidateUrls) {
                        try {
                            val conn =
                                (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 12000
                                    readTimeout = 12000
                                    requestMethod = "GET"
                                    useCaches = false
                                    setRequestProperty("Cache-Control", "no-cache")
                                    setRequestProperty("Pragma", "no-cache")
                                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                                }

                            val code = conn.responseCode
                            if (code !in 200..299) {
                                throw IllegalStateException("Preset download failed (HTTP $code)")
                            }

                            conn.inputStream.use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }

                            copied = true
                            break
                        } catch (t: Throwable) {
                            lastError = t
                        }
                    }

                    if (!copied) {
                        throw (lastError ?: IllegalStateException("Preset download failed"))
                    }
                }

                outFile.toURI().toString()
            }.getOrNull()
        }
    }

    val playPreviewRingtone: (String?) -> Unit = remember(ctx, customRingtoneUri) {
        { uriString ->
            val target =
                uriString?.takeIf { it.isNotBlank() } ?: customRingtoneUri ?: appDefaultRingtoneUri
            // Toggle: same ringtone → stop
            if (previewingUri == target && previewPlayer != null) {
                stopPreviewRingtone()
                previewingUri = null
                return@remember
            }
            if (target.isNullOrBlank()) return@remember

            // Ensure any previous preview is fully stopped before playing a new one
            stopPreviewRingtone()

            runCatching {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    setDataSource(ctx, Uri.parse(target))
                    isLooping = false
                    setOnCompletionListener {
                        try {
                            it.release()
                        } catch (_: Throwable) {
                        }
                        if (previewPlayer === it) previewPlayer = null
                    }
                    prepare()
                    start()
                }
                previewPlayer = mp
                previewingUri = target
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.stop()
            } catch (_: Throwable) {
            }
            try {
                previewPlayer?.release()
            } catch (_: Throwable) {
            }
            previewPlayer = null
            previewingUri = null
        }
    }

    val customRingtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val pickedName = runCatching {
                ctx.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull()?.ifBlank { null } ?: "Custom ringtone"

            customRingtoneUri = uri.toString()
            customRingtoneName = pickedName
            alertPrefs.edit().putString("custom_ringtone_uri", customRingtoneUri)
                .putString("custom_ringtone_name", customRingtoneName).apply()

            showToggleMessage("Custom ringtone selected: $pickedName")
        }
    }
    LaunchedEffect(Unit) {
        val savedUri = alertPrefs.getString("custom_ringtone_uri", null)
        val savedName = alertPrefs.getString("custom_ringtone_name", null)
        if (savedUri.isNullOrBlank() || savedName.isNullOrBlank()) {
            customRingtoneUri = appDefaultRingtoneUri
            customRingtoneName = appDefaultRingtoneName
            alertPrefs.edit().putString("custom_ringtone_uri", appDefaultRingtoneUri)
                .putString("custom_ringtone_name", appDefaultRingtoneName).apply()
        }
    }
    LaunchedEffect(showRingtonePickerPage) {
        if (!showRingtonePickerPage) {
            stopPreviewRingtone()
        }
    }
    val hostedRingtones by produceState(
        initialValue = emptyList<Pair<String, String>>(), key1 = showRingtonePickerPage
    ) {
        if (!showRingtonePickerPage) {
            presetLoadError = ""
            presetLoadedCount = 0
            value = emptyList()
            return@produceState
        }

        value = runCatching {
            loadPresetRingtonesFromServer()
        }.onSuccess {
            presetLoadError = if (it.isEmpty()) "No preset ringtone found from server" else ""
            presetLoadedCount = it.size
        }.onFailure {
            presetLoadError = it.message ?: "Failed to load preset ringtones"
            presetLoadedCount = 0
        }.getOrElse { emptyList() }
    }

    val alarmDurationOptions = listOf(
        5_000L to "5 sec",
        10_000L to "10 sec",
        15_000L to "15 sec",
        30_000L to "30 sec",
        60_000L to "1 min",
        120_000L to "2 min",
        180_000L to "3 min",
        300_000L to "5 min"
    )

    var alarmSoundDurationMs by rememberSaveable {
        mutableLongStateOf(
            alertPrefs.getLong("alarm_sound_duration_ms", 5_000L).coerceIn(5_000L, 5 * 60 * 1000L)
        )
    }

    var alarmVibrateDurationMs by rememberSaveable {
        mutableLongStateOf(
            alertPrefs.getLong("alarm_vibrate_duration_ms", 5_000L).coerceIn(5_000L, 5 * 60 * 1000L)
        )
    }

    var alarmSoundDurationMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var alarmVibrateDurationMenuExpanded by rememberSaveable { mutableStateOf(false) }

    var customVibrationPattern by rememberSaveable {
        mutableStateOf(
            alertPrefs.getString("custom_vibration_pattern", "Default vibration")
                ?: "Default vibration"
        )
    }

    var showVibrationPickerPage by rememberSaveable { mutableStateOf(false) }
    var previewingVibrationPattern by rememberSaveable { mutableStateOf("") }

    // Premium card styling (light mode)
    val premiumLightCard = CardSurfaceLight
    val premiumLightBorder = TimeChipBorderLight
    val premiumLightDivider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    val greenSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.secondary,
        checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
        checkedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )

    var routeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val showUpdateBanner by vm.showUpdateBanner.collectAsState()
    val compactMode by vm.compactMode.collectAsState()
    var showReloadPopup by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isSyncing, showReloadPopup) {
        if (!showReloadPopup) return@LaunchedEffect

        if (!isSyncing) {
            delay(800)
            if (!isSyncing) {
                showReloadPopup = false
            }
            return@LaunchedEffect
        }

        delay(20000)
        if (showReloadPopup && isSyncing) {
            showReloadPopup = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            val scrollState = rememberScrollState()
            val alarmGuardPrefs = remember {
                ctx.getSharedPreferences("alarm_route_guard_prefs", Context.MODE_PRIVATE)
            }

            // MainNav keeps Profile composed at startup; skip the first route pass so Home's single
            // initial refresh isn't followed by duplicate sync work (and extra loading feel).
            var skipInitialProfileRouteRefresh by rememberSaveable { mutableStateOf(true) }

            if (showReloadPopup || isSyncing) {
                Dialog(
                    onDismissRequest = { }, properties = DialogProperties(
                        dismissOnBackPress = false, dismissOnClickOutside = false
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        color = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator()

                            Text(
                                text = "Reloading latest data...",
                                style = MaterialTheme.typography.titleSmall,
                                color = primaryText,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "Please wait a moment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .padding(bottom = 24.dp + navBarBottomPad + 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    IconButton(
                        onClick = { showProfileFeatureGuide = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Show feature guide",
                            tint = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showProfileFeatureGuide) {
                    AppFeatureGuideDialog(
                        guide = AppFeatureGuideContent.model,
                        onClose = { showProfileFeatureGuide = false })
                }

                // ---------------- Route Select ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 8.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Select",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = primaryText
                    )

                    val refreshColor = if (dark) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary

                    var lastRefreshTapMs by rememberSaveable { mutableStateOf(0L) }
                    val canTapRefresh =
                        !isSyncing && (System.currentTimeMillis() - lastRefreshTapMs >= 5 * 60 * 1000L)

                    IconButton(
                        onClick = {
                            lastRefreshTapMs = System.currentTimeMillis()
                            showReloadPopup = true
                            vm.refresh(showBannerIfUpdated = true)
                        }, enabled = canTapRefresh, modifier = Modifier.size(36.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = refreshColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh routes",
                                tint = refreshColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                    border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { showRouteSelectionPage = true }
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Route Selection",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryText
                            )
                            Text(
                                text = "Tap to open daily and Friday route selection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open route selection",
                            tint = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // ---------------- Dark Mode ----------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                    border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dark Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryText
                            )
                            Text(
                                text = "Turn on dark theme",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText
                            )
                        }

                        Switch(
                            checked = dark, onCheckedChange = {
                                vm.setDarkMode(it)
                                showToggleMessage(if (it) "Dark mode ON" else "Dark mode OFF")
                            }, colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }
                }

                // ---------------- Features ----------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                    border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Features",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show update banner",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Show update notice after sync",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText
                                )
                            }
                            Switch(
                                checked = showUpdateBanner, onCheckedChange = {
                                    vm.setShowUpdateBanner(it)
                                    showToggleMessage(if (it) "Update banner ON" else "Update banner OFF")
                                }, colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                            )
                        }

                        HorizontalDivider(
                            color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.10f
                            ) else premiumLightDivider
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showNotificationSettingsPage = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notifications",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = primaryText
                                )

                                Text(
                                    text = "Tap to open all notification settings",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open notification settings",
                                tint = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (showRouteSelectionPage) {
                    Dialog(
                        onDismissRequest = { showRouteSelectionPage = false },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = false
                        )
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                    .padding(bottom = 24.dp + navBarBottomPad),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Spacer(Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Route Selection",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )

                                    Text(
                                        text = "Back",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showRouteSelectionPage = false }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold)
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                                    border = if (dark) null else BorderStroke(
                                        1.dp, premiumLightBorder
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Route Filter",
                                                    style = MaterialTheme.typography.titleMedium
                                                )

                                                Text(
                                                    text = "Select which route to show on Home",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = secondaryText,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        var dailyRouteMenuExpanded by rememberSaveable {
                                            mutableStateOf(
                                                false
                                            )
                                        }
                                        var fridayRouteMenuExpanded by rememberSaveable {
                                            mutableStateOf(
                                                false
                                            )
                                        }
                                        val selectedDailyRouteNo =
                                            remember(selectedDailyRouteOption.routeNo) { selectedDailyRouteOption.routeNo }

                                        LaunchedEffect(fridayDropdownItems) {
                                            if (fridayDropdownItems.isEmpty()) return@LaunchedEffect

                                            val savedFridayRoute =
                                                routePrefs.getString("selected_friday_route", "")
                                                    .orEmpty().trim()

                                            val resolvedFridayRoute =
                                                if (savedFridayRoute.isNotBlank() && fridayDropdownItems.any {
                                                        it.routeNo.equals(
                                                            savedFridayRoute, ignoreCase = true
                                                        )
                                                    }) {
                                                    fridayDropdownItems.first {
                                                        it.routeNo.equals(
                                                            savedFridayRoute, ignoreCase = true
                                                        )
                                                    }.routeNo
                                                } else {
                                                    ""
                                                }

                                            if (selectedFridayRouteNoUi != resolvedFridayRoute) {
                                                selectedFridayRouteNoUi = resolvedFridayRoute
                                            }

                                            routePrefs.edit().putString(
                                                "selected_friday_route", resolvedFridayRoute
                                            ).apply()
                                        }

                                        LaunchedEffect(
                                            selectedRoute, isFriday, selectedFridayRouteNoUi
                                        ) {
                                            val normalizedDailyRoute =
                                                selectedRoute.trim().ifBlank { "ALL" }
                                            val normalizedFridayRoute =
                                                selectedFridayRouteNoUi.trim().ifBlank { "ALL" }

                                            vm.setSelectedFridayRoute(normalizedFridayRoute)

                                            routePrefs.edit()
                                                .putString("selected_route", normalizedDailyRoute)
                                                .putString(
                                                    "selected_friday_route", normalizedFridayRoute
                                                ).apply()

                                            val guardedRoute = if (isFriday) {
                                                normalizedFridayRoute
                                            } else {
                                                normalizedDailyRoute
                                            }

                                            alarmGuardPrefs.edit().putString(
                                                "selected_route",
                                                guardedRoute.ifBlank { "ALL" }).apply()

                                            if (skipInitialProfileRouteRefresh) {
                                                skipInitialProfileRouteRefresh = false
                                            } else {
                                                vm.refreshFromLocalOnceIfAvailable(ctx)
                                            }
                                        }

                                        val effectiveNotificationRouteNo = remember(
                                            key1 = isFriday,
                                            key2 = selectedDailyRouteNo,
                                            key3 = selectedFridayRouteNoUi
                                        ) {
                                            if (isFriday) {
                                                selectedFridayRouteNoUi.trim()
                                            } else {
                                                selectedDailyRouteNo.trim()
                                            }
                                        }

                                        val dailyDropdownUiItems =
                                            remember(dailyDropdownItems, selectedDailyRouteNo) {
                                                dailyDropdownItems.map { opt ->
                                                    val isSelected =
                                                        opt.routeNo == selectedDailyRouteNo
                                                    Triple(opt, isSelected, opt.compactLabel)
                                                }
                                            }

                                        val fridayDropdownUiItems: List<Triple<RouteOption, Boolean, String>> =
                                            remember(
                                                fridayDropdownItems, selectedFridayRouteNoUi
                                            ) {
                                                val allItem: Triple<RouteOption, Boolean, String> =
                                                    Triple(
                                                        RouteOption(
                                                            routeNo = "ALL",
                                                            label = "No Friday route selected"
                                                        ),
                                                        selectedFridayRouteNoUi.isBlank() || selectedFridayRouteNoUi.equals(
                                                            "ALL", ignoreCase = true
                                                        ),
                                                        "No Friday route selected"
                                                    )

                                                val mappedItems: List<Triple<RouteOption, Boolean, String>> =
                                                    fridayDropdownItems.map { opt ->
                                                        val routeOption = RouteOption(
                                                            routeNo = opt.routeNo, label = opt.label
                                                        )

                                                        Triple(
                                                            routeOption,
                                                            selectedFridayRouteNoUi.equals(
                                                                opt.routeNo, ignoreCase = true
                                                            ),
                                                            compactRouteOptionLabel(
                                                                routeOption.routeNo,
                                                                routeOption.label
                                                            )
                                                        )
                                                    }

                                                listOf(allItem) + mappedItems
                                            }

                                        Text(
                                            text = "Daily Route",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = primaryText
                                        )

                                        ExposedDropdownMenuBox(
                                            expanded = dailyRouteMenuExpanded, onExpandedChange = {
                                                dailyRouteMenuExpanded = !dailyRouteMenuExpanded
                                            }) {
                                            OutlinedTextField(
                                                value = selectedDailyRouteOption.label,
                                                onValueChange = {},
                                                readOnly = true,
                                                singleLine = true,
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth(),
                                                shape = RoundedCornerShape(18.dp),
                                                label = { Text("Select daily route") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                        expanded = dailyRouteMenuExpanded
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = if (dailyRouteMenuExpanded) (if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                                    else MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.6f
                                                    ),
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.6f
                                                    ),
                                                    focusedTextColor = primaryText,
                                                    unfocusedTextColor = primaryText,
                                                    focusedLabelColor = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                                                    unfocusedLabelColor = if (dark) Color.White else secondaryText,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent
                                                )
                                            )

                                            ExposedDropdownMenu(
                                                expanded = dailyRouteMenuExpanded,
                                                onDismissRequest = {
                                                    dailyRouteMenuExpanded = false
                                                },
                                                modifier = Modifier
                                                    .exposedDropdownSize()
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .heightIn(max = 320.dp)
                                            ) {
                                                dailyDropdownUiItems.forEachIndexed { index, entry ->
                                                    val opt = entry.first
                                                    val isSelected = entry.second
                                                    val compactLabel = entry.third

                                                    DropdownMenuItem(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        contentPadding = PaddingValues(0.dp),
                                                        text = {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(
                                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(
                                                                            alpha = 0.08f
                                                                        ) else Color.Transparent,
                                                                        shape = RoundedCornerShape(
                                                                            12.dp
                                                                        )
                                                                    )
                                                                    .padding(
                                                                        vertical = 10.dp,
                                                                        horizontal = 6.dp
                                                                    )
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier.width(26.dp),
                                                                        contentAlignment = Alignment.CenterStart
                                                                    ) {
                                                                        if (isSelected) {
                                                                            Icon(
                                                                                imageVector = Icons.Filled.Check,
                                                                                contentDescription = "Selected",
                                                                                tint = if (dark) MaterialTheme.colorScheme.secondary
                                                                                else MaterialTheme.colorScheme.primary
                                                                            )
                                                                        }
                                                                    }

                                                                    Spacer(Modifier.size(2.dp))

                                                                    Text(
                                                                        text = opt.routeNo,
                                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                                            fontWeight = FontWeight.SemiBold
                                                                        ),
                                                                        color = if (isSelected) if (dark) MaterialTheme.colorScheme.secondary
                                                                        else MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.onSurface,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )

                                                                    Spacer(Modifier.size(6.dp))

                                                                    Box(
                                                                        modifier = Modifier
                                                                            .height(
                                                                                18.dp
                                                                            )
                                                                            .width(1.dp)
                                                                            .background(
                                                                                MaterialTheme.colorScheme.onSurface.copy(
                                                                                    alpha = 0.18f
                                                                                ),
                                                                                shape = RoundedCornerShape(
                                                                                    1.dp
                                                                                )
                                                                            )
                                                                    )

                                                                    Spacer(Modifier.size(6.dp))

                                                                    Text(
                                                                        text = compactLabel,
                                                                        style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        else MaterialTheme.typography.bodyLarge,
                                                                        color = if (isSelected) if (dark) MaterialTheme.colorScheme.secondary
                                                                        else MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.onSurface,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            dailyRouteMenuExpanded = false
                                                            val rid = opt.routeNo.trim()
                                                            val fullRoadText = buildString {
                                                                append(rid)
                                                                if (opt.compactLabel.isNotBlank()) {
                                                                    append(" — ")
                                                                    append(opt.compactLabel)
                                                                }
                                                            }
                                                            scope.launch {
                                                                vm.setSelectedRoute(opt.routeNo)
                                                                SelectedRoadStore.save(
                                                                    ctx, rid, fullRoadText
                                                                )
                                                                vm.selectedRoute.first {
                                                                    it.trim().equals(
                                                                        rid, ignoreCase = true
                                                                    )
                                                                }
                                                                if (routeAllowsNotifyTodayForDailyPick(
                                                                        rid
                                                                    ) && hasNotificationPermissionNow()
                                                                ) {
                                                                    vm.setNotificationsEnabled(true)
                                                                    vm.notificationsEnabled.first { it }
                                                                    alarmVibrate5mEnabled = true
                                                                    alarmSound5mEnabled = false
                                                                    alertPrefs.edit().putBoolean(
                                                                        "master_notifications_enabled",
                                                                        true
                                                                    ).putBoolean(
                                                                        "alarm_vibrate_5m", true
                                                                    ).putBoolean(
                                                                        "alarm_sound_5m", false
                                                                    ).apply()
                                                                }
                                                            }
                                                        })

                                                    if (index != dailyDropdownUiItems.lastIndex) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 14.dp),
                                                            color = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.08f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Text(
                                            text = "Friday Route",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = primaryText
                                        )

                                        val selectedFridayRouteLabelUi =
                                            remember(fridayDropdownItems, selectedFridayRouteNoUi) {
                                                if (selectedFridayRouteNoUi.isBlank() || selectedFridayRouteNoUi.equals(
                                                        "ALL", ignoreCase = true
                                                    )
                                                ) {
                                                    "No Friday route selected"
                                                } else {
                                                    fridayDropdownItems.firstOrNull {
                                                        it.routeNo.equals(
                                                            selectedFridayRouteNoUi,
                                                            ignoreCase = true
                                                        )
                                                    }?.label ?: "No Friday route selected"
                                                }
                                            }

                                        ExposedDropdownMenuBox(
                                            expanded = fridayRouteMenuExpanded, onExpandedChange = {
                                                fridayRouteMenuExpanded = !fridayRouteMenuExpanded
                                            }) {
                                            OutlinedTextField(
                                                value = selectedFridayRouteLabelUi,
                                                onValueChange = {},
                                                readOnly = true,
                                                singleLine = true,
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth(),
                                                shape = RoundedCornerShape(18.dp),
                                                label = { Text("Select Friday route") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                        expanded = fridayRouteMenuExpanded
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = if (fridayRouteMenuExpanded) (if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                                    else MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.6f
                                                    ),
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.6f
                                                    ),
                                                    focusedTextColor = primaryText,
                                                    unfocusedTextColor = primaryText,
                                                    focusedLabelColor = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                                                    unfocusedLabelColor = if (dark) Color.White else secondaryText,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent
                                                )
                                            )

                                            ExposedDropdownMenu(
                                                expanded = fridayRouteMenuExpanded,
                                                onDismissRequest = {
                                                    fridayRouteMenuExpanded = false
                                                },
                                                modifier = Modifier
                                                    .exposedDropdownSize()
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .heightIn(max = 320.dp)
                                            ) {
                                                fridayDropdownUiItems.forEachIndexed { index, entry ->
                                                    val opt = entry.first
                                                    val isSelected = entry.second
                                                    val labelText = entry.third

                                                    DropdownMenuItem(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        contentPadding = PaddingValues(0.dp),
                                                        text = {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(
                                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(
                                                                            alpha = 0.08f
                                                                        ) else Color.Transparent,
                                                                        shape = RoundedCornerShape(
                                                                            12.dp
                                                                        )
                                                                    )
                                                                    .padding(
                                                                        vertical = 10.dp,
                                                                        horizontal = 6.dp
                                                                    )
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier.width(26.dp),
                                                                        contentAlignment = Alignment.CenterStart
                                                                    ) {
                                                                        if (isSelected) {
                                                                            Icon(
                                                                                imageVector = Icons.Filled.Check,
                                                                                contentDescription = "Selected",
                                                                                tint = if (dark) MaterialTheme.colorScheme.secondary
                                                                                else MaterialTheme.colorScheme.primary
                                                                            )
                                                                        }
                                                                    }

                                                                    Spacer(Modifier.size(2.dp))

                                                                    Text(
                                                                        text = opt.routeNo.trim(),
                                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                                            fontWeight = FontWeight.SemiBold
                                                                        ),
                                                                        color = if (isSelected) if (dark) MaterialTheme.colorScheme.secondary
                                                                        else MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.onSurface,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )

                                                                    Spacer(Modifier.size(6.dp))

                                                                    Box(
                                                                        modifier = Modifier
                                                                            .height(
                                                                                18.dp
                                                                            )
                                                                            .width(1.dp)
                                                                            .background(
                                                                                MaterialTheme.colorScheme.onSurface.copy(
                                                                                    alpha = 0.18f
                                                                                ),
                                                                                shape = RoundedCornerShape(
                                                                                    1.dp
                                                                                )
                                                                            )
                                                                    )

                                                                    Spacer(Modifier.size(6.dp))

                                                                    Text(
                                                                        text = labelText,
                                                                        style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        else MaterialTheme.typography.bodyLarge,
                                                                        color = if (isSelected) if (dark) MaterialTheme.colorScheme.secondary
                                                                        else MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.onSurface,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            fridayRouteMenuExpanded = false

                                                            val normalizedFridayRoute =
                                                                if (opt.routeNo.equals(
                                                                        "ALL", ignoreCase = true
                                                                    )
                                                                ) {
                                                                    ""
                                                                } else {
                                                                    opt.routeNo.trim()
                                                                }

                                                            selectedFridayRouteNoUi =
                                                                normalizedFridayRoute

                                                            routePrefs.edit().putString(
                                                                "selected_friday_route",
                                                                normalizedFridayRoute
                                                            ).apply()

                                                            val fullRoadText =
                                                                if (normalizedFridayRoute.isBlank()) {
                                                                    "No Friday route selected"
                                                                } else {
                                                                    buildString {
                                                                        append(opt.routeNo.trim())
                                                                        val compactLabel =
                                                                            labelText.trim()
                                                                        if (compactLabel.isNotBlank()) {
                                                                            append(" — ")
                                                                            append(compactLabel)
                                                                        }
                                                                    }
                                                                }

                                                            scope.launch {
                                                                val frVm =
                                                                    normalizedFridayRoute.ifBlank { "ALL" }
                                                                vm.setSelectedFridayRoute(frVm)
                                                                SelectedRoadStore.save(
                                                                    ctx,
                                                                    normalizedFridayRoute,
                                                                    fullRoadText
                                                                )
                                                                if (routeAllowsNotifyTodayForFridayPick(
                                                                        normalizedFridayRoute
                                                                    ) && hasNotificationPermissionNow()
                                                                ) {
                                                                    vm.setNotificationsEnabled(true)
                                                                    vm.notificationsEnabled.first { it }
                                                                    alarmVibrate5mEnabled = true
                                                                    alarmSound5mEnabled = false
                                                                    alertPrefs.edit().putBoolean(
                                                                        "master_notifications_enabled",
                                                                        true
                                                                    ).putBoolean(
                                                                        "alarm_vibrate_5m", true
                                                                    ).putBoolean(
                                                                        "alarm_sound_5m", false
                                                                    ).apply()
                                                                }
                                                            }
                                                        })

                                                    if (index != fridayDropdownUiItems.lastIndex) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 14.dp),
                                                            color = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.08f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = "Tip: Pull to refresh on Home too.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (showNotificationSettingsPage) {
                    Dialog(
                        onDismissRequest = { showNotificationSettingsPage = false },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = false
                        )
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                    .padding(bottom = 24.dp + navBarBottomPad),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Spacer(Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Notification Settings",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )

                                    Text(
                                        text = "Back",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showNotificationSettingsPage = false }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold)
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                                    border = if (dark) null else BorderStroke(
                                        1.dp, premiumLightBorder
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "Notifications",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryText
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Master Notification",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = primaryText
                                                )
                                                Text(
                                                    text = when {
                                                        notificationsBlockedByRoute -> {
                                                            if (isFriday) {
                                                                "Friday: pick a Friday route (not ALL). Daily route does not apply today."
                                                            } else {
                                                                "Non-Friday: pick a daily route (not ALL, not a Friday route). Friday route does not apply today."
                                                            }
                                                        }

                                                        else -> "Turn this ON to enable notifications and vibration together. Ringtone is controlled separately below"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = primaryText
                                                )
                                            }

                                            Switch(
                                                checked = effectiveNotificationsEnabled,
                                                onCheckedChange = { enabled ->
                                                    if (!enabled) {
                                                        vm.setNotificationsEnabled(false)
                                                        alarmSound5mEnabled = false
                                                        alarmVibrate5mEnabled = false
                                                        alertPrefs.edit().putBoolean(
                                                            "master_notifications_enabled",
                                                            false
                                                        ).putBoolean("alarm_sound_5m", false)
                                                            .putBoolean("alarm_vibrate_5m", false)
                                                            .apply()
                                                        showToggleMessage("Notifications turned off")
                                                    } else {
                                                        if (notificationsBlockedByRoute) {
                                                            vm.setNotificationsEnabled(false)
                                                            alertPrefs.edit().putBoolean(
                                                                "master_notifications_enabled",
                                                                false
                                                            ).apply()
                                                            showToggleMessage(
                                                                if (isFriday) {
                                                                    "Select a Friday route (not ALL) to enable notifications"
                                                                } else {
                                                                    "Select a daily route (not ALL) to enable notifications"
                                                                }
                                                            )
                                                        } else if (hasNotificationPermissionNow()) {
                                                            vm.setNotificationsEnabled(true)

                                                            alarmVibrate5mEnabled = true
                                                            alarmSound5mEnabled =
                                                                alertPrefs.getBoolean(
                                                                    "alarm_sound_5m", false
                                                                )

                                                            alertPrefs.edit().putBoolean(
                                                                "master_notifications_enabled",
                                                                true
                                                            ).putBoolean(
                                                                "alarm_vibrate_5m", true
                                                            ).putBoolean(
                                                                "alarm_sound_5m",
                                                                alarmSound5mEnabled
                                                            ).apply()
                                                            showToggleMessage("Notifications enabled")
                                                        } else {
                                                            vm.setNotificationsEnabled(false)
                                                            alertPrefs.edit().putBoolean(
                                                                "master_notifications_enabled",
                                                                false
                                                            ).apply()

                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                                hostActivity?.showPermissionIntroThenRequest(
                                                                    permission = Manifest.permission.POST_NOTIFICATIONS,
                                                                    requestAction = {
                                                                        notificationPermissionLauncher.launch(
                                                                            Manifest.permission.POST_NOTIFICATIONS
                                                                        )
                                                                    })
                                                            } else {
                                                                try {
                                                                    val intent =
                                                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                                            putExtra(
                                                                                Settings.EXTRA_APP_PACKAGE,
                                                                                ctx.packageName
                                                                            )
                                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        }
                                                                    ctx.startActivity(intent)
                                                                } catch (_: Throwable) {
                                                                    val fallback =
                                                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                            this.data =
                                                                                Uri.fromParts(
                                                                                    "package",
                                                                                    ctx.packageName,
                                                                                    null
                                                                                )
                                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        }
                                                                    ctx.startActivity(fallback)
                                                                }
                                                                showToggleMessage("Enable app notifications from Settings")
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = true,
                                                colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                            )
                                        }

                                        if (notificationsBlockedByRoute) {
                                            Text(
                                                text = if (isFriday) "Today is Friday — only the Friday route dropdown controls whether notifications can be on."
                                                else "Any day except Friday — only the daily route dropdown controls whether notifications can be on.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryText
                                            )
                                        }

                                        HorizontalDivider(
                                            color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.10f
                                            )
                                            else premiumLightDivider
                                        )

                                        // --- Notification lead time state ---
                                        var notifyLeadMinutesDraft by remember(notifyLeadMinutes) {
                                            mutableStateOf(notifyLeadMinutes.toFloat())
                                        }
                                        var notifyLeadMinutesDragging by remember {
                                            mutableStateOf(
                                                false
                                            )
                                        }
                                        LaunchedEffect(notifyLeadMinutes) {
                                            if (!notifyLeadMinutesDragging) {
                                                notifyLeadMinutesDraft = notifyLeadMinutes.toFloat()
                                            }
                                        }

                                        AnimatedVisibility(visible = effectiveNotificationsEnabled) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = "Notify me ${
                                                        notifyLeadMinutesDraft.toInt()
                                                            .coerceIn(5, 120)
                                                    } minutes before",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = primaryText
                                                )

                                                Slider(
                                                    value = notifyLeadMinutesDraft,
                                                    onValueChange = {
                                                        notifyLeadMinutesDragging = true
                                                        notifyLeadMinutesDraft = it
                                                    },
                                                    onValueChangeFinished = {
                                                        notifyLeadMinutesDragging = false
                                                        val finalLeadMinutes =
                                                            notifyLeadMinutesDraft.toInt()
                                                                .coerceIn(5, 120)
                                                        resetAlarmStateForNotifyTimeChange(
                                                            ctx
                                                        )
                                                        vm.setNotifyLeadMinutes(finalLeadMinutes)
                                                        notifyLeadMinutesDraft =
                                                            finalLeadMinutes.toFloat()
                                                        val now = System.currentTimeMillis()
                                                        val lastShown = ctx.getSharedPreferences(
                                                            "notify_toast_guard",
                                                            Context.MODE_PRIVATE
                                                        ).getLong("last_notify_toast", 0L)

                                                        if (now - lastShown > 3000) {
                                                            ctx.getSharedPreferences(
                                                                "notify_toast_guard",
                                                                Context.MODE_PRIVATE
                                                            ).edit()
                                                                .putLong("last_notify_toast", now)
                                                                .apply()

                                                            scope.launch {
                                                                delay(400)
                                                                showToggleMessage("Notify time updated. Alarm, notification and vibration reset.")
                                                            }
                                                        }
                                                    },
                                                    valueRange = 5f..120f,
                                                    steps = 22,
                                                    colors = if (dark) SliderDefaults.colors(
                                                        thumbColor = MaterialTheme.colorScheme.secondary,
                                                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                                                        inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(
                                                            alpha = 0.30f
                                                        )
                                                    ) else SliderDefaults.colors()
                                                )

                                                Text(
                                                    text = "Default: 30 minutes",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = primaryText
                                                )
                                            }
                                            HorizontalDivider(
                                                color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.10f
                                                ) else premiumLightDivider
                                            )
                                        }
                                        AnimatedVisibility(visible = effectiveNotificationsEnabled) {
                                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                                HorizontalDivider(
                                                    color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.10f
                                                    ) else premiumLightDivider
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Ringtone",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = primaryText
                                                        )
                                                        Text(
                                                            text = "Play alarm ringtone when a notice arrives",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = secondaryText
                                                        )
                                                    }
                                                    Switch(
                                                        checked = alarmSound5mEnabled,
                                                        onCheckedChange = {
                                                            alarmSound5mEnabled = it
                                                            alertPrefs.edit()
                                                                .putBoolean("alarm_sound_5m", it)
                                                                .apply()
                                                            showToggleMessage(if (it) "Ringtone ON" else "Ringtone OFF")
                                                        },
                                                        enabled = effectiveNotificationsEnabled,
                                                        colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                                    )
                                                }

                                                AnimatedVisibility(visible = effectiveNotificationsEnabled && alarmSound5mEnabled) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                    ) {
                                                        HorizontalDivider(
                                                            color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.10f
                                                            ) else premiumLightDivider
                                                        )

                                                        Text(
                                                            text = "Ringtone duration",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = primaryText,
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                        Text(
                                                            text = "Choose how long ringtone should continue",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = secondaryText
                                                        )

                                                        ExposedDropdownMenuBox(
                                                            expanded = alarmSoundDurationMenuExpanded,
                                                            onExpandedChange = {
                                                                alarmSoundDurationMenuExpanded =
                                                                    !alarmSoundDurationMenuExpanded
                                                            }) {
                                                            val selectedAlarmSoundDurationLabel =
                                                                alarmDurationOptions.firstOrNull {
                                                                    it.first == alarmSoundDurationMs
                                                                }?.second ?: "30 sec"

                                                            OutlinedTextField(
                                                                value = selectedAlarmSoundDurationLabel,
                                                                onValueChange = {},
                                                                readOnly = true,
                                                                singleLine = true,
                                                                modifier = Modifier
                                                                    .menuAnchor()
                                                                    .fillMaxWidth(),
                                                                shape = RoundedCornerShape(18.dp),
                                                                label = { Text("Ringtone duration") },
                                                                trailingIcon = {
                                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                                        expanded = alarmSoundDurationMenuExpanded
                                                                    )
                                                                },
                                                                colors = OutlinedTextFieldDefaults.colors(
                                                                    focusedBorderColor = if (alarmSoundDurationMenuExpanded) (if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                                                    else MaterialTheme.colorScheme.outline.copy(
                                                                        alpha = 0.6f
                                                                    ),
                                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                                        alpha = 0.6f
                                                                    ),
                                                                    focusedTextColor = primaryText,
                                                                    unfocusedTextColor = primaryText,
                                                                    focusedLabelColor = if (alarmSoundDurationMenuExpanded) (if (dark) Color.White else MaterialTheme.colorScheme.primary)
                                                                    else (if (dark) Color.White else secondaryText),
                                                                    unfocusedLabelColor = if (dark) Color.White else secondaryText,
                                                                    focusedContainerColor = Color.Transparent,
                                                                    unfocusedContainerColor = Color.Transparent
                                                                )
                                                            )

                                                            ExposedDropdownMenu(
                                                                expanded = alarmSoundDurationMenuExpanded,
                                                                onDismissRequest = {
                                                                    alarmSoundDurationMenuExpanded =
                                                                        false
                                                                },
                                                                modifier = Modifier
                                                                    .exposedDropdownSize()
                                                                    .background(MaterialTheme.colorScheme.surface)
                                                            ) {
                                                                alarmDurationOptions.forEach { (durationMs, label) ->
                                                                    DropdownMenuItem(text = {
                                                                        Text(
                                                                            text = label,
                                                                            color = if (durationMs == alarmSoundDurationMs) {
                                                                                if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                                            } else {
                                                                                MaterialTheme.colorScheme.onSurface
                                                                            },
                                                                            fontWeight = if (durationMs == alarmSoundDurationMs) FontWeight.SemiBold else FontWeight.Normal
                                                                        )
                                                                    }, onClick = {
                                                                        alarmSoundDurationMenuExpanded =
                                                                            false
                                                                        alarmSoundDurationMs =
                                                                            durationMs
                                                                        alertPrefs.edit().putLong(
                                                                            "alarm_sound_duration_ms",
                                                                            durationMs
                                                                        ).apply()
                                                                        showToggleMessage("Ringtone duration set to $label")
                                                                    })
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                AnimatedVisibility(visible = effectiveNotificationsEnabled && alarmSound5mEnabled) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            10.dp
                                                        )
                                                    ) {
                                                        Spacer(Modifier.height(4.dp))

                                                        Text(
                                                            text = customRingtoneName,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(14.dp))
                                                                .background(
                                                                    if (hasPickedCustomFile) {
                                                                        if (dark) {
                                                                            MaterialTheme.colorScheme.secondary.copy(
                                                                                alpha = 0.16f
                                                                            )
                                                                        } else {
                                                                            MaterialTheme.colorScheme.primary.copy(
                                                                                alpha = 0.10f
                                                                            )
                                                                        }
                                                                    } else {
                                                                        if (dark) {
                                                                            MaterialTheme.colorScheme.onSurface.copy(
                                                                                alpha = 0.04f
                                                                            )
                                                                        } else {
                                                                            MaterialTheme.colorScheme.onSurface.copy(
                                                                                alpha = 0.025f
                                                                            )
                                                                        }
                                                                    }
                                                                )
                                                                .border(
                                                                    width = if (hasPickedCustomFile) 1.dp else 0.8.dp,
                                                                    color = if (hasPickedCustomFile) {
                                                                        if (dark) {
                                                                            MaterialTheme.colorScheme.secondary.copy(
                                                                                alpha = 0.55f
                                                                            )
                                                                        } else {
                                                                            MaterialTheme.colorScheme.primary.copy(
                                                                                alpha = 0.28f
                                                                            )
                                                                        }
                                                                    } else {
                                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                            alpha = if (dark) 0.10f else 0.08f
                                                                        )
                                                                    },
                                                                    shape = RoundedCornerShape(14.dp)
                                                                )
                                                                .padding(
                                                                    horizontal = 12.dp,
                                                                    vertical = 10.dp
                                                                ),
                                                            color = if (hasPickedCustomFile) {
                                                                if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                            } else {
                                                                primaryText
                                                            },
                                                            fontWeight = if (hasPickedCustomFile) FontWeight.SemiBold else FontWeight.Medium,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                10.dp
                                                            )
                                                        ) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    showRingtonePickerPage = true
                                                                },
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .height(44.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Custom",
                                                                    color = if (dark) Color.White else Color.Black,
                                                                    fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                                )
                                                            }

                                                            OutlinedButton(
                                                                onClick = {
                                                                    stopPreviewRingtone()
                                                                    customRingtoneUri =
                                                                        appDefaultRingtoneUri
                                                                    customRingtoneName =
                                                                        appDefaultRingtoneName
                                                                    alertPrefs.edit().putString(
                                                                        "custom_ringtone_uri",
                                                                        appDefaultRingtoneUri
                                                                    ).putString(
                                                                        "custom_ringtone_name",
                                                                        appDefaultRingtoneName
                                                                    ).apply()

                                                                    showToggleMessage("App default ringtone selected")
                                                                },
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .height(44.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Use default",
                                                                    color = if (dark) Color.White else Color.Black,
                                                                    fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(8.dp))
                                                    }
                                                }

                                                HorizontalDivider(
                                                    color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.10f
                                                    ) else premiumLightDivider
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Vibration",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = primaryText
                                                        )
                                                        Text(
                                                            text = "Vibrate strongly when a notice arrives",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = secondaryText
                                                        )
                                                    }
                                                    Switch(
                                                        checked = alarmVibrate5mEnabled,
                                                        onCheckedChange = {
                                                            alarmVibrate5mEnabled = it
                                                            alertPrefs.edit()
                                                                .putBoolean("alarm_vibrate_5m", it)
                                                                .apply()
                                                            showToggleMessage(if (it) "Vibration ON" else "Vibration OFF")
                                                        },
                                                        enabled = effectiveNotificationsEnabled,
                                                        colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                                    )
                                                }

                                                AnimatedVisibility(visible = effectiveNotificationsEnabled && alarmVibrate5mEnabled) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                    ) {
                                                        HorizontalDivider(
                                                            color = if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.10f
                                                            ) else premiumLightDivider
                                                        )

                                                        Text(
                                                            text = "Vibration duration",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = primaryText,
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                        Text(
                                                            text = "Choose how long vibration should continue",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = secondaryText
                                                        )

                                                        ExposedDropdownMenuBox(
                                                            expanded = alarmVibrateDurationMenuExpanded,
                                                            onExpandedChange = {
                                                                alarmVibrateDurationMenuExpanded =
                                                                    !alarmVibrateDurationMenuExpanded
                                                            }) {
                                                            val selectedAlarmVibrateDurationLabel =
                                                                alarmDurationOptions.firstOrNull {
                                                                    it.first == alarmVibrateDurationMs
                                                                }?.second ?: "30 sec"

                                                            OutlinedTextField(
                                                                value = selectedAlarmVibrateDurationLabel,
                                                                onValueChange = {},
                                                                readOnly = true,
                                                                singleLine = true,
                                                                modifier = Modifier
                                                                    .menuAnchor()
                                                                    .fillMaxWidth(),
                                                                shape = RoundedCornerShape(18.dp),
                                                                label = { Text("Vibration duration") },
                                                                trailingIcon = {
                                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                                        expanded = alarmVibrateDurationMenuExpanded
                                                                    )
                                                                },
                                                                colors = OutlinedTextFieldDefaults.colors(
                                                                    focusedBorderColor = if (alarmVibrateDurationMenuExpanded) (if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                                                    else MaterialTheme.colorScheme.outline.copy(
                                                                        alpha = 0.6f
                                                                    ),
                                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                                        alpha = 0.6f
                                                                    ),
                                                                    focusedTextColor = primaryText,
                                                                    unfocusedTextColor = primaryText,
                                                                    focusedLabelColor = if (alarmVibrateDurationMenuExpanded) (if (dark) Color.White else MaterialTheme.colorScheme.primary)
                                                                    else (if (dark) Color.White else secondaryText),
                                                                    unfocusedLabelColor = if (dark) Color.White else secondaryText,
                                                                    focusedContainerColor = Color.Transparent,
                                                                    unfocusedContainerColor = Color.Transparent
                                                                )
                                                            )

                                                            ExposedDropdownMenu(
                                                                expanded = alarmVibrateDurationMenuExpanded,
                                                                onDismissRequest = {
                                                                    alarmVibrateDurationMenuExpanded =
                                                                        false
                                                                },
                                                                modifier = Modifier
                                                                    .exposedDropdownSize()
                                                                    .background(MaterialTheme.colorScheme.surface)
                                                            ) {
                                                                alarmDurationOptions.forEach { (durationMs, label) ->
                                                                    DropdownMenuItem(text = {
                                                                        Text(
                                                                            text = label,
                                                                            color = if (durationMs == alarmVibrateDurationMs) {
                                                                                if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                                            } else {
                                                                                MaterialTheme.colorScheme.onSurface
                                                                            },
                                                                            fontWeight = if (durationMs == alarmVibrateDurationMs) FontWeight.SemiBold else FontWeight.Normal
                                                                        )
                                                                    }, onClick = {
                                                                        alarmVibrateDurationMenuExpanded =
                                                                            false
                                                                        alarmVibrateDurationMs =
                                                                            durationMs
                                                                        alertPrefs.edit().putLong(
                                                                            "alarm_vibrate_duration_ms",
                                                                            durationMs
                                                                        ).apply()
                                                                        showToggleMessage("Vibration duration set to $label")
                                                                    })
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                AnimatedVisibility(visible = effectiveNotificationsEnabled && alarmVibrate5mEnabled) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            10.dp
                                                        )
                                                    ) {
                                                        Spacer(Modifier.height(4.dp))

                                                        Text(
                                                            text = customVibrationPattern,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = secondaryText
                                                        )

                                                        OutlinedButton(
                                                            onClick = {
                                                                showVibrationPickerPage = true
                                                            },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(44.dp)
                                                        ) {
                                                            Text(
                                                                text = "Custom vibration",
                                                                color = if (dark) Color.White else Color.Black,
                                                                fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(Modifier.height(8.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Advanced",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = primaryText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showNotificationOptimizationPage = true
                                        },
                                    shape = RoundedCornerShape(22.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                                    border = if (dark) null else BorderStroke(
                                        1.dp, premiumLightBorder
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (dark) {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                        } else {
                                            CardSurfaceLight
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 22.dp, vertical = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Fix notification delays",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = primaryText,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                text = "Optimize battery settings for timely alerts",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = secondaryText
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Open notification optimization",
                                            tint = secondaryText,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                val versionName = remember {
                    runCatching {
                        val pm = ctx.packageManager
                        val pkg = ctx.packageName
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(
                                pkg, PackageManager.PackageInfoFlags.of(0)
                            ).versionName
                        } else {
                            @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0).versionName
                        }
                    }.getOrDefault("1.0")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                    border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dark) {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        } else {
                            CardSurfaceLight
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DIU Transport Schedule",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = primaryText
                        )

                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText
                        )

                        Text(
                            text = "Developed by Field-Chef Labs",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dark) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                LaunchedEffect(showVibrationPickerPage) {
                    if (!showVibrationPickerPage) {
                        previewingVibrationPattern = ""
                    }
                }

                if (showRingtonePickerPage) {
                    AlertDialog(onDismissRequest = {
                        stopPreviewRingtone()
                        showRingtonePickerPage = false
                    }, confirmButton = {}, dismissButton = {
                        TextButton(
                            onClick = {
                                stopPreviewRingtone()
                                showRingtonePickerPage = false
                            }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Close",
                                color = if (dark) Color.White else Color.Black,
                                fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                            )
                        }
                    }, title = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Custom ringtone",
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Preview a tone first, then select the one you want.",
                                color = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }, text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = "App default ringtone",
                                style = MaterialTheme.typography.labelLarge,
                                color = secondaryText,
                                fontWeight = FontWeight.Bold
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = if (dark) 0.dp else 8.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        clip = false
                                    ),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (dark) {
                                        if (customRingtoneUri == appDefaultRingtoneUri) MaterialTheme.colorScheme.secondary.copy(
                                            alpha = 0.16f
                                        )
                                        else MaterialTheme.colorScheme.surface
                                    } else {
                                        if (customRingtoneUri == appDefaultRingtoneUri) MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.08f
                                        )
                                        else CardSurfaceLight
                                    }
                                ),
                                border = BorderStroke(
                                    if (customRingtoneUri == appDefaultRingtoneUri) 1.5.dp else 1.dp,
                                    if (customRingtoneUri == appDefaultRingtoneUri) {
                                        if (dark) MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                        else premiumLightBorder
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = appDefaultRingtoneName,
                                        color = primaryText,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (customRingtoneUri == appDefaultRingtoneUri) {
                                        Text(
                                            text = "Selected",
                                            color = if (dark) Color(0xFF57E389) else Color(
                                                0xFF1FAA59
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (previewingUri == appDefaultRingtoneUri) {
                                        Text(
                                            text = "Playing preview",
                                            color = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "Built into the app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                playPreviewRingtone(appDefaultRingtoneUri)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(18.dp)
                                        ) {
                                            val isPlaying = previewingUri == appDefaultRingtoneUri

                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Check,
                                                contentDescription = if (isPlaying) "Stop preview" else "Play preview",
                                                tint = if (dark) Color.White else Color.Black
                                            )

                                            Spacer(Modifier.width(6.dp))

                                            Text(
                                                text = if (isPlaying) "Stop" else "Check",
                                                color = if (dark) Color.White else Color.Black,
                                                fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                stopPreviewRingtone()
                                                customRingtoneUri = appDefaultRingtoneUri
                                                customRingtoneName = appDefaultRingtoneName
                                                alertPrefs.edit().putString(
                                                    "custom_ringtone_uri",
                                                    appDefaultRingtoneUri
                                                ).putString(
                                                    "custom_ringtone_name",
                                                    appDefaultRingtoneName
                                                ).apply()

                                                showToggleMessage("App default ringtone selected")
                                                showRingtonePickerPage = false
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(18.dp)
                                        ) {
                                            Text(
                                                text = "Select",
                                                color = if (dark) Color.White else Color.Black,
                                                fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Local file",
                                style = MaterialTheme.typography.labelLarge,
                                color = secondaryText,
                                fontWeight = FontWeight.Bold
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = if (dark) 0.dp else 8.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        clip = false
                                    ),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    else premiumLightBorder
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "File Manager",
                                        color = primaryText,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Choose any audio file from your device",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryText
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            stopPreviewRingtone()
                                            showToggleMessage("Opening file manager...")
                                            showRingtonePickerPage = false
                                            customRingtonePicker.launch(arrayOf("audio/*"))
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text(
                                            text = "Browse",
                                            color = if (dark) Color.White else Color.Black,
                                            fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Preset ringtones",
                                style = MaterialTheme.typography.labelLarge,
                                color = secondaryText,
                                fontWeight = FontWeight.Bold
                            )

                            if (presetLoadError.isNotBlank()) {
                                Text(
                                    text = presetLoadError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (presetLoadedCount == 0) {
                                Text(
                                    text = "No preset ringtone found from server",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                            }
                            hostedRingtones.forEach { (ringtoneName, ringtoneUrl) ->
                                val isSelected = customRingtoneName == ringtoneName
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (dark) {
                                            if (isSelected) MaterialTheme.colorScheme.secondary.copy(
                                                alpha = 0.16f
                                            )
                                            else MaterialTheme.colorScheme.surface
                                        } else {
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.08f
                                            )
                                            else CardSurfaceLight
                                        }
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp, if (isSelected) {
                                            if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                alpha = 0.85f
                                            )
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        } else {
                                            if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.10f
                                            )
                                            else premiumLightBorder
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = ringtoneName,
                                            color = primaryText,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = "Selected",
                                                color = if (dark) Color(0xFF57E389) else Color(
                                                    0xFF1FAA59
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (previewingUri == ringtoneUrl) {
                                            Text(
                                                text = "Playing preview",
                                                color = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = when {
                                                presetBusyName == ringtoneName -> "Downloading preset..."
                                                previewingUri == ringtoneUrl -> "Preview is playing now"
                                                else -> "Preset ringtone from server"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (previewingUri == ringtoneUrl) {
                                                        stopPreviewRingtone()
                                                    } else {
                                                        scope.launch {
                                                            presetBusyName = ringtoneName
                                                            val localUri =
                                                                downloadPresetRingtoneToCache(
                                                                    ringtoneUrl, ringtoneName
                                                                )
                                                            presetBusyName = ""
                                                            if (localUri != null) {
                                                                playPreviewRingtone(ringtoneUrl)
                                                            } else {
                                                                showToggleMessage("Preset download failed: $ringtoneName")
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = previewingUri == ringtoneUrl || presetBusyName.isBlank() || presetBusyName == ringtoneName,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp),
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                val isPlaying = previewingUri == ringtoneUrl

                                                Icon(
                                                    imageVector = if (presetBusyName == ringtoneName) Icons.Filled.GraphicEq else if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Check,
                                                    contentDescription = if (isPlaying) "Stop preview" else "Play preview",
                                                    tint = if (dark) Color.White else Color.Black
                                                )

                                                Spacer(Modifier.width(6.dp))

                                                Text(
                                                    text = when {
                                                        presetBusyName == ringtoneName -> "Downloading"
                                                        isPlaying -> "Stop"
                                                        else -> "Check"
                                                    },
                                                    color = if (dark) Color.White else Color.Black,
                                                    fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        presetBusyName = ringtoneName
                                                        val localUri =
                                                            downloadPresetRingtoneToCache(
                                                                ringtoneUrl, ringtoneName
                                                            )
                                                        presetBusyName = ""
                                                        if (localUri != null) {
                                                            stopPreviewRingtone()
                                                            customRingtoneUri = localUri
                                                            customRingtoneName = ringtoneName
                                                            alertPrefs.edit().putString(
                                                                "custom_ringtone_uri",
                                                                localUri
                                                            ).putString(
                                                                "custom_ringtone_name",
                                                                ringtoneName
                                                            ).apply()

                                                            showToggleMessage("Preset selected: $ringtoneName")
                                                            showRingtonePickerPage = false
                                                        } else {
                                                            showToggleMessage("Preset download failed: $ringtoneName")
                                                        }
                                                    }
                                                },
                                                enabled = presetBusyName.isBlank() || presetBusyName == ringtoneName,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp),
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                Text(
                                                    text = if (presetBusyName == ringtoneName) "Downloading" else "Select",
                                                    color = if (dark) Color.White else Color.Black,
                                                    fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })
                }

                if (showVibrationPickerPage) {
                    AlertDialog(
                        onDismissRequest = { showVibrationPickerPage = false },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = {
                                showVibrationPickerPage = false
                            }) {
                                Text(
                                    text = "Close",
                                    color = if (dark) Color.White else Color.Black,
                                    fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                )
                            }
                        },
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Custom vibration",
                                    color = primaryText,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Check a pattern first, then tap select to save it.",
                                    color = secondaryText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "Available patterns",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = secondaryText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                listOf(
                                    "Default vibration",
                                    "Soft vibration",
                                    "Strong vibration",
                                    "Pulse vibration"
                                ).forEach { pattern ->
                                    val isSelected = customVibrationPattern == pattern
                                    val isPreviewing = previewingVibrationPattern == pattern
                                    val previewScale by animateFloatAsState(
                                        targetValue = if (isPreviewing) 1.06f else 1f,
                                        animationSpec = tween(durationMillis = 180),
                                        label = "vibration_check_scale"
                                    )

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (dark) {
                                                if (isSelected) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.16f
                                                )
                                                else MaterialTheme.colorScheme.surface
                                            } else {
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.08f
                                                )
                                                else CardSurfaceLight
                                            }
                                        ),
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp, if (isSelected) {
                                                if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.85f
                                                )
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            } else {
                                                if (dark) MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.10f
                                                )
                                                else premiumLightBorder
                                            }
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = pattern,
                                                        color = primaryText,
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Text(
                                                        text = when (pattern) {
                                                            "Soft vibration" -> "Light and short feedback"
                                                            "Strong vibration" -> "Longer and stronger feedback"
                                                            "Pulse vibration" -> "Quick pulse style feedback"
                                                            else -> "Balanced default feedback"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = secondaryText
                                                    )
                                                }
                                                if (isSelected) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            6.dp
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "Selected",
                                                            tint = if (dark) Color(0xFF57E389) else Color(
                                                                0xFF1FAA59
                                                            ),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = "Selected",
                                                            color = if (dark) Color(0xFF57E389) else Color(
                                                                0xFF1FAA59
                                                            ),
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        previewingVibrationPattern = pattern
                                                        playPreviewVibration(pattern)
                                                    }, modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            6.dp
                                                        )
                                                    ) {
                                                        val infinite =
                                                            rememberInfiniteTransition(label = "vibration_wave")
                                                        val waveScale by infinite.animateFloat(
                                                            initialValue = 1f,
                                                            targetValue = if (isPreviewing) 1.35f else 1f,
                                                            animationSpec = infiniteRepeatable(
                                                                animation = tween(700),
                                                                repeatMode = RepeatMode.Reverse
                                                            ),
                                                            label = "wave_scale"
                                                        )

                                                        Box(
                                                            modifier = Modifier.size(28.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isPreviewing) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size((20f * waveScale).dp)
                                                                        .background(
                                                                            color = if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                                                alpha = 0.25f
                                                                            )
                                                                            else MaterialTheme.colorScheme.primary.copy(
                                                                                alpha = 0.20f
                                                                            ),
                                                                            shape = RoundedCornerShape(
                                                                                50
                                                                            )
                                                                        )
                                                                )
                                                            }

                                                            Icon(
                                                                imageVector = Icons.Filled.GraphicEq,
                                                                contentDescription = "Check vibration",
                                                                tint = if (dark) Color.White else Color.Black,
                                                                modifier = Modifier.size((18f * previewScale).dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "Check",
                                                            color = if (dark) Color.White else Color.Black,
                                                            fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        customVibrationPattern = pattern
                                                        previewingVibrationPattern = ""
                                                        alertPrefs.edit().putString(
                                                            "custom_vibration_pattern", pattern
                                                        ).apply()

                                                        showToggleMessage("Vibration selected: $pattern")
                                                        showVibrationPickerPage = false
                                                    }, modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
                                                ) {
                                                    Text(
                                                        text = if (isSelected) "Selected" else "Select",
                                                        color = if (dark) Color.White else Color.Black,
                                                        fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        })
                }
            }
        }
        if (showNotificationOptimizationPage) {
            Dialog(
                onDismissRequest = { showNotificationOptimizationPage = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                            .padding(bottom = 24.dp + navBarBottomPad),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (dark) MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        ), contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SettingsApplications,
                                        contentDescription = "Notification Optimization",
                                        tint = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Notification Optimization",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "Make alerts faster and more reliable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryText
                                    )
                                }
                            }

                            Text(
                                text = "Back",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showNotificationOptimizationPage = false }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                            border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Fix your class notification delay issue",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = primaryText,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Follow these steps to make sure notifications arrive on time and the app keeps running properly in the background.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                            border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.12f
                                                )
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.BatteryChargingFull,
                                            contentDescription = "Battery optimization",
                                            tint = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Turn off battery optimization",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Let the app run in the background",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryText
                                        )
                                    }
                                }
                                Text(text = "1. Open your phone Settings", color = primaryText)
                                Text(
                                    text = "2. Go to Battery or App battery settings (name may vary)",
                                    color = primaryText
                                )
                                Text(
                                    text = "3. Find \"DIU Transport Schedule\" in the app list",
                                    color = primaryText
                                )
                                Text(
                                    text = "4. Select \"No restriction\", \"Unrestricted\" or \"Don't optimize\"",
                                    color = primaryText
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                            border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.12f
                                                )
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CleaningServices,
                                            contentDescription = "Clear cache",
                                            tint = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Clear app cache",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Remove temporary files",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryText
                                        )
                                    }
                                }

                                Text(
                                    text = "1. Open Settings > Apps or App management",
                                    color = primaryText
                                )
                                Text(
                                    text = "2. Select \"DIU Transport Schedule\"",
                                    color = primaryText
                                )
                                Text(
                                    text = "3. Open Storage or Storage & cache", color = primaryText
                                )
                                Text(text = "4. Tap Clear cache", color = primaryText)
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                            border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.12f
                                                )
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInNew,
                                            contentDescription = "Background activity",
                                            tint = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Allow background activity",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Keep the app running when not in use",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryText
                                        )
                                    }
                                }
                                Text(
                                    text = "1. Open Settings > Apps or App management",
                                    color = primaryText
                                )
                                Text(
                                    text = "2. Select \"DIU Transport Schedule\"",
                                    color = primaryText
                                )
                                Text(
                                    text = "3. Go to Battery or App battery settings",
                                    color = primaryText
                                )
                                Text(
                                    text = "4. Enable background activity / auto-start (if available)",
                                    color = primaryText
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                            border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = if (dark) MaterialTheme.colorScheme.surface else CardSurfaceLight
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (dark) MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.12f
                                                )
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInNew,
                                            contentDescription = "Open settings",
                                            tint = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Open App Settings",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Open your phone settings directly for this app",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryText
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                            )
                                            intent.data = Uri.parse("package:${ctx.packageName}")
                                            ctx.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_SETTINGS)
                                            ctx.startActivity(intent)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInNew,
                                            contentDescription = "Open settings"
                                        )
                                        Text(
                                            text = "Open App Settings",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Immutable
private data class RouteDropdownUi(
    val routeNo: String, val label: String, val compactLabel: String
)

@Composable
private fun RouteDropdownMenuItem(
    opt: RouteDropdownUi, isSelected: Boolean, dark: Boolean, onClick: () -> Unit
) {
    val selectedColor =
        if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val bgColor =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent

    DropdownMenuItem(
        contentPadding = PaddingValues(0.dp),
        interactionSource = remember { MutableInteractionSource() },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp, horizontal = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(26.dp), contentAlignment = Alignment.CenterStart
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = selectedColor
                            )
                        }
                    }

                    Spacer(Modifier.size(2.dp))

                    Text(
                        text = opt.routeNo,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.size(6.dp))

                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(1.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(1.dp)
                            )
                    )

                    Spacer(Modifier.size(6.dp))

                    Text(
                        text = opt.compactLabel,
                        style = if (isSelected) {
                            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        onClick = onClick
    )
}

private fun compactRouteEndpoints(label: String): String {
    val clean = label.trim()
    if (clean.isBlank()) return ""

    val parts =
        clean.split("->", "→", "<>", "—", "-", "|").map { it.trim() }.filter { it.isNotBlank() }

    return when {
        parts.isEmpty() -> clean
        parts.size == 1 -> parts.first()
        else -> "${parts.first()} → ${parts.last()}"
    }
}

fun compactRouteOptionLabel(routeNo: String, label: String): String {
    val routeNoTrimmed = routeNo.trim()

    if (routeNoTrimmed.equals("ALL", ignoreCase = true)) {
        return "All routes"
    }

    val compact = compactRouteEndpoints(label)

    // ⚡ Regex remove → ultra fast string check
    val suffix1 = " ($routeNoTrimmed)"
    val suffix2 = "($routeNoTrimmed)"

    return when {
        compact.endsWith(suffix1, ignoreCase = true) -> compact.dropLast(suffix1.length).trim()

        compact.endsWith(suffix2, ignoreCase = true) -> compact.dropLast(suffix2.length).trim()

        else -> compact
    }
}


// Compatibility: older Material3 versions may not include ColorScheme.surfaceColorAtElevation
private fun ColorScheme.surfaceColorAtElevation(elevation: Dp): Color {
    val base = this.surface
    if (elevation <= 0.dp) return base

    val alpha = when {
        elevation < 1.dp -> 0.04f
        elevation < 2.dp -> 0.06f
        elevation < 3.dp -> 0.08f
        elevation < 6.dp -> 0.10f
        else -> 0.12f
    }

    return this.surfaceVariant.copy(alpha = alpha).compositeOver(base)
}