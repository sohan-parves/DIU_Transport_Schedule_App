package com.sohan.diutransportschedule.ui
import androidx.lifecycle.Observer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation

import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.location.Location
import android.location.LocationManager
import android.content.Intent
import android.provider.Settings
import com.google.android.gms.tasks.CancellationTokenSource
import android.graphics.drawable.BitmapDrawable
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
import org.osmdroid.views.overlay.Polygon
import java.io.File
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.sohan.diutransportschedule.R
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import kotlin.math.max
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import android.graphics.Point
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent

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
    fun clear() {
        cache.clear()
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

private const val PREF_MAP_CACHE = "map_route_cache"
private const val PREF_FIRESTORE_WINDOW_MAP = "firestore_window_limit"
private const val KEY_FIRESTORE_WINDOW_DATE_MAP = "window_date"
private const val KEY_FIRESTORE_WINDOW_SLOT_MAP = "window_slot"
private const val PREF_FIRST_INSTALL_MAP_SYNC = "first_install_map_sync"
private const val KEY_FIRST_INSTALL_MAP_SYNC_DONE = "first_install_map_sync_done"

private fun currentFirestoreWindowSlotForMap(): Int {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour in 5..11 -> 1   // Morning
        hour in 12..16 -> 2  // Noon
        hour in 17..23 -> 3  // Evening
        else -> 0            // Night
    }
}

private fun canUseFirestoreWindowForMap(context: Context): Boolean {
    val slot = currentFirestoreWindowSlotForMap()
    if (slot == 0) return false

    val prefs = context.getSharedPreferences(PREF_FIRESTORE_WINDOW_MAP, Context.MODE_PRIVATE)
    val today = java.time.LocalDate.now().toString()
    val savedDate = prefs.getString(KEY_FIRESTORE_WINDOW_DATE_MAP, "") ?: ""
    val savedSlot = prefs.getInt(KEY_FIRESTORE_WINDOW_SLOT_MAP, -1)

    return !(savedDate == today && savedSlot == slot)
}

private fun markFirestoreWindowUsedForMap(context: Context) {
    val slot = currentFirestoreWindowSlotForMap()
    if (slot == 0) return

    context.getSharedPreferences(PREF_FIRESTORE_WINDOW_MAP, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_FIRESTORE_WINDOW_DATE_MAP, java.time.LocalDate.now().toString())
        .putInt(KEY_FIRESTORE_WINDOW_SLOT_MAP, slot)
        .apply()
}

private fun isFirstInstallMapSyncDone(context: Context): Boolean {
    return context.getSharedPreferences(PREF_FIRST_INSTALL_MAP_SYNC, Context.MODE_PRIVATE)
        .getBoolean(KEY_FIRST_INSTALL_MAP_SYNC_DONE, false)
}

private fun markFirstInstallMapSyncDone(context: Context) {
    context.getSharedPreferences(PREF_FIRST_INSTALL_MAP_SYNC, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_FIRST_INSTALL_MAP_SYNC_DONE, true)
        .apply()
}

private const val KEY_MAP_CACHE_VERSION = "map_cache_version"
private const val KEY_CACHED_SCHEDULE_ENTRIES = "cached_schedule_entries_json"
private const val KEY_ROUTE_CACHE_PREFIX = "route_cache_"

private fun readCachedMapVersion(ctx: Context): Long {
    return ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
        .getLong(KEY_MAP_CACHE_VERSION, 0L)
}

private fun saveCachedMapVersion(ctx: Context, version: Long) {
    ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_MAP_CACHE_VERSION, version)
        .apply()
}

private fun extractAppVersionFromMetaDoc(data: Map<String, Any?>?): Long {
    if (data == null) return 0L

    val raw = data["mapVersion"] ?: data["version"]
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }
}

private fun clearPersistentRouteMapCache(ctx: Context) {
    val prefs = ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    prefs.all.keys
        .filter { key -> key.startsWith(KEY_ROUTE_CACHE_PREFIX) }
        .forEach { key -> editor.remove(key) }
    editor.apply()
}

private fun saveCachedScheduleEntries(ctx: Context, entries: List<Map<*, *>>) {
    try {
        val arr = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            entry.forEach { (k, v) ->
                if (k != null) obj.put(k.toString(), JSONObject.wrap(v))
            }
            arr.put(obj)
        }
        ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHED_SCHEDULE_ENTRIES, arr.toString())
            .apply()
    } catch (_: Throwable) {
    }
}

