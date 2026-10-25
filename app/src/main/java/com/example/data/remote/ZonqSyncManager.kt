package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.FavoriteStationEntity
import com.example.data.local.StationDao
import com.example.data.model.Station
import com.example.data.preferences.RadioPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ZonqPendingOperation(
    val op: String, // "add" or "remove"
    val id: String,
    val station: Station? = null
)

/**
 * Two-way favorites synchronization engine with zonq.eu backend.
 * Implements optimistic local push, serialized background pending queue flush,
 * conditional rev-checked pull polling, guest favorites merge, and account lifecycle.
 */
class ZonqSyncManager(
    private val context: Context,
    val api: ZonqApi = ZonqApi(),
    private val stationDao: StationDao,
    private val preferences: RadioPreferences
) {

    companion object {
        private const val TAG = "ZonqSyncManager"
    }

    private val flushMutex = Mutex()
    private val queueMutex = Mutex()
    private var pollingJob: Job? = null

    private val _authExpiredFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authExpiredFlow: SharedFlow<Unit> = _authExpiredFlow.asSharedFlow()

    private val _limitReachedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val limitReachedFlow: SharedFlow<Unit> = _limitReachedFlow.asSharedFlow()

    fun isFlushRunning(): Boolean = flushMutex.isLocked

    private fun getCurrentFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Optimistically enqueue an add or remove operation for favorite stations,
     * then asynchronously trigger flushPending().
     */
    suspend fun onFavoriteToggled(
        station: Station,
        isFavoriteNow: Boolean
    ) = withContext(Dispatchers.IO) {
        val token = preferences.cachedToken ?: preferences.userPreferencesFlow.first().zonqToken
        if (token.isNullOrBlank()) {
            // Guest mode: favorites are strictly device-local, no pending queue
            return@withContext
        }

        val op = if (isFavoriteNow) {
            ZonqPendingOperation(op = "add", id = station.id, station = station)
        } else {
            ZonqPendingOperation(op = "remove", id = station.id, station = null)
        }

        enqueueOperation(op)

        syncScope.launch {
            flushPending()
        }
    }

    private suspend fun enqueueOperation(op: ZonqPendingOperation) {
        queueMutex.withLock {
            val list = loadPendingQueueInternal().toMutableList()
            // Consolidate redundant back-to-back operations for the same station id
            list.removeAll { it.id == op.id }
            list.add(op)
            savePendingQueueInternal(list)
        }
    }

    /**
     * Processes pending queue one by one in chronological order.
     * Prevents parallel execution, updates rev upon success, handles 401 auth, 409 limit, and offline retries.
     */
    suspend fun flushPending(): Unit = withContext(Dispatchers.IO) {
        if (!flushMutex.tryLock()) {
            return@withContext
        }

        try {
            val token = preferences.cachedToken ?: preferences.userPreferencesFlow.first().zonqToken
            if (token.isNullOrBlank()) {
                return@withContext
            }

            var queue = queueMutex.withLock { loadPendingQueueInternal().toMutableList() }
            if (queue.isEmpty()) {
                return@withContext
            }

            preferences.setSyncStatus("syncing")

            while (queue.isNotEmpty()) {
                val currentOp = queue.first()

                val result: Result<String> = when (currentOp.op) {
                    "add" -> {
                        val stationToPush = currentOp.station
                        if (stationToPush != null) {
                            api.favAdd(token, stationToPush)
                        } else {
                            Result.success(preferences.getZonqRev().orEmpty())
                        }
                    }
                    "remove" -> {
                        api.favRemove(token, currentOp.id)
                    }
                    else -> Result.success(preferences.getZonqRev().orEmpty())
                }

                if (result.isSuccess) {
                    val newRev = result.getOrThrow()
                    if (newRev.isNotBlank()) {
                        preferences.setZonqRev(newRev)
                    }

                    queueMutex.withLock {
                        queue = loadPendingQueueInternal().toMutableList()
                        if (queue.isNotEmpty() && queue.first().id == currentOp.id) {
                            queue.removeAt(0)
                            savePendingQueueInternal(queue)
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.w(TAG, "flushPending operation failed for ${currentOp.id}: ${error?.message}")

                    if (error is ZonqApiException) {
                        when (error.code) {
                            "auth" -> {
                                preferences.clearAuth()
                                _authExpiredFlow.tryEmit(Unit)
                                break
                            }
                            "limit" -> {
                                // 500 favorites limit reached: drop this operation and inform user
                                _limitReachedFlow.tryEmit(Unit)
                                queueMutex.withLock {
                                    queue = loadPendingQueueInternal().toMutableList()
                                    if (queue.isNotEmpty() && queue.first().id == currentOp.id) {
                                        queue.removeAt(0)
                                        savePendingQueueInternal(queue)
                                    }
                                }
                                continue
                            }
                            else -> {
                                // Other permanent API error: drop operation to prevent infinite stall
                                queueMutex.withLock {
                                    queue = loadPendingQueueInternal().toMutableList()
                                    if (queue.isNotEmpty() && queue.first().id == currentOp.id) {
                                        queue.removeAt(0)
                                        savePendingQueueInternal(queue)
                                    }
                                }
                                continue
                            }
                        }
                    } else {
                        // Network error, 5xx, or offline: pause queue and leave items for next poll
                        preferences.setSyncStatus("offline")
                        break
                    }
                }
            }

            val remainingCount = queueMutex.withLock { loadPendingQueueInternal().size }
            if (remainingCount == 0) {
                preferences.setSyncStatus("synced", getCurrentFormattedTime())
            }
        } catch (e: Exception) {
            Log.e(TAG, "flushPending error: ${e.message}")
            preferences.setSyncStatus("offline")
        } finally {
            flushMutex.unlock()
        }
    }

    /**
     * Checks if remote fingerprint (rev) changed, and if so, replaces local favorites with the server's list.
     * Skips pull if not logged in, if pending queue has operations, or if flush is currently running.
     */
    suspend fun pullIfChanged(): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = preferences.cachedToken ?: preferences.userPreferencesFlow.first().zonqToken
        if (token.isNullOrBlank()) {
            return@withContext Result.success(false)
        }

        val pendingCount = queueMutex.withLock { loadPendingQueueInternal().size }
        if (pendingCount > 0 || flushMutex.isLocked) {
            // Unpushed local changes exist; do not overwrite with remote state until flush succeeds
            return@withContext Result.success(false)
        }

        try {
            preferences.setSyncStatus("syncing")

            val revResult = api.rev(token)
            if (revResult.isFailure) {
                val err = revResult.exceptionOrNull()
                if (err is ZonqApiException && err.code == "auth") {
                    preferences.clearAuth()
                    _authExpiredFlow.tryEmit(Unit)
                    return@withContext Result.failure(err)
                }
                preferences.setSyncStatus("offline")
                return@withContext Result.failure(err ?: IOException("rev check failed"))
            }

            val serverRev = revResult.getOrThrow()
            val storedRev = preferences.getZonqRev()

            if (serverRev.isNotBlank() && serverRev == storedRev) {
                // No changes on backend
                preferences.setSyncStatus("synced", getCurrentFormattedTime())
                return@withContext Result.success(false)
            }

            // Fingerprint changed (or initial pull): fetch full list
            val listResult = api.list(token)
            if (listResult.isFailure) {
                val err = listResult.exceptionOrNull()
                if (err is ZonqApiException && err.code == "auth") {
                    preferences.clearAuth()
                    _authExpiredFlow.tryEmit(Unit)
                    return@withContext Result.failure(err)
                }
                preferences.setSyncStatus("offline")
                return@withContext Result.failure(err ?: IOException("list fetch failed"))
            }

            val listResponse = listResult.getOrThrow()
            val remoteStations = listResponse.favorites
            val newRev = listResponse.rev

            // Replace local Room favorites with server state (server is source of truth)
            val entities = remoteStations.mapIndexed { index, st ->
                FavoriteStationEntity(
                    id = st.id,
                    name = st.name,
                    url = st.url,
                    favicon = st.favicon,
                    country = st.country,
                    countryCode = st.countryCode,
                    language = st.language,
                    tags = st.tags,
                    codec = st.codec,
                    bitrate = st.bitrate,
                    orderIndex = index,
                    addedAt = System.currentTimeMillis()
                )
            }
            stationDao.replaceFavorites(entities)
            if (newRev.isNotBlank()) {
                preferences.setZonqRev(newRev)
            }
            preferences.setSyncStatus("synced", getCurrentFormattedTime())
            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "pullIfChanged error: ${e.message}")
            preferences.setSyncStatus("offline")
            return@withContext Result.failure(e)
        }
    }

    /**
     * Executes initial guest merge on login/register:
     * If user had local guest favorites, uploads them once via POST ?a=favs (merge only),
     * then uses the returned list and rev as the local state.
     */
    suspend fun mergeGuestFavoritesOnLogin(
        token: String,
        remoteFavorites: List<Station>,
        initialRev: String
    ): List<Station> = withContext(Dispatchers.IO) {
        val localEntities = stationDao.getAllFavoritesList()
        val localFavorites = localEntities.map { entity ->
            Station(
                id = entity.id,
                name = entity.name,
                url = entity.url,
                favicon = entity.favicon,
                country = entity.country,
                countryCode = entity.countryCode,
                language = entity.language,
                tags = entity.tags,
                codec = entity.codec,
                bitrate = entity.bitrate,
                isFavorite = true
            )
        }

        val effectiveList: List<Station>
        val effectiveRev: String

        if (localFavorites.isNotEmpty()) {
            val mergeResult = api.favs(token, localFavorites)
            if (mergeResult.isSuccess) {
                val mergedData = mergeResult.getOrThrow()
                effectiveList = mergedData.favorites
                effectiveRev = mergedData.rev
            } else {
                // If merge fails, combine locally and use remote
                val combined = (remoteFavorites + localFavorites).distinctBy { it.id }
                effectiveList = combined
                effectiveRev = initialRev
            }
        } else {
            effectiveList = remoteFavorites
            effectiveRev = initialRev
        }

        val entities = effectiveList.mapIndexed { index, st ->
            FavoriteStationEntity(
                id = st.id,
                name = st.name,
                url = st.url,
                favicon = st.favicon,
                country = st.country,
                countryCode = st.countryCode,
                language = st.language,
                tags = st.tags,
                codec = st.codec,
                bitrate = st.bitrate,
                orderIndex = index,
                addedAt = System.currentTimeMillis()
            )
        }
        stationDao.replaceFavorites(entities)

        if (effectiveRev.isNotBlank()) {
            preferences.setZonqRev(effectiveRev)
        }
        queueMutex.withLock {
            savePendingQueueInternal(emptyList())
        }
        preferences.setSyncStatus("synced", getCurrentFormattedTime())

        effectiveList
    }

    /**
     * Permanently deletes user account, clearing all credentials, favorites, and pending operations.
     */
    suspend fun deleteAccount(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = preferences.cachedToken ?: preferences.userPreferencesFlow.first().zonqToken
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(Exception("not_logged_in"))
        }

        val res = api.deleteAccount(token, password)
        if (res.isSuccess) {
            preferences.clearAuth()
            stationDao.clearAllFavorites()
            queueMutex.withLock {
                savePendingQueueInternal(emptyList())
            }
        }
        res
    }

    /**
     * Logs out the user on backend, clears local auth, favorites, and pending queue.
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = preferences.cachedToken ?: preferences.userPreferencesFlow.first().zonqToken
        if (!token.isNullOrBlank()) {
            try {
                api.logout(token)
            } catch (_: Exception) {}
        }

        preferences.clearAuth()
        stationDao.clearAllFavorites()
        queueMutex.withLock {
            savePendingQueueInternal(emptyList())
        }
        Result.success(Unit)
    }

    /**
     * Starts foreground periodic 30-second poll while the app is visible.
     */
    fun startPolling(scope: CoroutineScope) {
        stopPolling()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                if (isActive) {
                    try {
                        flushPending()
                        pullIfChanged()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Pauses/stops polling when app enters background.
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun loadPendingQueueInternal(): List<ZonqPendingOperation> {
        val jsonStr = preferences.getPendingQueueJson()
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ZonqPendingOperation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val op = obj.getString("op")
                val id = obj.getString("id")
                val stationObj = obj.optJSONObject("station")
                val station = if (stationObj != null) {
                    api.parseFavoritesArray(JSONArray().apply { put(stationObj) }).firstOrNull()
                } else null

                list.add(ZonqPendingOperation(op = op, id = id, station = station))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun savePendingQueueInternal(queue: List<ZonqPendingOperation>) {
        val array = JSONArray()
        for (item in queue) {
            val obj = JSONObject().apply {
                put("op", item.op)
                put("id", item.id)
                if (item.station != null) {
                    put("station", api.stationToJsonObject(item.station))
                }
            }
            array.put(obj)
        }
        preferences.setPendingQueueJson(array.toString())
    }
}
