package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_stations")
data class FavoriteStationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