private fun readCachedScheduleEntries(ctx: Context): List<Map<String, Any?>> {
    return try {
        val raw = ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_SCHEDULE_ENTRIES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val map = linkedMapOf<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = obj.opt(key)
                }
                add(map)
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun geoPointsToJson(points: List<GeoPoint>): JSONArray {
    val arr = JSONArray()
    points.forEach { point ->
        arr.put(
            JSONObject().apply {
                put("lat", point.latitude)
                put("lng", point.longitude)
            }
        )
    }
    return arr
}

private fun labelsToJson(labels: List<String>): JSONArray {
    val arr = JSONArray()
    labels.forEach { arr.put(it) }
    return arr
}

private fun jsonToGeoPoints(arr: JSONArray?): List<GeoPoint> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lat = obj.optDouble("lat", Double.NaN)
            val lng = obj.optDouble("lng", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) add(GeoPoint(lat, lng))
        }
    }
}

private fun jsonToLabels(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val value = arr.optString(i).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun savePersistentRouteMapCache(ctx: Context, data: CachedRouteMapData) {
    try {
        val obj = JSONObject().apply {
            put("routeNo", data.routeNo)
            put("routeName", data.routeName)
            put("routeDetails", data.routeDetails)
            put("routePoints", geoPointsToJson(data.routePoints))
            put("routePointLabels", labelsToJson(data.routePointLabels))
            put("routeStopMarkerPoints", geoPointsToJson(data.routeStopMarkerPoints))
            put("routeStopMarkerLabels", labelsToJson(data.routeStopMarkerLabels))
            put("hasRealRoadPolyline", data.hasRealRoadPolyline)
        }
        ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTE_CACHE_PREFIX + data.routeNo.trim().uppercase(Locale.getDefault()), obj.toString())
            .apply()
    } catch (_: Throwable) {
    }
}

