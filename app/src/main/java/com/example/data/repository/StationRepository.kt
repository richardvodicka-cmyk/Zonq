package com.example.data.repository

import com.example.data.CuratedStations
import com.example.data.local.CustomStationEntity
import com.example.data.local.FavoriteStationEntity
import com.example.data.local.StationDao
import com.example.data.model.Station
import com.example.data.model.distinctStations
import com.example.data.model.matchesSearchQuery
import com.example.data.model.searchRelevance
import com.example.data.preferences.AudioQuality
import com.example.data.preferences.RadioPreferences
import com.example.data.remote.RadioBrowserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StationRepository(
    private val stationDao: StationDao,
    private val radioPreferences: RadioPreferences,
    private val context: android.content.Context? = null,
    private val api: RadioBrowserApi = RadioBrowserApi.create(),
    val zonqApi: com.example.data.remote.ZonqApi = com.example.data.remote.ZonqApi(),
    val zonqClient: com.example.data.remote.ZonqSyncClient = com.example.data.remote.ZonqSyncClient(zonqApi),
    zonqSyncManagerInstance: com.example.data.remote.ZonqSyncManager? = null
) {
    val zonqSyncManager: com.example.data.remote.ZonqSyncManager? = zonqSyncManagerInstance ?: context?.let {
        com.example.data.remote.ZonqSyncManager(
            context = it,
            api = zonqApi,
            stationDao = stationDao,
            preferences = radioPreferences
        )
    }

    private val mutationMutex = Mutex()

    val favoritesFlow: Flow<List<Station>> = stationDao.getAllFavorites().combine(
        radioPreferences.userPreferencesFlow
    ) { entities, _ ->
        entities.map { entity ->
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
    }

    val customStationsFlow: Flow<List<Station>> = stationDao.getAllCustomStations().combine(
        stationDao.getAllFavorites()
    ) { customEntities, favEntities ->
        val favIds = favEntities.map { it.id }.toSet()
        customEntities.map { custom ->
            Station(
                id = custom.id,
                name = custom.name,
                url = custom.url,
                favicon = custom.favicon,
                country = custom.country,
                countryCode = custom.countryCode,
                language = custom.language,
                tags = custom.tags,
                codec = custom.codec,
                bitrate = custom.bitrate,
                isCustom = true,
                isFavorite = favIds.contains(custom.id)
            )
        }
    }

    suspend fun getCuratedAndCustomStations(): List<Station> {
        val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
        val customEntities = stationDao.getAllCustomStations().first()
        val customStations = customEntities.map { custom ->
            Station(
                id = custom.id,
                name = custom.name,
                url = custom.url,
                favicon = custom.favicon,
                country = custom.country,
                countryCode = custom.countryCode,
                language = custom.language,
                tags = custom.tags,
                codec = custom.codec,
                bitrate = custom.bitrate,
                isCustom = true,
                isFavorite = favIds.contains(custom.id)
            )
        }

        val curated = CuratedStations.STATIONS.map { st ->
            st.copy(isFavorite = favIds.contains(st.id))
        }

        return (customStations + curated).distinctStations()
    }

    private fun mapDtos(dtos: List<com.example.data.remote.RadioBrowserStationDto>, favIds: Set<String>): List<Station> {
        return dtos.mapNotNull { dto ->
            val streamUrl = dto.urlResolved ?: dto.url ?: return@mapNotNull null
            val id = dto.stationUuid ?: return@mapNotNull null
            val name = dto.name?.trim() ?: "Online Rádió"
            if (name.isBlank() || streamUrl.isBlank()) return@mapNotNull null
            Station(
                id = id,
                name = name,
                url = streamUrl,
                favicon = dto.favicon ?: "",
                country = dto.country ?: "",
                countryCode = dto.countryCode ?: "",
                language = dto.language ?: "",
                tags = dto.tags ?: "",
                codec = dto.codec ?: "MP3",
                bitrate = dto.bitrate ?: 128,
                isFavorite = favIds.contains(id)
            )
        }.distinctStations()
    }

    suspend fun getGlobalStations(offset: Int = 0, limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val dtos = api.getStations(offset = offset, limit = limit, order = "votes", reverse = true)
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getHungarianStations(offset: Int = 0, limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val dtos = api.searchStations(countryCode = "HU", offset = offset, limit = limit, order = "votes", reverse = true)
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTopVotedStations(limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val dtos = api.getTopStations(limit = limit)
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTopClickStations(limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val dtos = api.getTopClickStations(limit = limit)
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchOnline(query: String, offset: Int = 0, limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val dtos = api.searchStations(name = query.trim(), offset = offset, limit = limit, order = "votes", reverse = true)
            mapDtos(dtos, favIds)
                .filter { it.matchesSearchQuery(query) }
                .sortedByDescending { it.searchRelevance(query) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getOnlineByCountry(country: String, offset: Int = 0, limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val (countryCode, fallbackName) = when (country) {
                "Magyarország" -> "HU" to "Hungary"
                "Szlovákia" -> "SK" to "Slovakia"
                "Csehország" -> "CZ" to "Czechia"
                "Egyesült Királyság" -> "GB" to "The United Kingdom Of Great Britain And Northern Ireland"
                "Németország" -> "DE" to "Germany"
                "Egyesült Államok" -> "US" to "The United States Of America"
                "Franciaország" -> "FR" to "France"
                "Olaszország" -> "IT" to "Italy"
                "Spanyolország" -> "ES" to "Spain"
                "Svájc" -> "CH" to "Switzerland"
                "Ausztria" -> "AT" to "Austria"
                "Lengyelország" -> "PL" to "Poland"
                "Románia" -> "RO" to "Romania"
                "Hollandia" -> "NL" to "The Netherlands"
                else -> null to country
            }
            val dtos = if (countryCode != null) {
                api.searchStations(countryCode = countryCode, offset = offset, limit = limit, order = "votes", reverse = true)
            } else {
                api.searchStations(country = fallbackName, offset = offset, limit = limit, order = "votes", reverse = true)
            }
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getOnlineByTag(tag: String, offset: Int = 0, limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val apiTag = when (tag) {
                "Dance / Electro" -> "dance"
                "Hírek" -> "news"
                "Klasszikus" -> "classical"
                "Retro" -> "retro"
                "Chillout" -> "chillout"
                "Metal" -> "metal"
                else -> tag.lowercase()
            }
            val dtos = api.searchStations(tag = apiTag, offset = offset, limit = limit, order = "votes", reverse = true)
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getOnlineByLanguage(language: String, offset: Int = 0, limit: Int = 50): List<Station> = withContext(Dispatchers.IO) {
        try {
            val favIds = stationDao.getAllFavoritesList().map { it.id }.toSet()
            val apiLang = when (language) {
                "Magyar" -> "hungarian"
                "Szlovák" -> "slovak"
                "Cseh" -> "czech"
                "Angol" -> "english"
                "Német" -> "german"
                "Francia" -> "french"
                "Olasz" -> "italian"
                "Spanyol" -> "spanish"
                else -> language.lowercase()
            }
            val dtos = api.searchStations(language = apiLang, offset = offset, limit = limit, order = "votes", reverse = true)
            mapDtos(dtos, favIds)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun registerClick(stationId: String) = withContext(Dispatchers.IO) {
        if (stationId.contains("-") && stationId.length > 10) {
            try {
                api.registerClick(stationId)
            } catch (_: Exception) {}
        }
    }

    suspend fun toggleFavorite(station: Station) = withContext(Dispatchers.IO) {
        val wasFav = stationDao.isFavoriteDirect(station.id)
        mutationMutex.withLock {
            try {
                if (wasFav) {
                    stationDao.deleteFavorite(station.id)
                } else {
                    val nextIndex = stationDao.getNextOrderIndex()
                    stationDao.insertFavorite(
                        FavoriteStationEntity(
                            id = station.id,
                            name = station.name,
                            url = station.url,
                            favicon = station.favicon,
                            country = station.country,
                            countryCode = station.countryCode,
                            language = station.language,
                            tags = station.tags,
                            codec = station.codec,
                            bitrate = station.bitrate,
                            orderIndex = nextIndex
                        )
                    )
                }
            } catch (_: Exception) {
                // Safe ignore to prevent crash
            }
        }
        val isFavNow = !wasFav
        zonqSyncManager?.onFavoriteToggled(station, isFavNow)
    }

    suspend fun reorderFavorites(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            try {
                orderedIds.forEachIndexed { index, id ->
                    stationDao.updateFavoriteOrder(id, index)
                }
            } catch (_: Exception) {
                // Safe ignore
            }
        }
    }

    suspend fun moveFavoriteUp(stationId: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            try {
                val currentFavs = stationDao.getAllFavoritesList().toMutableList()
                val index = currentFavs.indexOfFirst { it.id == stationId }
                if (index > 0) {
                    val item = currentFavs.removeAt(index)
                    currentFavs.add(index - 1, item)
                    currentFavs.forEachIndexed { newIndex, fav ->
                        stationDao.updateFavoriteOrder(fav.id, newIndex)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun moveFavoriteDown(stationId: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            try {
                val currentFavs = stationDao.getAllFavoritesList().toMutableList()
                val index = currentFavs.indexOfFirst { it.id == stationId }
                if (index in 0 until currentFavs.size - 1) {
                    val item = currentFavs.removeAt(index)
                    currentFavs.add(index + 1, item)
                    currentFavs.forEachIndexed { newIndex, fav ->
                        stationDao.updateFavoriteOrder(fav.id, newIndex)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun moveFavorite(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            try {
                val currentFavs = stationDao.getAllFavoritesList().toMutableList()
                if (fromIndex in currentFavs.indices && toIndex in currentFavs.indices && fromIndex != toIndex) {
                    val item = currentFavs.removeAt(fromIndex)
                    currentFavs.add(toIndex, item)
                    currentFavs.forEachIndexed { index, fav ->
                        stationDao.updateFavoriteOrder(fav.id, index)
                    }
                }
            } catch (_: Exception) {
                // Safe ignore
            }
        }
    }

    suspend fun removeFavorite(id: String) = withContext(Dispatchers.IO) {
        val existing = stationDao.getAllFavoritesList().find { it.id == id }
        mutationMutex.withLock {
            try {
                stationDao.deleteFavorite(id)
            } catch (_: Exception) {
                // Safe ignore
            }
        }
        val dummy = existing?.let {
            Station(
                id = it.id, name = it.name, url = it.url, favicon = it.favicon,
                country = it.country, countryCode = it.countryCode, language = it.language,
                tags = it.tags, codec = it.codec, bitrate = it.bitrate, isFavorite = false
            )
        } ?: Station(id = id, name = "", url = "", isFavorite = false)
        zonqSyncManager?.onFavoriteToggled(dummy, false)
    }

    suspend fun addCustomStation(
        name: String,
        url: String,
        country: String = "Magyarország",
        genre: String = "Egyéni",
        language: String = "Magyar",
        iconUrl: String = ""
    ) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            try {
                val newStation = CustomStationEntity(
                    id = "custom-${System.currentTimeMillis()}",
                    name = name.trim(),
                    url = url.trim(),
                    favicon = iconUrl.trim(),
                    country = if (country.isBlank()) "Saját adó" else country.trim(),
                    countryCode = "HU",
                    language = if (language.isBlank()) "Magyar" else language.trim(),
                    tags = if (genre.isBlank()) "Egyéni" else genre.trim(),
                    codec = "MP3",
                    bitrate = 128
                )
                stationDao.insertCustomStation(newStation)
            } catch (_: Exception) {
                // Safe ignore
            }
        }
    }

    suspend fun deleteCustomStation(id: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            try {
                stationDao.deleteCustomStation(id)
            } catch (_: Exception) {
                // Safe ignore
            }
        }
    }

    fun isFavorite(id: String): Flow<Boolean> = stationDao.isFavorite(id)

    fun saveCachedStations(stations: List<Station>) {
        val ctx = context ?: return
        try {
            val file = java.io.File(ctx.filesDir, "daily_cached_stations.json")
            val jsonArray = org.json.JSONArray()
            for (st in stations) {
                val obj = org.json.JSONObject()
                obj.put("id", st.id)
                obj.put("name", st.name)
                obj.put("url", st.url)
                obj.put("favicon", st.favicon)
                obj.put("country", st.country)
                obj.put("countryCode", st.countryCode)
                obj.put("language", st.language)
                obj.put("tags", st.tags)
                obj.put("codec", st.codec)
                obj.put("bitrate", st.bitrate)
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    fun loadCachedStations(): List<Station> {
        val ctx = context ?: return emptyList()
        try {
            val file = java.io.File(ctx.filesDir, "daily_cached_stations.json")
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val jsonArray = org.json.JSONArray(text)
            val list = mutableListOf<Station>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("name")
                val url = obj.optString("url")
                if (id.isNotBlank() && url.isNotBlank()) {
                    list.add(
                        Station(
                            id = id,
                            name = name,
                            url = url,
                            favicon = obj.optString("favicon"),
                            country = obj.optString("country"),
                            countryCode = obj.optString("countryCode"),
                            language = obj.optString("language"),
                            tags = obj.optString("tags"),
                            codec = obj.optString("codec", "MP3"),
                            bitrate = obj.optInt("bitrate", 128)
                        )
                    )
                }
            }
            return list.distinctStations()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    suspend fun loginZonq(email: String, pass: String): Result<com.example.data.remote.ZonqAuthResult> = withContext(Dispatchers.IO) {
        val result = zonqApi.login(email, pass)
        if (result.isSuccess) {
            val auth = result.getOrThrow()
            radioPreferences.saveAuth(auth.token, auth.user.email, auth.rev)
            zonqSyncManager?.mergeGuestFavoritesOnLogin(auth.token, auth.favorites, auth.rev)
            zonqSyncManager?.flushPending()
            Result.success(com.example.data.remote.ZonqAuthResult(auth.token, auth.user.email, auth.favorites))
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Login failed"))
        }
    }

    suspend fun registerZonq(email: String, pass: String): Result<com.example.data.remote.ZonqAuthResult> = withContext(Dispatchers.IO) {
        val result = zonqApi.register(email, pass)
        if (result.isSuccess) {
            val auth = result.getOrThrow()
            radioPreferences.saveAuth(auth.token, auth.user.email, auth.rev)
            zonqSyncManager?.mergeGuestFavoritesOnLogin(auth.token, auth.favorites, auth.rev)
            zonqSyncManager?.flushPending()
            Result.success(com.example.data.remote.ZonqAuthResult(auth.token, auth.user.email, auth.favorites))
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Registration failed"))
        }
    }

    suspend fun forgotZonq(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        zonqApi.forgot(email)
    }

    suspend fun deleteAccountZonq(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        zonqSyncManager?.deleteAccount(password) ?: run {
            val token = radioPreferences.cachedToken ?: radioPreferences.userPreferencesFlow.first().zonqToken ?: ""
            zonqApi.deleteAccount(token, password)
        }
    }

    suspend fun logoutZonq(): Result<Unit> = withContext(Dispatchers.IO) {
        zonqSyncManager?.logout() ?: run {
            val token = radioPreferences.cachedToken
            if (!token.isNullOrBlank()) {
                try { zonqApi.logout(token) } catch (_: Exception) {}
            }
            radioPreferences.clearAuth()
            stationDao.clearAllFavorites()
            Result.success(Unit)
        }
    }

    suspend fun syncFavoritesWithZonq(): Result<Int> = withContext(Dispatchers.IO) {
        val token = radioPreferences.cachedToken ?: radioPreferences.userPreferencesFlow.first().zonqToken
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(Exception("not_logged_in"))
        }
        zonqSyncManager?.flushPending()
        val pullRes = zonqSyncManager?.pullIfChanged()
        if (pullRes != null && pullRes.isFailure) {
            val err = pullRes.exceptionOrNull()
            return@withContext Result.failure(err ?: Exception("sync_failed"))
        }
        val count = stationDao.getAllFavoritesList().size
        Result.success(count)
    }
}
