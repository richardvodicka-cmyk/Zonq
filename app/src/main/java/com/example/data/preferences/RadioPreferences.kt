package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

import com.example.playback.NormalizationProfile

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "radio_settings")

enum class AudioQuality(val minBitrate: Int) {
    AUTO(0),
    HIGH(192),
    STANDARD(128),
    SAVER(64)
}

enum class BufferDuration(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForRebufferMs: Int
) {
    SHORT(5000, 15000, 1000, 1500),       // 5s-15s puffer, gyors indulás
    NORMAL(10000, 30000, 1500, 2500),     // 10s-30s puffer (alapértelmezett, utazáshoz és menet közbeni stabilitáshoz)
    LARGE(20000, 60000, 2000, 3500)       // 20s-60s puffer, extra stabilitás gyenge hálózaton
}

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class DefaultScreen {
    ALL, FAVORITES
}

enum class AppLanguage(val code: String, val displayNameRes: Int) {
    SYSTEM("", com.example.R.string.lang_system_default),
    HUNGARIAN("hu", com.example.R.string.lang_hungarian),
    SLOVAK("sk", com.example.R.string.lang_slovak),
    ENGLISH("en", com.example.R.string.lang_english)
}

data class UserPreferences(
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val bufferDuration: BufferDuration = BufferDuration.NORMAL,
    val autoPlayOnStart: Boolean = false,
    val defaultScreen: DefaultScreen = DefaultScreen.ALL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val audioNormalizationEnabled: Boolean = true,
    val normalizationProfile: NormalizationProfile = NormalizationProfile.BALANCED,
    val tvKeepScreenOn: Boolean = true,
    val lastStationId: String? = null,
    val lastStationName: String? = null,
    val lastStationUrl: String? = null,
    val zonqToken: String? = null,
    val zonqEmail: String? = null,
    val zonqRev: String? = null,
    val zonqSyncStatus: String = "idle", // "synced", "syncing", "offline", "idle"
    val zonqSyncTime: String? = null,
    val lastZonqSyncTimestamp: Long = 0L,
    val lastDismissedUpdateTimestamp: Long = 0L
) {
    val isLoggedIn: Boolean get() = !zonqToken.isNullOrBlank()
}

class RadioPreferences(private val context: Context) {

    private object Keys {
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val BUFFER_DURATION = stringPreferencesKey("buffer_duration")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_on_start")
        val DEFAULT_SCREEN = stringPreferencesKey("default_screen")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val AUDIO_NORMALIZATION_ENABLED = booleanPreferencesKey("audio_normalization_enabled")
        val NORMALIZATION_PROFILE = stringPreferencesKey("normalization_profile")
        val TV_KEEP_SCREEN_ON = booleanPreferencesKey("tv_keep_screen_on")
        val LAST_STATION_ID = stringPreferencesKey("last_station_id")
        val LAST_STATION_NAME = stringPreferencesKey("last_station_name")
        val LAST_STATION_URL = stringPreferencesKey("last_station_url")
        val ZONQ_TOKEN = stringPreferencesKey("zonq_token")
        val ZONQ_EMAIL = stringPreferencesKey("zonq_email")
        val ZONQ_REV = stringPreferencesKey("zonq_rev")
        val ZONQ_PENDING = stringPreferencesKey("zonq_pending")
        val ZONQ_SYNC_STATUS = stringPreferencesKey("zonq_sync_status")
        val ZONQ_SYNC_TIME = stringPreferencesKey("zonq_sync_time")
        val LAST_DAILY_UPDATE_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_daily_update_timestamp")
        val LAST_ZONQ_SYNC_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_zonq_sync_timestamp")
        val LAST_DISMISSED_UPDATE_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_dismissed_update_timestamp")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch {
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs ->
            val qualityStr = prefs[Keys.AUDIO_QUALITY] ?: AudioQuality.AUTO.name
            val quality = try { AudioQuality.valueOf(qualityStr) } catch (_: Exception) { AudioQuality.AUTO }

            val bufferStr = prefs[Keys.BUFFER_DURATION] ?: BufferDuration.NORMAL.name
            val buffer = try { BufferDuration.valueOf(bufferStr) } catch (_: Exception) { BufferDuration.NORMAL }

            val screenStr = prefs[Keys.DEFAULT_SCREEN] ?: DefaultScreen.ALL.name
            val screen = try { DefaultScreen.valueOf(screenStr) } catch (_: Exception) { DefaultScreen.ALL }

            val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val theme = try { ThemeMode.valueOf(themeStr) } catch (_: Exception) { ThemeMode.SYSTEM }

            val langStr = prefs[Keys.APP_LANGUAGE] ?: AppLanguage.SYSTEM.name
            val lang = try { AppLanguage.valueOf(langStr) } catch (_: Exception) { AppLanguage.SYSTEM }
            currentLanguage = lang
            currentBufferDuration = buffer

            val normalizationEnabled = prefs[Keys.AUDIO_NORMALIZATION_ENABLED] ?: true
            val profileStr = prefs[Keys.NORMALIZATION_PROFILE] ?: NormalizationProfile.BALANCED.id
            val profile = NormalizationProfile.entries.find { it.id == profileStr } ?: NormalizationProfile.BALANCED

            val tvKeepScreenOn = prefs[Keys.TV_KEEP_SCREEN_ON] ?: true

            val token = prefs[Keys.ZONQ_TOKEN]
            val email = prefs[Keys.ZONQ_EMAIL]
            val rev = prefs[Keys.ZONQ_REV]
            val syncStatus = prefs[Keys.ZONQ_SYNC_STATUS] ?: "idle"
            val syncTime = prefs[Keys.ZONQ_SYNC_TIME]
            cachedToken = token
            cachedRev = rev

