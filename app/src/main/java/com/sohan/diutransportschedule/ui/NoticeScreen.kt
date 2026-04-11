package com.sohan.diutransportschedule.ui

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.sohan.diutransportschedule.sync.ACTION_NEW_NOTICE
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Firestore Notice (text-only on Spark plan)
data class AdminNoticeUi(
    val id: String,
    val title: String,
    val body: String,
    val createdAtMs: Long,
    val releaseAtMs: Long,
    val isRead: Boolean
)
private const val PREF_ADMIN_NOTICES_CACHE = "admin_notices_cache"
private const val KEY_CACHED_NOTICES_JSON = "cached_notices_json"

internal fun readCachedNotices(ctx: Context): List<AdminNoticeUi> {
    return try {
        val prefs = ctx.getSharedPreferences(PREF_ADMIN_NOTICES_CACHE, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_NOTICES_JSON, "[]") ?: "[]"
        val arr = JSONArray(raw)

        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    AdminNoticeUi(
                        id = o.optString("id", ""),
                        title = o.optString("title", "").ifBlank { "Notice" },
                        body = o.optString("body", ""),
                        createdAtMs = o.optLong("createdAtMs", 0L),
                        releaseAtMs = o.optLong("releaseAtMs", o.optLong("createdAtMs", 0L)),
                        isRead = false
                    )
                )
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun saveCachedNotices(ctx: Context, notices: List<AdminNoticeUi>) {
    try {
        val prefs = ctx.getSharedPreferences(PREF_ADMIN_NOTICES_CACHE, Context.MODE_PRIVATE)
        val arr = JSONArray()

        notices.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("body", n.body)
            o.put("createdAtMs", n.createdAtMs)
            o.put("releaseAtMs", n.releaseAtMs)
            arr.put(o)
        }

        prefs.edit()
            .putString(KEY_CACHED_NOTICES_JSON, arr.toString())
            .apply()
    } catch (_: Throwable) {
    }
}

internal const val PREF_NOTICES = "notice_prefs"
internal const val KEY_LAST_MANUAL_NOTICE_REFRESH_MS = "last_manual_refresh"
private const val KEY_READ_IDS = "read_ids" // StringSet

private const val KEY_INITIAL_SYNC_DONE = "initial_sync_done"
private const val KEY_CACHED_NOTICE_VERSION = "cached_notice_version"


fun isInitialNoticeSyncDone(ctx: Context): Boolean {
    return ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .getBoolean(KEY_INITIAL_SYNC_DONE, false)
}

private fun setInitialNoticeSyncDone(ctx: Context, done: Boolean) {
    ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_INITIAL_SYNC_DONE, done)
        .apply()
}

fun readCachedNoticeVersion(ctx: Context): Long {
    return ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .getLong(KEY_CACHED_NOTICE_VERSION, 0L)
}

fun saveCachedNoticeVersion(ctx: Context, version: Long) {
    ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_CACHED_NOTICE_VERSION, version)
        .apply()
}

fun mergeCachedNotices(ctx: Context, incoming: List<AdminNoticeUi>) {
    val existing = readCachedNotices(ctx)
    val mergedMap = LinkedHashMap<String, AdminNoticeUi>()

    for (notice in existing) {
        if (notice.id.isNotBlank()) {
            mergedMap[notice.id] = notice
        }
    }

    for (notice in incoming) {
        if (notice.id.isBlank()) continue
        val old = mergedMap[notice.id]
        mergedMap[notice.id] = if (
            old == null || maxOf(notice.releaseAtMs, notice.createdAtMs) >= maxOf(old.releaseAtMs, old.createdAtMs)
        ) {
            notice
        } else {
            old
        }
    }

    val merged = mergedMap.values
        .sortedByDescending { maxOf(it.releaseAtMs, it.createdAtMs) }

    saveCachedNotices(ctx, merged)
}

