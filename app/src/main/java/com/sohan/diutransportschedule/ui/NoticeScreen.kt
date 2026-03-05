package com.sohan.diutransportschedule.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Firestore Notice (text-only on Spark plan)
data class AdminNoticeUi(
    val id: String,
    val title: String,
    val body: String,
    val createdAtMs: Long,
    val releaseAtMs: Long,
    val isRead: Boolean
)

private const val PREF_NOTICES = "notice_prefs"
private const val KEY_READ_IDS = "read_ids" // StringSet

private const val KEY_NOTICES_VERSION = "notices_version" // Long

private fun readIds(ctx: Context): MutableSet<String> {
    val p = ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
    return (p.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()).toMutableSet()
}

private fun saveReadIds(ctx: Context, ids: Set<String>) {
    ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(KEY_READ_IDS, ids)
        .apply()
}

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

@Composable
fun NoticeScreen(pad: PaddingValues) {
    val ctx = LocalContext.current

    val db = remember { FirebaseFirestore.getInstance() }

    var notices by remember { mutableStateOf<List<AdminNoticeUi>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Keep local read-state
    var readSet by remember { mutableStateOf(readIds(ctx)) }

    val dark = isSystemInDarkTheme()

    val unreadCardColors = CardDefaults.cardColors(
        containerColor = if (dark)
            MaterialTheme.colorScheme.surfaceContainerHigh
        else
            MaterialTheme.colorScheme.surfaceContainerHigh
    )

    val readCardColors = CardDefaults.cardColors(
        containerColor = if (dark)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surfaceContainer
    )

    // Smaller, softer border (user asked to reduce border)
    val noticeCardBorder = BorderStroke(
        width = 0.35.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = if (dark) 0.14f else 0.10f)
    )

    // No green: unread is brighter white-ish; read is the same but with lower opacity.
    val unreadTextColor = MaterialTheme.colorScheme.onSurface
    val readTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.62f else 0.74f)
    val readSubTextColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.55f else 0.68f)

    val dateFmt = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }

    fun formatDate(ms: Long): String {
        return try {
            Instant.ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(dateFmt)
        } catch (_: Throwable) {
            ""
        }
    }

    fun applyReadState(list: List<AdminNoticeUi>): List<AdminNoticeUi> {
        return list.map { it.copy(isRead = readSet.contains(it.id)) }
    }

    // Re-apply read state when readSet changes
    LaunchedEffect(readSet) {
        notices = applyReadState(notices)
    }

    // Version-based refresh (reduced reads)
    // Reduce reads: listen only to a tiny meta doc for version changes.
    // When version changes, fetch notices once.
    fun fetchNoticesOnce() {
        db.collection("notices")
            .orderBy("createdAtMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { d ->
                    AdminNoticeUi(
                        id = d.id,
                        title = d.getString("title").orEmpty().ifBlank { "Notice" },
                        body = d.getString("body").orEmpty()
                            .ifBlank { d.getString("extractedText").orEmpty() },
                        createdAtMs = (d.getLong("createdAtMs") ?: 0L),
                        releaseAtMs = (d.getLong("releaseAtMs") ?: (d.getLong("createdAtMs") ?: 0L)),
                        isRead = false
                    )
                }

                error = null
                notices = applyReadState(list)
            }
            .addOnFailureListener { e ->
                error = e.message
            }
    }

    DisposableEffect(Unit) {
        // 1) Initial: fetch once on first entry (or if you want always show latest on open)
        //    We still keep reads low because subsequent updates depend on meta version.
        if (notices.isEmpty()) {
            fetchNoticesOnce()
        }

        // 2) Version listener: only one tiny document read on changes
        val savedVersion = readNoticesVersion(ctx)
        var reg: ListenerRegistration? = null

        reg = db.collection("meta")
            .document("notices")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    // Don't block UI; just show error if nothing loaded
                    if (notices.isEmpty()) error = e.message
                    return@addSnapshotListener
                }
                val remoteVersion = snap?.getLong("version") ?: 0L
                if (remoteVersion > savedVersion) {
                    saveNoticesVersion(ctx, remoteVersion)
                    fetchNoticesOnce()
                }
            }

        onDispose { reg?.remove() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(pad)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 26.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notice",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                IconButton(
                    onClick = {
                        // Mark all as read locally
                        val next = readSet.toMutableSet()
                        notices.forEach { next.add(it.id) }
                        readSet = next
                        saveReadIds(ctx, next)
                    }
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = "Mark all read")
                }
            }

            if (error != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = unreadCardColors,
                    border = noticeCardBorder
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Failed to load notices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = readTextColor
                        )
                    }
                }
            } else if (notices.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = unreadCardColors,
                    border = noticeCardBorder
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No notices yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Admin notices will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = readTextColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notices, key = { it.id }) { n ->
                        var open by remember(n.id) { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!n.isRead) {
                                        val next = readSet.toMutableSet()
                                        next.add(n.id)
                                        readSet = next
                                        saveReadIds(ctx, next)

                                        // Update list UI immediately
                                        notices =
                                            notices.map { if (it.id == n.id) it.copy(isRead = true) else it }
                                    }
                                    open = true
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = if (!n.isRead) unreadCardColors else readCardColors,
                            border = noticeCardBorder,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (!n.isRead) 2.dp else 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = if (!n.isRead) unreadTextColor else readTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = n.title.ifBlank { "Notice" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (!n.isRead) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (!n.isRead) unreadTextColor else readTextColor,
                                            modifier = Modifier.weight(1f)
                                        )

                                        val dateText = formatDate(n.releaseAtMs)
                                        if (dateText.isNotBlank()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = readSubTextColor,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = n.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!n.isRead) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (!n.isRead) unreadTextColor else readSubTextColor,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = if (!n.isRead) "Unread" else "Read",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (!n.isRead) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!n.isRead) unreadTextColor else readTextColor
                                )
                            }
                        }

                        if (open) {
                            Dialog(
                                onDismissRequest = { open = false },
                                properties = DialogProperties(
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = true,
                                    usePlatformDefaultWidth = false
                                )
                            ) {
                                // Full screen surface
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Top bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .padding(horizontal = 18.dp, vertical = 18.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = n.title.ifBlank { "Notice" },
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val dateText = formatDate(n.releaseAtMs)
                                                if (dateText.isNotBlank()) {
                                                    Text(
                                                        text = dateText,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = if (dark) 0.72f else 0.78f
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            TextButton(
                                                onClick = { open = false },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Text(
                                                    text = "Close",
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(
                                                alpha = if (dark) 0.16f else 0.12f
                                            )
                                        )

                                        // Body
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = n.body,
                                                style = MaterialTheme.typography.bodyLarge,
                                                lineHeight = 26.sp,
                                                letterSpacing = 0.3.sp,
                                                color = MaterialTheme.colorScheme.onBackground
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
}
