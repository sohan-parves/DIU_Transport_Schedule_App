package com.sohan.diutransportschedule.ui
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation

import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.map
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import androidx.compose.foundation.isSystemInDarkTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sohan.diutransportschedule.R
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import kotlin.math.max

sealed class OfflineState {
    data object NotDownloaded : OfflineState()
    data class Downloading(val progress: Int) : OfflineState()
    data class Ready(val filePath: String) : OfflineState()
    data class Failed(val message: String) : OfflineState()
}

private val Context.dataStore by preferencesDataStore("transport_prefs")

object SelectedRoadStore {
    // NEW key (string) — use this going forward
    private val KEY_ROUTE_ID_STR = stringPreferencesKey("selected_route_id_str")
    private val KEY_ROUTE_TEXT_STR = stringPreferencesKey("selected_route_text_str")

    // OLD key (long) — may exist from earlier builds
    private val KEY_ROUTE_ID_LONG = longPreferencesKey("selected_route_id")

    fun routeIdFlow(ctx: Context) =
        ctx.dataStore.data.map { prefs ->
            // Prefer new string value
            val s = prefs[KEY_ROUTE_ID_STR]
            if (!s.isNullOrBlank()) return@map s

            // Fallback: old long value (migrate by returning as string)
            val old = prefs[KEY_ROUTE_ID_LONG]
            if (old != null) old.toString() else ""
        }

    fun routeTextFlow(ctx: Context) =
        ctx.dataStore.data.map { prefs ->
            prefs[KEY_ROUTE_TEXT_STR] ?: ""
        }

    suspend fun save(ctx: Context, routeId: String, routeText: String = "") {
        val cleaned = routeId.trim()
        val cleanedText = routeText.trim()
        ctx.dataStore.edit {
            it[KEY_ROUTE_ID_STR] = cleaned
            it[KEY_ROUTE_TEXT_STR] = cleanedText
            // Remove old long key to prevent future type-cast crashes
            it.remove(KEY_ROUTE_ID_LONG)
        }
    }
}

// --- Route map in-memory cache model and cache object ---
private data class CachedRouteMapData(
    val routeNo: String,
    val routeName: String,
    val routeDetails: String,
    val routePoints: List<GeoPoint>,
    val routePointLabels: List<String>,
    val routeStopMarkerPoints: List<GeoPoint>,
    val routeStopMarkerLabels: List<String>,
    val hasRealRoadPolyline: Boolean
)


private object RouteMapMemoryCache {
    private val cache = linkedMapOf<String, CachedRouteMapData>()

    fun get(routeNo: String): CachedRouteMapData? = cache[routeNo.trim().uppercase()]

    fun put(data: CachedRouteMapData) {
        cache[data.routeNo.trim().uppercase()] = data
    }

    fun remove(routeNo: String) {
        cache.remove(routeNo.trim().uppercase())
    }
}

private object RouteScheduleMemoryCache {
    private var cachedEntries: List<Map<*, *>>? = null

    fun get(): List<Map<*, *>>? = cachedEntries

    fun put(entries: List<Map<*, *>>) {
        cachedEntries = entries
    }

    fun clear() {
        cachedEntries = null
    }
}