fun cacheNoticeFromPush(
    ctx: Context,
    id: String,
    title: String,
    body: String,
    createdAtMs: Long,
    releaseAtMs: Long
) {
    val finalReleaseAtMs = if (releaseAtMs > 0L) releaseAtMs else createdAtMs
    val notice = AdminNoticeUi(
        id = id,
        title = title.ifBlank { "Notice" },
        body = body,
        createdAtMs = createdAtMs,
        releaseAtMs = finalReleaseAtMs,
        isRead = false
    )
    mergeCachedNotices(ctx, listOf(notice))

    val nextVersion = readCachedNoticeVersion(ctx) + 1L
    saveCachedNoticeVersion(ctx, nextVersion)
    setInitialNoticeSyncDone(ctx, true)
}

internal const val PREF_NOTICE_HOME_POPUP = "notice_home_popup"
internal const val KEY_LAST_PUSH_NOTICE_ID = "last_push_notice_id"
private const val KEY_LAST_PUSH_TITLE = "last_push_title"
private const val KEY_LAST_PUSH_BODY = "last_push_body"
private const val KEY_LAST_PUSH_DATE_MS = "last_push_date_ms"
private const val KEY_LAST_SHOWN_NOTICE_POPUP_ON_HOME_ID = "last_shown_notice_popup_on_home_id"

internal data class PendingNoticeHomePopup(
    val id: String,
    val title: String,
    val body: String,
    val dateMs: Long
)

/** Called only from FCM when a notice is cached — drives Home “new notice” popup after cold start / resume. */
internal fun registerNoticePushForHomePopup(
    ctx: Context,
    id: String,
    title: String,
    body: String,
    createdAtMs: Long,
    releaseAtMs: Long
) {
    if (id.isBlank() || body.isBlank()) return
    val dateMs = when {
        releaseAtMs > 0L || createdAtMs > 0L -> maxOf(releaseAtMs, createdAtMs)
        else -> System.currentTimeMillis()
    }
    ctx.getSharedPreferences(PREF_NOTICE_HOME_POPUP, Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_PUSH_NOTICE_ID, id)
        .putString(KEY_LAST_PUSH_TITLE, title.ifBlank { "Notice" })
        .putString(KEY_LAST_PUSH_BODY, body)
        .putLong(KEY_LAST_PUSH_DATE_MS, dateMs)
        .apply()
}

internal fun readPendingNoticeHomePopup(ctx: Context): PendingNoticeHomePopup? {
    val p = ctx.getSharedPreferences(PREF_NOTICE_HOME_POPUP, Context.MODE_PRIVATE)
    val pushId = p.getString(KEY_LAST_PUSH_NOTICE_ID, "").orEmpty()
    val shownId = p.getString(KEY_LAST_SHOWN_NOTICE_POPUP_ON_HOME_ID, "").orEmpty()
    if (pushId.isBlank() || pushId == shownId) return null
    val body = p.getString(KEY_LAST_PUSH_BODY, "").orEmpty()
    if (body.isBlank()) return null
    return PendingNoticeHomePopup(
        id = pushId,
        title = p.getString(KEY_LAST_PUSH_TITLE, "").orEmpty().ifBlank { "Notice" },
        body = body,
        dateMs = p.getLong(KEY_LAST_PUSH_DATE_MS, 0L)
    )
}

internal fun markNoticeHomePopupShown(ctx: Context, id: String) {
    if (id.isBlank()) return
    ctx.getSharedPreferences(PREF_NOTICE_HOME_POPUP, Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_SHOWN_NOTICE_POPUP_ON_HOME_ID, id)
        .apply()
}


