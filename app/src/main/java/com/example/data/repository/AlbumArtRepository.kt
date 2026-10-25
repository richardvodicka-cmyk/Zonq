package com.example.data.repository

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AlbumArtRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    // Cache up to 150 songs in memory: "artist - title" -> artwork URL (or empty string if not found)
    private val memoryCache = LruCache<String, String>(150)

    suspend fun getAlbumArtUrl(artist: String?, title: String?): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(title) ?: return@withContext null
        val cleanArtist = cleanSearchTerm(artist)

        val cacheKey = if (!cleanArtist.isNullOrBlank()) {
            "$cleanArtist - $cleanTitle".lowercase()
        } else {
            cleanTitle.lowercase()
        }

        synchronized(memoryCache) {
            val cached = memoryCache.get(cacheKey)
            if (cached != null) {
                return@withContext cached.ifBlank { null }
            }
        }

        // 1. Try iTunes Search API with Artist + Title
        var artworkUrl = searchItunes(cleanArtist, cleanTitle)

        // 2. Fallback: Try Deezer API with Artist + Title
        if (artworkUrl == null) {
            artworkUrl = searchDeezer(cleanArtist, cleanTitle)
        }

        // 3. Fallback: If artist was present and no result, try just Title on iTunes
        if (artworkUrl == null && !cleanArtist.isNullOrBlank()) {
            artworkUrl = searchItunes(null, cleanTitle)
        }

        // 4. Fallback: If still nothing, try just Title on Deezer
        if (artworkUrl == null && !cleanArtist.isNullOrBlank()) {
            artworkUrl = searchDeezer(null, cleanTitle)
        }

        synchronized(memoryCache) {
            memoryCache.put(cacheKey, artworkUrl ?: "")
        }

        artworkUrl
    }

    private fun searchItunes(artist: String?, title: String): String? {
        return try {
            val query = if (!artist.isNullOrBlank()) "$artist $title" else title
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "zonq/1.0 (Android; Radio App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return null
                if (results.length() == 0) return null

                val first = results.getJSONObject(0)
                val art100 = first.optString("artworkUrl100").ifBlank { null }
                    ?: first.optString("artworkUrl60").ifBlank { null }
                    ?: return null

                // Replace 100x100 with 600x600 for sharp, high-resolution album cover
                art100.replace("100x100bb.jpg", "600x600bb.jpg")
                    .replace("60x60bb.jpg", "600x600bb.jpg")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchDeezer(artist: String?, title: String): String? {
        return try {
            val query = if (!artist.isNullOrBlank()) "$artist $title" else title
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encodedQuery&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "zonq/1.0 (Android; Radio App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                val first = data.getJSONObject(0)
                val album = first.optJSONObject("album") ?: return null
                album.optString("cover_xl").ifBlank { null }
                    ?: album.optString("cover_big").ifBlank { null }
                    ?: album.optString("cover_medium").ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanSearchTerm(term: String?): String? {
        if (term.isNullOrBlank()) return null
        var cleaned = term.trim()

        // Filter out typical radio promos / station identifiers
        val lower = cleaned.lowercase()
        if (lower.contains("élő adás") || lower.contains("elo adas") || lower.contains("live stream") ||
            lower == "rádió" || lower == "radio"
        ) {
            return null
        }

        // Remove video / audio tags like (Official Video), [Official Audio], (Radio Edit)
        cleaned = cleaned.replace(Regex("""\s*[\(\[](official\s*(video|audio|music\s*video|lyric\s*video)|radio\s*edit|remaster(ed)?\s*\d*|clip\s*officiel)[\)\]]""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s*\[feat\..*?\]""", RegexOption.IGNORE_CASE), "")

        return cleaned.trim().ifBlank { null }
    }
}