@Composable
fun LiveMapScreen() {
    val ctx = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // --- Permission state & launcher
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    val routeId by SelectedRoadStore.routeIdFlow(ctx).collectAsState(initial = "")
    val shouldPromptRoadSelection = routeId.isBlank() || routeId.trim().equals("ALL", ignoreCase = true)
    val routeText by SelectedRoadStore.routeTextFlow(ctx).collectAsState(initial = "")
    val style = if (isDark) "dark" else "light"

    var routePoints by remember(routeId) { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var routePointLabels by remember(routeId) { mutableStateOf<List<String>>(emptyList()) }
    var routeStopMarkerPoints by remember(routeId) { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var routeStopMarkerLabels by remember(routeId) { mutableStateOf<List<String>>(emptyList()) }
    var routeLoading by remember(routeId) { mutableStateOf(false) }
    var routeLoadError by remember(routeId) { mutableStateOf("") }
    var routeDetails by remember(routeId) { mutableStateOf("") }
    var routeNameFromDb by remember(routeId) { mutableStateOf("") }
    var routeHasRealRoadPolyline by remember(routeId) { mutableStateOf(false) }

    val offlineFile = remember(style) {
        File(ctx.filesDir, "offline/route_dhaka_transport_${style}.mbtiles")
    }

    var offlineState by remember(style) {
        mutableStateOf<OfflineState>(
            if (offlineFile.exists()) OfflineState.Ready(offlineFile.absolutePath)
            else OfflineState.NotDownloaded
        )
    }
    var lastDoneBytes by remember { mutableStateOf(0L) }
    var lastTotalBytes by remember { mutableStateOf(-1L) }

    val workManager = remember { WorkManager.getInstance(ctx) }
    val firestore = remember { FirebaseFirestore.getInstance() }

    suspend fun geocodeRoutePoints(rawText: String): Pair<List<GeoPoint>, List<String>> {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return emptyList<GeoPoint>() to emptyList()

        val normalized = cleaned
            .replace("\n", " ")
            .replace("->", "<>")
            .replace("=>", "<>")
            .replace("＞", ">")

        val parts = normalized
            .split(Regex("\\s*<>\\s*|\\s*>\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (parts.isEmpty()) return emptyList<GeoPoint>() to emptyList()

        val geocoder = Geocoder(ctx, Locale.getDefault())
        val foundPoints = mutableListOf<GeoPoint>()
        val foundLabels = mutableListOf<String>()

        for (part in parts) {
            val queries = listOf(
                "$part, Dhaka, Bangladesh",
                "$part, Savar, Dhaka, Bangladesh",
                "$part, Ashulia, Dhaka, Bangladesh",
                "$part, Uttara, Dhaka, Bangladesh",
                "$part, Mirpur, Dhaka, Bangladesh",
                "$part, Bangladesh",
                part
            )

            var point: GeoPoint? = null
            for (query in queries) {
                point = try {
                    withContext(Dispatchers.IO) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            var gp: GeoPoint? = null
                            val latch = java.util.concurrent.CountDownLatch(1)
                            geocoder.getFromLocationName(query, 1) { addresses ->
                                val a = addresses.firstOrNull()
                                if (a != null) gp = GeoPoint(a.latitude, a.longitude)
                                latch.countDown()
                            }
                            latch.await()
                            gp
                        } else {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocationName(query, 1)
                                ?.firstOrNull()
                                ?.let { GeoPoint(it.latitude, it.longitude) }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (point != null) break
            }

            if (point != null) {
                foundPoints.add(point)
                foundLabels.add(part)
            }
        }

        return foundPoints to foundLabels
    }

    fun normalizeRouteNo(value: String): String {
        return value.trim().uppercase(Locale.getDefault())
    }

    fun collectRouteEntries(node: Any?): List<Map<*, *>> {
        return when (node) {
            is Map<*, *> -> {
                val asRoute = if (
                    node.containsKey("routeNo") ||
                    node.containsKey("routeName") ||
                    node.containsKey("routeDetails") ||
                    node.containsKey("routeStops") ||
                    node.containsKey("routePolyline") ||
                    node.containsKey("routeRoadPolyline")
                ) listOf(node) else emptyList()

                asRoute + node.values.flatMap { collectRouteEntries(it) }
            }
            is List<*> -> node.flatMap { collectRouteEntries(it) }
            else -> emptyList()
        }
    }

    fun extractGeoPointsFromPolyline(raw: Any?): List<GeoPoint> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val lat = (map["lat"] as? Number)?.toDouble()
            val lng = (map["lng"] as? Number)?.toDouble()
            if (lat != null && lng != null) GeoPoint(lat, lng) else null
        }
    }

    fun extractGeoPointsFromStops(raw: Any?): List<GeoPoint> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val lat = (map["lat"] as? Number)?.toDouble()
            val lng = (map["lng"] as? Number)?.toDouble()
            if (lat != null && lng != null) GeoPoint(lat, lng) else null
        }
    }

    fun extractStopLabelsFromStops(raw: Any?): List<String> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            map["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun extractStopLabelsFromStopNames(raw: Any?): List<String> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { it?.toString()?.trim()?.takeIf { name -> name.isNotBlank() } }
    }

    // Snap a polyline to the road network using OSRM public API, segment by segment
    suspend fun snapPolylineToRoad(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size < 2) return points

        suspend fun routeSegment(a: GeoPoint, b: GeoPoint): List<GeoPoint> {
            return try {
                val coords = "${a.longitude},${a.latitude};${b.longitude},${b.latitude}"
                val url = "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson"

                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(text)
                val routes = json.optJSONArray("routes") ?: return listOf(a, b)
                if (routes.length() == 0) return listOf(a, b)

                val geometry = routes.getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONArray("coordinates")

                val snapped = mutableListOf<GeoPoint>()
                for (i in 0 until geometry.length()) {
                    val pair = geometry.getJSONArray(i)
                    val lng = pair.getDouble(0)
                    val lat = pair.getDouble(1)
                    snapped.add(GeoPoint(lat, lng))
                }
                if (snapped.isNotEmpty()) snapped else listOf(a, b)
            } catch (_: Exception) {
                listOf(a, b)
            }
        }

        val merged = mutableListOf<GeoPoint>()
        for (i in 0 until points.lastIndex) {
            val seg = routeSegment(points[i], points[i + 1])
            if (seg.isEmpty()) continue
            if (merged.isEmpty()) {
                merged.addAll(seg)
            } else {
                // Avoid duplicating the segment join point
                merged.addAll(seg.drop(1))
            }
        }

        return if (merged.isNotEmpty()) merged else points
    }

    LaunchedEffect(routeId) {
        if (routeId.isBlank()) {
            routePoints = emptyList()
            routePointLabels = emptyList()
            routeStopMarkerPoints = emptyList()
            routeStopMarkerLabels = emptyList()
            routeLoading = false
            routeLoadError = ""
            routeDetails = ""
            routeNameFromDb = ""
            routeHasRealRoadPolyline = false
            return@LaunchedEffect
        }

        routeLoading = true
        routeLoadError = ""
        routeDetails = ""
        routeNameFromDb = ""
        routePoints = emptyList()
        routePointLabels = emptyList()
        routeStopMarkerPoints = emptyList()
        routeStopMarkerLabels = emptyList()
        routeHasRealRoadPolyline = false

        // --- Cache-first block ---
        val normalizedRouteId = normalizeRouteNo(routeId)
        val cached = RouteMapMemoryCache.get(normalizedRouteId)
        if (cached != null) {
            routeNameFromDb = cached.routeName
            routeDetails = cached.routeDetails
            routePoints = cached.routePoints
            routePointLabels = cached.routePointLabels
            routeStopMarkerPoints = cached.routeStopMarkerPoints
            routeStopMarkerLabels = cached.routeStopMarkerLabels
            routeHasRealRoadPolyline = cached.hasRealRoadPolyline
            routeLoading = false
            routeLoadError = ""
            return@LaunchedEffect
        }

        try {
            val entries = RouteScheduleMemoryCache.get() ?: run {
                val snapshot = firestore
                    .collection("schedules")
                    .document("current")
                    .collection("data")
                    .document("items")
                    .get()
                    .await()

                collectRouteEntries(snapshot.data).also { RouteScheduleMemoryCache.put(it) }
            }

            val wantedRouteNo = normalizedRouteId
            val matched = entries.firstOrNull { entry ->
                normalizeRouteNo(entry["routeNo"]?.toString().orEmpty()) == wantedRouteNo
            }

            if (matched == null) {
                routePoints = emptyList()
                routePointLabels = emptyList()
                routeStopMarkerPoints = emptyList()
                routeStopMarkerLabels = emptyList()
                routeDetails = ""
                routeNameFromDb = if (routeText.isNotBlank()) {
                    routeText.substringAfter("—", routeText).trim()
                } else {
                    ""
                }
                routeHasRealRoadPolyline = false
                routeLoadError = "Route not found for $routeId (found ${entries.size} route entries)"
                routeLoading = false
                return@LaunchedEffect
            }

            routeDetails = matched.get("routeDetails")?.toString()?.trim().orEmpty()
            routeNameFromDb = matched.get("routeName")?.toString()?.trim().orEmpty()
            if (routeNameFromDb.isBlank() && routeText.isNotBlank()) {
                routeNameFromDb = routeText.substringAfter("—", routeText).trim()
            }

            val routeMapSnapshot = firestore
                .collection("route_maps")
                .document("current")
                .collection("routes")
                .document(wantedRouteNo)
                .get()
                .await()

            val routeMapData = routeMapSnapshot.data

            val adminAnchorPolylinePoints = extractGeoPointsFromPolyline(routeMapData?.get("routeRoadPolylineAnchors"))
            val roadPolylinePoints = extractGeoPointsFromPolyline(routeMapData?.get("routeRoadPolyline"))
            val stopPoints = extractGeoPointsFromStops(routeMapData?.get("routeStops"))
            val rawPolylinePoints = extractGeoPointsFromPolyline(matched?.get("routePolyline"))

            val useAdminAnchorPolyline = adminAnchorPolylinePoints.isNotEmpty()
            val useRoadPolyline = roadPolylinePoints.isNotEmpty()

            val polylinePoints = when {
                roadPolylinePoints.isNotEmpty() -> roadPolylinePoints
                adminAnchorPolylinePoints.size >= 2 -> snapPolylineToRoad(adminAnchorPolylinePoints)
                stopPoints.size >= 2 -> snapPolylineToRoad(stopPoints)
                rawPolylinePoints.size >= 2 -> snapPolylineToRoad(rawPolylinePoints)
                adminAnchorPolylinePoints.isNotEmpty() -> adminAnchorPolylinePoints
                stopPoints.isNotEmpty() -> stopPoints
                else -> rawPolylinePoints
            }
            val stopLabelsFromStops = extractStopLabelsFromStops(routeMapData?.get("routeStops"))
            val stopLabelsFromNames = extractStopLabelsFromStopNames(routeMapData?.get("routeStopNames"))
            val stopLabels = if (stopLabelsFromStops.isNotEmpty()) stopLabelsFromStops else stopLabelsFromNames
            val stopMarkerPoints = when {
                stopPoints.isNotEmpty() -> stopPoints
                adminAnchorPolylinePoints.isNotEmpty() && stopLabels.isNotEmpty() -> {
                    adminAnchorPolylinePoints.take(stopLabels.size)
                }
                else -> emptyList()
            }
            val stopMarkerLabels = if (stopLabels.isNotEmpty()) stopLabels else emptyList()

            when {
                polylinePoints.isNotEmpty() -> {
                    val labels = when {
                        stopLabels.isNotEmpty() && polylinePoints.size == stopLabels.size -> stopLabels
                        useAdminAnchorPolyline && !useRoadPolyline -> polylinePoints.indices.map { "Selected point ${it + 1}" }
                        else -> polylinePoints.indices.map { "Point ${it + 1}" }
                    }
                    routePoints = polylinePoints
                    routePointLabels = labels
                    routeStopMarkerPoints = stopMarkerPoints
                    routeStopMarkerLabels = stopMarkerLabels
                    routeHasRealRoadPolyline = useRoadPolyline || adminAnchorPolylinePoints.size >= 2 || stopPoints.size >= 2
                    routeLoading = false
                    routeLoadError = ""
                    RouteMapMemoryCache.put(
                        CachedRouteMapData(
                            routeNo = wantedRouteNo,
                            routeName = routeNameFromDb,
                            routeDetails = routeDetails,
                            routePoints = polylinePoints,
                            routePointLabels = labels,
                            routeStopMarkerPoints = stopMarkerPoints,
                            routeStopMarkerLabels = stopMarkerLabels,
                            hasRealRoadPolyline = useRoadPolyline || adminAnchorPolylinePoints.size >= 2 || stopPoints.size >= 2
                        )
                    )
                }
                routeDetails.isNotBlank() -> {
                    val (points, labels) = geocodeRoutePoints(routeDetails)
                    routePoints = points
                    routePointLabels = labels
                    routeStopMarkerPoints = emptyList()
                    routeStopMarkerLabels = emptyList()
                    routeHasRealRoadPolyline = false
                    routeLoading = false
                    if (points.isEmpty()) {
                        routeLoadError = "Could not map route details for $routeId"
                    } else {
                        routeLoadError = ""
                        RouteMapMemoryCache.put(
                            CachedRouteMapData(
                                routeNo = wantedRouteNo,
                                routeName = routeNameFromDb,
                                routeDetails = routeDetails,
                                routePoints = points,
                                routePointLabels = labels,
                                routeStopMarkerPoints = emptyList(),
                                routeStopMarkerLabels = emptyList(),
                                hasRealRoadPolyline = false
                            )
                        )
                    }
                }
                else -> {
                    routeLoadError = "Route details / map data not found for $routeId (found ${entries.size} route entries)"
                    routeLoading = false
                }
            }
        } catch (e: Exception) {
            routePoints = emptyList()
            routePointLabels = emptyList()
            routeStopMarkerPoints = emptyList()
            routeStopMarkerLabels = emptyList()
            routeLoading = false
            routeLoadError = e.message ?: "Failed to load route details"
            routeHasRealRoadPolyline = false
        }
    }

    fun startDownload() {
        val mapId = "dhaka_transport"
        val url = "https://sohanparves.unaux.com/diu/maps/dhaka_transport_${style}.mbtiles"

        val req = OneTimeWorkRequestBuilder<MbtilesDownloadWorker>()
            .setInputData(workDataOf("route_id" to mapId, "url" to url, "style" to style))
            .build()

        workManager.enqueueUniqueWork(
            "mbtiles_route_${mapId}_$style",
            ExistingWorkPolicy.REPLACE,
            req
        )

        workManager.getWorkInfoByIdLiveData(req.id).observeForever { info ->
            if (info == null) return@observeForever
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    offlineState = OfflineState.Downloading(-1)
                }
                WorkInfo.State.RUNNING -> {
                    val p = info.progress.getInt("progress", 0)
                    val doneBytes = info.progress.getLong("done_bytes", 0L)
                    val totalBytes = info.progress.getLong("total_bytes", -1L)

                    offlineState = OfflineState.Downloading(p)
                    lastDoneBytes = doneBytes
                    lastTotalBytes = totalBytes
                }
                WorkInfo.State.SUCCEEDED -> {
                    lastDoneBytes = 0L
                    lastTotalBytes = -1L
                    val path = info.outputData.getString("file_path")
                    offlineState =
                        if (path != null && File(path).exists()) OfflineState.Ready(path)
                        else OfflineState.Failed("Downloaded file missing")
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    offlineState = OfflineState.Failed("Download failed (state=${info.state})")
                }
                else -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        var requestCenterOnUser by remember { mutableStateOf(false) }
        val ready = offlineState as? OfflineState.Ready

        // Only start live GPS when the map is ready AND permission is granted
        val shouldTrack = (ready != null) && hasLocationPermission
        OsmdroidLiveMap(
            mbtilesPath = ready?.filePath,
            enableLiveLocation = shouldTrack,
            routeId = routeId,
            routeText = routeText,
            routePoints = routePoints,
            routePointLabels = routePointLabels,
            routeStopMarkerPoints = routeStopMarkerPoints,
            routeStopMarkerLabels = routeStopMarkerLabels,
            drawRoadLine = routeHasRealRoadPolyline,
            centerOnUserRequest = requestCenterOnUser,
            onCenterConsumed = { requestCenterOnUser = false }
        )
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val topCardBg = if (isDark) Color(0xFF0B2A66) else Color.White
        val topCardBorder = if (isDark) Color.Transparent else Color(0xFFE5E7EB)
        val topPrimaryText = if (isDark) Color.White else Color(0xFF111111)
        val topSecondaryText = if (isDark) Color.White.copy(alpha = 0.96f) else Color(0xFF111111)
        val topAccentText = if (isDark) Color.White else Color(0xFF111111)

        // Show currently selected road/filter on top
        if (routeId.isNotBlank() && !routeId.trim().equals("ALL", ignoreCase = true)) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp, start = 12.dp, end = 12.dp)
                    .shadow(
                        elevation = if (isDark) 0.dp else 14.dp,
                        shape = RoundedCornerShape(18.dp),
                        clip = false
                    )
                    .border(
                        width = if (isDark) 0.dp else 1.dp,
                        color = topCardBorder,
                        shape = RoundedCornerShape(18.dp)
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = topCardBg
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Selected road: $routeId",
                        style = MaterialTheme.typography.labelLarge,
                        color = topPrimaryText,
                        fontWeight = FontWeight.Bold
                    )
                    if (routeNameFromDb.isNotBlank()) {
                        Text(
                            text = routeNameFromDb,
                            style = MaterialTheme.typography.bodySmall,
                            color = topSecondaryText,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (routeText.isNotBlank()) {
                        Text(
                            text = routeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = topSecondaryText,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (routeDetails.isNotBlank()) {
                        Text(
                            text = routeDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = topSecondaryText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3
                        )
                    }
                }
            }
        }

        if (shouldPromptRoadSelection) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp, start = 16.dp, end = 16.dp)
                    .shadow(
                        elevation = if (isDark) 0.dp else 14.dp,
                        shape = RoundedCornerShape(20.dp),
                        clip = false
                    )
                    .border(
                        width = if (isDark) 0.dp else 1.dp,
                        color = if (isDark) Color.Transparent else Color(0xFFE5E7EB),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF0B2A66) else Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Please select a road",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isDark) Color.White else Color(0xFF111111),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Please select a map to find the Location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.White else Color(0xFF111111),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Auto-request permission when user opens Map and offline map is ready
        LaunchedEffect(ready, hasLocationPermission) {
            if (ready != null && !hasLocationPermission) {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // If permission denied, show a small prompt (non-blocking)
        if (ready != null && !hasLocationPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f)),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Location permission required for live tracking")
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        LaunchedEffect(offlineState) {
            if (offlineState is OfflineState.NotDownloaded) {
                startDownload()
            }
        }

        OfflineOverlay(
            state = offlineState,
            doneBytes = lastDoneBytes,
            totalBytes = lastTotalBytes,
            onDownload = { startDownload() },
            onRetry = { startDownload() }
        )

        if (ready != null && hasLocationPermission) {
            FloatingActionButton(
                onClick = { requestCenterOnUser = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 90.dp),
                containerColor = Color(0xFF0B2A66)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun OfflineOverlay(
    state: OfflineState,
    doneBytes: Long,
    totalBytes: Long,
    onDownload: () -> Unit,
    onRetry: () -> Unit
){
    if (state is OfflineState.Ready || state is OfflineState.NotDownloaded) return

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Offline map download dorkar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                when (state) {
                    is OfflineState.Downloading -> {
                        val p = state.progress
                        val infoLine = if (totalBytes > 0) {
                            val mbDone = doneBytes / (1024.0 * 1024.0)
                            val mbTotal = totalBytes / (1024.0 * 1024.0)
                            String.format("%.1f / %.1f MB", mbDone, mbTotal)
                        } else {
                            val mbDone = doneBytes / (1024.0 * 1024.0)
                            String.format("%.1f MB", mbDone)
                        }

                        if (p >= 0) {
                            Text("Downloading… $p%  ($infoLine)")
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(progress = p / 100f)
                        } else {
                            Text("Downloading… ($infoLine)")
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator()
                        }
                    }
                    is OfflineState.Failed -> {
                        Text("Error: ${state.message}")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                    is OfflineState.NotDownloaded, is OfflineState.Ready -> {
                        // hidden by early return
                    }
                }
            }
        }
    }
}
private fun createScaledDrawableBitmap(
    ctx: Context,
    drawableRes: Int,
    widthPx: Int,
    heightPx: Int
): Bitmap? {
    val d = ContextCompat.getDrawable(ctx, drawableRes) ?: return null
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, widthPx, heightPx)
    d.draw(canvas)
    return bmp
}

private fun createRouteStopMarkerBitmap(
    ctx: Context,
    label: String
): Bitmap {
    val density = ctx.resources.displayMetrics.density
    val pinWidth = (44f * density).toInt()
    val pinHeight = (48f * density).toInt()
    val bubblePadH = 10f * density
    val bubblePadV = 6f * density
    val bubbleRadius = 12f * density
    val bubbleGap = 6f * density
    val shadowBlur = 10f * density
    val shadowDy = 3f * density
    val textSize = 13f * density
    val minBubbleWidth = 84f * density
    val baseShadowHeight = 8f * density
    val baseShadowWidthExtra = 18f * density
    val pinLift = 6f * density

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#111111")
        this.textSize = textSize
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
    }

    val safeLabel = label.trim().ifBlank { "Stop" }
    val textWidth = textPaint.measureText(safeLabel)
    val bubbleWidth = max(minBubbleWidth, textWidth + bubblePadH * 2)
    val textHeight = textPaint.fontMetrics.run { bottom - top }
    val bubbleHeight = textHeight + bubblePadV * 2

    val totalWidth = max(bubbleWidth, pinWidth.toFloat()).toInt() + (24f * density).toInt()
    val totalHeight = (bubbleHeight + bubbleGap + pinHeight + baseShadowHeight + pinLift + shadowBlur * 2).toInt()

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bubbleLeft = (totalWidth - bubbleWidth) / 2f
    val bubbleTop = shadowBlur
    val bubbleRight = bubbleLeft + bubbleWidth
    val bubbleBottom = bubbleTop + bubbleHeight

    val baseShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(10f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    val baseShadowRect = RectF(
        (totalWidth - (pinWidth + baseShadowWidthExtra)) / 2f,
        bubbleBottom + bubbleGap + pinHeight + pinLift - (baseShadowHeight * 0.45f),
        (totalWidth + (pinWidth + baseShadowWidthExtra)) / 2f,
        bubbleBottom + bubbleGap + pinHeight + pinLift + (baseShadowHeight * 0.55f)
    )
    canvas.drawOval(baseShadowRect, baseShadowPaint)

    val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        setShadowLayer(shadowBlur, 0f, shadowDy, android.graphics.Color.argb(65, 0, 0, 0))
    }

    canvas.drawRoundRect(
        RectF(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom),
        bubbleRadius,
        bubbleRadius,
        bubblePaint
    )

    val textX = bubbleLeft + (bubbleWidth - textWidth) / 2f
    val textBaseline = bubbleTop + bubblePadV - textPaint.fontMetrics.top
    canvas.drawText(safeLabel, textX, textBaseline, textPaint)

    val pinBitmap = createScaledDrawableBitmap(ctx, R.drawable.route_stop_pin, pinWidth, pinHeight)
    if (pinBitmap != null) {
        val pinLeft = (totalWidth - pinWidth) / 2f
        val pinTop = bubbleBottom + bubbleGap + pinLift

        val pinShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(90, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(8f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(
            RectF(
                pinLeft + 6f * density,
                pinTop + pinHeight - 3f * density,
                pinLeft + pinWidth - 6f * density,
                pinTop + pinHeight + 5f * density
            ),
            pinShadowPaint
        )

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                pinLeft,
                pinTop,
                pinLeft,
                pinTop + pinHeight,
                intArrayOf(
                    android.graphics.Color.argb(90, 255, 255, 255),
                    android.graphics.Color.argb(0, 255, 255, 255)
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }

        canvas.drawBitmap(pinBitmap, pinLeft, pinTop, null)
        canvas.drawOval(
            RectF(
                pinLeft + 5f * density,
                pinTop + 4f * density,
                pinLeft + pinWidth - 5f * density,
                pinTop + pinHeight * 0.50f
            ),
            highlightPaint
        )
    }

    return bitmap
}

@SuppressLint("MissingPermission")
@Composable
private fun OsmdroidLiveMap(
    mbtilesPath: String?,
    enableLiveLocation: Boolean,
    routeId: String,
    routeText: String,
    routePoints: List<GeoPoint>,
    routePointLabels: List<String>,
    routeStopMarkerPoints: List<GeoPoint>,
    routeStopMarkerLabels: List<String>,
    drawRoadLine: Boolean,
    centerOnUserRequest: Boolean,
    onCenterConsumed: () -> Unit
){
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = ctx.packageName
    }

    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    var locationCallback by remember { mutableStateOf<LocationCallback?>(null) }
    var lastUserLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var hasFittedRoute by remember(routeId, routeText) { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                // TODO: MBTiles tile source attach (dependency-specific)
                mapViewRef = this
            }
        },
        update = { map ->
            mapViewRef = map
            // TODO: if mbtilesPath changes, reload tilesource accordingly

            // Remove previous selected-road overlays before drawing the latest one
            map.overlays.removeAll { overlay ->
                (overlay is Polyline && (overlay.title == "selected_route_polyline_outer" || overlay.title == "selected_route_polyline_inner")) ||
                (overlay is Marker && overlay.relatedObject == "selected_route_marker")
            }

            if (routePoints.isNotEmpty()) {
                // routePoints now prefer verified road polyline, otherwise we snap admin anchors / stop points
                // to roads so the visible line stays smooth and follows the road as closely as possible.
                if (drawRoadLine) {
                    val outer = Polyline().apply {
                        setPoints(routePoints)
                        title = "selected_route_polyline_outer"
                        outlinePaint.color = android.graphics.Color.parseColor("#111827")
                        outlinePaint.strokeWidth = 22f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    val inner = Polyline().apply {
                        setPoints(routePoints)
                        title = "selected_route_polyline_inner"
                        outlinePaint.color = android.graphics.Color.parseColor("#2563EB")
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    map.overlays.add(outer)
                    map.overlays.add(inner)
                }

                val markerCount = minOf(routeStopMarkerPoints.size, routeStopMarkerLabels.size)
                for (index in 0 until markerCount) {
                    val point = routeStopMarkerPoints[index]
                    val label = routeStopMarkerLabels[index].trim().ifBlank {
                        "Stop ${index + 1}"
                    }

                    val marker = Marker(map).apply {
                        position = point
                        title = label
                        infoWindow = null
                        relatedObject = "selected_route_marker"
                        alpha = 0.98f

                        // custom marker icon
                        val markerBitmap = createRouteStopMarkerBitmap(ctx, label)
                        icon = android.graphics.drawable.BitmapDrawable(ctx.resources, markerBitmap)
                        // pin এর bottom point যেন location এ লাগে
                        setAnchor(Marker.ANCHOR_CENTER, 0.90f)
                    }
                    map.overlays.add(marker)
                }

                if (!hasFittedRoute) {
                    if (routePoints.size == 1) {
                        map.controller.setZoom(14.5)
                        map.controller.animateTo(routePoints.first())
                    } else {
                        val box = BoundingBox.fromGeoPointsSafe(routePoints)
                        map.zoomToBoundingBox(box, true, 120)
                    }
                    hasFittedRoute = true
                }
                // Keep selected route overlays above other overlays
                map.overlays.sortBy { overlay ->
                    when {
                        overlay is Marker && overlay.relatedObject == "live_location_marker" -> 100
                        overlay is Marker && overlay.relatedObject == "selected_route_marker" -> 50
                        overlay is Polyline -> 10
                        else -> 0
                    }
                }
                map.invalidate()
            }

            if (routePoints.isEmpty()) {
                hasFittedRoute = false
            }

            if (enableLiveLocation) {
                val existingLiveMarker = map.overlays
                    .filterIsInstance<Marker>()
                    .firstOrNull { it.relatedObject == "live_location_marker" }

                val marker = existingLiveMarker ?: run {
                    val liveDot = ShapeDrawable(OvalShape()).apply {
                        intrinsicWidth = 44
                        intrinsicHeight = 44
                        paint.style = android.graphics.Paint.Style.FILL_AND_STROKE
                        paint.strokeWidth = 5f
                        paint.color = android.graphics.Color.parseColor("#0A84FF")
                        paint.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)
                    }

                    // Draw a white border behind the blue dot
                    val borderedBitmap = Bitmap.createBitmap(54, 54, Bitmap.Config.ARGB_8888)
                    val borderedCanvas = Canvas(borderedBitmap)
                    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                        color = android.graphics.Color.WHITE
                    }
                    val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                        color = android.graphics.Color.parseColor("#0A84FF")
                    }
                    borderedCanvas.drawCircle(27f, 27f, 23f, borderPaint)
                    borderedCanvas.drawCircle(27f, 27f, 18f, dotPaint)

                    Marker(map).apply {
                        title = "My live location"
                        relatedObject = "live_location_marker"
                        icon = android.graphics.drawable.BitmapDrawable(ctx.resources, borderedBitmap)
                        alpha = 1f
                        infoWindow = null
                        setAnchor(0.5f, 0.5f)
                        map.overlays.add(this)
                    }
                }

                if (locationCallback == null) {
                    val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                        .setMinUpdateDistanceMeters(5f)
                        .build()

                    val cb = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val loc = result.lastLocation ?: return
                            val p = GeoPoint(loc.latitude, loc.longitude)
                            marker.position = p
                            lastUserLocation = p

                            // Keep own location visually above every other overlay
                            map.overlays.remove(marker)
                            map.overlays.add(marker)
                            map.invalidate()
                        }
                    }
                    try {
                        fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
                    } catch (_: SecurityException) {
                        // Permission missing; ignore to avoid crash
                    }
                    locationCallback = cb
                }
            } else {
                locationCallback?.let {
                    try { fused.removeLocationUpdates(it) } catch (_: SecurityException) { }
                }
                locationCallback = null
                map.overlays.removeAll { overlay ->
                    overlay is Marker && overlay.relatedObject == "live_location_marker"
                }
            }

            if (centerOnUserRequest && lastUserLocation != null) {
                map.controller.animateTo(lastUserLocation)
                onCenterConsumed()
            }
        }
    )

    // Map screen leave করলে location off (আপনার requirement)

    DisposableEffect(Unit) {
        onDispose {
            locationCallback?.let {
                try { fused.removeLocationUpdates(it) } catch (_: SecurityException) { }
            }
            locationCallback = null

            runCatching {
                mapViewRef?.overlays?.clear()
                mapViewRef?.onDetach()
            }
            mapViewRef = null
        }
    }
}