private fun readPersistentRouteMapCache(ctx: Context, routeNo: String): CachedRouteMapData? {
    return try {
        val raw = ctx.getSharedPreferences(PREF_MAP_CACHE, Context.MODE_PRIVATE)
            .getString(KEY_ROUTE_CACHE_PREFIX + routeNo.trim().uppercase(Locale.getDefault()), null)
            ?: return null
        val obj = JSONObject(raw)
        CachedRouteMapData(
            routeNo = obj.optString("routeNo", routeNo),
            routeName = obj.optString("routeName", ""),
            routeDetails = obj.optString("routeDetails", ""),
            routePoints = jsonToGeoPoints(obj.optJSONArray("routePoints")),
            routePointLabels = jsonToLabels(obj.optJSONArray("routePointLabels")),
            routeStopMarkerPoints = jsonToGeoPoints(obj.optJSONArray("routeStopMarkerPoints")),
            routeStopMarkerLabels = jsonToLabels(obj.optJSONArray("routeStopMarkerLabels")),
            hasRealRoadPolyline = obj.optBoolean("hasRealRoadPolyline", false)
        )
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun LiveMapScreen() {
    val ctx = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val overlayScrim = if (isDark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.18f)

    // --- Permission state & launcher
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var requestCenterOnUser by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            requestCenterOnUser = false
            requestCenterOnUser = true
        }
    }

    fun isDeviceLocationEnabled(): Boolean {
        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    var showEnableLocationDialog by remember { mutableStateOf(false) }

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
    var lastDoneBytes by remember(style) { mutableStateOf(0L) }
    var lastTotalBytes by remember(style) { mutableStateOf(-1L) }
    var currentDownloadWorkId by remember(style) { mutableStateOf<java.util.UUID?>(null) }
    var mapVersionLabel by remember(style) { mutableStateOf("") }
    var checkingForMapUpdates by remember(style) { mutableStateOf(false) }

    val workManager = remember { WorkManager.getInstance(ctx) }
    val firestore = remember { FirebaseFirestore.getInstance() }
    var mapCacheVersion by remember { mutableLongStateOf(readCachedMapVersion(ctx)) }
    var appVersionChecked by remember { mutableStateOf(false) }

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

    fun extractStopLabelsFromScheduleStops(raw: Any?): List<String> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { row ->
            when (row) {
                is Map<*, *> -> row["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                else -> row?.toString()?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }

    fun mergePreferredLabels(vararg labelLists: List<String>): List<String> {
        return labelLists.firstOrNull { it.isNotEmpty() } ?: emptyList()
    }

    fun buildPointLabelsFromStops(
        polylinePoints: List<GeoPoint>,
        stopMarkerPoints: List<GeoPoint>,
        stopMarkerLabels: List<String>
    ): List<String> {
        if (polylinePoints.isEmpty() || stopMarkerPoints.isEmpty() || stopMarkerLabels.isEmpty()) return emptyList()

        fun distanceSquared(a: GeoPoint, b: GeoPoint): Double {
            val dLat = a.latitude - b.latitude
            val dLng = a.longitude - b.longitude
            return dLat * dLat + dLng * dLng
        }

        return polylinePoints.map { point ->
            val nearestIndex = stopMarkerPoints.indices.minByOrNull { index ->
                distanceSquared(point, stopMarkerPoints[index])
            } ?: -1

            stopMarkerLabels.getOrNull(nearestIndex)?.trim()?.takeIf { it.isNotBlank() }
                ?: "Point"
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
        if (routeId.isBlank() || routeId.trim().equals("ALL", ignoreCase = true)) {
            routePoints = emptyList()
            routePointLabels = emptyList()
            routeStopMarkerPoints = emptyList()
            routeStopMarkerLabels = emptyList()
            routeLoading = false
            routeLoadError = ""
            routeDetails = ""
            routeNameFromDb = ""
            routeHasRealRoadPolyline = false
            appVersionChecked = false
            return@LaunchedEffect
        }

        appVersionChecked = false

        routeLoading = true
        routeLoadError = ""
        routeDetails = ""
        routeNameFromDb = ""
        routePoints = emptyList()
        routePointLabels = emptyList()
        routeStopMarkerPoints = emptyList()
        routeStopMarkerLabels = emptyList()
        routeHasRealRoadPolyline = false


        val normalizedRouteId = normalizeRouteNo(routeId)
        fun candidateRouteDocIds(rawRouteId: String): List<String> {
            val normalized = normalizeRouteNo(rawRouteId)
            if (normalized.isBlank()) return emptyList()

            val match = Regex("^([A-Za-z]+)(\\d+)$").matchEntire(normalized)
            if (match == null) return listOf(normalized)

            val prefix = match.groupValues[1].uppercase()
            val number = match.groupValues[2].toIntOrNull()
            if (number == null) return listOf(normalized)

            return linkedSetOf(
                normalized,
                prefix + number.toString(),
                prefix + number.toString().padStart(2, '0'),
                prefix + number.toString().padStart(3, '0')
            ).toList()
        }
        val allowFirstInstallMapRead = !isFirstInstallMapSyncDone(ctx)

        // --- Cache-first block ---
        var cached = RouteMapMemoryCache.get(normalizedRouteId)
            ?: readPersistentRouteMapCache(ctx, normalizedRouteId)?.also {
                RouteMapMemoryCache.put(it)
            }
        val hasSelectedRouteCache = cached != null

        var firestoreWindowConsumedByMeta = false
        var forceReadAfterMapVersionChange = false
        if (!appVersionChecked) {
            try {
                if (allowFirstInstallMapRead || !hasSelectedRouteCache || canUseFirestoreWindowForMap(ctx)) {
                    if (!allowFirstInstallMapRead && hasSelectedRouteCache && canUseFirestoreWindowForMap(ctx)) {
                        markFirestoreWindowUsedForMap(ctx)
                        firestoreWindowConsumedByMeta = true
                    }

                    val metaData = firestore
                        .collection("meta")
                        .document("app")
                        .get()
                        .await()
                        .data

                    val remoteVersion = extractAppVersionFromMetaDoc(metaData)
                    if (remoteVersion > 0L && remoteVersion != mapCacheVersion) {
                        clearPersistentRouteMapCache(ctx)
                        RouteMapMemoryCache.clear()
                        cached = null
                        saveCachedMapVersion(ctx, remoteVersion)
                        mapCacheVersion = remoteVersion
                        forceReadAfterMapVersionChange = true
                    }
                }
            } catch (_: Exception) {
            } finally {
                appVersionChecked = true
            }
        }
        if (cached != null && (
                cached.routePoints.isNotEmpty() ||
                cached.routeStopMarkerPoints.isNotEmpty()
            )) {
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
            suspend fun readFullScheduleFromFirestore(): List<Map<*, *>> {
                val snapshot = firestore
                    .collection("schedules")
                    .document("current")
                    .collection("data")
                    .document("items")
                    .get()
                    .await()

                val scheduleDocData = snapshot.data
                return collectRouteEntries(scheduleDocData).also {
                    RouteScheduleMemoryCache.put(it)
                    saveCachedScheduleEntries(ctx, it)
                }
            }

            var entries = RouteScheduleMemoryCache.get() ?: run {
                val persisted = readCachedScheduleEntries(ctx)
                if (persisted.isNotEmpty()) {
                    RouteScheduleMemoryCache.put(persisted)
                    persisted
                } else {
                    if (allowFirstInstallMapRead) {
                        readFullScheduleFromFirestore()
                    } else if (firestoreWindowConsumedByMeta) {
                        emptyList<Map<*, *>>()
                    } else if (!canUseFirestoreWindowForMap(ctx)) {
                        readFullScheduleFromFirestore()
                    } else {
                        markFirestoreWindowUsedForMap(ctx)
                        readFullScheduleFromFirestore()
                    }
                }
            }

            val wantedRouteNo = normalizedRouteId
            var matched = entries.firstOrNull { entry ->
                normalizeRouteNo(entry["routeNo"]?.toString().orEmpty()) == wantedRouteNo
            }

            if (matched == null && !firestoreWindowConsumedByMeta) {
                entries = readFullScheduleFromFirestore()
                matched = entries.firstOrNull { entry ->
                    normalizeRouteNo(entry["routeNo"]?.toString().orEmpty()) == wantedRouteNo
                }
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

            suspend fun readSelectedRouteMapData(): Map<String, Any?>? {
                val routesCollection = firestore
                    .collection("route_maps")
                    .document("current")
                    .collection("routes")

                val candidateIds = candidateRouteDocIds(routeId)
                for (candidateId in candidateIds) {
                    val snapshot = routesCollection
                        .document(candidateId)
                        .get()
                        .await()

                    if (snapshot.exists()) {
                        return snapshot.data
                    }
                }

                val wanted = normalizeRouteNo(routeId)
                val allDocs = routesCollection.get().await()
                return allDocs.documents.firstOrNull { doc ->
                    val docIdMatch = normalizeRouteNo(doc.id) == wanted
                    val routeNoMatch = normalizeRouteNo(doc.getString("routeNo").orEmpty()) == wanted
                    docIdMatch || routeNoMatch
                }?.data
            }

            val routeMapData = if (allowFirstInstallMapRead || forceReadAfterMapVersionChange) {
                readSelectedRouteMapData()
            } else if (firestoreWindowConsumedByMeta) {
                null
            } else if (!canUseFirestoreWindowForMap(ctx)) {
                readSelectedRouteMapData()
            } else {
                markFirestoreWindowUsedForMap(ctx)
                readSelectedRouteMapData()
            }

            val routeNameFromMapDoc = routeMapData?.get("routeName")?.toString()?.trim().orEmpty()
            val routeNameFromSchedule = matched.get("routeName")?.toString()?.trim().orEmpty()
            routeNameFromDb = when {
                routeNameFromMapDoc.isNotBlank() -> routeNameFromMapDoc
                routeNameFromSchedule.isNotBlank() -> routeNameFromSchedule
                routeText.isNotBlank() -> routeText.substringAfter("—", routeText).trim()
                else -> ""
            }
            android.util.Log.d(
                "LiveMap",
                "routeId=$routeId wantedRouteNo=$wantedRouteNo entries=${entries.size} matched=${matched != null} routeMapFound=${routeMapData != null}"
            )

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
            val stopLabelsFromScheduleStops = extractStopLabelsFromScheduleStops(matched.get("routeStops"))
            val stopLabels = mergePreferredLabels(
                stopLabelsFromStops,
                stopLabelsFromNames,
                stopLabelsFromScheduleStops
            )
            val stopMarkerPoints = when {
                stopPoints.isNotEmpty() -> stopPoints
                adminAnchorPolylinePoints.isNotEmpty() && stopLabels.isNotEmpty() -> {
                    adminAnchorPolylinePoints.take(stopLabels.size)
                }
                rawPolylinePoints.isNotEmpty() && stopLabels.isNotEmpty() -> {
                    rawPolylinePoints.take(stopLabels.size)
                }
                else -> emptyList()
            }
            val stopMarkerLabels = if (stopLabels.isNotEmpty()) stopLabels else emptyList()

            when {
                polylinePoints.isNotEmpty() -> {
                    val resolvedStopMarkerLabels = when {
                        stopMarkerLabels.isNotEmpty() -> stopMarkerLabels
                        stopLabels.isNotEmpty() && stopMarkerPoints.isNotEmpty() -> stopLabels.take(stopMarkerPoints.size)
                        else -> emptyList()
                    }
                    val labels = when {
                        resolvedStopMarkerLabels.isNotEmpty() && stopMarkerPoints.isNotEmpty() ->
                            buildPointLabelsFromStops(polylinePoints, stopMarkerPoints, resolvedStopMarkerLabels)
                        stopLabels.isNotEmpty() && polylinePoints.size == stopLabels.size -> stopLabels
                        useAdminAnchorPolyline && !useRoadPolyline -> polylinePoints.indices.map { "Selected point ${it + 1}" }
                        else -> polylinePoints.indices.map { "Point ${it + 1}" }
                    }
                    routePoints = polylinePoints
                    routePointLabels = labels
                    routeStopMarkerPoints = stopMarkerPoints
                    routeStopMarkerLabels = resolvedStopMarkerLabels
                    routeHasRealRoadPolyline = useRoadPolyline || adminAnchorPolylinePoints.size >= 2 || stopPoints.size >= 2
                    routeLoading = false
                    routeLoadError = ""
                    val cacheData = CachedRouteMapData(
                        routeNo = wantedRouteNo,
                        routeName = routeNameFromDb,
                        routeDetails = routeDetails,
                        routePoints = polylinePoints,
                        routePointLabels = labels,
                        routeStopMarkerPoints = stopMarkerPoints,
                        routeStopMarkerLabels = resolvedStopMarkerLabels,
                        hasRealRoadPolyline = useRoadPolyline || adminAnchorPolylinePoints.size >= 2 || stopPoints.size >= 2
                    )
                    RouteMapMemoryCache.put(cacheData)
                    savePersistentRouteMapCache(ctx, cacheData)
                    if (allowFirstInstallMapRead) {
                        markFirstInstallMapSyncDone(ctx)
                    }
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
                        val cacheData = CachedRouteMapData(
                            routeNo = wantedRouteNo,
                            routeName = routeNameFromDb,
                            routeDetails = routeDetails,
                            routePoints = points,
                            routePointLabels = labels,
                            routeStopMarkerPoints = emptyList(),
                            routeStopMarkerLabels = emptyList(),
                            hasRealRoadPolyline = false
                        )
                        RouteMapMemoryCache.put(cacheData)
                        savePersistentRouteMapCache(ctx, cacheData)
                        if (allowFirstInstallMapRead) {
                            markFirstInstallMapSyncDone(ctx)
                        }
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

    fun startDownload(forceRestart: Boolean = false, allowWhileReady: Boolean = false) {
        val mapId = "dhaka_transport"
        val url = "https://sohanparves.unaux.com/diu/maps/dhaka_transport_${style}.mbtiles"
        val uniqueWorkName = "mbtiles_route_${mapId}_$style"

        if (!forceRestart) {
            when (offlineState) {
                is OfflineState.Downloading -> return
                is OfflineState.Ready -> if (!allowWhileReady) return
                else -> Unit
            }
        }

        if (!forceRestart && offlineState is OfflineState.Ready) {
            checkingForMapUpdates = true
        } else {
            checkingForMapUpdates = false
            lastDoneBytes = 0L
            lastTotalBytes = -1L
            mapVersionLabel = ""
            offlineState = OfflineState.Downloading(0)
        }

        val req = OneTimeWorkRequestBuilder<MbtilesDownloadWorker>()
            .setInputData(
                workDataOf(
                    "route_id" to mapId,
                    "url" to url,
                    "style" to style,
                    "force" to forceRestart
                )
            )
            .build()

        currentDownloadWorkId = req.id

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    DisposableEffect(currentDownloadWorkId) {
        val workId = currentDownloadWorkId
        if (workId == null) {
            onDispose { }
        } else {
            val liveData = workManager.getWorkInfoByIdLiveData(workId)
            val observer = Observer<WorkInfo> { info ->
                if (info == null) return@Observer
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        if (!checkingForMapUpdates) {
                            lastDoneBytes = 0L
                            lastTotalBytes = -1L
                            offlineState = OfflineState.Downloading(0)
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        val p = info.progress.getInt("progress", 0)
                        val doneBytes = info.progress.getLong("done_bytes", 0L)
                        val totalBytes = info.progress.getLong("total_bytes", -1L)
                        val versionLabel = info.progress.getString("version_label").orEmpty()
                        if (versionLabel.isNotBlank()) mapVersionLabel = versionLabel
                        if (checkingForMapUpdates && totalBytes <= 0L && doneBytes <= 0L) {
                            return@Observer
                        }
                        offlineState = OfflineState.Downloading(p)
                        lastDoneBytes = doneBytes
                        lastTotalBytes = totalBytes
                        checkingForMapUpdates = false
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString("file_path")
                        val unchanged = info.outputData.getBoolean("unchanged", false)
                        val versionLabel = info.outputData.getString("version_label").orEmpty()
                        if (versionLabel.isNotBlank()) mapVersionLabel = versionLabel
                        checkingForMapUpdates = false
                        lastDoneBytes = 0L
                        lastTotalBytes = -1L
                        offlineState = when {
                            path != null && File(path).exists() -> OfflineState.Ready(path)
                            unchanged && offlineFile.exists() -> OfflineState.Ready(offlineFile.absolutePath)
                            offlineFile.exists() -> OfflineState.Ready(offlineFile.absolutePath)
                            else -> OfflineState.Failed("Downloaded file missing")
                        }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        checkingForMapUpdates = false
                        lastDoneBytes = 0L
                        lastTotalBytes = -1L
                        offlineState = OfflineState.Failed("Map download failed. Internet check kore abar try korun.")
                    }
                    else -> Unit
                }
            }
            liveData.observeForever(observer)
            onDispose {
                liveData.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(style, offlineFile.absolutePath) {
        if (offlineFile.exists()) {
            offlineState = OfflineState.Ready(offlineFile.absolutePath)
            lastDoneBytes = 0L
            lastTotalBytes = -1L
            mapVersionLabel = ""
            checkingForMapUpdates = true
            startDownload(forceRestart = false, allowWhileReady = true)
            return@LaunchedEffect
        }

        checkingForMapUpdates = false
        mapVersionLabel = ""
        lastDoneBytes = 0L
        lastTotalBytes = -1L
        when (offlineState) {
            is OfflineState.NotDownloaded,
            is OfflineState.Failed -> startDownload(forceRestart = false)
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        val downloadProgressPercent = (offlineState as? OfflineState.Downloading)?.progress ?: 0
        val downloadedMbText = if (lastDoneBytes > 0L) String.format(Locale.US, "%.1f MB", lastDoneBytes / 1024f / 1024f) else "0 MB"
        val totalMbText = if (lastTotalBytes > 0L) String.format(Locale.US, "%.1f MB", lastTotalBytes / 1024f / 1024f) else "--"
        val shouldShowDownloadCard = false
        val ready = offlineState as? OfflineState.Ready


        // Only start live GPS when map is ready, permission is granted, and device location is on
        val shouldTrack = hasLocationPermission && isDeviceLocationEnabled()
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
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val topCardBg = if (isDark) Color(0xFF0A1F44) else Color.White
        val topCardBorder = if (isDark) Color(0xFF17356E) else Color(0xFFE5E7EB)
        val topPrimaryText = if (isDark) Color.White else Color(0xFF111827)
        val topSecondaryText = if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF4B5563)
        val topAccentText = if (isDark) Color.White.copy(alpha = 0.96f) else Color(0xFF1F2937)
        val topCardShadow = if (isDark) 0.dp else 18.dp
        val topCardShape = RoundedCornerShape(22.dp)

        // Show currently selected road/filter on top
        if (routeId.isNotBlank() && !routeId.trim().equals("ALL", ignoreCase = true)) {
            Card(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 14.dp, end = 14.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = topCardShadow,
                        shape = topCardShape,
                        clip = false
                    )
                    .border(
                        width = 1.dp,
                        color = topCardBorder,
                        shape = topCardShape
                    )
                    .align(Alignment.TopCenter),
                shape = topCardShape,
                colors = CardDefaults.cardColors(containerColor = topCardBg)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Selected road • $routeId",
                        style = MaterialTheme.typography.titleSmall,
                        color = topPrimaryText,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )

                    val subtitle = when {
                        routeNameFromDb.isNotBlank() -> routeNameFromDb
                        routeText.isNotBlank() -> routeText
                        else -> "Road selected"
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = topAccentText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    if (routeDetails.isNotBlank()) {
                        Text(
                            text = routeDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = topSecondaryText,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        if (shouldPromptRoadSelection) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .shadow(
                        elevation = if (isDark) 0.dp else 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color(0xFF17356E) else Color(0xFFE5E7EB),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF0A1F44) else Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Select a road first",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Color.White else Color(0xFF111827),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Choose a road to view the route, nearby stops, and your current location on the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF4B5563),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        var showLocationPermissionDialog by remember { mutableStateOf(false) }

        // Auto-show explanation first, then ask permission
        LaunchedEffect(hasLocationPermission) {
            if (!hasLocationPermission) {
                showLocationPermissionDialog = true
            }
        }

        if (showLocationPermissionDialog && !hasLocationPermission) {
            AlertDialog(
                onDismissRequest = { showLocationPermissionDialog = false },
                title = {
                    Text(
                        text = "Location permission needed",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Current location dekhano, My Location button diye map ke apnar nijer position-e neya, ar live tracking map-e show korar jonno location permission dorkar."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLocationPermissionDialog = false
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationPermissionDialog = false }) {
                        Text("Not now")
                    }
                }
            )
        }

        if (showEnableLocationDialog && hasLocationPermission) {
            AlertDialog(
                onDismissRequest = { showEnableLocationDialog = false },
                title = {
                    Text(
                        text = "Turn on device location",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Permission deya ache, kintu phone er Location/GPS off. Current location dekhate hole device location on korte hobe."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEnableLocationDialog = false
                            ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    ) {
                        Text("Open settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEnableLocationDialog = false }) {
                        Text("Not now")
                    }
                }
            )
        }

        // If permission still denied, show a small non-blocking prompt
        if (!hasLocationPermission && !showLocationPermissionDialog) {
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
                        Text("Location permission required for current location")
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { showLocationPermissionDialog = true }) {
                            Text("Allow")
                        }
                    }
                }
            }
        }


        FloatingActionButton(
            onClick = {
                when {
                    !hasLocationPermission -> {
                        showLocationPermissionDialog = true
                    }
                    !isDeviceLocationEnabled() -> {
                        showEnableLocationDialog = true
                    }
                    else -> {
                        requestCenterOnUser = false
                        requestCenterOnUser = true
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 104.dp)
                .size(56.dp)
                .shadow(
                    elevation = if (isDark) 0.dp else 12.dp,
                    shape = CircleShape,
                    clip = false
                )
                .border(
                    width = if (isDark) 0.dp else 1.dp,
                    color = if (isDark) Color.Transparent else Color(0xFFE5E7EB),
                    shape = CircleShape
                )
                .zIndex(24f),
            containerColor = if (isDark) Color(0xFF0B2A66) else Color.White,
            contentColor = if (isDark) Color.White else Color(0xFF0B2A66),
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "My Location"
            )
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

private fun createMyLocationMarkerBitmap(ctx: Context): Bitmap {
    val density = ctx.resources.displayMetrics.density
    val size = (24f * density).toInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(4f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0A84FF")
    }

    val cx = size / 2f
    val cy = size / 2f
    val shadowRadius = size * 0.34f
    val outerRadius = size * 0.28f
    val innerRadius = size * 0.18f

    canvas.drawCircle(cx, cy + density, shadowRadius, shadowPaint)
    canvas.drawCircle(cx, cy, outerRadius, outerPaint)
    canvas.drawCircle(cx, cy, innerRadius, innerPaint)

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
    var lastAccuracyMeters by remember { mutableStateOf<Float?>(null) }
    var hasCenteredOnUserOnce by remember { mutableStateOf(false) }
    var hasFittedRoute by remember(routeId, routeText) { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var userScreenOffset by remember { mutableStateOf<IntOffset?>(null) }
    val latestCenterOnUserRequest by rememberUpdatedState(centerOnUserRequest)
    val latestOnCenterConsumed by rememberUpdatedState(onCenterConsumed)
    val latestRoutePoints by rememberUpdatedState(routePoints)
    val density = LocalDensity.current

    val pulseTransition = rememberInfiniteTransition(label = "my_location_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "my_location_pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "my_location_pulse_alpha"
    )

    fun refreshUserScreenOffset(map: MapView) {
        val point = lastUserLocation ?: run {
            userScreenOffset = null
            return
        }
        val projected = map.projection.toPixels(point, Point())
        userScreenOffset = IntOffset(projected.x, projected.y)
    }

    fun updateUserLocationOnMap(location: Location, shouldCenterNow: Boolean) {
        val point = GeoPoint(location.latitude, location.longitude)
        lastUserLocation = point
        lastAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null

        mapViewRef?.let { map ->
            map.overlays.removeAll { overlay ->
                (overlay is Marker && overlay.relatedObject == "live_location_marker")
            }

            val accuracyMeters = if (location.hasAccuracy()) location.accuracy else null

            if (shouldCenterNow) {
                map.controller.setZoom(maxOf(map.zoomLevelDouble, 17.0))
                map.controller.animateTo(point)
                hasCenteredOnUserOnce = true
            }

            map.overlays.sortBy { overlay ->
                when {
                    overlay is Marker && overlay.relatedObject == "live_location_marker" -> 100
                    overlay is Polygon && overlay.title == "live_location_accuracy_circle" -> 90
                    overlay is Marker && overlay.relatedObject == "selected_route_marker" -> 50
                    overlay is Polyline -> 10
                    else -> 0
                }
            }
            map.postInvalidate()
            refreshUserScreenOffset(map)
        }
    }

    suspend fun requestFreshLocationAndShow(centerNow: Boolean): Boolean {
        return try {
            val freshLocation = try {
                fused.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            } catch (_: Exception) {
                null
            }

            val resolvedLocation = freshLocation ?: try {
                fused.lastLocation.await()
            } catch (_: Exception) {
                null
            }

            if (resolvedLocation != null) {
                updateUserLocationOnMap(resolvedLocation, centerNow)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                clipToPadding = false
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        refreshUserScreenOffset(this@apply)
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        refreshUserScreenOffset(this@apply)
                        return false
                    }
                })
                mapViewRef = this
            }
        },
        update = { map ->
            mapViewRef = map
            refreshUserScreenOffset(map)
            // TODO: if mbtilesPath changes, reload tilesource accordingly

            // Remove previous selected-road overlays before drawing the latest one
            map.overlays.removeAll { overlay ->
                (overlay is Polyline && (overlay.title == "selected_route_polyline_outer" || overlay.title == "selected_route_polyline_inner")) ||
                (overlay is Marker && overlay.relatedObject == "selected_route_marker") ||
                (overlay is Marker && overlay.relatedObject == "live_location_marker")
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
                        overlay is Polygon && overlay.title == "live_location_accuracy_circle" -> 90
                        overlay is Marker && overlay.relatedObject == "selected_route_marker" -> 50
                        overlay is Polyline -> 10
                        else -> 0
                    }
                }
                map.postInvalidate()
                refreshUserScreenOffset(map)
            }

            if (routePoints.isEmpty()) {
                hasFittedRoute = false
            }

            lastUserLocation?.let { userPoint ->
                val accuracyMeters = lastAccuracyMeters
                if (enableLiveLocation && !hasCenteredOnUserOnce && latestRoutePoints.isEmpty()) {
                    map.controller.setZoom(17.0)
                    map.controller.animateTo(userPoint)
                    hasCenteredOnUserOnce = true
                }
            }

            if (latestCenterOnUserRequest) {
                lastUserLocation?.let {
                    map.controller.setZoom(maxOf(map.zoomLevelDouble, 17.0))
                    map.controller.animateTo(it)
                    latestOnCenterConsumed()
                }
            }
            }
        )
    userScreenOffset?.let { screenOffset ->
        val pulseBaseSize = 56.dp
        val dotOuterSize = 18.dp
        val dotInnerSize = 10.dp

        val pulseSizePx = with(density) { (pulseBaseSize * pulseScale).toPx() }
        val pulseHalfPx = (pulseSizePx / 2f).toInt()
        val dotOuterHalfPx = with(density) { (dotOuterSize.toPx() / 2f).toInt() }
        val dotInnerHalfPx = with(density) { (dotInnerSize.toPx() / 2f).toInt() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(12f)
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            screenOffset.x - pulseHalfPx,
                            screenOffset.y - pulseHalfPx
                        )
                    }
                    .size(with(density) { pulseSizePx.toDp() })
                    .background(
                        color = Color(0xFF0A84FF).copy(alpha = pulseAlpha),
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            screenOffset.x - dotOuterHalfPx,
                            screenOffset.y - dotOuterHalfPx
                        )
                    }
                    .size(dotOuterSize)
                    .background(Color.White, CircleShape)
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            screenOffset.x - dotInnerHalfPx,
                            screenOffset.y - dotInnerHalfPx
                        )
                    }
                    .size(dotInnerSize)
                    .background(Color(0xFF0A84FF), CircleShape)
            )
        }
    }

    DisposableEffect(enableLiveLocation) {
        if (!enableLiveLocation) {
            locationCallback?.let {
                try { fused.removeLocationUpdates(it) } catch (_: SecurityException) { }
            }
            locationCallback = null
            lastUserLocation = null
            lastAccuracyMeters = null
            userScreenOffset = null
            hasCenteredOnUserOnce = false
            mapViewRef?.let { map ->
                map.overlays.removeAll { overlay ->
                    (overlay is Marker && overlay.relatedObject == "live_location_marker")
                }
                map.postInvalidate()
            }
            onDispose { }
        } else {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location: Location = result.lastLocation ?: return
                    val shouldCenterNow =
                        (!hasCenteredOnUserOnce && latestRoutePoints.isEmpty()) || latestCenterOnUserRequest
                    updateUserLocationOnMap(location, shouldCenterNow)
                    if (latestCenterOnUserRequest) {
                        latestOnCenterConsumed()
                    }
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 2000L)
                .setMinUpdateDistanceMeters(1f)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = callback
            try {
                fused.lastLocation.addOnSuccessListener { lastKnown ->
                    if (lastKnown != null && lastUserLocation == null) {
                        updateUserLocationOnMap(
                            lastKnown,
                            !hasCenteredOnUserOnce && latestRoutePoints.isEmpty()
                        )
                    }
                }
                fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (_: SecurityException) {
                locationCallback = null
            }

            onDispose {
                try { fused.removeLocationUpdates(callback) } catch (_: SecurityException) { }
                if (locationCallback === callback) {
                    locationCallback = null
                }
            }
        }
    }

    LaunchedEffect(centerOnUserRequest, enableLiveLocation) {
        if (latestCenterOnUserRequest && enableLiveLocation) {
            val found = requestFreshLocationAndShow(true)
            if (found) {
                latestOnCenterConsumed()
            }
        }
    }

    // Map screen leave করলে location off (আপনার requirement)

    DisposableEffect(Unit) {
        onDispose {
            locationCallback?.let {
                try { fused.removeLocationUpdates(it) } catch (_: SecurityException) { }
            }
            locationCallback = null
            lastUserLocation = null
            lastAccuracyMeters = null
            userScreenOffset = null

            runCatching {
                mapViewRef?.overlays?.clear()
                mapViewRef?.onDetach()
            }
            mapViewRef = null
        }
    }
    }