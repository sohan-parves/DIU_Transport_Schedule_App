
package com.sohan.diutransportschedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
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
// import androidx.compose.foundation.layout.calculateBottomPadding -- removed, not available
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
import androidx.compose.material3.DropdownMenu
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
import android.widget.Toast
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: HomeViewModel) {
    val dark by vm.darkMode.collectAsState()
    val selectedRoute by vm.selectedRoute.collectAsState()
    val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY

    // Keep ALL persisted as OFF. Friday should only force a temporary OFF without overwriting saved preference.
    LaunchedEffect(selectedRoute) {
        val isAll = selectedRoute.trim().equals("ALL", ignoreCase = true)
        if (isAll && vm.notificationsEnabled.value) {
            vm.setNotificationsEnabled(false)
        } else if (isAll) {
            vm.setNotificationsEnabled(false)
        }
    }

// ✅ Always use FULL route list from VM (not filtered by Home)
    val routeOptions by vm.routeOptions.collectAsState()
    val selectedRouteLabel by vm.selectedRouteLabel.collectAsState()
    val isSyncing by vm.isSyncing.collectAsState()
    val primaryText = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (dark) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant
    val view = LocalView.current
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val effectiveNotificationsEnabled = notificationsEnabled && !isFriday && !selectedRoute.trim().equals("ALL", ignoreCase = true)
    val notifyLeadMinutes by vm.notifyLeadMinutes.collectAsState()
    val navBarBottomPad = with(LocalDensity.current) {
        val bottomPx = runCatching {
            ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.bottom
        }.getOrNull() ?: 0
        bottomPx.toDp()
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val showToggleMessage: (String) -> Unit = remember(ctx) {
        { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    val alertPrefs = remember(ctx) { ctx.getSharedPreferences("notice_alert_prefs", Context.MODE_PRIVATE) }

    var customRingtoneUri by rememberSaveable {
        mutableStateOf(alertPrefs.getString("custom_ringtone_uri", null))
    }
    var customRingtoneName by rememberSaveable {
        mutableStateOf(alertPrefs.getString("custom_ringtone_name", "Default ringtone") ?: "Default ringtone")
    }
    var showRingtonePickerPage by rememberSaveable { mutableStateOf(false) }

    val playPreviewVibration: (String) -> Unit = remember(ctx) {
        { patternName ->
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            } catch (_: Throwable) {
            }
        }
    }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

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
        }
    }
    var presetBusyName by rememberSaveable { mutableStateOf("") }
    var presetLoadError by rememberSaveable { mutableStateOf("") }
    var presetLoadedCount by rememberSaveable { mutableStateOf(0) }

    suspend fun downloadPresetRingtoneToCache(remoteUrl: String, displayName: String): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val safeBase = displayName
                    .trim()
                    .ifBlank { "preset_ringtone" }
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_")

                val ext = remoteUrl
                    .substringAfterLast('/', "")
                    .substringAfterLast('.', "mp3")
                    .substringBefore('?')
                    .ifBlank { "mp3" }

                val dir = File(ctx.cacheDir, "preset_ringtones")
                if (!dir.exists()) dir.mkdirs()

                val outFile = File(dir, "$safeBase.$ext")

                if (!outFile.exists() || outFile.length() <= 0L) {
                    val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 12000
                        requestMethod = "GET"
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
                }

                outFile.toURI().toString()
            }.getOrNull()
        }
    }

    val playPreviewRingtone: (String?) -> Unit = remember(ctx, customRingtoneUri) {
        { uriString ->
            val target = uriString?.takeIf { it.isNotBlank() } ?: customRingtoneUri
            if (target.isNullOrBlank()) return@remember

            try {
                previewPlayer?.stop()
            } catch (_: Throwable) {
            }
            try {
                previewPlayer?.release()
            } catch (_: Throwable) {
            }
            previewPlayer = null

            runCatching {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
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
        }
    }

    val customRingtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val pickedName = runCatching {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull()?.ifBlank { null } ?: "Custom ringtone"

            customRingtoneUri = uri.toString()
            customRingtoneName = pickedName
            alertPrefs.edit()
                .putString("custom_ringtone_uri", customRingtoneUri)
                .putString("custom_ringtone_name", customRingtoneName)
                .apply()

            showToggleMessage("Custom ringtone selected: $pickedName")
        }
    }
    val hostedRingtones by produceState(
        initialValue = emptyList<Pair<String, String>>(),
        key1 = showRingtonePickerPage
    ) {
        if (!showRingtonePickerPage) {
            presetLoadError = ""
            presetLoadedCount = 0
            value = emptyList()
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("https://cdn.jsdelivr.net/gh/sohan-parves/DIUtransportschedule@master/assets/ringtones/ringtones.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    useCaches = false
                    setRequestProperty("Accept", "application/json,text/plain,*/*")
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
                List(arr.length()) { i ->
                    val obj = arr.getJSONObject(i)
                    obj.getString("name").trim() to obj.getString("url").trim()
                }.filter { it.first.isNotBlank() && it.second.isNotBlank() }
            }.onSuccess {
                presetLoadError = if (it.isEmpty()) "No preset ringtone found from server" else ""
                presetLoadedCount = it.size
            }.onFailure {
                presetLoadError = it.message ?: "Failed to load preset ringtones"
                presetLoadedCount = 0
            }.getOrElse { emptyList() }
        }
    }

    var alarmSound5mEnabled by rememberSaveable {
        mutableStateOf(alertPrefs.getBoolean("alarm_sound_5m", true))
    }

    var alarmVibrate5mEnabled by rememberSaveable {
        mutableStateOf(alertPrefs.getBoolean("alarm_vibrate_5m", true))
    }

    var customVibrationPattern by rememberSaveable {
        mutableStateOf(alertPrefs.getString("custom_vibration_pattern", "Default vibration") ?: "Default vibration")
    }

    var showVibrationPickerPage by rememberSaveable { mutableStateOf(false) }
    var previewingVibrationPattern by rememberSaveable { mutableStateOf("") }

    // Premium card styling (light mode)
    val premiumLightCard = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val premiumLightBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
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

    LaunchedEffect(isSyncing) {
        if (!isSyncing) {
            showReloadPopup = false
        }
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollState = rememberScrollState()
        if (showReloadPopup || isSyncing) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    color = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
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

            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineSmall,
                color = primaryText
            )

            // ---------------- Route Select ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                colors = CardDefaults.cardColors(
                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
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

                        val refreshColor = if (dark)
                            MaterialTheme.colorScheme.secondary   // green in dark mode
                        else
                            MaterialTheme.colorScheme.primary

                        // 🔒 UI-level throttle for refresh button (5 minutes)
                        var lastRefreshTapMs by rememberSaveable { mutableStateOf(0L) }
                        val canTapRefresh = !isSyncing &&
                            (System.currentTimeMillis() - lastRefreshTapMs >= 5 * 60 * 1000L)

                        IconButton(
                            onClick = {
                                lastRefreshTapMs = System.currentTimeMillis()
                                showReloadPopup = true
                                vm.refresh(showBannerIfUpdated = true)
                            },
                            enabled = canTapRefresh,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = refreshColor)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = refreshColor
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = refreshColor)
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = routeMenuExpanded,
                        onExpandedChange = { routeMenuExpanded = !routeMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRouteLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("Selected route", color = secondaryText) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        // ✅ Anchored menu (starts from the Selected route field)
                        ExposedDropdownMenu(
                            expanded = routeMenuExpanded,
                            onDismissRequest = { routeMenuExpanded = false },
                            modifier = Modifier
                                .exposedDropdownSize(true)
                                .heightIn(max = 420.dp)
                                .padding(bottom = navBarBottomPad)
                                .background(
                                    color = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard,
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            val dailyRouteOptions = routeOptions.filter { opt ->
                                val rn = opt.routeNo.trim()
                                rn.isNotBlank() && !rn.startsWith("F", ignoreCase = true)
                            }

                            dailyRouteOptions.forEachIndexed { index, opt ->

                                val isSelected = opt.routeNo == selectedRoute

                                DropdownMenuItem(
                                    contentPadding = PaddingValues(0.dp),
                                    text = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                    else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(vertical = 10.dp, horizontal = 6.dp)
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
                                                    text = if (opt.routeNo.trim().equals("ALL", ignoreCase = true)) {
                                                        "All"
                                                    } else {
                                                        opt.routeNo.trim()
                                                    },
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isSelected)
                                                        if (dark) MaterialTheme.colorScheme.secondary
                                                        else MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface,
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
                                                    text = compactRouteOptionLabel(opt.routeNo, opt.label),
                                                    style = if (isSelected)
                                                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                                    else
                                                        MaterialTheme.typography.bodyLarge,
                                                    color = if (isSelected)
                                                        if (dark) MaterialTheme.colorScheme.secondary
                                                        else MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        routeMenuExpanded = false
                                        vm.setSelectedRoute(opt.routeNo)
                                        // Save selected road for LiveMap (Map tab)
                                        val rid = opt.routeNo.trim()
                                        val fullRoadText = buildString {
                                            append(opt.routeNo.trim())
                                            val compactLabel = compactRouteOptionLabel(opt.routeNo, opt.label)
                                            if (compactLabel.isNotBlank()) {
                                                append(" — ")
                                                append(compactLabel)
                                            }
                                        }
                                        scope.launch {
                                            SelectedRoadStore.save(ctx, rid, fullRoadText)
                                        }

                                // If user picks ALL, persist notifications OFF.
                                // Friday OFF is temporary in UI only; do not overwrite saved preference.
                                val picked = opt.routeNo.trim()
                                if (picked.equals("ALL", ignoreCase = true)) {
                                    vm.setNotificationsEnabled(false)
                                }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // ✅ divider between items
                                if (index != dailyRouteOptions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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

            // ---------------- Dark Mode ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                colors = CardDefaults.cardColors(
                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
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
                        checked = dark,
                        onCheckedChange = {
                            vm.setDarkMode(it)
                            showToggleMessage(if (it) "Dark mode ON" else "Dark mode OFF")
                        },
                        colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
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
                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
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
                            checked = showUpdateBanner,
                            onCheckedChange = {
                                vm.setShowUpdateBanner(it)
                                showToggleMessage(if (it) "Update banner ON" else "Update banner OFF")
                            },
                            colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Compact cards",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Smaller padding for faster scrolling (UI only)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = compactMode,
                            onCheckedChange = {
                                vm.setCompactMode(it)
                                showToggleMessage(if (it) "Compact cards ON" else "Compact cards OFF")
                            },
                            colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }

                    HorizontalDivider(color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                color = primaryText
                            )
                            Text(
                                text = "Get alerts before start/departure time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText
                            )
                        }
                        Switch(
                            checked = effectiveNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                vm.setNotificationsEnabled(enabled)

                                if (!enabled) {
                                    alarmSound5mEnabled = false
                                    alarmVibrate5mEnabled = false
                                    alertPrefs.edit()
                                        .putBoolean("alarm_sound_5m", false)
                                        .putBoolean("alarm_vibrate_5m", false)
                                        .apply()
                                }

                                showToggleMessage(if (enabled) "Notifications ON" else "Notifications OFF")
                            },
                            enabled = !isFriday && !selectedRoute.trim().equals("ALL", ignoreCase = true),
                            colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                        )
                    }

                    AnimatedVisibility(visible = effectiveNotificationsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Notify me ${notifyLeadMinutes} minutes before",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText
                            )

                            Slider(
                                value = notifyLeadMinutes.toFloat(),
                                onValueChange = { vm.setNotifyLeadMinutes(it.toInt()) },
                                valueRange = 5f..120f,
                                steps = 22,
                                colors = if (dark) SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
                                ) else SliderDefaults.colors()
                            )

                            Text(
                                text = "Default: 30 minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryText
                            )
                        }
                        HorizontalDivider(
                            color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider
                        )
                    }
                    AnimatedVisibility(visible = effectiveNotificationsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            HorizontalDivider(color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ringtone (5 min)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "Play alarm ringtone for ~5 minutes when a notice arrives",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText
                                    )
                                }
                                Switch(
                                    checked = alarmSound5mEnabled,
                                    onCheckedChange = {
                                        alarmSound5mEnabled = it
                                        alertPrefs.edit().putBoolean("alarm_sound_5m", it).apply()
                                        showToggleMessage(if (it) "Ringtone ON" else "Ringtone OFF")
                                    },
                                    enabled = notificationsEnabled,
                                    colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                )
                            }

                            AnimatedVisibility(visible = alarmSound5mEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = customRingtoneName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { showRingtonePickerPage = true },
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

                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                customRingtoneUri = null
                                                customRingtoneName = "Default ringtone"
                                                alertPrefs.edit()
                                                    .remove("custom_ringtone_uri")
                                                    .putString("custom_ringtone_name", "Default ringtone")
                                                    .apply()

                                                showToggleMessage("Default ringtone selected")
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

                            HorizontalDivider(color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else premiumLightDivider)

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
                                        alertPrefs.edit().putBoolean("alarm_vibrate_5m", it).apply()
                                        showToggleMessage(if (it) "Vibration ON" else "Vibration OFF")
                                    },
                                    enabled = notificationsEnabled,
                                    colors = if (dark) greenSwitchColors else SwitchDefaults.colors()
                                )
                            }

                            AnimatedVisibility(visible = alarmVibrate5mEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = customVibrationPattern,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryText
                                    )

                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { showVibrationPickerPage = true },
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

                    Text(
                        text = "These settings are saved automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LaunchedEffect(showVibrationPickerPage) {
                if (!showVibrationPickerPage) {
                    previewingVibrationPattern = ""
                }
            }

            if (showRingtonePickerPage) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRingtonePickerPage = false },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showRingtonePickerPage = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Close",
                                color = if (dark) Color.White else Color.Black,
                                fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                            )
                        }
                    },
                    title = {
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
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
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
                                    containerColor = if (dark) MaterialTheme.colorScheme.surface else premiumLightCard
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

                                    androidx.compose.material3.OutlinedButton(
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
                                            if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surface
                                        } else {
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            else premiumLightCard
                                        }
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) {
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
                                                color = if (dark) Color(0xFF57E389) else Color(0xFF1FAA59),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = if (presetBusyName == ringtoneName) "Downloading preset..." else "Preset ringtone from server",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        presetBusyName = ringtoneName
                                                        val localUri = downloadPresetRingtoneToCache(ringtoneUrl, ringtoneName)
                                                        presetBusyName = ""
                                                        if (localUri != null) {
                                                            playPreviewRingtone(localUri)
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
                                                    text = if (presetBusyName == ringtoneName) "Downloading" else "Check",
                                                    color = if (dark) Color.White else Color.Black,
                                                    fontWeight = if (dark) FontWeight.Medium else FontWeight.Bold
                                                )
                                            }

                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        presetBusyName = ringtoneName
                                                        val localUri = downloadPresetRingtoneToCache(ringtoneUrl, ringtoneName)
                                                        presetBusyName = ""
                                                        if (localUri != null) {
                                                            stopPreviewRingtone()
                                                            customRingtoneUri = localUri
                                                            customRingtoneName = ringtoneName
                                                            alertPrefs.edit()
                                                                .putString("custom_ringtone_uri", localUri)
                                                                .putString("custom_ringtone_name", ringtoneName)
                                                                .apply()

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
                    }
                )
            }

            if (showVibrationPickerPage) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showVibrationPickerPage = false },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showVibrationPickerPage = false }) {
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
                                            if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surface
                                        } else {
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            else premiumLightCard
                                        }
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) {
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
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Selected",
                                                        tint = if (dark) Color(0xFF57E389) else Color(0xFF1FAA59),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "Selected",
                                                        color = if (dark) Color(0xFF57E389) else Color(0xFF1FAA59),
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
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    previewingVibrationPattern = pattern
                                                    playPreviewVibration(pattern)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val infinite = rememberInfiniteTransition(label = "vibration_wave")
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
                                                                        color = if (dark)
                                                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                                                        else
                                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                                        shape = RoundedCornerShape(50)
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

                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    customVibrationPattern = pattern
                                                    previewingVibrationPattern = ""
                                                    alertPrefs.edit()
                                                        .putString("custom_vibration_pattern", pattern)
                                                        .apply()

                                                    showToggleMessage("Vibration selected: $pattern")
                                                    showVibrationPickerPage = false
                                                },
                                                modifier = Modifier
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
                    }
                )
            }
        }
    }
}

