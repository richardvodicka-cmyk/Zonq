package com.example.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.R

object CategoryTranslations {

    fun getCountryResId(country: String): Int? {
        return when (country.trim()) {
            "Magyarország", "Slovensko", "Hungary" -> R.string.country_hungary
            "Szlovákia", "Slovakia" -> R.string.country_slovakia
            "Csehország", "Česko", "Czechia", "Czech Republic" -> R.string.country_czechia
            "Egyesült Államok", "USA", "Spojené štáty", "United States" -> R.string.country_usa
            "Egyesült Királyság", "UK", "Spojené kráľovstvo", "United Kingdom" -> R.string.country_uk
            "Németország", "Nemecko", "Germany" -> R.string.country_germany
            "Franciaország", "Francúzsko", "France" -> R.string.country_france
            "Olaszország", "Taliansko", "Italy" -> R.string.country_italy
            "Spanyolország", "Španielsko", "Spain" -> R.string.country_spain
            "Svájc", "Švajčiarsko", "Switzerland" -> R.string.country_switzerland
            "Ausztria", "Rakúsko", "Austria" -> R.string.country_austria
            else -> null
        }
    }

    fun getGenreResId(genre: String): Int? {
        return when (genre.trim()) {
            "Pop" -> R.string.genre_pop
            "Rock" -> R.string.genre_rock
            "Retro" -> R.string.genre_retro
            "Dance / Electro", "Dance", "Electro" -> R.string.genre_dance_electro
            "Hírek", "Správy", "News" -> R.string.genre_news
            "Jazz" -> R.string.genre_jazz
            "Klasszikus", "Klasická hudba", "Classical" -> R.string.genre_classical
            "Chillout", "Chill" -> R.string.genre_chillout
            "Country" -> R.string.genre_country
            "Hip-Hop", "Hip Hop" -> R.string.genre_hiphop
            else -> null
        }
    }

    fun getLanguageResId(language: String): Int? {
        return when (language.trim()) {
            "Magyar", "Maďarčina", "Hungarian" -> R.string.lang_hungarian
            "Szlovák", "Slovenčina", "Slovak" -> R.string.lang_slovak
            "Cseh", "Čeština", "Czech" -> R.string.lang_czech
            "Angol", "Angličtina", "English" -> R.string.lang_english
            "Német", "Nemčina", "German" -> R.string.lang_german
            "Francia", "Francúzština", "French" -> R.string.lang_french
            "Olasz", "Taliančina", "Italian" -> R.string.lang_italian
            "Spanyol", "Španielčina", "Spanish" -> R.string.lang_spanish
            else -> null
        }
    }

    fun getTranslatedCountry(context: Context, rawCountry: String): String {
        val resId = getCountryResId(rawCountry) ?: return rawCountry
        return context.getString(resId)
    }

    fun getTranslatedGenre(context: Context, rawGenre: String): String {
        val resId = getGenreResId(rawGenre) ?: return rawGenre
        return context.getString(resId)
    }

    fun getTranslatedLanguage(context: Context, rawLanguage: String): String {
        val resId = getLanguageResId(rawLanguage) ?: return rawLanguage
        return context.getString(resId)
    }
}
