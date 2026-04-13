package com.sohan.diutransportschedule.ui.home

import android.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohan.diutransportschedule.db.DbScheduleItem
import com.sohan.diutransportschedule.db.JsonConverters
import com.sohan.diutransportschedule.data.repository.ScheduleRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sohan.diutransportschedule.BuildConfig
import com.sohan.diutransportschedule.ui.notice.checkAndSyncNoticesFromMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class UiSchedule(
    val routeNo: String,
    val routeName: String,
    val routeDetails: String,
    val startTimes: List<String>,
    val departureTimes: List<String>,
    // FRIDAY schedules are tagged by routeNo prefix (e.g., F1/F2). DAILY otherwise.
    val appliesOn: String
)

data class RouteOption(
    val routeNo: String,
    val label: String
)

private const val PREF_FIRESTORE_WINDOW = "firestore_window_limit"
private const val KEY_FIRESTORE_WINDOW_DATE = "window_date"
private const val KEY_FIRESTORE_WINDOW_SLOT = "window_slot"

private fun DbScheduleItem.toUi(): UiSchedule = UiSchedule(
    routeNo = routeNo,
    routeName = routeName,
    routeDetails = routeDetails,
    startTimes = JsonConverters.jsonToList(startTimesJson),
    departureTimes = JsonConverters.jsonToList(departureTimesJson),
    appliesOn = if (routeNo.trim().startsWith("F", ignoreCase = true)) "FRIDAY" else "DAILY"
)

private fun latestOptionsContainSelectedRoute(
    selectedRoute: String,
    routeOptions: List<RouteOption>
): Boolean {
    val normalized = selectedRoute.trim()
    if (normalized.isBlank()) return false
    if (normalized.equals("ALL", ignoreCase = true)) return true

    return routeOptions.any { option ->
        option.routeNo.trim().equals(normalized, ignoreCase = true)
    }
}

