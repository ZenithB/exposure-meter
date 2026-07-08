package com.zenni.exposuremeter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User settings persisted across launches (brief §6). */
data class Settings(
    /** Incident calibration offset, EV (±3, tenths). */
    val incidentCalibration: Double = 0.0,
    /** Reflected calibration offset, EV (±3, tenths) — used from Phase 4. */
    val reflectedCalibration: Double = 0.0,
    /** Haptic tick on wheel detents. */
    val haptics: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads and writes [Settings] via Jetpack DataStore (brief §6). No Android
 * `SharedPreferences`, no proprietary storage — F-Droid clean.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            incidentCalibration = prefs[INCIDENT_CAL] ?: 0.0,
            reflectedCalibration = prefs[REFLECTED_CAL] ?: 0.0,
            haptics = prefs[HAPTICS] ?: true,
        )
    }

    suspend fun setIncidentCalibration(value: Double) {
        store.edit { it[INCIDENT_CAL] = value.coerceIn(-3.0, 3.0) }
    }

    suspend fun setReflectedCalibration(value: Double) {
        store.edit { it[REFLECTED_CAL] = value.coerceIn(-3.0, 3.0) }
    }

    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    private companion object {
        val INCIDENT_CAL = doublePreferencesKey("incident_calibration")
        val REFLECTED_CAL = doublePreferencesKey("reflected_calibration")
        val HAPTICS = booleanPreferencesKey("haptics")
    }
}
