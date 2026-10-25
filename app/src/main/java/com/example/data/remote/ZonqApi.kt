package com.example.data.remote

import android.os.Build
import android.util.Log
import com.example.data.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ZonqUser(
    val email: String,
    val isAdmin: Boolean = false
)

data class ZonqAuthResponse(
    val user: ZonqUser,
    val favorites: List<Station>,
    val rev: String,
    val token: String
)

data class ZonqListResponse(
    val favorites: List<Station>,
    val rev: String
)

class ZonqApiException(val code: String, message: String = code) : Exception(message)

/**
 * Low-level API client for zonq.eu.
 * Base URL: https://zonq.eu/api.php
 * Authentication: Header X-Zonq-Token (never cookies).
 */
class ZonqApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "ZonqApi"
        const val API_BASE = "https://zonq.eu/api.php"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val DEFAULT_DEVICE_NAME: String by lazy {
            "Android (${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL})"
        }
    }

    /**
     * POST ?a=login
     * Body: { email, password, client: "app", device: "<short device name>" }
     * Header: no token header
     * Response: { user: { email, is_admin }, favorites: [...], rev, token }
     */
    suspend fun login(
        email: String,
        pass: String,
        device: String = DEFAULT_DEVICE_NAME
    ): Result<ZonqAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
                put("client", "app")
                put("device", device)
            }.toString()

            val request = Request.Builder()
                .url("$API_BASE?a=login")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("User-Agent", "zonq/1.0 (Android; Radio App)")
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val userObj = json.optJSONObject("user")
                val userEmail = userObj?.optString("email") ?: email.trim()
                val isAdmin = userObj?.optBoolean("is_admin") ?: false
                val favs = parseFavoritesArray(json.optJSONArray("favorites"))
                val rev = json.optString("rev", "")
                val token = json.optString("token", "")

                if (token.isBlank()) {
                    throw ZonqApiException("invalid_response", "Missing token in response")
                }

                Result.success(
                    ZonqAuthResponse(
                        user = ZonqUser(email = userEmail, isAdmin = isAdmin),
                        favorites = favs,
                        rev = rev,
                        token = token
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "login error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * POST ?a=register
     * Body: { email, password, client: "app", device: "<short device name>" }
     * Header: no token header
     * Response: same as login
     */
    suspend fun register(
        email: String,
        pass: String,
        device: String = DEFAULT_DEVICE_NAME
    ): Result<ZonqAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
                put("client", "app")
                put("device", device)
            }.toString()

            val request = Request.Builder()
                .url("$API_BASE?a=register")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("User-Agent", "zonq/1.0 (Android; Radio App)")
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val userObj = json.optJSONObject("user")
                val userEmail = userObj?.optString("email") ?: email.trim()
                val isAdmin = userObj?.optBoolean("is_admin") ?: false
                val favs = parseFavoritesArray(json.optJSONArray("favorites"))
                val rev = json.optString("rev", "")
                val token = json.optString("token", "")

                if (token.isBlank()) {
                    throw ZonqApiException("invalid_response", "Missing token in response")
                }

                Result.success(
                    ZonqAuthResponse(
                        user = ZonqUser(email = userEmail, isAdmin = isAdmin),
                        favorites = favs,
                        rev = rev,
                        token = token
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "register error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * POST ?a=forgot
     * Body: { email, client: "app" }
     * Header: no token header
     * Response: { ok: true } (always)
     */
    suspend fun forgot(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email.trim())
                put("client", "app")
            }.toString()

            val request = Request.Builder()
                .url("$API_BASE?a=forgot")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("User-Agent", "zonq/1.0 (Android; Radio App)")
                .build()

            client.newCall(request).execute().use { response ->
                parseJsonResponse(response)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "forgot error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * GET ?a=me
     * Header: X-Zonq-Token
     * Response: { user: { email } | null, favorites: [...], rev }
     */
    suspend fun me(token: String): Result<Pair<ZonqUser?, ZonqListResponse>> = withContext(Dispatchers.IO) {
        try {
            val request = buildAuthenticatedRequest("$API_BASE?a=me", token)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val userObj = json.optJSONObject("user")
                val user = if (userObj != null) {
                    ZonqUser(
                        email = userObj.optString("email", ""),
                        isAdmin = userObj.optBoolean("is_admin", false)
                    )
                } else null

                val favs = parseFavoritesArray(json.optJSONArray("favorites"))
                val rev = json.optString("rev", "")
                Result.success(user to ZonqListResponse(favorites = favs, rev = rev))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET ?a=list
     * Header: X-Zonq-Token
     * Response: { favorites: [...], rev }
     */
    suspend fun list(token: String): Result<ZonqListResponse> = withContext(Dispatchers.IO) {
        try {
            val request = buildAuthenticatedRequest("$API_BASE?a=list", token)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val favs = parseFavoritesArray(json.optJSONArray("favorites"))
                val rev = json.optString("rev", "")
                Result.success(ZonqListResponse(favorites = favs, rev = rev))
            }
        } catch (e: Exception) {
            Log.e(TAG, "list error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * GET ?a=rev
     * Header: X-Zonq-Token
     * Response: { rev }
     */
    suspend fun rev(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildAuthenticatedRequest("$API_BASE?a=rev", token)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val rev = json.optString("rev", "")
                Result.success(rev)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST ?a=fav_add
     * Header: X-Zonq-Token
     * Body: { station: { id, name, url, logo, country, cc, genres, fmt } }
     * Response: { ok: true, rev }
     */
    suspend fun favAdd(token: String, station: Station): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stationObj = stationToJsonObject(station)
            val body = JSONObject().apply {
                put("station", stationObj)
            }.toString()

            val request = buildAuthenticatedRequest("$API_BASE?a=fav_add", token)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val rev = json.optString("rev", "")
                Result.success(rev)
            }
        } catch (e: Exception) {
            Log.e(TAG, "favAdd error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * POST ?a=fav_remove
     * Header: X-Zonq-Token
     * Body: { id: "<station id>" }
     * Response: { ok: true, rev }
     */
    suspend fun favRemove(token: String, stationId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("id", stationId)
            }.toString()

            val request = buildAuthenticatedRequest("$API_BASE?a=fav_remove", token)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val rev = json.optString("rev", "")
                Result.success(rev)
            }
        } catch (e: Exception) {
            Log.e(TAG, "favRemove error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * POST ?a=favs
     * Header: X-Zonq-Token
     * Body: { favorites: [ ... ] }
     * Response: { favorites: [ ... ], rev } (MERGE only)
     */
    suspend fun favs(token: String, favorites: List<Station>): Result<ZonqListResponse> = withContext(Dispatchers.IO) {
        try {
            val array = JSONArray()
            for (st in favorites) {
                array.put(stationToJsonObject(st))
            }
            val body = JSONObject().apply {
                put("favorites", array)
            }.toString()

            val request = buildAuthenticatedRequest("$API_BASE?a=favs", token)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val json = parseJsonResponse(response)
                val mergedFavs = parseFavoritesArray(json.optJSONArray("favorites"))
                val rev = json.optString("rev", "")
                Result.success(ZonqListResponse(favorites = mergedFavs, rev = rev))
            }
        } catch (e: Exception) {
            Log.e(TAG, "favs merge error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * POST ?a=delete_account
     * Header: X-Zonq-Token
     * Body: { password }
     * Response: { ok: true } (or error "admin_account", "invalid_credentials")
     */
    suspend fun deleteAccount(token: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("password", password)
            }.toString()

            val request = buildAuthenticatedRequest("$API_BASE?a=delete_account", token)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                parseJsonResponse(response)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * POST ?a=logout
     * Header: X-Zonq-Token
     * Body: {}
     * Response: { ok: true }
     */
    suspend fun logout(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildAuthenticatedRequest("$API_BASE?a=logout", token)
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use {
                // Ignore failure on logout, just complete
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildAuthenticatedRequest(url: String, token: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("X-Zonq-Token", token)
            .header("User-Agent", "zonq/1.0 (Android; Radio App)")
    }

    private fun parseJsonResponse(response: okhttp3.Response): JSONObject {
        val bodyStr = response.body?.string().orEmpty()
        val json = if (bodyStr.isNotBlank()) {
            try {
                JSONObject(bodyStr)
            } catch (_: Exception) {
                JSONObject()
            }
        } else JSONObject()

        if (response.code == 401 || json.optString("error") == "auth") {
            throw ZonqApiException("auth", "Authentication failed or token expired (401)")
        }

        if (response.code == 409 || json.optString("error") == "limit") {
            throw ZonqApiException("limit", "Favorites limit reached (500)")
        }

        val err = json.optString("error")
        if (err.isNotBlank()) {
            throw ZonqApiException(err, "Backend error: $err")
        }

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }

        return json
    }

    fun stationToJsonObject(station: Station): JSONObject {
        return JSONObject().apply {
            put("id", station.id)
            put("name", station.name)
            put("url", station.url)
            put("logo", station.favicon)
            put("country", station.country)
            put("cc", station.countryCode)
            put("genres", station.tags)
            put("fmt", if (station.codec.isNotBlank()) station.codec else "MP3")
        }
    }

    fun parseFavoritesArray(array: JSONArray?): List<Station> {
        if (array == null) return emptyList()
        val list = mutableListOf<Station>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifEmpty {
                val url = obj.optString("url")
                "fav_${url.hashCode()}"
            }
            val name = obj.optString("name", "Rádió")
            val url = obj.optString("url", "")
            if (url.isBlank()) continue

            val logo = obj.optString("logo").ifEmpty { obj.optString("favicon", "") }
            val country = obj.optString("country", "")
            val cc = obj.optString("cc").ifEmpty { obj.optString("countryCode", "") }
            val genres = obj.optString("genres").ifEmpty { obj.optString("tags", "") }
            val fmt = obj.optString("fmt").ifEmpty { obj.optString("codec", "MP3") }

            list.add(
                Station(
                    id = id,
                    name = name,
                    url = url,
                    favicon = logo,
                    country = country,
                    countryCode = cc,
                    language = "",
                    tags = genres,
                    codec = fmt,
                    bitrate = 128,
                    isFavorite = true
                )
            )
        }
        return list
    }
}
