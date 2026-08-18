package com.sohan.diutransportschedule.sync

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("prefs")

class VersionStore(private val context: Context) {
    private val KEY_VERSION = intPreferencesKey("version")
    private val KEY_SEEN_VERSION = intPreferencesKey("seen_version")
    private val KEY_SCHEDULE_UPDATED_AT = longPreferencesKey("schedule_updated_at")

    suspend fun getLocalVersion(): Int =
        (context.dataStore.data.first()[KEY_VERSION] ?: 0)

    suspend fun setLocalVersion(v: Int) {
        context.dataStore.edit { it[KEY_VERSION] = v }
    }

    suspend fun getSeenVersion(): Int =
        (context.dataStore.data.first()[KEY_SEEN_VERSION] ?: 0)

    suspend fun setSeenVersion(v: Int) {
        context.dataStore.edit { it[KEY_SEEN_VERSION] = v }
    }

    val scheduleUpdatedAtMillisFlow = context.dataStore.data.map { preferences ->
        preferences[KEY_SCHEDULE_UPDATED_AT] ?: 0L
    }

    suspend fun setScheduleUpdatedAtMillis(value: Long) {
        context.dataStore.edit { it[KEY_SCHEDULE_UPDATED_AT] = value }
    }
}