fun checkAndSyncNoticesFromMeta(
    ctx: Context,
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    forceBypass: Boolean = false,
    onDone: (() -> Unit)? = null,
    onError: ((String?) -> Unit)? = null,
    onVersionCompared: ((Boolean) -> Unit)? = null
) {
    db.collection("meta")
        .document("notice")
        .get()
        .addOnSuccessListener { metaSnap ->
            val remoteVersion = metaSnap.getLong("version") ?: 0L
            val cachedVersion = readCachedNoticeVersion(ctx)
            val versionChanged = remoteVersion > cachedVersion
            val needsFullSync = forceBypass || !isInitialNoticeSyncDone(ctx) || versionChanged

            if (!needsFullSync) {
                onVersionCompared?.invoke(false)
                onDone?.invoke()
                return@addOnSuccessListener
            }

            db.collection("notices")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snap ->
                    val now = System.currentTimeMillis()

                    val fetched = snap.documents.mapNotNull { doc ->
                        val title = doc.getString("title").orEmpty().ifBlank { "Notice" }
                        val body = doc.getString("body").orEmpty()
                        val createdAtMs = doc.getLong("createdAtMs") ?: 0L
                        val releaseAtMs = when {
                            doc.contains("releaseAtMs") -> doc.getLong("releaseAtMs") ?: createdAtMs
                            doc.contains("releaseDateMs") -> doc.getLong("releaseDateMs") ?: createdAtMs
                            else -> createdAtMs
                        }

                        if (body.isBlank() && title == "Notice") return@mapNotNull null

                        AdminNoticeUi(
                            id = doc.id,
                            title = title,
                            body = body,
                            createdAtMs = createdAtMs,
                            releaseAtMs = releaseAtMs,
                            isRead = false
                        )
                    }

                    mergeCachedNotices(ctx, fetched)
                    val ids = fetched.map { it.id }.filter { it.isNotBlank() }.toSet()
                    if (ids.isNotEmpty()) {
                        val existingRead = readIds(ctx)
                        saveReadIds(ctx, existingRead.filter { it in ids }.toSet())
                    }

                    saveCachedNoticeVersion(ctx, remoteVersion)
                    setInitialNoticeSyncDone(ctx, true)
                    onVersionCompared?.invoke(versionChanged)
                    onDone?.invoke()
                }
                .addOnFailureListener { e ->
                    onVersionCompared?.invoke(false)
                    onError?.invoke(e.message)
                }
        }
        .addOnFailureListener { e ->
            onVersionCompared?.invoke(false)
            onError?.invoke(e.message)
        }
}

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


