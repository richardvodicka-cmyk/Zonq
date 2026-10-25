package com.example.data.model

import java.text.Normalizer

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String = "",
    val country: String = "",
    val countryCode: String = "",
    val language: String = "",
    val tags: String = "",
    val codec: String = "MP3",
    val bitrate: Int = 128,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false
) {
    fun getDisplaySubtitle(context: android.content.Context? = null): String {
        val parts = mutableListOf<String>()
        if (country.isNotBlank()) {
            val translated = if (context != null) {
                com.example.util.CategoryTranslations.getTranslatedCountry(context, country)
            } else country
            parts.add(translated)
        }
        if (tags.isNotBlank()) {
            val firstTag = tags.split(",").firstOrNull()?.trim()
            if (!firstTag.isNullOrBlank()) {
                val translatedGenre = if (context != null) {
                    com.example.util.CategoryTranslations.getTranslatedGenre(context, firstTag)
                } else firstTag
                parts.add(translatedGenre)
            }
        }
        if (bitrate > 0) parts.add("${bitrate}k")
        return parts.joinToString(" • ")
    }

    val displaySubtitle: String
        get() = getDisplaySubtitle(null)
}

/**
 * Normalizes text by removing accents/diacritics (e.g. á -> a, ő -> o, é -> e)
 * and special characters for accurate, natural Hungarian & international searching.
 */
fun String.normalizeForSearch(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return "\\p{InCombiningDiacriticalMarks}+".toRegex()
        .replace(normalized, "")
        .lowercase()
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

/**
 * Checks whether a station's name matches the user's search query.
 * Only searches within the radio station's NAME (not unrelated genre tags or countries)
 * so that users only see stations whose name actually matches their input.
 */
fun Station.matchesSearchQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true

    val normName = name.normalizeForSearch()
    val normQuery = cleanQuery.normalizeForSearch()
    if (normQuery.isBlank()) return true

    val queryWords = normQuery.split(" ").filter { it.isNotBlank() }
    if (queryWords.isEmpty()) return true

    // All words typed by user must appear in the station name
    return queryWords.all { word -> normName.contains(word) }
}

/**
 * Calculates search relevance score so exact and prefix name matches appear at top.
 */
fun Station.searchRelevance(query: String): Int {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return 0

    val normName = name.normalizeForSearch()
    val normQuery = cleanQuery.normalizeForSearch()

    return when {
        normName == normQuery -> 100
        normName.startsWith(normQuery) -> 80
        normName.split(" ").any { it.startsWith(normQuery) } -> 60
        normName.contains(normQuery) -> 40
        else -> 10
    }
}

/**
 * Deduplicates stations by ID, normalized stream URL, and normalized station name.
 * Ensures no radio station appears multiple times in any list screen.
 */
fun List<Station>.distinctStations(): List<Station> {
    val seenIds = HashSet<String>()
    val seenNormalizedUrls = HashSet<String>()
    val seenNormalizedNames = HashSet<String>()
    val result = ArrayList<Station>(this.size)

    for (rawStation in this) {
        val effectiveId = if (rawStation.id.isNotBlank()) rawStation.id.trim() else "st-${rawStation.url.hashCode()}"
        val station = if (rawStation.id == effectiveId) rawStation else rawStation.copy(id = effectiveId)
        val normId = effectiveId
        val normName = station.name.trim().lowercase().replace("\\s+".toRegex(), " ")

        val normUrl = station.url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .lowercase()

        // Check if ID was seen
        if (normId.isNotEmpty() && seenIds.contains(normId)) {
            continue
        }

        // Check if stream URL was seen
        if (normUrl.isNotEmpty() && seenNormalizedUrls.contains(normUrl)) {
            continue
        }

        // Check if Station Name was seen
        if (normName.isNotEmpty() && seenNormalizedNames.contains(normName)) {
            continue
        }

        seenIds.add(normId)
        if (normUrl.isNotEmpty()) seenNormalizedUrls.add(normUrl)
        if (normName.isNotEmpty()) seenNormalizedNames.add(normName)
        result.add(station)
    }

    return result
}
