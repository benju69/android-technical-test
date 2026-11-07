package fr.leboncoin.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query(
        "SELECT a.*, EXISTS(SELECT 1 FROM favorites f WHERE f.albumId = a.id) AS favorite " +
            "FROM albums a ORDER BY a.id"
    )
    fun observeAllWithFavorite(): Flow<List<AlbumWithFavorite>>

    /**
     * Cache the network response. REPLACE keeps the albums table in sync with
     * the source; favorites are unaffected (separate table).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE albumId = :albumId")
    suspend fun deleteFavorite(albumId: Int)
}