@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun NoticeScreen(pad: PaddingValues) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val db = remember { FirebaseFirestore.getInstance() }

    var notices by remember { mutableStateOf(readCachedNotices(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }

    // Keep local read-state
    var readSet by remember { mutableStateOf(readIds(ctx)) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var lastPullRefreshAt by remember { mutableStateOf(0L) }

    fun applyReadState(list: List<AdminNoticeUi>): List<AdminNoticeUi> {
        return list.map { it.copy(isRead = readSet.contains(it.id)) }
    }

    fun refreshFromCache() {
        error = null
        readSet = readIds(ctx)
        notices = applyReadState(
            readCachedNotices(ctx)
                .sortedByDescending { maxOf(it.releaseAtMs, it.createdAtMs) }
        )
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            if (isRefreshing) return@rememberPullRefreshState
            val now = System.currentTimeMillis()
            if (now - lastPullRefreshAt < 3000L) return@rememberPullRefreshState
            lastPullRefreshAt = now
            isRefreshing = true
            scope.launch {
                val prefs = ctx.getSharedPreferences(PREF_NOTICES, Context.MODE_PRIVATE)
                val lastManualRefresh = prefs.getLong(KEY_LAST_MANUAL_NOTICE_REFRESH_MS, 0L)
                val now = System.currentTimeMillis()

                if (now - lastManualRefresh > 60 * 60 * 1000L) {
                    checkAndSyncNoticesFromMeta(
                        ctx = ctx,
                        db = db,
                        forceBypass = false,
                        onDone = {
                            prefs.edit().putLong(KEY_LAST_MANUAL_NOTICE_REFRESH_MS, System.currentTimeMillis()).apply()
                            readSet = readIds(ctx)
                            refreshFromCache()
                            notices = applyReadState(
                                readCachedNotices(ctx)
                                    .sortedByDescending { maxOf(it.releaseAtMs, it.createdAtMs) }
                            )
                            isRefreshing = false
                        },
                        onError = { msg ->
                            if (notices.isEmpty()) error = msg
                            isRefreshing = false
                        }
                    )
                } else {
                    readSet = readIds(ctx)
                    refreshFromCache()
                    notices = applyReadState(
                        readCachedNotices(ctx)
                            .sortedByDescending { maxOf(it.releaseAtMs, it.createdAtMs) }
                    )
                    delay(400)
                    isRefreshing = false
                }
            }
        }
    )

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


    // Re-apply read state when readSet changes
    LaunchedEffect(readSet) {
        notices = applyReadState(notices)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshFromCache()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    DisposableEffect(Unit) {
        refreshFromCache()

        val prefs = ctx.getSharedPreferences(PREF_ADMIN_NOTICES_CACHE, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CACHED_NOTICES_JSON) {
                refreshFromCache()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Extra safety: refresh immediately when push-broadcast arrives,
        // even if a device delays SharedPreferences change callbacks.
        val noticeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_NEW_NOTICE) return
                refreshFromCache()
                notices = applyReadState(
                    readCachedNotices(ctx)
                        .sortedByDescending { maxOf(it.releaseAtMs, it.createdAtMs) }
                )
                error = null
            }
        }
        val noticeFilter = IntentFilter(ACTION_NEW_NOTICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(noticeReceiver, noticeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ctx.registerReceiver(noticeReceiver, noticeFilter)
        }

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            try {
                ctx.unregisterReceiver(noticeReceiver)
            } catch (_: Throwable) {
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (dark) Color(0xFF07111F) else Color(0xFFF8FBFF),
                        if (dark) Color(0xFF040B16) else Color(0xFFFDFEFF)
                    )
                )
            )
            .padding(pad)
            .pullRefresh(pullRefreshState)
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val bubblePrimary = if (dark) Color(0xFF60A5FA).copy(alpha = 0.07f) else Color(0xFF60A5FA).copy(alpha = 0.10f)
            val bubbleSoft = if (dark) Color.White.copy(alpha = 0.020f) else Color.White.copy(alpha = 0.78f)
            val bubbleAccent = if (dark) Color(0xFF93C5FD).copy(alpha = 0.040f) else Color(0xFFBFDBFE).copy(alpha = 0.36f)
            val bubbleDeep = if (dark) Color(0xFF1D4ED8).copy(alpha = 0.055f) else Color(0xFFDBEAFE).copy(alpha = 0.30f)

            drawCircle(
                color = bubblePrimary,
                radius = size.minDimension * 0.28f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.06f)
            )
            drawCircle(
                color = bubbleDeep,
                radius = size.minDimension * 0.24f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.10f)
            )
            drawCircle(
                color = bubbleSoft,
                radius = size.minDimension * 0.12f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.34f)
            )
            drawCircle(
                color = bubbleAccent,
                radius = size.minDimension * 0.15f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.90f, size.height * 0.28f)
            )
            drawCircle(
                color = bubblePrimary,
                radius = size.minDimension * 0.11f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.56f)
            )
            drawCircle(
                color = bubbleSoft,
                radius = size.minDimension * 0.14f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.66f)
            )
            drawCircle(
                color = bubbleDeep,
                radius = size.minDimension * 0.18f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.78f)
            )
            drawCircle(
                color = bubbleAccent,
                radius = size.minDimension * 0.09f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.86f)
            )
            drawCircle(
                color = bubbleSoft,
                radius = size.minDimension * 0.07f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.80f)
            )

            val lineColor = if (dark) Color.White.copy(alpha = 0.028f) else Color(0xFFBFDBFE).copy(alpha = 0.20f)
            val step = size.height / 42f
            for (i in 0..18) {
                val y = size.height * 0.46f + i * step * 0.55f
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, y),
                    strokeWidth = 1.2f
                )
            }

            val dotColor = if (dark) Color(0xFF60A5FA).copy(alpha = 0.055f) else Color(0xFF93C5FD).copy(alpha = 0.24f)
            val dotGap = size.width / 28f
            for (row in 0..5) {
                for (col in 0..14) {
                    val x = size.width * 0.06f + col * dotGap
                    val y = size.height * 0.62f + row * dotGap * 0.72f
                    drawCircle(
                        color = dotColor,
                        radius = 1.8f,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }
        }
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
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = if (dark) Color(0xFF0B0F17) else Color(0xFFF8FBFF)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Canvas(
                                            modifier = Modifier.matchParentSize()
                                        ) {
                                            val bubbleColorPrimary = if (dark) Color(0xFF60A5FA).copy(alpha = 0.09f) else Color(0xFF60A5FA).copy(alpha = 0.11f)
                                            val bubbleColorSecondary = if (dark) Color.White.copy(alpha = 0.035f) else Color.White.copy(alpha = 0.80f)
                                            val bubbleColorAccent = if (dark) Color(0xFF93C5FD).copy(alpha = 0.05f) else Color(0xFFBFDBFE).copy(alpha = 0.36f)

                                            drawCircle(
                                                color = bubbleColorPrimary,
                                                radius = size.minDimension * 0.34f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.10f)
                                            )
                                            drawCircle(
                                                color = bubbleColorSecondary,
                                                radius = size.minDimension * 0.22f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.05f)
                                            )
                                            drawCircle(
                                                color = bubbleColorAccent,
                                                radius = size.minDimension * 0.18f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.90f, size.height * 0.34f)
                                            )
                                            drawCircle(
                                                color = bubbleColorPrimary,
                                                radius = size.minDimension * 0.12f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.10f, size.height * 0.34f)
                                            )
                                            drawCircle(
                                                color = bubbleColorSecondary,
                                                radius = size.minDimension * 0.16f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.84f, size.height * 0.62f)
                                            )
                                            drawCircle(
                                                color = bubbleColorAccent,
                                                radius = size.minDimension * 0.10f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.76f)
                                            )
                                            drawCircle(
                                                color = bubbleColorPrimary,
                                                radius = size.minDimension * 0.20f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.88f)
                                            )
                                            drawCircle(
                                                color = bubbleColorSecondary,
                                                radius = size.minDimension * 0.08f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.78f)
                                            )
                                            drawCircle(
                                                color = bubbleColorAccent,
                                                radius = size.minDimension * 0.14f,
                                                center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.92f)
                                            )
                                        }

                                        Column(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            if (dark) Color(0xFF182033) else Color(0xFFF9FBFF),
                                                            if (dark) Color(0xFF0F1523) else Color(0xFFFFFFFF)
                                                        )
                                                    )
                                                )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Surface(
                                                                modifier = Modifier.size(42.dp),
                                                                shape = CircleShape,
                                                                color = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.92f),
                                                                tonalElevation = if (dark) 0.dp else 1.dp,
                                                                shadowElevation = if (dark) 0.dp else 6.dp
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Notifications,
                                                                        contentDescription = null,
                                                                        tint = if (dark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            }

                                                            Surface(
                                                                shape = RoundedCornerShape(999.dp),
                                                                color = if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFFEEF2FF)
                                                            ) {
                                                                Text(
                                                                    text = if (!n.isRead) "New notice" else "Notice",
                                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (dark) Color.White else Color(0xFF334155)
                                                                )
                                                            }
                                                        }

                                                        Text(
                                                            text = n.title.ifBlank { "Notice" },
                                                            style = MaterialTheme.typography.headlineSmall,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        val dateText = formatDate(n.releaseAtMs)
                                                        if (dateText.isNotBlank()) {
                                                            Text(
                                                                text = dateText,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                                    alpha = if (dark) 0.68f else 0.74f
                                                                ),
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.92f),
                                                        tonalElevation = if (dark) 0.dp else 1.dp,
                                                        shadowElevation = if (dark) 0.dp else 6.dp,
                                                        modifier = Modifier.clickable { open = false }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "Close",
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }

                                            }
                                        }

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(
                                                alpha = if (dark) 0.12f else 0.10f
                                            )
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp, vertical = 16.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(28.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (dark) {
                                                        Color(0xFF111827).copy(alpha = 0.96f)
                                                    } else {
                                                        Color(0xFFFEFEFF)
                                                    }
                                                ),
                                                border = BorderStroke(
                                                    0.8.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = if (dark) 0.14f else 0.08f)
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 8.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                                ) {
                                                    Text(
                                                        text = "Announcement details",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.88f else 0.72f)
                                                    )
                                                    Text(
                                                        text = n.body,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        lineHeight = 30.sp,
                                                        letterSpacing = 0.15.sp,
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        fontWeight = FontWeight.Normal
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
        }
        
        androidx.compose.material.pullrefresh.PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = if (dark) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
        )
    }
}