private fun compactRouteEndpoints(label: String): String {
    val clean = label.trim()
    if (clean.isBlank()) return ""

    val parts = clean
        .split("->", "→", "<>", "—", "-", "|")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return when {
        parts.isEmpty() -> clean
        parts.size == 1 -> parts.first()
        else -> "${parts.first()} → ${parts.last()}"
    }
}

private fun compactRouteOptionLabel(routeNo: String, label: String): String {
    if (routeNo.trim().equals("ALL", ignoreCase = true)) {
        return "All routes"
    }

    val compact = compactRouteEndpoints(label)
    val routeNoTrimmed = routeNo.trim()

    return compact
        .replace(Regex("\\s*\\(${Regex.escape(routeNoTrimmed)}\\)\\s*$", RegexOption.IGNORE_CASE), "")
        .trim()
}

// Compatibility: older Material3 versions may not include ColorScheme.surfaceColorAtElevation
private fun androidx.compose.material3.ColorScheme.surfaceColorAtElevation(elevation: Dp): Color {
    // Simple, stable approximation: use Surface as base and lightly overlay SurfaceVariant
    // to create a subtle elevated look in light theme.
    val base = this.surface
    if (elevation <= 0.dp) return base

    // Higher elevation => slightly stronger overlay
    val alpha = when {
        elevation < 1.dp -> 0.04f
        elevation < 2.dp -> 0.06f
        elevation < 3.dp -> 0.08f
        elevation < 6.dp -> 0.10f
        else -> 0.12f
    }

    return this.surfaceVariant.copy(alpha = alpha).compositeOver(base)
}