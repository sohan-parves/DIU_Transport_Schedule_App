package com.sohan.diutransportschedule.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import androidx.datastore.preferences.core.intPreferencesKey

private const val DATASTORE_NAME = "user_prefs"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

class UserPrefs(private val context: Context) {

    private companion object {
        private val KEY_SELECTED_ROUTE = stringPreferencesKey("selected_route")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")

        // নতুন দুইটা option
        private val KEY_SHOW_UPDATE_BANNER = booleanPreferencesKey("show_update_banner")
        private val KEY_COMPACT_MODE = booleanPreferencesKey("compact_mode")

        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFY_LEAD_MINUTES = intPreferencesKey("notify_lead_minutes")
    }

    // ---------------- Selected Route ----------------
    val selectedRouteFlow: Flow<String> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { pref -> pref[KEY_SELECTED_ROUTE] ?: "ALL" }

    val notificationsEnabledFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }

    val notifyLeadMinutesFlow: Flow<Int> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_NOTIFY_LEAD_MINUTES] ?: 30 }
    suspend fun setSelectedRoute(routeNo: String) {
        context.dataStore.edit { pref -> pref[KEY_SELECTED_ROUTE] = routeNo }
    }

    // ---------------- Dark Mode ----------------
    val darkModeFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { pref -> pref[KEY_DARK_MODE] ?: true }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_DARK_MODE] = enabled }
    }

    // ---------------- Update Banner ----------------
    val showUpdateBannerFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { pref -> pref[KEY_SHOW_UPDATE_BANNER] ?: true }

    suspend fun setShowUpdateBanner(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_SHOW_UPDATE_BANNER] = enabled }
    }

    // ---------------- Compact Mode ----------------
    val compactModeFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { pref -> pref[KEY_COMPACT_MODE] ?: true }

    suspend fun setCompactMode(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_COMPACT_MODE] = enabled }
    }
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotifyLeadMinutes(minutes: Int) {
        val safe = minutes.coerceIn(5, 120)
        context.dataStore.edit { it[KEY_NOTIFY_LEAD_MINUTES] = safe }
    }
    suspend fun ensureDefaults() {
        context.dataStore.edit { pref ->
            if (!pref.contains(KEY_DARK_MODE)) pref[KEY_DARK_MODE] = true
            if (!pref.contains(KEY_SHOW_UPDATE_BANNER)) pref[KEY_SHOW_UPDATE_BANNER] = true
            if (!pref.contains(KEY_COMPACT_MODE)) pref[KEY_COMPACT_MODE] = true
            if (!pref.contains(KEY_NOTIFICATIONS_ENABLED)) pref[KEY_NOTIFICATIONS_ENABLED] = true
            if (!pref.contains(KEY_NOTIFY_LEAD_MINUTES)) pref[KEY_NOTIFY_LEAD_MINUTES] = 30
            if (!pref.contains(KEY_SELECTED_ROUTE)) pref[KEY_SELECTED_ROUTE] = "ALL"
        }
    }
}