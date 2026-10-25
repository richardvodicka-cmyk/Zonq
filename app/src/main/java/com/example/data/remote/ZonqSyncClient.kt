package com.example.data.remote

import com.example.data.model.Station

data class ZonqAuthResult(
    val token: String,
    val email: String,
    val favorites: List<Station>,
    val rev: String = ""
)

/**
 * High-level wrapper for zonq.eu authentication and sync actions.
 * Delegates to [ZonqApi] using header-based X-Zonq-Token authentication.
 */
class ZonqSyncClient(
    val api: ZonqApi = ZonqApi()
) {
    suspend fun login(email: String, pass: String): Result<ZonqAuthResult> {
        return api.login(email, pass).map {
            ZonqAuthResult(
                token = it.token,
                email = it.user.email,
                favorites = it.favorites,
                rev = it.rev
            )
        }
    }

    suspend fun register(email: String, pass: String): Result<ZonqAuthResult> {
        return api.register(email, pass).map {
            ZonqAuthResult(
                token = it.token,
                email = it.user.email,
                favorites = it.favorites,
                rev = it.rev
            )
        }
    }

    suspend fun forgot(email: String): Result<Unit> = api.forgot(email)

    suspend fun deleteAccount(token: String, pass: String): Result<Unit> = api.deleteAccount(token, pass)

    suspend fun getRemoteFavorites(token: String): Result<List<Station>> {
        return api.list(token).map { it.favorites }
    }

    suspend fun uploadFavorites(token: String, stations: List<Station>): Result<Unit> {
        return api.favs(token, stations).map { Unit }
    }

    suspend fun logout(token: String): Result<Unit> = api.logout(token)
}
