package com.sohan.diutransportschedule.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
// Removed WindowInsets, asPaddingValues, calculateTopPadding (not supported in this Compose version)
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sohan.diutransportschedule.R
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.blur
import android.content.Intent
import android.net.Uri
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
import com.sohan.diutransportschedule.sync.ACTION_NEW_NOTICE
import com.sohan.diutransportschedule.MainActivity
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.Manifest
import android.app.PendingIntent
// --- Notice notification channels ---
private const val NOTICE_CHANNEL_ID = "admin_notices_v2"
// import androidx.compose.foundation.isSystemInDarkTheme
/* ------------------ ENTRY (PUBLIC) ------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable


fun HomeScreen(
    vm: HomeViewModel,
    pad: PaddingValues
) {
    // ---- VM state ----
    val selectedRoute by vm.selectedRoute.collectAsState()
    val syncing by vm.isSyncing.collectAsState()
    val items by vm.items.collectAsState()

    val appDark by vm.darkMode.collectAsState()
    val compact by vm.compactMode.collectAsState()

    val showUpdate by vm.showUpdate.collectAsState()
    val updateMessage by vm.updateMessage.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }

    var showUpdateOverlay by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    var showNoticeAlert by remember { mutableStateOf(false) }
    var noticeTitle by remember { mutableStateOf("") }
    var noticeBody by remember { mutableStateOf("") }
    var noticeDate by remember { mutableStateOf("") }

    // ✅ Auto-open update popup when update info arrives
    LaunchedEffect(showUpdate, updateMessage) {
        if (showUpdate && updateMessage.isNotBlank()) {
            showUpdateOverlay = true
        }
    }

    // ✅ Restore previous behavior: auto-sync when Home opens
    LaunchedEffect(Unit) {
        vm.refresh(showBannerIfUpdated = true)
    }

    // Keep VM query in sync (VM items depends on it)
    LaunchedEffect(query) {
        delay(150)
        vm.setQuery(query)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Expanded state per card
    val expandedIds = rememberSaveable { mutableStateOf(setOf<String>()) }

    // Low-read Notice updates: listen only to meta/notices.version
    // ✅ Notice popup + notification when a NEW notice is published
// We listen only to the latest notice (limit 1). This is a single-document listener.
    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        var reg: ListenerRegistration? = null

        val df = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        fun formatDate(ms: Long): String {
            return try {
                if (ms <= 0L) return ""
                Instant.ofEpochMilli(ms)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(df)
            } catch (_: Throwable) { "" }
        }

        // 1) Receive instant popup while app is open (from FCM service broadcast)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val i = intent ?: return
                if (i.action != ACTION_NEW_NOTICE) return

                val t = i.getStringExtra("title").orEmpty().ifBlank { "Transport Notice" }
                val b = i.getStringExtra("body").orEmpty()
                val ms = i.getLongExtra("tsMillis", 0L)
                if (b.isBlank()) return

                noticeTitle = t
                noticeBody = b
                noticeDate = formatDate(ms)
                showNoticeAlert = true
            }
        }

        val filter = IntentFilter(ACTION_NEW_NOTICE)
        try {
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
            ctx.registerReceiver(receiver, filter)
        }

        // 2) Firestore: listen only the latest notice doc
        val prefs = ctx.getSharedPreferences("admin_notices", Context.MODE_PRIVATE)
        val lastShownKey = "last_notice_id"

        reg = db.collection("notices")
            .orderBy("createdAtMs", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                val snapshot = snap ?: return@addSnapshotListener

                val d = snapshot.documents.firstOrNull() ?: return@addSnapshotListener
                val id = d.id
                val ms = d.getLong("releaseAtMs") ?: (d.getLong("createdAtMs") ?: 0L)

                val lastId = prefs.getString(lastShownKey, "") ?: ""
                val lastTs = prefs.getLong("last_notice_ts", 0L)

                if (id == lastId && ms <= lastTs) return@addSnapshotListener

                prefs.edit()
                    .putString(lastShownKey, id)
                    .putLong("last_notice_ts", ms)
                    .apply()

                val t = (d.getString("title") ?: "").ifBlank { "Transport Notice" }
                val b = (d.getString("body") ?: "").ifBlank { d.getString("extractedText") ?: "" }
                if (b.isBlank()) return@addSnapshotListener

                // In-app popup
                noticeTitle = t
                noticeBody = b
                noticeDate = formatDate(ms)
                showNoticeAlert = true

                // System notification
                postNoticeNotificationV2(ctx, t, b)
            }

        onDispose {
            reg?.remove()
            try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showUpdateOverlay) Modifier.blur(18.dp) else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            PremiumHeader(selectedRoute = selectedRoute, syncing = syncing)

            Spacer(Modifier.height(8.dp))

            Column(Modifier.padding(horizontal = 16.dp)) {
                PremiumSearchBar(
                    value = query,
                    onChange = { query = it },
                    onClear = { query = "" }
                )

                Spacer(Modifier.height(2.dp))

                if (showUpdate && updateMessage.isNotBlank()) {
                    UpdateBanner(
                        message = updateMessage,
                        onOk = { vm.dismissUpdate() },
                        appDark = appDark,
                        onOpen = { showUpdateOverlay = true }
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            if (items.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    EmptyStateCard(isSyncing = syncing, appDark = appDark)
                    Spacer(Modifier.height(96.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = items,
                        key = { it.stableId() }
                    ) { schedule ->
                        val id = schedule.stableId()
                        val expanded = expandedIds.value.contains(id)

                        // If user is filtering (search text or route filter), keep cards expanded by default
                        val collapseWhenMany = query.isBlank() && selectedRoute.equals("ALL", ignoreCase = true)

                        PremiumAccordionScheduleCard(
                            item = schedule,
                            expanded = expanded,
                            onToggle = {
                                expandedIds.value = if (expanded) {
                                    expandedIds.value - id
                                } else {
                                    expandedIds.value + id
                                }
                            },
                            defaultCollapsedWhenMany = collapseWhenMany,
                            compact = compact,
                            appDark = appDark
                        )
                    }
                }
            }
        }

        if (showUpdateOverlay && updateMessage.isNotBlank()) {
            FullUpdateOverlay(
                title = "Update",
                message = updateMessage,
                appDark = appDark,
                onClose = { showUpdateOverlay = false },
                onOk = {
                    showUpdateOverlay = false
                    vm.dismissUpdate()
                }
            )
        }


        if (showNoticeAlert && noticeTitle.isNotBlank() && noticeBody.isNotBlank()) {
            val dark = appDark
            val btnColor = if (dark) Color.White else Color.Black

            // show only a short preview in popup
            val preview = remember(noticeBody) {
                val clean = noticeBody.trim()
                if (clean.length <= 220) clean else clean.take(220).trimEnd() + "…"
            }

            AlertDialog(
                onDismissRequest = { showNoticeAlert = false },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = noticeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (noticeDate.isNotBlank()) {
                            Text(
                                text = noticeDate,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showNoticeAlert = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}


/* ------------------ NOTICE NOTIFICATION ------------------ */

