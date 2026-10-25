package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM favorite_stations ORDER BY orderIndex ASC, addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteStationEntity>>

    @Query("SELECT * FROM favorite_stations ORDER BY orderIndex ASC, addedAt DESC")
    suspend fun getAllFavoritesList(): List<FavoriteStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteStationEntity)

    @Query("UPDATE favorite_stations SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateFavoriteOrder(id: String, orderIndex: Int)

    @Query("SELECT COALESCE(MAX(orderIndex), 0) + 1 FROM favorite_stations")
    suspend fun getNextOrderIndex(): Int

    @Query("DELETE FROM favorite_stations WHERE id = :id")
    suspend fun deleteFavorite(id: String)

    @Query("DELETE FROM favorite_stations")
    suspend fun clearAllFavorites()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteStationEntity>)

    @androidx.room.Transaction
    suspend fun replaceFavorites(favorites: List<FavoriteStationEntity>) {
        clearAllFavorites()
        if (favorites.isNotEmpty()) {
            insertFavorites(favorites)
        }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stations WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stations WHERE id = :id)")
    suspend fun isFavoriteDirect(id: String): Boolean

    // Custom Stations
    @Query("SELECT * FROM custom_stations ORDER BY createdAt DESC")
    fun getAllCustomStations(): Flow<List<CustomStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomStation(station: CustomStationEntity)

    @Query("DELETE FROM custom_stations WHERE id = :id")
    suspend fun deleteCustomStation(id: String)
}
