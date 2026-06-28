package com.omnisearch.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnisearch_settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val OMNISEARCH_ADDRESS = stringPreferencesKey("omnisearch_address")
        private val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        private val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val ACTIVE_SEARCH_DRIVE = stringPreferencesKey("active_search_drive")
        private val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        private val SORT_TYPE = stringPreferencesKey("sort_type")
        private val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        private val USE_VIDEO_GRID = booleanPreferencesKey("use_video_grid")
        private val USE_IMAGE_ALBUMS = booleanPreferencesKey("use_image_albums")
        private val IMAGE_SORT_TYPE = stringPreferencesKey("image_sort_type")
        private val IMAGE_SORT_ASCENDING = booleanPreferencesKey("image_sort_ascending")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val THEME_COLOR = stringPreferencesKey("theme_color")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "SYSTEM"
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    val themeColorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_COLOR] ?: "SLATE"
    }

    suspend fun saveThemeColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_COLOR] = color
        }
    }

    val showHiddenFilesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_HIDDEN_FILES] ?: false
    }

    suspend fun saveShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_FILES] = show
        }
    }

    val sortTypeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SORT_TYPE] ?: "NAME"
    }

    suspend fun saveSortType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[SORT_TYPE] = type
        }
    }

    val sortAscendingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SORT_ASCENDING] ?: true
    }

    suspend fun saveSortAscending(ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SORT_ASCENDING] = ascending
        }
    }

    val useVideoGridFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_VIDEO_GRID] ?: false
    }

    suspend fun saveUseVideoGrid(grid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_VIDEO_GRID] = grid
        }
    }

    val useImageAlbumsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_IMAGE_ALBUMS] ?: true
    }

    suspend fun saveUseImageAlbums(albums: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_IMAGE_ALBUMS] = albums
        }
    }

    val imageSortTypeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IMAGE_SORT_TYPE] ?: "DATE"
    }

    suspend fun saveImageSortType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_SORT_TYPE] = type
        }
    }

    val imageSortAscendingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IMAGE_SORT_ASCENDING] ?: false
    }

    suspend fun saveImageSortAscending(ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_SORT_ASCENDING] = ascending
        }
    }

    val activeSearchDriveFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_SEARCH_DRIVE] ?: "C"
    }

    suspend fun saveActiveSearchDrive(drive: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_SEARCH_DRIVE] = drive
        }
    }

    val omniSearchAddressFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[OMNISEARCH_ADDRESS] ?: ""
    }

    suspend fun saveAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[OMNISEARCH_ADDRESS] = address
        }
    }

    suspend fun clearAddress() {
        context.dataStore.edit { preferences ->
            preferences.remove(OMNISEARCH_ADDRESS)
        }
    }

    val appLockPinFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_PIN] ?: ""
    }

    suspend fun saveAppLockPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_PIN] = pin
        }
    }

    suspend fun removeAppLockPin() {
        context.dataStore.edit { preferences ->
            preferences.remove(APP_LOCK_PIN)
            preferences.remove(APP_LOCK_BIOMETRIC)
        }
    }

    val appLockBiometricFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_BIOMETRIC] ?: false
    }

    suspend fun saveAppLockBiometric(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_BIOMETRIC] = enabled
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        var id = ""
        context.dataStore.edit { preferences ->
            val existing = preferences[DEVICE_ID]
            if (existing != null && existing.isNotEmpty()) {
                id = existing
            } else {
                id = "mobile-${System.currentTimeMillis().toString(36)}-${UUID.randomUUID().toString().replace("-", "").take(6)}"
                preferences[DEVICE_ID] = id
            }
        }
        return id
    }
}
