package com.sohan.diutransportschedule.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.sohan.diutransportschedule.db.DbScheduleItem
import com.sohan.diutransportschedule.db.JsonConverters
import com.sohan.diutransportschedule.db.ScheduleDao
import com.sohan.diutransportschedule.prefs.UserPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    suspend fun needsVersionRefresh(): Boolean {
        return try {
            val remote = remoteMetaVersion()
            val local = localMetaVersion()
            remote > 0 && remote != local
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

    private suspend fun readRemoteMeta(): SyncResult {
        val meta = fs.collection("meta").document("app").get().await()
        val remoteVersion = (
                meta.getLong("scheduleVersion")
                    ?: meta.getLong("version")
                    ?: 0L
                ).toInt()
        val message = meta.getString("message") ?: ""
        return SyncResult(updated = false, version = remoteVersion, message = message)
    }

    private suspend fun remoteMetaVersion(): Int = readRemoteMeta().version

    private suspend fun localMetaVersion(): Int {
        return try {
            store.getLocalVersion()
        } catch (_: Throwable) {
            0
        }
    }

    // ---------------- Preferences (Expose from UserPrefs) ----------------

    val selectedRouteFlow: Flow<String> = prefs.selectedRouteFlow
    suspend fun setSelectedRoute(routeNo: String) = prefs.setSelectedRoute(routeNo)

    val darkModeFlow: Flow<Boolean> = prefs.darkModeFlow
    suspend fun ensureDefaultPrefs() = prefs.ensureDefaults()
    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)

    // ✅ NEW: Update banner preference
    val showUpdateBannerFlow: Flow<Boolean> = prefs.showUpdateBannerFlow
    suspend fun setShowUpdateBanner(enabled: Boolean) = prefs.setShowUpdateBanner(enabled)

    // ✅ NEW: Compact mode preference
    val compactModeFlow: Flow<Boolean> = prefs.compactModeFlow
    suspend fun setCompactMode(enabled: Boolean) = prefs.setCompactMode(enabled)

    // ✅ NEW: Notifications preference
    val notificationsEnabledFlow: Flow<Boolean> = prefs.notificationsEnabledFlow
    suspend fun setNotificationsEnabled(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)

    val notifyLeadMinutesFlow: Flow<Int> = prefs.notifyLeadMinutesFlow
    suspend fun setNotifyLeadMinutes(minutes: Int) = prefs.setNotifyLeadMinutes(minutes)

    // ---------------- Sync ----------------

    suspend fun syncIfNeeded(
        allowDataRead: Boolean = true,
        forceReadOnVersionChange: Boolean = false,
        forceMetaCheckOnly: Boolean = false
    ): SyncResult {
        val meta = readRemoteMeta()
        val remoteVersion = meta.version
        val message = meta.message

        val localVersion = store.getLocalVersion()
        val updated = remoteVersion > localVersion
        val hasLocalData = hasReadableLocalData()

        // Slot/hour remote checks should stop at meta/version when Room already has data
        // and no version change requires a full schedule reload.
        if (forceMetaCheckOnly && hasLocalData && !updated) {
            return SyncResult(updated = false, version = remoteVersion, message = message)
        }

        // If version is unchanged and Room already has readable data,
        // only use the meta/version check and keep serving Room data.
        if (!updated && hasLocalData) {
            return SyncResult(updated = false, version = remoteVersion, message = message)
        }

        // When Room is empty, allow a full read even if version is the same,
        // so local cache can be rebuilt.
        val shouldReadFullData = when {
            !hasLocalData -> allowDataRead
            updated && forceReadOnVersionChange -> true
            else -> allowDataRead && updated
        }

        if (!shouldReadFullData) {
            return SyncResult(updated = false, version = remoteVersion, message = message)
        }

        val doc = fs.collection("schedules")
            .document("current")
            .collection("data")
            .document("items")
            .get().await()

        val raw = doc.get("items") as? List<Map<String, Any?>> ?: emptyList()
        fun isValidRouteNo(rn: String): Boolean {
            val s = rn.trim()
            if (s.isBlank()) return false

            // Reject section headers like "Friday Schedule @ DSC"
            if (s.contains("schedule", ignoreCase = true)) return false
            if (s.contains("@")) return false

            // Real route numbers look like R15 / F1 / F12 etc.
            return Regex("^[A-Za-z]+\\d+$").matches(s)
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

        dao.clearAll()
        dao.upsertAll(dbItems)

        // Persist the remote version only after a successful fetch + local DB update
        store.setLocalVersion(remoteVersion)

        return SyncResult(updated, remoteVersion, message)
    }

    suspend fun shouldShowUpdate(version: Int): Boolean {
        val seen = store.getSeenVersion()
        return version > seen
    }

    suspend fun markSeen(version: Int) {
        store.setSeenVersion(version)
    }
}