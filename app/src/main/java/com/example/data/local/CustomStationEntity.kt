package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_stations")
data class CustomStationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val favicon: String = "",
    val country: String = "Saját adó",
    val countryCode: String = "",
    val language: String = "Magyar",
    val tags: String = "Egyéni",
    val codec: String = "MP3",
    val bitrate: Int = 128,
    val createdAt: Long = System.currentTimeMillis()
)
