package com.sohan.diutransportschedule.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sohan.diutransportschedule.db.DbScheduleItem
import com.sohan.diutransportschedule.db.JsonConverters
import com.sohan.diutransportschedule.db.ScheduleDao
import com.sohan.diutransportschedule.prefs.UserPrefs
import com.sohan.diutransportschedule.sync.ScheduleSyncLock
import com.sohan.diutransportschedule.sync.VersionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await


data class SyncResult(
    val updated: Boolean,
    val version: Int,
    val message: String
)

class ScheduleRepository(
    private val dao: ScheduleDao,
    private val fs: FirebaseFirestore,
    private val store: VersionStore,
    private val prefs: UserPrefs
) {

    fun observeLocal() = dao.observeAll()

    /**
     * One-time migration for installs that already have a schedule cache from before
     * `updatedAt` began being persisted locally.
     */
    suspend fun backfillScheduleUpdatedAtIfMissing(): Long =
        ScheduleSyncLock.mutex.withLock {
            val cachedValue = scheduleUpdatedAtMillisFlow.first()
            if (cachedValue > 0L) return@withLock cachedValue

            val doc = fs.collection("schedules")
                .document("current")
                .collection("data")
                .document("items")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            val updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
            if (updatedAtMillis > 0L) {
                store.setScheduleUpdatedAtMillis(updatedAtMillis)
            }
            updatedAtMillis
        }

    suspend fun needsVersionRefresh(): Boolean {
        return try {
            val remote = remoteMetaVersion()
            val local = localMetaVersion()
            remote > 0 && remote > local
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun hasReadableLocalData(): Boolean {
        return try {
            observeLocal().first().any { entity ->
                val routeNo = entity.routeNo.trim()
                val looksLikeRouteNo = Regex("^[A-Za-z]+\\d+$").matches(routeNo)
                val hasAnyTime =
                    JsonConverters.jsonToList(entity.startTimesJson).any { it.trim().isNotBlank() } ||
                            JsonConverters.jsonToList(entity.departureTimesJson).any { it.trim().isNotBlank() }

                looksLikeRouteNo && hasAnyTime
            }
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun clearLocalCache() {
        dao.clearAll()
    }

    // ── Priority 1: FCM schedule update → fetch full schedule, then replace cache atomically ──

    suspend fun forceRefreshFromFcm(context: android.content.Context? = null): SyncResult =
        ScheduleSyncLock.mutex.withLock {
            val localVersion = store.getLocalVersion()
            Log.d(TAG, "Sync[fcm] started | localVersion=$localVersion")
            try {
                syncRouteMapsIfNeeded(context)
                val meta = readRemoteMeta()
                // Fetch and atomically replace — old cache stays intact until new data is confirmed
                val result = fetchAndPersistSchedule(meta.version, meta.message)
                Log.d(TAG, "Sync[fcm] completed | old=$localVersion new=${result.version} updated=${result.updated}")
                result
            } catch (e: Throwable) {
                Log.w(TAG, "Sync[fcm] FAILED | localVersion=$localVersion kept intact", e)
                throw e
            }
        }

    // ── Priority 2: Version check (foreground / background) ──

    suspend fun syncDailyVersionCheck(
        context: android.content.Context? = null,
        trigger: String = "foreground"
    ): SyncResult =
        ScheduleSyncLock.mutex.withLock {
            val localVersion = store.getLocalVersion()
            Log.d(TAG, "Sync[$trigger] started | localVersion=$localVersion")
            try {
                syncRouteMapsIfNeeded(context)
                val meta = readRemoteMeta()
                if (meta.version <= localVersion) {
                    Log.d(TAG, "Sync[$trigger] skipped — version unchanged | remote=${meta.version} local=$localVersion")
                    return@withLock SyncResult(updated = false, version = meta.version, message = meta.message)
                }
                // Version changed — fetch and atomically replace (old cache stays until new data confirmed)
                val result = fetchAndPersistSchedule(meta.version, meta.message)
                Log.d(TAG, "Sync[$trigger] completed | old=$localVersion new=${result.version} updated=${result.updated}")
                result
            } catch (e: Throwable) {
                Log.w(TAG, "Sync[$trigger] FAILED | localVersion=$localVersion kept intact", e)
                throw e
            }
        }

    // ── Priority 3: Cache miss → direct full fetch (no version gate) ──

    suspend fun syncOnCacheMiss(context: android.content.Context? = null): SyncResult =
        ScheduleSyncLock.mutex.withLock {
            Log.d(TAG, "Sync[cache_miss] started | no local data")
            try {
                syncRouteMapsIfNeeded(context)
                val meta = readRemoteMeta()
                val result = fetchAndPersistSchedule(meta.version, meta.message)
                Log.d(TAG, "Sync[cache_miss] completed | version=${result.version} updated=${result.updated}")
                result
            } catch (e: Throwable) {
                Log.w(TAG, "Sync[cache_miss] FAILED", e)
                throw e
            }
        }

    private suspend fun readRemoteMeta(): SyncResult {
        var lastException: Throwable? = null
        for (i in 0 until 2) {
            try {
                val meta = fs.collection("meta").document("app")
                    .get(com.google.firebase.firestore.Source.SERVER).await()
                val remoteVersion = (
                        meta.getLong("scheduleVersion")
                            ?: meta.getLong("version")
                            ?: 0L
                        ).toInt()
                val message = meta.getString("message") ?: ""
                return SyncResult(updated = false, version = remoteVersion, message = message)
            } catch (e: Throwable) {
                lastException = e
                val msg = e.message?.lowercase() ?: ""
                val isNetworkError = msg.contains("network") || msg.contains("unavailable") || msg.contains("offline")
                if (isNetworkError) break

                if (i < 1) kotlinx.coroutines.delay(1000)
            }
        }
        throw lastException ?: Exception("Failed to fetch meta/app")
    }

    private suspend fun remoteMetaVersion(): Int = readRemoteMeta().version

    private suspend fun localMetaVersion(): Int {
        return try {
            store.getLocalVersion()
        } catch (_: Throwable) {
            0
        }
    }

    private suspend fun syncRouteMapsIfNeeded(context: android.content.Context?) {
        if (context == null) return
        try {
            var remoteMapVersion = 0L
            for (i in 0 until 3) {
                try {
                    val mapMeta = fs.collection("meta").document("route_maps")
                        .get(com.google.firebase.firestore.Source.SERVER).await()
                    remoteMapVersion = (mapMeta.getLong("version") ?: 0L).toLong()
                    if (remoteMapVersion > 0L) break
                } catch (e: Throwable) {
                    if (i == 2 && remoteMapVersion == 0L) throw e
                    kotlinx.coroutines.delay(1000)
                }
            }

            if (remoteMapVersion > 0L) {
                val mapPrefs = context.getSharedPreferences("map_route_cache", android.content.Context.MODE_PRIVATE)
                val localMapVersion = mapPrefs.getLong("map_cache_version", 0L)

                if (remoteMapVersion != localMapVersion) {
                    val editor = mapPrefs.edit()
                    mapPrefs.all.keys.filter { it.startsWith("route_cache_") }.forEach { editor.remove(it) }
                    editor.putLong("map_cache_version", remoteMapVersion)
                    editor.apply()

                    for (i in 0 until 3) {
                        try {
                            fs.collection("route_maps")
                                .document("current")
                                .collection("routes")
                                .get(com.google.firebase.firestore.Source.SERVER).await()
                            break
                        } catch (e: Throwable) {
                            if (i == 2) throw e
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore map sync failures to not interrupt schedule sync
        }
    }

    private suspend fun fetchAndPersistSchedule(
        remoteVersion: Int,
        message: String
    ): SyncResult {
        var raw: List<Map<String, Any?>> = emptyList()
        var updatedAtMillis = 0L
        var fetchSuccess = false
        for (i in 0 until 3) {
            try {
                val doc = fs.collection("schedules")
                    .document("current")
                    .collection("data")
                    .document("items")
                    .get(com.google.firebase.firestore.Source.SERVER).await()
                raw = doc.get("items") as? List<Map<String, Any?>> ?: emptyList()
                updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                fetchSuccess = true
                break
            } catch (e: Throwable) {
                if (i == 2) throw e
                kotlinx.coroutines.delay(1000)
            }
        }

        if (!fetchSuccess) {
            return SyncResult(updated = false, version = store.getLocalVersion(), message = message)
        }

        val dbItems = raw.mapNotNull { m ->
            val routeNo = m["routeNo"] as? String ?: ""
            if (!isValidRouteNo(routeNo)) return@mapNotNull null

            val routeName = m["routeName"] as? String ?: ""
            val routeDetails = m["routeDetails"] as? String ?: ""
            val startTimes =
                (m["startTimes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val depTimes =
                (m["departureTimes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            DbScheduleItem(
                id = "${routeNo}_${routeName}".trim(),
                routeNo = routeNo,
                routeName = routeName,
                startTimesJson = JsonConverters.listToJson(startTimes),
                departureTimesJson = JsonConverters.listToJson(depTimes),
                routeDetails = routeDetails
            )
        }

        // Atomically replace: old cache stays intact until new data is fully committed
        dao.replaceAll(dbItems)
        store.setLocalVersion(remoteVersion)
        if (updatedAtMillis > 0L) {
            store.setScheduleUpdatedAtMillis(updatedAtMillis)
        }

        return SyncResult(updated = true, version = remoteVersion, message = message)
    }

    private fun isValidRouteNo(rn: String): Boolean {
        val s = rn.trim()
        if (s.isBlank()) return false
        if (s.contains("schedule", ignoreCase = true)) return false
        if (s.contains("@")) return false
        return Regex("^[A-Za-z]+\\d+$").matches(s)
    }

    // ---------------- Preferences (Expose from UserPrefs) ----------------

    val selectedRouteFlow: Flow<String> = prefs.selectedRouteFlow
    suspend fun setSelectedRoute(routeNo: String) = prefs.setSelectedRoute(routeNo)

    val scheduleUpdatedAtMillisFlow: Flow<Long> = store.scheduleUpdatedAtMillisFlow

    val darkModeFlow: Flow<Boolean> = prefs.darkModeFlow
    suspend fun ensureDefaultPrefs() = prefs.ensureDefaults()
    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)

    val showUpdateBannerFlow: Flow<Boolean> = prefs.showUpdateBannerFlow
    suspend fun setShowUpdateBanner(enabled: Boolean) = prefs.setShowUpdateBanner(enabled)

    val compactModeFlow: Flow<Boolean> = prefs.compactModeFlow
    suspend fun setCompactMode(enabled: Boolean) = prefs.setCompactMode(enabled)

    val notificationsEnabledFlow: Flow<Boolean> = prefs.notificationsEnabledFlow
    suspend fun setNotificationsEnabled(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)

    val notifyLeadMinutesFlow: Flow<Int> = prefs.notifyLeadMinutesFlow
    suspend fun setNotifyLeadMinutes(minutes: Int) = prefs.setNotifyLeadMinutes(minutes)

    // ---------------- Legacy sync entry (manual meta-only paths) ----------------

    suspend fun syncIfNeeded(
        allowDataRead: Boolean = true,
        forceReadOnVersionChange: Boolean = false,
        forceMetaCheckOnly: Boolean = false,
        context: android.content.Context? = null
    ): SyncResult = ScheduleSyncLock.mutex.withLock {
        val meta = readRemoteMeta()
        val remoteVersion = meta.version
        val message = meta.message

        syncRouteMapsIfNeeded(context)

        val localVersion = store.getLocalVersion()
        val updated = remoteVersion > localVersion
        val hasLocalData = hasReadableLocalData()

        if (forceMetaCheckOnly && hasLocalData && !updated) {
            return@withLock SyncResult(updated = false, version = remoteVersion, message = message)
        }

        if (!updated && hasLocalData) {
            return@withLock SyncResult(updated = false, version = remoteVersion, message = message)
        }

        val shouldReadFullData = when {
            !hasLocalData -> allowDataRead
            updated && forceReadOnVersionChange -> true
            else -> allowDataRead && updated
        }

        if (!shouldReadFullData) {
            return@withLock SyncResult(updated = false, version = remoteVersion, message = message)
        }

        fetchAndPersistSchedule(remoteVersion, message)
    }

    suspend fun shouldShowUpdate(version: Int): Boolean {
        val seen = store.getSeenVersion()
        return version > seen
    }

    suspend fun markSeen(version: Int) {
        store.setSeenVersion(version)
    }

    companion object {
        private const val TAG = "ScheduleSync"
    }
}
