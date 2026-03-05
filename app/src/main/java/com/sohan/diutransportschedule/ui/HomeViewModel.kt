package com.sohan.diutransportschedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohan.diutransportschedule.db.DbScheduleItem
import com.sohan.diutransportschedule.db.JsonConverters
import com.sohan.diutransportschedule.sync.ScheduleRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sohan.diutransportschedule.BuildConfig
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

private fun DbScheduleItem.toUi(): UiSchedule = UiSchedule(
    routeNo = routeNo,
    routeName = routeName,
    routeDetails = routeDetails,
    startTimes = JsonConverters.jsonToList(startTimesJson),
    departureTimes = JsonConverters.jsonToList(departureTimesJson),
    appliesOn = if (routeNo.trim().startsWith("F", ignoreCase = true)) "FRIDAY" else "DAILY"
)
class HomeViewModel(
    private val repo: ScheduleRepository
) : ViewModel() {

    init {
        viewModelScope.launch { repo.ensureDefaultPrefs() }
    }

    // Current installed app version (from app/build.gradle)
    val currentAppVersionName: String = BuildConfig.VERSION_NAME

    private val query = MutableStateFlow("")

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // 🔒 Throttle sync calls to reduce unnecessary Firestore reads
    private var lastSyncAtMillis: Long = 0L
    private val minSyncIntervalMillis = 5 * 60 * 1000L // 5 minutes
    // 🔒 Daily sync cap (extra protection against excessive reads)
    private var syncCountToday: Int = 0
    private var syncDayStamp: String = java.time.LocalDate.now().toString()
    private val maxSyncsPerDay = 30

    private fun canSyncToday(): Boolean {
        val today = java.time.LocalDate.now().toString()
        if (today != syncDayStamp) {
            syncDayStamp = today
            syncCountToday = 0
        }
        return syncCountToday < maxSyncsPerDay
    }

    private fun markSyncDone() {
        syncCountToday++
    }

    private val _showUpdate = MutableStateFlow(false)
    val showUpdate: StateFlow<Boolean> = _showUpdate.asStateFlow()

    private val _updateMessage = MutableStateFlow("")
    val updateMessage: StateFlow<String> = _updateMessage.asStateFlow()


    // prefs
    val selectedRoute: StateFlow<String> = repo.selectedRouteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ALL")

    val darkMode: StateFlow<Boolean> = repo.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

            val options = unique.entries
                .map { (no, name) ->
                    val label = if (name.isNotBlank()) "$name ($no)" else no
                    RouteOption(routeNo = no, label = label)
                }
                .sortedBy { it.routeNo }

            listOf(RouteOption(routeNo = "ALL", label = "All Routes")) + options
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            listOf(RouteOption("ALL", "All Routes"))
        )

    val selectedRouteLabel: StateFlow<String> = combine(selectedRoute, routeOptions) { sel, opts ->
        opts.firstOrNull { it.routeNo.equals(sel, ignoreCase = true) }?.label ?: sel
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "All Routes")

    // ✅ Home items: Search overrides Profile route filter
    val items: StateFlow<List<UiSchedule>> =
        combine(localUi, selectedRoute, query) { list, route, q ->
            val rawQuery = q.trim()
            val qq = rawQuery.lowercase()

            val todayIsFriday = try {
                java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
            } catch (_: Throwable) {
                false
            }

            val noFilterHome = rawQuery.isBlank() && route.equals("ALL", ignoreCase = true)

            val base = when {
                // SEARCH: always show everything
                rawQuery.isNotEmpty() -> list

                // HOME (no filter): show everything including Friday
                noFilterHome -> list

                // FILTERED (route selected): apply day rule
                else -> {
                    val dayFiltered = if (todayIsFriday) {
                        list.filter { it.appliesOn == "FRIDAY" }
                    } else {
                        list.filter { it.appliesOn != "FRIDAY" }
                    }

                    // ✅ Friday override: if a specific route is selected (not ALL), still show Friday schedules.
                    if (todayIsFriday) {
                        dayFiltered
                    } else {
                        if (route.equals("ALL", ignoreCase = true)) dayFiltered
                        else dayFiltered.filter { it.routeNo.equals(route, ignoreCase = true) }
                    }
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

            // DAILY first, FRIDAY at the end
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

    fun sync() {
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            val now = System.currentTimeMillis()
            if (now - lastSyncAtMillis < minSyncIntervalMillis) {
                return@launch
            }
            if (!canSyncToday()) {
                return@launch
            }
            _isSyncing.value = true
            try {
                val res = repo.syncIfNeeded()
                if (res.message.isNotBlank()) _updateMessage.value = res.message

                if (showUpdateBanner.value && res.updated && repo.shouldShowUpdate(res.version)) {
                    _showUpdate.value = true
                    repo.markSeen(res.version)
                }
            } catch (_: Throwable) {
            } finally {
                lastSyncAtMillis = System.currentTimeMillis()
                markSyncDone()
                _isSyncing.value = false
            }
        }
    }

    fun refresh(showBannerIfUpdated: Boolean = true) {
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            val now = System.currentTimeMillis()
            if (now - lastSyncAtMillis < minSyncIntervalMillis) {
                // Too soon since last sync → skip to avoid extra reads
                return@launch
            }
            if (!canSyncToday()) {
                return@launch
            }
            _isSyncing.value = true
            try {
                val res = repo.syncIfNeeded()
                if (res.message.isNotBlank()) _updateMessage.value = res.message

                if (showBannerIfUpdated && showUpdateBanner.value &&
                    res.updated && repo.shouldShowUpdate(res.version)
                ) {
                    _showUpdate.value = true
                    repo.markSeen(res.version)
                }
            } catch (_: Throwable) {
            } finally {
                lastSyncAtMillis = System.currentTimeMillis()
                markSyncDone()
                _isSyncing.value = false
            }
        }
    }

    fun setSelectedRoute(routeNo: String) {
        viewModelScope.launch { repo.setSelectedRoute(routeNo) }
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
        context: android.content.Context,
        selectedRoute: String,
        currentItems: List<UiSchedule>,
        enabled: Boolean,
        leadMinutes: Int
    ) {
        // ✅ Friday: keep notifications OFF
        val todayIsFriday = try {
            java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
        } catch (_: Throwable) {
            false
        }
        if (todayIsFriday) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

        if (!enabled || selectedRoute.equals("ALL", ignoreCase = true)) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

        val item = currentItems.firstOrNull { it.routeNo.equals(selectedRoute, ignoreCase = true) }
        if (item == null) {
            RouteNotificationScheduler.cancelAll(context)
            return
        }

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

/* ---------------- Notifications Scheduler ---------------- */

private object RouteNotificationScheduler {
    private const val CHANNEL_ID = "route_notifications"

    fun ensureChannel(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm =
                context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Route Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                ch.description = "Alerts before selected route times"
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun cancelAll(context: android.content.Context) {
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
    }

    fun scheduleForRoute(
        context: android.content.Context,
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

        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager

        val canExact = !(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !am.canScheduleExactAlarms())
        if (!canExact) {
            android.util.Log.w("RouteNotificationScheduler", "Exact alarm not allowed; using inexact alarms")
        }

        all.forEach { (kind, raw) ->
            val parsed = parseTime(raw) ?: return@forEach
            val time = parsed.first
            val note = parsed.second

            var whenZdt = now.with(time)

            if (appliesOn.equals("FRIDAY", ignoreCase = true)) {
                // Move to next-or-today Friday at this time
                val dow = now.dayOfWeek
                val daysUntil = (java.time.DayOfWeek.FRIDAY.value - dow.value + 7) % 7
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

            val intent = android.content.Intent(context, RouteAlarmReceiver::class.java).apply {
                action = "ROUTE_ALARM"
                putExtra("routeNo", routeNo)
                putExtra("kind", kind)
                putExtra("timeText", formatTime(time))
                putExtra("note", note)
            }

            val pi = android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = fireAt.toInstant().toEpochMilli()
            when {
                canExact && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                canExact -> {
                    am.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                else -> {
                    am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            }
        }
    }

    private fun parseTime(raw: String): Pair<java.time.LocalTime, String>? {
        val r = raw.trim()
        if (r.isBlank()) return null

        val regex = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[APap][Mm])")
        val m = regex.find(r) ?: return null
        val timeStr = m.value.replace(" ", "").uppercase()
        val note = r.replace(m.value, "").trim().trimStart('-', '—').trim()

        val fmt1 = java.time.format.DateTimeFormatter.ofPattern("h:mma")
        val fmt2 = java.time.format.DateTimeFormatter.ofPattern("h:mm:ssa")

        val t = try {
            if (timeStr.count { it == ':' } == 2) java.time.LocalTime.parse(timeStr, fmt2)
            else java.time.LocalTime.parse(timeStr, fmt1)
        } catch (_: Throwable) {
            return null
        }

        return t to note
    }

    private fun formatTime(t: java.time.LocalTime): String =
        t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}

class RouteAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
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

        val notif = androidx.core.app.NotificationCompat.Builder(context, "route_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            // TIME is the primary headline
            .setContentTitle(title)
            // Secondary info in the collapsed view
            .setContentText(line1)
            // Expanded view keeps TIME as big title + shows details
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText("DIU Transport")
                    .bigText(bigText)
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        androidx.core.app.NotificationManagerCompat.from(context)
            .notify((routeNo + kind + timeText).hashCode(), notif)
    }
}