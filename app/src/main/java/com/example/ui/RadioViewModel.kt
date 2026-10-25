package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Station
import com.example.data.model.distinctStations
import com.example.data.preferences.AudioQuality
import com.example.data.preferences.BufferDuration
import com.example.data.preferences.DefaultScreen
import com.example.data.preferences.RadioPreferences
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferences
import com.example.data.repository.StationRepository
import com.example.playback.PlaybackState
import com.example.playback.RadioPlaybackManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab {
    ALL,
    FAVORITES,
    CATEGORIES
}

enum class StationSection {
    ALL,
    HUNGARIAN,
    TOP_VOTED,
    TRENDING
}

enum class CategoryType {
    COUNTRIES,
    GENRES,
    LANGUAGES
}

data class RadioUiState(
    val selectedTab: MainTab = MainTab.ALL,
    val selectedCategoryType: CategoryType = CategoryType.COUNTRIES,
    val selectedSection: StationSection = StationSection.ALL,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val selectedCountry: String? = null,
    val selectedGenre: String? = null,
    val selectedLanguage: String? = null,
    val globalStations: List<Station> = emptyList(),
    val hungarianStations: List<Station> = emptyList(),
    val topVotedStations: List<Station> = emptyList(),
    val trendingStations: List<Station> = emptyList(),
    val onlineResults: List<Station> = emptyList(),
    val isOnlineLoading: Boolean = false,
    val isReorderMode: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showAddStationDialog: Boolean = false,
    val showManageFavoritesDialog: Boolean = false,
    val showNowPlayingSheet: Boolean = false,
    val showZonqAuthDialog: Boolean = false,
    val isSyncingZonq: Boolean = false,
    val zonqSyncMessage: String? = null,
    val isCheckingForUpdate: Boolean = false,
    val availableUpdateInfo: com.example.data.remote.AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val updateCheckStatusMessage: String? = null,
    val shuffleEpoch: Int = 0
)