            UserPreferences(
                audioQuality = quality,
                bufferDuration = buffer,
                autoPlayOnStart = prefs[Keys.AUTO_PLAY] ?: false,
                defaultScreen = screen,
                themeMode = theme,
                appLanguage = lang,
                audioNormalizationEnabled = normalizationEnabled,
                normalizationProfile = profile,
                tvKeepScreenOn = tvKeepScreenOn,
                lastStationId = prefs[Keys.LAST_STATION_ID],
                lastStationName = prefs[Keys.LAST_STATION_NAME],
                lastStationUrl = prefs[Keys.LAST_STATION_URL],
                zonqToken = token,
                zonqEmail = email,
                zonqRev = rev,
                zonqSyncStatus = syncStatus,
                zonqSyncTime = syncTime,
                lastZonqSyncTimestamp = prefs[Keys.LAST_ZONQ_SYNC_TIMESTAMP] ?: 0L,
                lastDismissedUpdateTimestamp = prefs[Keys.LAST_DISMISSED_UPDATE_TIMESTAMP] ?: 0L
            )
        }

    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedRev: String? = null
        private set

    @Volatile
    var currentLanguage: AppLanguage = AppLanguage.SYSTEM
        private set

    @Volatile
    var currentBufferDuration: BufferDuration = BufferDuration.NORMAL
        private set

    suspend fun setAudioQuality(quality: AudioQuality) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.AUDIO_QUALITY] = quality.name
            }
        } catch (_: Exception) {}
    }

    suspend fun setBufferDuration(duration: BufferDuration) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.BUFFER_DURATION] = duration.name
            }
        } catch (_: Exception) {}
    }

    suspend fun setAutoPlayOnStart(enabled: Boolean) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.AUTO_PLAY] = enabled
            }
        } catch (_: Exception) {}
    }

    suspend fun setDefaultScreen(screen: DefaultScreen) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.DEFAULT_SCREEN] = screen.name
            }
        } catch (_: Exception) {}
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.THEME_MODE] = mode.name
            }
        } catch (_: Exception) {}
    }

    suspend fun setAppLanguage(lang: AppLanguage) {
        currentLanguage = lang
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.APP_LANGUAGE] = lang.name
            }
        } catch (_: Exception) {}
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.AUDIO_NORMALIZATION_ENABLED] = enabled
            }
        } catch (_: Exception) {}
    }

    suspend fun setNormalizationProfile(profile: NormalizationProfile) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.NORMALIZATION_PROFILE] = profile.id
            }
        } catch (_: Exception) {}
    }

    suspend fun saveAuth(token: String, email: String, rev: String? = null) {
        cachedToken = token
        cachedRev = rev
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.ZONQ_TOKEN] = token
                prefs[Keys.ZONQ_EMAIL] = email
                if (!rev.isNullOrBlank()) {
                    prefs[Keys.ZONQ_REV] = rev
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun clearAuth() {
        cachedToken = null
        cachedRev = null
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(Keys.ZONQ_TOKEN)
                prefs.remove(Keys.ZONQ_EMAIL)
                prefs.remove(Keys.ZONQ_REV)
                prefs.remove(Keys.ZONQ_PENDING)
                prefs[Keys.ZONQ_SYNC_STATUS] = "idle"
                prefs.remove(Keys.ZONQ_SYNC_TIME)
            }
        } catch (_: Exception) {}
    }

    suspend fun setZonqRev(rev: String?) {
        cachedRev = rev
        try {
            context.dataStore.edit { prefs ->
                if (!rev.isNullOrBlank()) {
                    prefs[Keys.ZONQ_REV] = rev
                } else {
                    prefs.remove(Keys.ZONQ_REV)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun getZonqRev(): String? {
        if (!cachedRev.isNullOrBlank()) return cachedRev
        return try {
            context.dataStore.data.firstOrNull()?.get(Keys.ZONQ_REV)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPendingQueueJson(): String {
        return try {
            context.dataStore.data.firstOrNull()?.get(Keys.ZONQ_PENDING) ?: "[]"
        } catch (_: Exception) {
            "[]"
        }
    }

    suspend fun setPendingQueueJson(json: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.ZONQ_PENDING] = json
            }
        } catch (_: Exception) {}
    }

    suspend fun setSyncStatus(status: String, formattedTime: String? = null) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.ZONQ_SYNC_STATUS] = status
                if (formattedTime != null) {
                    prefs[Keys.ZONQ_SYNC_TIME] = formattedTime
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun saveLastStation(id: String, name: String, url: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.LAST_STATION_ID] = id
                prefs[Keys.LAST_STATION_NAME] = name
                prefs[Keys.LAST_STATION_URL] = url
            }
        } catch (_: Exception) {}
    }

    suspend fun getLastDailyUpdateTime(): Long {
        return try {
            val prefs = context.dataStore.data.firstOrNull()
            prefs?.get(Keys.LAST_DAILY_UPDATE_TIMESTAMP) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun setLastDailyUpdateTime(timestamp: Long) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.LAST_DAILY_UPDATE_TIMESTAMP] = timestamp
            }
        } catch (_: Exception) {}
    }

    suspend fun setTvKeepScreenOn(enabled: Boolean) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.TV_KEEP_SCREEN_ON] = enabled
            }
        } catch (_: Exception) {}
    }

    suspend fun setLastZonqSyncTime(timestamp: Long) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.LAST_ZONQ_SYNC_TIMESTAMP] = timestamp
            }
        } catch (_: Exception) {}
    }

    suspend fun setLastDismissedUpdateTimestamp(timestamp: Long) {
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.LAST_DISMISSED_UPDATE_TIMESTAMP] = timestamp
            }
        } catch (_: Exception) {}
    }
}