/* ------------------ NOTICE NOTIFICATION ------------------ */

private fun postNoticeNotificationV2(context: Context, title: String, body: String) {
    // ✅ Admin/Notice notifications should NOT behave like alarms
    val channelId = NOTICE_CHANNEL_ID

    // Android 13+: if notifications permission is denied, we cannot post.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    // Tap notification: open app
    val openIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

    val contentPending = if (openIntent != null) {
        PendingIntent.getActivity(
            context,
            5101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
    } else null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Normal channel (silent, no vibration)
        run {
            val ch = NotificationChannel(
                NOTICE_CHANNEL_ID,
                "Admin Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Transport notices and important updates"
                // No alarm sound / no strong vibration for notices
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setOngoing(false)

    if (contentPending != null) builder.setContentIntent(contentPending)

    // Use a fixed notification ID so only one notice notification is shown at a time
    NotificationManagerCompat.from(context).notify(1002, builder.build())
}

/* ------------------ HEADER ------------------ */

@Composable
private fun PremiumHeader(selectedRoute: String, syncing: Boolean) {

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
    )

    val today = remember {
        val fmt = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)
        LocalDate.now().format(fmt)
    }

    // Status bar height (works on older Compose versions)
    val view = LocalView.current
    val density = LocalDensity.current
    val statusTop = remember(view, density) {
        val topPx = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        with(density) { topPx.toDp() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = statusTop + 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIU Transport Schedule",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                DatePill(text = today, syncing = syncing)
            }

            val sub = if (selectedRoute == "ALL")
                "Daily Schedule • All Routes"
            else
                "Daily Schedule • Route: $selectedRoute"

            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DatePill(text: String, syncing: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (syncing) "Syncing…" else text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DayTagPill(appliesOn: String, appDark: Boolean) {
    val tag = if (appliesOn.equals("FRIDAY", ignoreCase = true)) "FRIDAY" else "DAILY"
    val dark = appDark

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (dark) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = tag,
            color = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------ SEARCH ------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumSearchBar(
    value: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    // More transparent so the content behind remains visible (like the bottom bar glass feel)
    val pillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(pillColor)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            singleLine = true,
            placeholder = { Text("Search route / stop / route no") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.70f),
                cursorColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/* ------------------ UPDATE BANNER ------------------ */

@Composable
private fun UpdateBanner(message: String, onOk: () -> Unit, appDark: Boolean, onOpen: () -> Unit) {
    val dark = appDark

    // More visible in dark mode, still clean in light mode
    val container = if (dark) {
        // a slightly elevated surface reads better on dark backgrounds
        surfaceAtElevationCompat(isDark = true, elevation = 6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val onContainer = if (dark) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // In dark mode, secondary pops better than primary on many palettes
    val accent = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    val gradient = Brush.horizontalGradient(
        colors = listOf(
            container,
            container.copy(alpha = if (dark) 0.98f else 0.92f)
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dark) 0.65f else 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .background(gradient)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent.copy(alpha = 0.85f))
            )

            Spacer(Modifier.width(10.dp))

            // Icon pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = accent
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.90f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            FilledTonalButton(
                onClick = onOk,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accent.copy(alpha = 0.14f),
                    contentColor = accent
                )
            ) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* ------------------ EMPTY ------------------ */


@Composable
private fun FullUpdateOverlay(
    title: String,
    message: String,
    appDark: Boolean,
    onClose: () -> Unit,
    onOk: () -> Unit
) {
    val dark = appDark
    val accent = if (dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    // Slightly stronger scrim so text remains readable
    val scrim = if (dark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = accent)
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                // Full message (no truncation)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onOk,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accent.copy(alpha = 0.14f),
                            contentColor = accent
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("OK", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(isSyncing: Boolean, appDark: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No schedule found", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isSyncing) "Syncing… please wait"
                else "Pull down to refresh or change filter in Profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
            )
        }
    }
}

/* ------------------ CARD ------------------ */

@Composable
private fun PremiumAccordionScheduleCard(
    item: UiSchedule,
    expanded: Boolean,
    onToggle: () -> Unit,
    defaultCollapsedWhenMany: Boolean,
    compact: Boolean,
    appDark: Boolean
) {
    val dark = appDark
    val cardColor = if (dark) surfaceAtElevationCompat(isDark = true, elevation = 2f) else MaterialTheme.colorScheme.surface
    val cardBorder = if (dark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    val lightText = Color.Black
    val lightSubText = Color.Black
    val actionGreen = Color(0xFF00C853) // green for dark mode action

    val key = remember(item) { item.stableId() }

    val stops = remember(key) { extractStops(item.routeDetails) }
    val parts = remember(key) { parseRouteParts(item.routeDetails) }

    val startOne = remember(item.startTimes) { item.startTimes.firstOrNull()?.trim().orEmpty() }
    val depOne =
        remember(item.departureTimes) { item.departureTimes.firstOrNull()?.trim().orEmpty() }

    val rightLabel by remember(startOne, depOne) {
        derivedStateOf {
            when {
                startOne.isNotBlank() -> "Start • $startOne"
                depOne.isNotBlank() -> "Dep • $depOne"
                else -> ""
            }
        }
    }

    val summaryTimes by remember(startOne, depOne) {
        derivedStateOf {
            buildString {
                if (startOne.isNotBlank()) append("Start: $startOne")
                if (depOne.isNotBlank()) {
                    if (isNotEmpty()) append("  •  ")
                    append("Dep: $depOne")
                }
            }
        }
    }

    val viaPreview by remember(parts.via) {
        derivedStateOf {
            if (parts.via.isBlank()) "" else {
                val list = parts.via.split(" • ").filter { it.isNotBlank() }
                val show = list.take(3)
                val extra = list.size - show.size
                if (extra > 0) show.joinToString(" • ") + "  (+$extra more)" else show.joinToString(" • ")
            }
        }
    }

    val viaText by remember(parts.via, expanded, defaultCollapsedWhenMany) {
        derivedStateOf {
            if (parts.via.isBlank()) "" else {
                // expanded হলে পুরো via দেখাবে, collapsed হলে preview
                if (expanded || !defaultCollapsedWhenMany) parts.via else viaPreview
            }
        }
    }


    // Hide route/stops by default; user can tap to show
    var showRoute by rememberSaveable(key) { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, RoundedCornerShape(22.dp)) else Modifier
            )
            .clickable {
                // if user is collapsing, hide route details next time
                if (expanded) showRoute = false
                onToggle()
            },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (compact) 4.dp else 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                RouteBadgeSmall(item.routeNo, appDark = dark)
                Spacer(Modifier.width(8.dp))
                DayTagPill(appliesOn = item.appliesOn, appDark = dark)
                Spacer(Modifier.width(10.dp))

                // No main column here; FROM/TO will go below the road name

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    if (rightLabel.isNotBlank()) TimePill(text = rightLabel, appDark = dark)

                    if (defaultCollapsedWhenMany) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (expanded) "Hide ▲" else "Show ▼",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (dark) Color.White else Color.Black,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
            )

            Spacer(Modifier.height(2.dp))

            val stopsList = item.routeDetails
                .replace("<>", "→")
                .split("→")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (stopsList.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", modifier = Modifier.padding(end = 6.dp))
                    Text(
                        text = stopsList.first(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dark) MaterialTheme.colorScheme.onSurface else lightText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (stopsList.size > 1) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏁", modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = stopsList.last(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dark) MaterialTheme.colorScheme.onSurface else lightText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (viaText.isNotBlank()) {
                Text(
                    text = "Via: $viaText",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else lightSubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!expanded && defaultCollapsedWhenMany) {
                val small = stops.take(2)
                if (small.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        small.forEach { s -> MiniStopRow(title = s) }
                        if (stops.size > 2) {
                            Text(
                                text = "+ ${stops.size - 2} more stops (tap to expand)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dark) Color.White.copy(alpha = 0.88f) else lightSubText,
                            )
                        }
                        if (startOne.isNotBlank() || depOne.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (startOne.isNotBlank()) {
                                    Text(
                                        text = "Start: $startOne",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                if (depOne.isNotBlank()) {
                                    Text(
                                        text = "Dep: $depOne",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
                return@ElevatedCard
            }

            AnimatedVisibility(
                visible = expanded || !defaultCollapsedWhenMany,
                enter = fadeIn(tween(110)) + expandVertically(tween(140)),
                exit = fadeOut(tween(90)) + shrinkVertically(tween(120))
            ) {
                Column {
                    // ✅ Main focus: show Times first
                    TimesSection(
                        startTimes = item.startTimes,
                        departureTimes = item.departureTimes,
                        compact = compact,
                        appDark = dark
                    )

                    Spacer(Modifier.height(10.dp))

                    // Toggle for route details / stops
                    TextButton(
                        onClick = { showRoute = !showRoute },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (showRoute) "Hide route details ▲" else "Show route details ▼",
                            color = if (dark) actionGreen else Color.Black
                        )
                    }

                    AnimatedVisibility(
                        visible = showRoute,
                        enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                        exit = fadeOut(tween(90)) + shrinkVertically(tween(140))
                    ) {
                        Column {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                            )
                            Spacer(Modifier.height(8.dp))

                            if (stops.isEmpty()) {
                                Text(
                                    item.routeDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else lightSubText,
                                    maxLines = 12,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    stops.forEachIndexed { i, stop ->
                                        TimelineRowPolished(
                                            title = stop,
                                            isFirst = i == 0,
                                            isLast = i == stops.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------ Timeline Row ------------------ */

@Composable
private fun TimelineRowPolished(title: String, isFirst: Boolean, isLast: Boolean) {
    val dotColor = when {
        isFirst -> Color(0xFFE53935)
        isLast -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.secondary
    }
    val lineColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
    val textColor = if (isLast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(12.dp)
                        .background(lineColor)
                )
            } else Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(12.dp)
                        .background(lineColor)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------ Mini + Summary ------------------ */

@Composable
private fun MiniStopRow(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SummaryTimesPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------ Times (FIXED) ------------------ */

@Composable
private fun TimesSection(
    startTimes: List<String>,
    departureTimes: List<String>,
    compact: Boolean,
    appDark: Boolean
) {
    val dark = appDark
    val timesCardColor =
        if (dark) surfaceAtElevationCompat(isDark = true, elevation = 1f)
        else MaterialTheme.colorScheme.surface

    val timesBorder =
        if (dark) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = timesCardColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (timesBorder != null) Modifier.border(timesBorder, RoundedCornerShape(18.dp)) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Text(
                text = "Times",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (dark) MaterialTheme.colorScheme.onSurface else Color.Black
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

            TimesBlock(
                title = "Start Times",
                times = startTimes,
                multiline = false,
                appDark = dark
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))

            TimesBlock(
                title = "Departure Times",
                times = departureTimes,
                multiline = true,
                appDark = dark
            )
        }
    }
}

@Composable
private fun TimesBlock(
    title: String,
    times: List<String>,
    multiline: Boolean,
    appDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (appDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (
                title.equals("Start Times", ignoreCase = true) ||
                title.equals("Departure Times", ignoreCase = true)
            ) {
                Text(
                    text = "Remaining Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
        }

        if (times.isEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
            )
        } else {
            TimeChipsRow(times, multiline = multiline, appDark = appDark)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeChipsRow(times: List<String>, multiline: Boolean, appDark: Boolean) {
    val clean = remember(times) {
        times.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    if (clean.isEmpty()) return

    // ✅ light = Black, dark = White (app toggle অনুযায়ী)
    val chipTextColor = if (appDark) Color.White else Color.Black

    // Recompose every second so "expired" styling and countdown stay accurate
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = LocalTime.now()
        }
    }

    fun splitTimeAndNote(raw: String): Pair<String, String?> {
        // Prefer explicit newline split first (your data often has time + note on next line)
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size >= 2) return lines.first() to lines.drop(1).joinToString(" ")

        // Fallback: try to extract a time like 7:20 am / 7:20AM / 7:20:00 pm
        val r = raw.trim()
        val regex = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[APap][Mm])")
        val m = regex.find(r)
        return if (m != null) {
            val time = m.value.trim().replace(Regex("\\s+"), " ")
            val note = r.replace(m.value, "").trim().trimStart('-', '—').trim()
            if (note.isBlank()) time to null else time to note
        } else {
            r to null
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        clean.forEach { t ->
            val (timeText, noteText) = splitTimeAndNote(t)
            val parsed = parseLocalTimeOrNull(timeText)
            val expired = parsed?.isBefore(now) == true

            // Remaining time countdown (right side). If time is over => 00:00
            val remainingText = remember(parsed, now) {
                if (parsed == null) null
                else {
                    val seconds = try {
                        java.time.Duration.between(now, parsed).seconds
                    } catch (_: Throwable) {
                        0L
                    }
                    val safe = if (seconds <= 0) 0L else seconds
                    val h = safe / 3600
                    val m = (safe % 3600) / 60
                    val s = safe % 60
                    String.format(Locale.ENGLISH, "%02d:%02d:%02d", h, m, s)
                }
            }

            // When expired: reduce opacity so it's obvious time is over
            val textAlpha = if (expired) 0.45f else 1f
            val noteAlpha = if (expired) 0.28f else 0.65f

            val baseContainer = if (appDark) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }

            val container = if (expired) baseContainer.copy(alpha = baseContainer.alpha * 0.45f) else baseContainer
            val border = if (expired) {
                (if (appDark)
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                ).copy(alpha = 0.22f)
            } else {
                if (appDark)
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }

            AssistChip(
                onClick = {},
                modifier = (if (multiline) Modifier.fillMaxWidth() else Modifier)
                    .heightIn(min = if (multiline) 72.dp else 54.dp),
                label = {
                    Box(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = if (multiline) 12.dp else 10.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = chipTextColor.copy(alpha = textAlpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (remainingText != null) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = remainingText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = chipTextColor.copy(alpha = if (expired) 0.40f else 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }

                            if (noteText != null && noteText.isNotBlank()) {
                                Text(
                                    text = noteText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = chipTextColor.copy(alpha = noteAlpha),
                                    maxLines = if (multiline) 3 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(999.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = container,
                    labelColor = chipTextColor.copy(alpha = textAlpha)
                ),
                border = BorderStroke(
                    1.dp,
                    border
                )
            )
        }
    }
}

/* ------------------ Helpers ------------------ */

@Composable
private fun RouteBadgeSmall(routeNo: String, appDark: Boolean) {
    val dark = appDark

    val bg = if (dark) MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)

    val fg = if (dark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(routeNo, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
private fun TimePill(text: String, appDark: Boolean) {
    val dark = appDark

    val bg = if (dark) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.90f)

    val fg = if (dark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------ Extract + Parts ------------------ */

private fun hasPostNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private const val ADMIN_MSG_CHANNEL_ID = "admin_updates"

private fun showAdminMessageNotification(context: Context, message: String) {
    val appContext = context.applicationContext

    // Create channel (Android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            ADMIN_MSG_CHANNEL_ID,
            "Admin messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Admin updates and announcements"
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    // NOTE: For Android 13+ you must already have POST_NOTIFICATIONS permission granted.
    val notification = NotificationCompat.Builder(appContext, ADMIN_MSG_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("DIU Transport Schedule")
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(appContext)
        .notify(2001, notification)
}

private fun parseLocalTimeOrNull(raw: String): LocalTime? {
    // Accept formats like "7:20 AM", "7:20AM", "07:20 am", and optional seconds "7:20:00 PM"
    val r = raw.trim()
    val m = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[APap][Mm])").find(r) ?: return null
    val token = m.value
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("(?i)(\\d)(am|pm)$"), "$1 $2")
        .uppercase(Locale.ENGLISH)

    return try {
        val hasSeconds = token.contains(":") && token.count { it == ':' } == 2
        val fmt = if (hasSeconds) {
            java.time.format.DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
        } else {
            java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        }
        LocalTime.parse(token, fmt)
    } catch (_: Throwable) {
        null
    }
}

private fun extractStops(details: String): List<String> {
    return details
        .replace("<>", " > ")
        .replace("→", " > ")
        .replace("—", " > ")
        .replace("-", " > ")
        .replace(">", " > ")
        .replace("<", " > ")
        .split(" > ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private data class RouteParts(val from: String, val to: String, val via: String)

private fun parseRouteParts(details: String): RouteParts {
    val stops = extractStops(details)
    return when {
        stops.size >= 2 -> {
            val from = stops.first()
            val to = stops.last()
            val mid = stops.drop(1).dropLast(1)
            val via = if (mid.isEmpty()) "" else mid.joinToString(" • ")
            RouteParts(from, to, via)
        }

        stops.size == 1 -> RouteParts(stops[0], "", "")
        else -> RouteParts("", "", "")
    }
}

/* ------------------ Stable ID ------------------ */

private fun UiSchedule.stableId(): String {
    return "${routeNo}__${routeName}__${routeDetails.hashCode()}"
}

/* ------------------ Surface Elevation Helper (Compat) ------------------ */

@Composable
private fun surfaceAtElevationCompat(isDark: Boolean, elevation: Float): Color {
    val base = MaterialTheme.colorScheme.surface
    val lifted = MaterialTheme.colorScheme.surfaceVariant
    val t = (elevation / 12f).coerceIn(0f, 1f)
    return if (isDark) {
        lerp(base, lifted, 0.35f + 0.35f * t)
    } else {
        lerp(base, lifted, 0.15f + 0.20f * t)
    }
}
// ---- Notice in-app alert + notification helpers ----
private const val PREF_NOTICES = "notice_prefs"
private const val ADMIN_UPDATES_CHANNEL_ID = "admin_updates"

private const val KEY_NOTICES_VERSION = "notices_version" // Long
private const val KEY_LAST_NOTICE_ID = "last_notice_id"    // String


private fun readNoticesVersion(ctx: Context): Long {
    val p = ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
    return p.getLong(KEY_NOTICES_VERSION, 0L)
}

private fun saveNoticesVersion(ctx: Context, v: Long) {
    ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_NOTICES_VERSION, v)
        .apply()
}

private fun readLastNoticeId(ctx: Context): String {
    val p = ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
    return p.getString(KEY_LAST_NOTICE_ID, "") ?: ""
}

private fun saveLastNoticeId(ctx: Context, id: String) {
    ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LAST_NOTICE_ID, id)
        .apply()
}

private fun ensureAdminUpdatesChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(ADMIN_UPDATES_CHANNEL_ID) != null) return

    val ch = NotificationChannel(
        ADMIN_UPDATES_CHANNEL_ID,
        "Admin Updates",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Admin notices and important updates"
        enableVibration(true)
        setShowBadge(true)
    }
    nm.createNotificationChannel(ch)
}

private fun postNoticeNotification(context: Context, title: String, body: String) {
    // Channel (Android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(NOTICE_CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                NOTICE_CHANNEL_ID,
                "Admin Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Transport notices and important updates"
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    // Android 13+ permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val openIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

    val pending = if (openIntent != null) {
        PendingIntent.getActivity(
            context,
            4101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
    } else null

    val shortBody = body.trim().replace(Regex("\\s+"), " ")
    val shown = if (shortBody.length > 160) shortBody.take(160) + "…" else shortBody

    val n = NotificationCompat.Builder(context, NOTICE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(shown)
        .setStyle(NotificationCompat.BigTextStyle().bigText(shortBody))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    if (pending != null) n.setContentIntent(pending)

    NotificationManagerCompat.from(context)
        .notify(("notice:" + title + shown).hashCode(), n.build())
}
@Composable
private fun NoticePopupWatcher() {
    val ctx = LocalContext.current

    var show by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    // gate so popup shows only once per new notice
    val gatePrefs = remember(ctx) {
        ctx.getSharedPreferences("notice_popup_gate", Context.MODE_PRIVATE)
    }

    fun formatTs(tsMillis: Long): String {
        return try {
            val z = ZoneId.systemDefault()
            val dt = Instant.ofEpochMilli(tsMillis).atZone(z)
            DateTimeFormatter.ofPattern("d MMM yyyy • h:mm a", Locale.ENGLISH).format(dt)
        } catch (_: Throwable) {
            ""
        }
    }

    fun maybeShow(id: String, t: String, b: String, ts: Long) {
        if (id.isBlank() || b.isBlank()) return

        val lastShown = gatePrefs.getString("last_shown_id", "") ?: ""
        if (id == lastShown) return

        // mark shown first to avoid loops
        gatePrefs.edit().putString("last_shown_id", id).apply()

        title = t.ifBlank { "Transport Notice" }
        body = b
        dateText = if (ts > 0L) formatTs(ts) else ""
        show = true

        // system notification too
        postNoticeNotification(ctx, title, body)
    }

    // ✅ A) Firestore: listen latest notice (app open থাকলে সাথে সাথে popup)
    DisposableEffect(ctx) {
        val db = FirebaseFirestore.getInstance()

        val reg = db.collection("notices")
            .orderBy("createdAtMs", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                val d = snap?.documents?.firstOrNull() ?: return@addSnapshotListener

                val id = d.id
                val t = d.getString("title").orEmpty().ifBlank { "Transport Notice" }
                val b = d.getString("body").orEmpty()
                    .ifBlank { d.getString("extractedText").orEmpty() }

                // Prefer releaseAtMs (admin-set) else createdAtMs
                val ts = d.getLong("releaseAtMs") ?: (d.getLong("createdAtMs") ?: 0L)

                maybeShow(id = id, t = t, b = b, ts = ts)
            }

        onDispose { reg.remove() }
    }

    // ✅ B) Fallback: FCM service যদি SharedPreferences এ লেখে, সেটাও ধরবে
    DisposableEffect(ctx) {
        val prefs = ctx.getSharedPreferences("admin_notices", Context.MODE_PRIVATE)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != "json") return@OnSharedPreferenceChangeListener
            try {
                val raw = prefs.getString("json", "[]") ?: "[]"
                val arr = org.json.JSONArray(raw)
                if (arr.length() == 0) return@OnSharedPreferenceChangeListener

                val o = arr.getJSONObject(0)
                val id = o.optString("id", "")
                val t = o.optString("title", "").ifBlank { "Transport Notice" }
                val b = o.optString("body", "")
                val ts = o.optLong("tsMillis", 0L)

                maybeShow(id = id, t = t, b = b, ts = ts)
            } catch (_: Throwable) {}
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (show) {
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val btnColor = if (dark) Color.White else Color.Black

        val preview = remember(body) {
            val clean = body.trim()
            if (clean.length <= 220) clean else clean.take(220).trimEnd() + "…"
        }

        AlertDialog(
            shape = RoundedCornerShape(20.dp),
            onDismissRequest = { show = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (dateText.isNotBlank()) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "View চাপলে Notice সেকশনে গিয়ে পুরোটা দেখতে পারবেন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        show = false
                        val i = Intent(ctx, com.sohan.diutransportschedule.MainActivity::class.java).apply {
                            putExtra(com.sohan.diutransportschedule.MainActivity.EXTRA_OPEN_NOTICE, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        ctx.startActivity(i)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
                ) {
                    Text("View", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { show = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
                ) {
                    Text("Close")
                }
            }
        )
    }
}