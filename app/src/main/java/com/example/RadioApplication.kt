package com.example

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.example.data.CuratedStations
import com.example.data.local.AppDatabase
import com.example.data.model.Station
import com.example.data.preferences.RadioPreferences
import com.example.data.repository.StationRepository
import com.example.playback.RadioPlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@UnstableApi
class RadioApplication : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this) }
    val preferences by lazy { RadioPreferences(this) }
    val repository by lazy { StationRepository(database.stationDao(), preferences, this) }
    val albumArtRepository by lazy { com.example.data.repository.AlbumArtRepository() }
    val playbackManager by lazy { RadioPlaybackManager.getInstance(this, repository, preferences, albumArtRepository) }
    val appUpdateManager by lazy { com.example.data.remote.AppUpdateManager(this) }

    override fun onCreate() {
        super.onCreate()

        // Install safety crash handler to intercept and log unexpected AudioEffect / AudioSession / Compose crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("RadioApplication", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            val msg = throwable.message.orEmpty()
            val isAudioOrComposeGlitch = throwable is IllegalArgumentException &&
                (msg.contains("valueRange") || msg.contains("Cannot coerce value") || msg.contains("Equalizer"))
            if (!isAudioOrComposeGlitch) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Restore last listened radio station on startup, and auto-play if enabled
        applicationScope.launch {
            try {
                val userPrefs = preferences.userPreferencesFlow.firstOrNull()
                val lastId = userPrefs?.lastStationId
                val lastUrl = userPrefs?.lastStationUrl
                val allStations = repository.getCuratedAndCustomStations()

                val stationToRestore = allStations.firstOrNull { it.id == lastId || it.url == lastUrl }
                    ?: (if (!lastId.isNullOrBlank() && !lastUrl.isNullOrBlank()) {
                        Station(
                            id = lastId,
                            name = userPrefs.lastStationName ?: "zonq",
                            url = lastUrl
                        )
                    } else null)
                    ?: allStations.firstOrNull()
                    ?: CuratedStations.STATIONS.first()

                playbackManager.restoreLastStation(stationToRestore)
                playbackManager.setStationList(allStations)
                playbackManager.ensureMediaServiceStarted()

                if (userPrefs?.autoPlayOnStart == true) {
                    playbackManager.playStation(stationToRestore, allStations)
                }
            } catch (_: Exception) {}
        }
    }
}