class RadioViewModel(
    private val repository: StationRepository,
    private val preferences: RadioPreferences,
    val playbackManager: RadioPlaybackManager,
    val appUpdateManager: com.example.data.remote.AppUpdateManager
) : ViewModel() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("RadioViewModel", "Caught coroutine error", throwable)
    }

    private val stationRandomWeights = java.util.concurrent.ConcurrentHashMap<String, Double>()

    fun getStationRandomWeight(id: String): Double {
        return stationRandomWeights.computeIfAbsent(id) {
            kotlin.random.Random.nextDouble()
        }
    }

    fun reshuffleAllStations() {
        stationRandomWeights.clear()
        _uiState.update { it.copy(shuffleEpoch = it.shuffleEpoch + 1) }
    }

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    val favorites: StateFlow<List<Station>> = repository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customStations: StateFlow<List<Station>> = repository.customStationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentLanguageOverride = MutableStateFlow<com.example.data.preferences.AppLanguage?>(null)

    val userPreferences: StateFlow<UserPreferences> = kotlinx.coroutines.flow.combine(
        preferences.userPreferencesFlow,
        _currentLanguageOverride
    ) { prefs, overrideLang ->
        if (overrideLang != null) prefs.copy(appLanguage = overrideLang) else prefs
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState
    val equalizerState: StateFlow<com.example.playback.EqualizerState> = playbackManager.equalizerManager.equalizerState

    private var searchJob: Job? = null
    private var filterJob: Job? = null
    private var globalOffset = 0
    private var isFetchingGlobal = false

    init {
        // Read default startup screen
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val prefs = preferences.userPreferencesFlow.firstOrNull()
                if (prefs?.defaultScreen == DefaultScreen.FAVORITES) {
                    _uiState.update { it.copy(selectedTab = MainTab.FAVORITES) }
                }
            } catch (_: Exception) {}
        }

        // Daily background update check & initial cache load
        viewModelScope.launch(coroutineExceptionHandler) {
            performDailyBackgroundUpdateCheck()
        }

        // Two-way favorites background sync with zonq.eu
        repository.zonqSyncManager?.startPolling(viewModelScope)

        viewModelScope.launch(coroutineExceptionHandler) {
            repository.zonqSyncManager?.authExpiredFlow?.collect {
                _uiState.update { it.copy(showZonqAuthDialog = true) }
            }
        }

        viewModelScope.launch(coroutineExceptionHandler) {
            repository.zonqSyncManager?.limitReachedFlow?.collect {
                _uiState.update { it.copy(zonqSyncMessage = "error:limit") }
            }
        }

        // Initial sync with zonq.eu if user is logged in
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val prefs = preferences.userPreferencesFlow.firstOrNull()
                if (prefs?.isLoggedIn == true) {
                    repository.syncFavoritesWithZonq()
                }
            } catch (_: Exception) {}
        }

        // Check for app updates from zonq.eu in the background on startup
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                checkForAppUpdate(isManual = false)
            } catch (_: Exception) {}
        }
    }

    private suspend fun performDailyBackgroundUpdateCheck() {
        // Step 1: Immediately populate memory from persistent disk cache if available
        val cached = repository.loadCachedStations()
        if (cached.isNotEmpty()) {
            val hu = cached.filter {
                it.countryCode.equals("HU", ignoreCase = true) || it.country == "Magyarország"
            }
            _uiState.update { current ->
                val combined = (cached + current.globalStations).distinctStations()
                current.copy(
                    globalStations = combined,
                    hungarianStations = if (hu.isNotEmpty()) hu else current.hungarianStations
                )
            }
        }

        // Step 2: Check if 24 hours (1 day) have passed since the last background update
        val lastUpdateTime = preferences.getLastDailyUpdateTime()
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val isDue = (now - lastUpdateTime) >= oneDayMillis || lastUpdateTime == 0L

        if (isDue) {
            Log.d("RadioViewModel", "Running once-a-day background radio stations refresh...")
            try {
                // Fetch fresh Hungarian stations from Radio Browser
                val freshHungarian = repository.getHungarianStations(offset = 0, limit = 60)
                // Fetch top voted stations from Radio Browser
                val freshTopVoted = repository.getTopVotedStations(limit = 60)
                // Fetch trending / popular stations from Radio Browser
                val freshTrending = repository.getTopClickStations(limit = 60)
                // Fetch global catalog batch
                val freshGlobal = repository.getGlobalStations(offset = 0, limit = 60)

                val allFresh = (freshHungarian + freshTopVoted + freshTrending + freshGlobal).distinctStations()
                if (allFresh.isNotEmpty()) {
                    // Persist to disk cache
                    repository.saveCachedStations(allFresh)
                    // Mark timestamp
                    preferences.setLastDailyUpdateTime(now)

                    // Update UI state with fresh data
                    _uiState.update { current ->
                        current.copy(
                            hungarianStations = if (freshHungarian.isNotEmpty()) freshHungarian else current.hungarianStations,
                            topVotedStations = if (freshTopVoted.isNotEmpty()) freshTopVoted else current.topVotedStations,
                            trendingStations = if (freshTrending.isNotEmpty()) freshTrending else current.trendingStations,
                            globalStations = (allFresh + current.globalStations).distinctStations()
                        )
                    }
                    Log.d("RadioViewModel", "Daily background stations update finished successfully with ${allFresh.size} stations")
                }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Daily background stations update error", e)
            }
        } else {
            Log.d("RadioViewModel", "Daily update skipped: updated within the past 24 hours")
        }
    }

    fun selectSection(section: StationSection) {
        _uiState.update { it.copy(selectedSection = section) }
        when (section) {
            StationSection.HUNGARIAN -> if (_uiState.value.hungarianStations.isEmpty()) loadHungarianStations()
            StationSection.TOP_VOTED -> if (_uiState.value.topVotedStations.isEmpty()) loadTopVotedStations()
            StationSection.TRENDING -> if (_uiState.value.trendingStations.isEmpty()) loadTrendingStations()
            StationSection.ALL -> {}
        }
    }

    fun loadHungarianStations() {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val list = repository.getHungarianStations(offset = 0, limit = 60)
                if (list.isNotEmpty()) {
                    _uiState.update { it.copy(hungarianStations = list) }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadTopVotedStations() {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                _uiState.update { it.copy(isOnlineLoading = true) }
                val list = repository.getTopVotedStations(limit = 60)
                _uiState.update { it.copy(topVotedStations = list, isOnlineLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOnlineLoading = false) }
            }
        }
    }

    fun loadTrendingStations() {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                _uiState.update { it.copy(isOnlineLoading = true) }
                val list = repository.getTopClickStations(limit = 60)
                _uiState.update { it.copy(trendingStations = list, isOnlineLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOnlineLoading = false) }
            }
        }
    }

    fun loadMoreGlobalStations() {
        if (isFetchingGlobal) return
        isFetchingGlobal = true
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val nextBatch = repository.getGlobalStations(offset = globalOffset, limit = 40)
                if (nextBatch.isNotEmpty()) {
                    globalOffset += nextBatch.size
                    _uiState.update { current ->
                        val combined = (current.globalStations + nextBatch).distinctBy { it.id }
                        current.copy(globalStations = combined)
                    }
                }
            } catch (_: Exception) {} finally {
                isFetchingGlobal = false
            }
        }
    }

    fun selectTab(tab: MainTab) {
        filterJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                selectedCountry = null,
                selectedGenre = null,
                selectedLanguage = null,
                onlineResults = emptyList(),
                isOnlineLoading = false
            )
        }
        if (tab == MainTab.FAVORITES) {
            viewModelScope.launch(coroutineExceptionHandler) {
                try {
                    val prefs = preferences.userPreferencesFlow.firstOrNull()
                    if (prefs?.isLoggedIn == true) {
                        repository.syncFavoritesWithZonq()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun selectCategoryType(type: CategoryType) {
        filterJob?.cancel()
        _uiState.update {
            it.copy(
                selectedCategoryType = type,
                selectedCountry = null,
                selectedGenre = null,
                selectedLanguage = null,
                onlineResults = emptyList(),
                isOnlineLoading = false
            )
        }
    }

    fun selectCountry(country: String?) {
        _uiState.update { it.copy(selectedCountry = country, onlineResults = emptyList(), isOnlineLoading = country != null) }
        filterJob?.cancel()
        if (country != null) {
            loadStationsByCountry(country)
        }
    }

    fun selectGenre(genre: String?) {
        _uiState.update { it.copy(selectedGenre = genre, onlineResults = emptyList(), isOnlineLoading = genre != null) }
        filterJob?.cancel()
        if (genre != null) {
            loadStationsByGenre(genre)
        }
    }

    fun selectLanguage(language: String?) {
        _uiState.update { it.copy(selectedLanguage = language, onlineResults = emptyList(), isOnlineLoading = language != null) }
        filterJob?.cancel()
        if (language != null) {
            loadStationsByLanguage(language)
        }
    }

    private fun loadStationsByCountry(country: String) {
        filterJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val online = repository.getOnlineByCountry(country, offset = 0, limit = 50)
                _uiState.update { it.copy(onlineResults = online, isOnlineLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOnlineLoading = false) }
            }
        }
    }

    private fun loadStationsByGenre(genre: String) {
        filterJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val online = repository.getOnlineByTag(genre, offset = 0, limit = 50)
                _uiState.update { it.copy(onlineResults = online, isOnlineLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOnlineLoading = false) }
            }
        }
    }

    private fun loadStationsByLanguage(language: String) {
        filterJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                val online = repository.getOnlineByLanguage(language, offset = 0, limit = 50)
                _uiState.update { it.copy(onlineResults = online, isOnlineLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOnlineLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(onlineResults = emptyList(), isOnlineLoading = false) }
            return
        }

        searchJob = viewModelScope.launch(coroutineExceptionHandler) {
            delay(350) // debounce
            _uiState.update { it.copy(isOnlineLoading = true) }
            try {
                val results = repository.searchOnline(query, offset = 0, limit = 50)
                _uiState.update { it.copy(onlineResults = results, isOnlineLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOnlineLoading = false) }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", onlineResults = emptyList(), isOnlineLoading = false) }
    }

    fun playStation(station: Station, listContext: List<Station>? = null) {
        playbackManager.playStation(station, listContext)
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.registerClick(station.id)
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun toggleFavorite(station: Station) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.toggleFavorite(station)
                val updated = !station.isFavorite
                playbackManager.updateFavoriteStatus(station.id, updated)
            } catch (_: Exception) {}
        }
    }

    fun toggleReorderMode() {
        _uiState.update { it.copy(isReorderMode = !it.isReorderMode) }
    }

    fun setReorderMode(enabled: Boolean) {
        _uiState.update { it.copy(isReorderMode = enabled) }
    }

    fun moveFavoriteUp(stationId: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.moveFavoriteUp(stationId)
            } catch (_: Exception) {}
        }
    }

    fun moveFavoriteDown(stationId: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.moveFavoriteDown(stationId)
            } catch (_: Exception) {}
        }
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.moveFavorite(fromIndex, toIndex)
            } catch (_: Exception) {}
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.removeFavorite(id)
                playbackManager.updateFavoriteStatus(id, false)
            } catch (_: Exception) {}
        }
    }

    fun addCustomStation(name: String, url: String, country: String, genre: String, language: String, iconUrl: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.addCustomStation(name, url, country, genre, language, iconUrl)
                setShowAddStationDialog(false)
            } catch (_: Exception) {}
        }
    }

    fun deleteCustomStation(id: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.deleteCustomStation(id)
            } catch (_: Exception) {}
        }
    }

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setShowAddStationDialog(show: Boolean) {
        _uiState.update { it.copy(showAddStationDialog = show) }
    }

    fun setShowManageFavoritesDialog(show: Boolean) {
        _uiState.update { it.copy(showManageFavoritesDialog = show) }
    }

    fun setShowNowPlayingSheet(show: Boolean) {
        _uiState.update { it.copy(showNowPlayingSheet = show) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setThemeMode(mode)
            } catch (_: Exception) {}
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setAudioQuality(quality)
            } catch (_: Exception) {}
        }
    }

    fun setBufferDuration(duration: BufferDuration) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setBufferDuration(duration)
            } catch (_: Exception) {}
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setAutoPlayOnStart(enabled)
            } catch (_: Exception) {}
        }
    }

    fun setDefaultScreen(screen: DefaultScreen) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setDefaultScreen(screen)
            } catch (_: Exception) {}
        }
    }

    fun setAppLanguage(lang: com.example.data.preferences.AppLanguage) {
        _currentLanguageOverride.value = lang
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setAppLanguage(lang)
            } catch (_: Exception) {}
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playbackManager.equalizerManager.setEnabled(enabled)
    }

    fun setEqualizerPreset(preset: com.example.playback.EqualizerPreset) {
        playbackManager.equalizerManager.setPreset(preset)
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        playbackManager.equalizerManager.setBandLevel(bandIndex, levelMb)
    }

    fun setAudioNormalizationEnabled(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setAudioNormalizationEnabled(enabled)
            } catch (_: Exception) {}
        }
    }

    fun setNormalizationProfile(profile: com.example.playback.NormalizationProfile) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setNormalizationProfile(profile)
            } catch (_: Exception) {}
        }
    }

    fun setTvKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                preferences.setTvKeepScreenOn(enabled)
            } catch (_: Exception) {}
        }
    }

    fun setShowZonqAuthDialog(show: Boolean) {
        _uiState.update { it.copy(showZonqAuthDialog = show) }
    }

    fun loginZonq(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(isSyncingZonq = true, zonqSyncMessage = null) }
            val res = repository.loginZonq(email, pass)
            _uiState.update { it.copy(isSyncingZonq = false) }
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "error"
                onResult(false, err)
            }
        }
    }

    fun registerZonq(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(isSyncingZonq = true, zonqSyncMessage = null) }
            val res = repository.registerZonq(email, pass)
            _uiState.update { it.copy(isSyncingZonq = false) }
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "error"
                onResult(false, err)
            }
        }
    }

    fun logoutZonq() {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.logoutZonq()
        }
    }

    fun syncFavoritesWithZonq(onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(isSyncingZonq = true, zonqSyncMessage = null) }
            val res = repository.syncFavoritesWithZonq()
            _uiState.update { it.copy(isSyncingZonq = false) }
            if (res.isSuccess) {
                val count = res.getOrThrow()
                _uiState.update { it.copy(zonqSyncMessage = "success:$count") }
                onComplete?.invoke(true, "$count")
            } else {
                val err = res.exceptionOrNull()?.message ?: "sync_failed"
                _uiState.update { it.copy(zonqSyncMessage = "error:$err") }
                onComplete?.invoke(false, err)
            }
        }
    }

    fun onAppResume() {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                repository.zonqSyncManager?.startPolling(viewModelScope)
                repository.zonqSyncManager?.flushPending()
                repository.zonqSyncManager?.pullIfChanged()
            } catch (_: Exception) {}
        }
    }

    fun onAppPause() {
        repository.zonqSyncManager?.stopPolling()
    }

    fun forgotZonq(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val res = repository.forgotZonq(email)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "error"
                onResult(false, err)
            }
        }
    }

    fun deleteAccountZonq(password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val res = repository.deleteAccountZonq(password)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "error"
                onResult(false, err)
            }
        }
    }

    fun clearZonqSyncMessage() {
        _uiState.update { it.copy(zonqSyncMessage = null) }
    }

    fun checkForAppUpdate(isManual: Boolean = false) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.update { it.copy(isCheckingForUpdate = true, updateCheckStatusMessage = null) }
            val res = appUpdateManager.checkForUpdate()
            _uiState.update { it.copy(isCheckingForUpdate = false) }
            if (res.isSuccess) {
                val info = res.getOrThrow()
                _uiState.update { it.copy(availableUpdateInfo = info) }
                if (info.isUpdateAvailable) {
                    val prefs = preferences.userPreferencesFlow.firstOrNull()
                    val lastDismissed = prefs?.lastDismissedUpdateTimestamp ?: 0L
                    if (isManual || info.remoteLastModified != lastDismissed) {
                        _uiState.update { it.copy(showUpdateDialog = true) }
                    }
                } else if (isManual) {
                    _uiState.update { it.copy(updateCheckStatusMessage = "up_to_date:${info.remoteLastModifiedFormatted}") }
                }
            } else {
                if (isManual) {
                    _uiState.update { it.copy(updateCheckStatusMessage = "error") }
                }
            }
        }
    }

    fun setShowUpdateDialog(show: Boolean) {
        _uiState.update { it.copy(showUpdateDialog = show) }
    }

    fun dismissUpdateDialog(rememberDismiss: Boolean = true) {
        val updateInfo = _uiState.value.availableUpdateInfo
        _uiState.update { it.copy(showUpdateDialog = false) }
        if (rememberDismiss && updateInfo != null) {
            viewModelScope.launch(coroutineExceptionHandler) {
                preferences.setLastDismissedUpdateTimestamp(updateInfo.remoteLastModified)
            }
        }
    }

    fun downloadUpdate(): Boolean {
        val updateInfo = _uiState.value.availableUpdateInfo
        _uiState.update { it.copy(showUpdateDialog = false) }
        if (updateInfo != null) {
            viewModelScope.launch(coroutineExceptionHandler) {
                preferences.setLastDismissedUpdateTimestamp(updateInfo.remoteLastModified)
            }
        }
        return appUpdateManager.startDownload()
    }

    fun clearUpdateStatusMessage() {
        _uiState.update { it.copy(updateCheckStatusMessage = null) }
    }
}