class HomeViewModel(
    private val repo: ScheduleRepository,
    private val initialDarkModePref: Boolean = true
) : ViewModel() {

    // Current installed app version (from app/build.gradle)
    val currentAppVersionName: String = BuildConfig.VERSION_NAME

    private val query = MutableStateFlow("")
    private val selectedFridayRoute = MutableStateFlow("ALL")

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Manual remote read window: allow only once every 1 hour for swipe-to-refresh.
    // Auto refresh uses separate slot-based state and does not share this timestamp.
    private var manualLastRemoteReadAtMillis: Long = 0L
    private val manualRemoteReadIntervalMillis = 60 * 60 * 1000L // 1 hour
    private val syncMutex = Mutex()


    private fun currentFirestoreWindowSlot(): Int {
        val hour = LocalTime.now().hour
        return when {
            hour in 5..11 -> 1   // Morning
            hour in 12..16 -> 2  // Noon
            hour in 17..23 -> 3  // Evening
            else -> 0            // Night: no read
        }
    }

    private fun firestoreWindowPrefs(context: Context?): SharedPreferences? {
        return context?.getSharedPreferences(PREF_FIRESTORE_WINDOW, Context.MODE_PRIVATE)
    }

    private fun syncFridayRouteFromPrefs(context: Context?) {
        val prefs = context?.getSharedPreferences("profile_route_prefs", Context.MODE_PRIVATE)
        val saved = prefs?.getString("selected_friday_route", "").orEmpty().trim()
        selectedFridayRoute.value = if (saved.isBlank()) "ALL" else saved
    }

    private fun syncNoticeMetaWithHomeAutoScan(context: Context?) {
        if (context == null) return

        checkAndSyncNoticesFromMeta(
            ctx = context,
            db = FirebaseFirestore.getInstance(),
            forceBypass = false,
            onDone = { },
            onError = { },
            onVersionCompared = { noticeUpdated ->
                if (noticeUpdated) {
                    _syncStatusMessage.value = "A new notice এসেছে"
                }
            }
        )
    }

    private fun effectiveMapRoute(
        selectedDailyRoute: String,
        selectedFridayRoute: String
    ): String {
        val todayIsFriday = try {
            LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY
        } catch (_: Throwable) {
            false
        }

        val daily = selectedDailyRoute.trim().ifBlank { "ALL" }
        val friday = selectedFridayRoute.trim().ifBlank { "ALL" }

        return if (todayIsFriday) {
            // If user did NOT select any Friday route, show the full Friday schedule.
            // (Daily route selection should not hide Friday-only routes.)
            if (friday.equals("ALL", ignoreCase = true)) {
                "ALL"
            } else if (friday.startsWith("F", ignoreCase = true)) {
                friday
            } else {
                "ALL"
            }
        } else {
            if (!daily.startsWith("F", ignoreCase = true)) {
                daily
            } else {
                "ALL"
            }
        }
    }

    private fun readAutoLastSlot(context: Context?): Pair<String, Int> {
        val prefs = firestoreWindowPrefs(context)
        val date = prefs?.getString(KEY_FIRESTORE_WINDOW_DATE, "") ?: ""
        val slot = prefs?.getInt(KEY_FIRESTORE_WINDOW_SLOT, -1) ?: -1
        return date to slot
    }

    private fun isAutoSlotAvailable(context: Context?): Boolean {
        val slot = currentFirestoreWindowSlot()
        if (slot == 0) return false

        val today = LocalDate.now().toString()
        val (savedDate, savedSlot) = readAutoLastSlot(context)
        return !(savedDate == today && savedSlot == slot)
    }

    private fun currentAutoReadableSlotOrNone(): Int {
        return currentFirestoreWindowSlot()
    }

    private fun markAutoSlotRead(context: Context?, slot: Int) {
        if (slot == 0) return

        val prefs = firestoreWindowPrefs(context) ?: return
        prefs.edit()
            .putString(KEY_FIRESTORE_WINDOW_DATE, LocalDate.now().toString())
            .putInt(KEY_FIRESTORE_WINDOW_SLOT, slot)
            .apply()
    }

    private val _showUpdate = MutableStateFlow(false)
    val showUpdate: StateFlow<Boolean> = _showUpdate.asStateFlow()

    private val _updateMessage = MutableStateFlow("")
    val updateMessage: StateFlow<String> = _updateMessage.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private val _initialUiReady = MutableStateFlow(false)
    val initialUiReady: StateFlow<Boolean> = _initialUiReady.asStateFlow()


    // prefs
    val selectedRoute: StateFlow<String> = repo.selectedRouteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ALL")


    val darkMode: StateFlow<Boolean> = repo.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialDarkModePref)

    val showUpdateBanner: StateFlow<Boolean> = repo.showUpdateBannerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val compactMode: StateFlow<Boolean> = repo.compactModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val notificationsEnabled: StateFlow<Boolean> = repo.notificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val notifyLeadMinutes: StateFlow<Int> = repo.notifyLeadMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    // local data -> ui
    private val localUi: Flow<List<UiSchedule>> = repo.observeLocal()
        .map { list ->
            list.map { it.toUi() }
                .filter { ui ->
                    val rn = ui.routeNo.trim()
                    val looksLikeRouteNo = Regex("^[A-Za-z]+\\d+$").matches(rn)   // R15, F1 etc
                    val hasAnyTime =
                        ui.startTimes.any { it.trim().isNotBlank() } ||
                                ui.departureTimes.any { it.trim().isNotBlank() }

                    looksLikeRouteNo && hasAnyTime
                }
        }



    // ✅ Profile dropdown: routeNo + routeName label
    val routeOptions: StateFlow<List<RouteOption>> = localUi
        .map { list ->
            val unique = LinkedHashMap<String, String>()
            for (it in list) {
                val no = it.routeNo.trim()
                if (no.isBlank()) continue
                if (!unique.containsKey(no)) unique[no] = it.routeName.trim()
            }

            val allOptions = unique.entries
                .map { (no, name) ->
                    val label = if (name.isNotBlank()) "$name ($no)" else no
                    RouteOption(routeNo = no, label = label)
                }

            val dailyOptions = allOptions
                .filterNot { it.routeNo.trim().startsWith("F", ignoreCase = true) }
                .sortedBy { it.routeNo }

            val fridayOptions = allOptions
                .filter { it.routeNo.trim().startsWith("F", ignoreCase = true) }
                .sortedBy { it.routeNo }

            buildList {
                add(RouteOption(routeNo = "ALL", label = "All Routes"))
                addAll(dailyOptions)
                addAll(fridayOptions)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            listOf(RouteOption("ALL", "All Routes"))
        )

    val selectedRouteLabel: StateFlow<String> = combine(selectedRoute, routeOptions) { sel, opts ->
        opts.firstOrNull { it.routeNo.equals(sel, ignoreCase = true) }?.label ?: sel
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "All Routes")

    val effectiveSelectedRouteForToday: StateFlow<String> =
        combine(selectedRoute, selectedFridayRoute) { daily, friday ->
            effectiveMapRoute(
                selectedDailyRoute = daily,
                selectedFridayRoute = friday
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            "ALL"
        )

    init {
        viewModelScope.launch { repo.ensureDefaultPrefs() }

        viewModelScope.launch {
            routeOptions.collect { options ->
                val currentSelectedRoute = selectedRoute.value.trim()

                when {
                    options.isEmpty() -> {
                        // Keep the user's current selection during transient sync/replace states.
                    }

                    latestOptionsContainSelectedRoute(currentSelectedRoute, options) -> {
                        // Keep the user's existing route selection unchanged.
                    }

                    else -> {
                        repo.setSelectedRoute("ALL")
                    }
                }
            }
        }
    }

    // ✅ Home items: Search overrides Profile route filter
    val items: StateFlow<List<UiSchedule>> =
        combine(localUi, selectedRoute, query, selectedFridayRoute) { list, route, q, fridayRoute ->
            val rawQuery = q.trim()
            val qq = rawQuery.lowercase()

            val todayIsFriday = try {
                LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY
            } catch (_: Throwable) {
                false
            }

            val normalizedDailyRoute = route.trim().ifBlank { "ALL" }
            val normalizedFridayRoute = fridayRoute.trim().ifBlank { "ALL" }
            val effectiveRoute = effectiveMapRoute(
                selectedDailyRoute = normalizedDailyRoute,
                selectedFridayRoute = normalizedFridayRoute
            )

            val dayFilteredList = if (todayIsFriday) {
                list.filter { it.appliesOn == "FRIDAY" }
            } else {
                list.filter { !it.appliesOn.equals("FRIDAY", ignoreCase = true) }
            }

            val base = when {
                rawQuery.isNotEmpty() -> list
                effectiveRoute.equals("ALL", ignoreCase = true) -> dayFilteredList
                else -> dayFilteredList.filter {
                    it.routeNo.equals(effectiveRoute, ignoreCase = true)
                }
            }

            val filtered = if (qq.isBlank()) base
            else base.filter {
                it.routeNo.lowercase().contains(qq) ||
                        it.routeName.lowercase().contains(qq) ||
                        it.routeDetails.lowercase().contains(qq) ||
                        it.startTimes.any { t -> t.lowercase().contains(qq) } ||
                        it.departureTimes.any { t -> t.lowercase().contains(qq) }
            }

            filtered.sortedWith(
                compareBy<UiSchedule> { it.appliesOn.equals("FRIDAY", ignoreCase = true) }
                    .thenBy { it.routeNo }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        query.value = q
    }

    fun dismissUpdate() {
        _showUpdate.value = false
    }

    fun dismissSyncStatus() {
        _syncStatusMessage.value = ""
    }

    fun showInlineUpdateFromPush(title: String, body: String) {
        val t = title.trim()
        val b = body.trim()
        if (b.isBlank()) return

        _updateMessage.value = if (t.isBlank()) b else "$t\n$b"
        _showUpdate.value = true
    }

    private fun hasInternetConnection(context: Context?): Boolean {
        if (context == null) return false

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun performRefresh(
        showBannerIfUpdated: Boolean,
        allowDataRead: Boolean,
        context: Context?,
        isManualRefresh: Boolean,
        showSyncingUi: Boolean = true
    ) {
        syncMutex.withLock {
            if (_isSyncing.value) return
            _syncStatusMessage.value = ""
            syncFridayRouteFromPrefs(context)

            val isAutoRefresh = !isManualRefresh

            val hasLocalData = try {
                repo.hasReadableLocalData()
            } catch (_: Throwable) {
                false
            }
            if (hasLocalData) {
                _initialUiReady.value = true
            }

            val now = System.currentTimeMillis()
            val hasInternet = withContext(Dispatchers.IO) {
                withTimeoutOrNull(if (hasLocalData) 900L else 1500L) {
                    hasInternetConnection(context)
                } ?: false
            }
            // First install / empty local DB should bypass time-window gating
            // so the app can perform a full remote read immediately.
            val allowFirstInstallFullRead = allowDataRead && hasInternet && !hasLocalData
            val currentAutoSlot = currentAutoReadableSlotOrNone()
            val manualWithinReadInterval =
                now - manualLastRemoteReadAtMillis >= manualRemoteReadIntervalMillis
            val manualRemoteAllowed = allowFirstInstallFullRead || (hasInternet && manualWithinReadInterval)

            val autoSlotAvailable = hasInternet && currentAutoSlot != 0 && isAutoSlotAvailable(context)
            val shouldCheckRemoteForAuto = isAutoRefresh && allowDataRead && autoSlotAvailable

            val autoRemoteAllowed = allowFirstInstallFullRead || shouldCheckRemoteForAuto

            val allowRemoteRead = allowDataRead && when {
                allowFirstInstallFullRead -> true
                isManualRefresh -> manualRemoteAllowed
                else -> autoRemoteAllowed
            }


            if (showSyncingUi) {
                _isSyncing.value = true
            }
            var remoteReadSucceeded = false

            val autoSlotUsedForThisAttempt = currentAutoSlot

            try {
                val res = withContext(Dispatchers.IO) {
                    if (!allowRemoteRead) {
                        repo.syncIfNeeded(allowDataRead = false)
                    } else {
                        withTimeoutOrNull(4_000L) {
                            repo.syncIfNeeded(
                                allowDataRead = !hasLocalData,
                                forceReadOnVersionChange = true,
                                forceMetaCheckOnly = hasLocalData
                            )
                        }
                    }
                } ?: run {
                    if (isManualRefresh && _isSyncing.value) {
                        _syncStatusMessage.value = "Internet connection failed"
                    }
                    return@withLock
                }

                if (allowRemoteRead) {
                    remoteReadSucceeded = true

                    if (isAutoRefresh) {
                        syncNoticeMetaWithHomeAutoScan(context)
                    }

                    if (!hasLocalData) {
                        val localReadyNow = try {
                            repo.hasReadableLocalData()
                        } catch (_: Throwable) {
                            false
                        }

                        if (isManualRefresh && !localReadyNow) {
                            _syncStatusMessage.value = "Schedule data is still loading"
                        }
                    }
                }

                if (res.message.isNotBlank()) _updateMessage.value = res.message

                if (showBannerIfUpdated && showUpdateBanner.value &&
                    res.updated && repo.shouldShowUpdate(res.version)
                ) {
                    _showUpdate.value = true
                    repo.markSeen(res.version)
                }
            } catch (_: Throwable) {
                if (isManualRefresh && _isSyncing.value) {
                    _syncStatusMessage.value = "Internet connection failed"
                }
            } finally {

                if (remoteReadSucceeded) {
                    val completedAt = System.currentTimeMillis()
                    if (isManualRefresh) {
                        manualLastRemoteReadAtMillis = completedAt
                    } else if (autoSlotUsedForThisAttempt != 0) {
                        markAutoSlotRead(context, autoSlotUsedForThisAttempt)
                    }
                }
                _initialUiReady.value = true
                if (showSyncingUi) {
                    _isSyncing.value = false
                }
            }
        }
    }

    fun sync(context: Context? = null) {
        viewModelScope.launch {
            performRefresh(
                showBannerIfUpdated = true,
                allowDataRead = true,
                context = context,
                isManualRefresh = false
            )
        }
    }

    fun refresh(
        showBannerIfUpdated: Boolean = true,
        allowDataRead: Boolean = true,
        context: Context? = null,
        isManualRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            val hasLocalDataBeforeRefresh = try {
                repo.hasReadableLocalData()
            } catch (_: Throwable) {
                false
            }

            if (hasLocalDataBeforeRefresh) {
                _initialUiReady.value = true
            }
            else {
                val cachedItemsNow = try {
                    items.value.isNotEmpty()
                } catch (_: Throwable) {
                    false
                }
                if (cachedItemsNow) {
                    _initialUiReady.value = true
                }
            }

            performRefresh(
                showBannerIfUpdated = showBannerIfUpdated,
                allowDataRead = allowDataRead,
                context = context,
                isManualRefresh = isManualRefresh
            )
        }
    }

    fun refreshFromLocalOnceIfAvailable(context: Context? = null) {
        viewModelScope.launch {
            val hasLocalData = try {
                repo.hasReadableLocalData()
            } catch (_: Throwable) {
                false
            }

            if (!hasLocalData) return@launch

            _initialUiReady.value = true

            performRefresh(
                showBannerIfUpdated = false,
                allowDataRead = false,
                context = context,
                isManualRefresh = false,
                showSyncingUi = false
            )
        }
    }

    fun setSelectedRoute(routeNo: String) {
        viewModelScope.launch { repo.setSelectedRoute(routeNo) }
    }

    fun setSelectedFridayRoute(routeNo: String) {
        selectedFridayRoute.value = routeNo.trim().ifBlank { "ALL" }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repo.setDarkMode(enabled) }
    }

    fun setShowUpdateBanner(enabled: Boolean) {
        viewModelScope.launch { repo.setShowUpdateBanner(enabled) }
    }

    fun setCompactMode(enabled: Boolean) {
        viewModelScope.launch { repo.setCompactMode(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setNotificationsEnabled(enabled) }
    }

    fun setNotifyLeadMinutes(minutes: Int) {
        viewModelScope.launch { repo.setNotifyLeadMinutes(minutes) }
    }


    private fun isVersionNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> = v.trim()
            .removePrefix("v")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
            .let { if (it.isEmpty()) listOf(0) else it }

        val a = parts(latest)
        val b = parts(current)
        val n = maxOf(a.size, b.size)

        for (i in 0 until n) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai > bi
        }
        return false
    }

    fun updateRouteNotifications(
        context: Context,
        selectedRoute: String,
        currentItems: List<UiSchedule>,
        enabled: Boolean,
        leadMinutes: Int
    ) {
        val todayIsFriday = try {
            LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY
        } catch (_: Throwable) {
            false
        }

        val normalizedRoute = selectedRoute.trim()
        val isAllRoute = normalizedRoute.equals("ALL", ignoreCase = true)
        val isFridayRoute = normalizedRoute.startsWith("F", ignoreCase = true)

        if (!enabled || isAllRoute) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

        if (todayIsFriday && !isFridayRoute) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

        if (!todayIsFriday && isFridayRoute) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

        val routeItems = currentItems.filter { it.routeNo.equals(selectedRoute, ignoreCase = true) }

        if (routeItems.isEmpty()) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

        // আগে পুরানো alarm clear
        RouteNotificationScheduler.cancelAll(context)

        // এখন selected route-এর সব matching item schedule
        routeItems.forEach { item ->
            RouteNotificationScheduler.scheduleForRoute(
                context = context,
                routeNo = item.routeNo,
                routeName = item.routeName,
                startTimes = item.startTimes,
                departureTimes = item.departureTimes,
                leadMinutes = leadMinutes,
                appliesOn = item.appliesOn
            )
        }
    }
}

/* ---------------- Notifications Scheduler ---------------- */

private object RouteNotificationScheduler {
    private const val CHANNEL_ID = "route_notifications"
    private const val PREFS_NAME = "route_notification_scheduler"
    private const val KEY_SCHEDULED_CODES = "scheduled_request_codes"
    private const val NOTIF_PREFS_NAME = "route_notification_ids"
    private const val KEY_NOTIF_IDS = "route_notification_ids"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Route Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                )
                ch.description = "Alerts before selected route times"
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun cancelAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val notifPrefs = context.getSharedPreferences(NOTIF_PREFS_NAME, Context.MODE_PRIVATE)
        val notifRaw = notifPrefs.getString(KEY_NOTIF_IDS, "").orEmpty()

        notifRaw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .forEach { notificationId ->
                try {
                    nm.cancel(notificationId)
                } catch (_: Throwable) {
                }
            }

        notifPrefs.edit().remove(KEY_NOTIF_IDS).apply()

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SCHEDULED_CODES, "").orEmpty()

        raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .forEach { requestCode ->
                val intent = Intent(context, RouteAlarmReceiver::class.java).apply {
                    Intent.setAction = "ROUTE_ALARM"
                }

                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                PendingIntent.FLAG_IMMUTABLE else 0)
                )

                try {
                    am.cancel(pi)
                    pi.cancel()
                } catch (_: Throwable) {
                }
            }

        prefs.edit().remove(KEY_SCHEDULED_CODES).apply()
    }
    fun rememberNotificationId(
        context: Context,
        notificationId: Int
    ) {
        val prefs = context.getSharedPreferences(NOTIF_PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_NOTIF_IDS, "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toMutableSet()

        existing.add(notificationId)

        prefs.edit()
            .putString(KEY_NOTIF_IDS, existing.sorted().joinToString(","))
            .apply()
    }

    private fun rememberScheduledRequestCode(
        context: Context,
        requestCode: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_SCHEDULED_CODES, "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toMutableSet()

        existing.add(requestCode)

        prefs.edit()
            .putString(KEY_SCHEDULED_CODES, existing.sorted().joinToString(","))
            .apply()
    }

    fun scheduleForRoute(
        context: Context,
        routeNo: String,
        routeName: String,
        startTimes: List<String>,
        departureTimes: List<String>,
        leadMinutes: Int,
        appliesOn: String
    ) {
        ensureChannel(context)

        val all = buildList {
            startTimes.forEach { add("Start" to it) }
            departureTimes.forEach { add("Departure" to it) }
        }

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val canExact = !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
        if (!canExact) {
            Log.w("RouteNotificationScheduler", "Exact alarm not allowed; using inexact alarms")
        }

        all.forEach { (kind, raw) ->
            val parsed = parseTime(raw) ?: return@forEach
            val time = parsed.first
            val note = parsed.second

            var whenZdt = now.with(time)

            if (appliesOn.equals("FRIDAY", ignoreCase = true)) {
                // Move to next-or-today Friday at this time
                val dow = now.dayOfWeek
                val daysUntil = (DayOfWeek.FRIDAY.value - dow.value + 7) % 7
                whenZdt = now.plusDays(daysUntil.toLong()).with(time)
                // If already passed (or too close), move to next week
                if (whenZdt.isBefore(now.plusMinutes(1))) whenZdt = whenZdt.plusWeeks(1)
            } else {
                // DAILY behavior
                if (whenZdt.isBefore(now.plusMinutes(1))) whenZdt = whenZdt.plusDays(1)
            }
            val fireAt = whenZdt.minusMinutes(leadMinutes.toLong())
            if (fireAt.isBefore(now.plusSeconds(10))) return@forEach

            val minutesOfDay = time.hour * 60 + time.minute
            val requestCode = (routeNo.hashCode() * 31) + minutesOfDay + (kind.hashCode() * 17)

            val intent = Intent(context, RouteAlarmReceiver::class.java).apply {
                Intent.setAction = "ROUTE_ALARM"
                putExtra("routeNo", routeNo)
                putExtra("kind", kind)
                putExtra("timeText", formatTime(time))
                putExtra("note", note)
            }

            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            rememberScheduledRequestCode(context, requestCode)

            val triggerAtMillis = fireAt.toInstant().toEpochMilli()
            when {
                canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                canExact -> {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                else -> {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            }
        }
    }

    private fun parseTime(raw: String): Pair<LocalTime, String>? {
        val r = raw.trim()
        if (r.isBlank()) return null

        val regex = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[APap][Mm])")
        val m = regex.find(r) ?: return null
        val timeStr = m.value.replace(" ", "").uppercase()
        val note = r.replace(m.value, "").trim().trimStart('-', '—').trim()

        val fmt1 = DateTimeFormatter.ofPattern("h:mma")
        val fmt2 = DateTimeFormatter.ofPattern("h:mm:ssa")

        val t = try {
            if (timeStr.count { it == ':' } == 2) LocalTime.parse(timeStr, fmt2)
            else LocalTime.parse(timeStr, fmt1)
        } catch (_: Throwable) {
            return null
        }

        return t to note
    }

    private fun formatTime(t: LocalTime): String =
        t.format(DateTimeFormatter.ofPattern("h:mm a"))
}

class RouteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routeNo = intent.getStringExtra("routeNo").orEmpty()
        val kind = intent.getStringExtra("kind").orEmpty()
        val timeText = intent.getStringExtra("timeText").orEmpty()
        val note = intent.getStringExtra("note").orEmpty()

        if (routeNo.isBlank() || timeText.isBlank()) return

        RouteNotificationScheduler.ensureChannel(context)

        val title = timeText  // ✅ time is primary
        val line1 = buildString {
            if (kind.isNotBlank()) {
                append(kind)
                append(" • ")
            }
            append(routeNo)
        }

        val bigText = buildString {
            append(line1)
            if (note.isNotBlank()) {
                append("\n")
                append(note)
            }
        }

        val notificationId = (routeNo + kind + timeText).hashCode()
        val notif = NotificationCompat.Builder(context, "route_notifications")
            .setSmallIcon(R.drawable.ic_dialog_info)
            // TIME is the primary headline
            .setContentTitle(title)
            // Secondary info in the collapsed view
            .setContentText(line1)
            // Expanded view keeps TIME as big title + shows details
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText("DIU Transport")
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId, notif)

        try {
            RouteNotificationScheduler.rememberNotificationId(context, notificationId)
        } catch (_: Throwable) {
        }
    }
}