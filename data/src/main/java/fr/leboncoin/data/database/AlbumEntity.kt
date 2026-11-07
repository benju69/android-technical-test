package fr.leboncoin.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Album item as fetched from the network, cached as-is (schema mirrors the
 * remote JSON). Refresh merges with REPLACE, favorite state lives in a
 * separate table so a network refresh can never wipe user favorites.
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: Int,
    val albumId: Int,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
)

/**
 * One row per favorited album ID. A separate table instead of a `favorite`
 * column on [AlbumEntity] so the network refresh (INSERT/REPLACE of albums)
 * does not reset user selections.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val albumId: Int,
)

/**
 * Projection returned by the DAO query: cached album + whether its ID has a
 * row in the favorites table.
 */
data class AlbumWithFavorite(
    @Embedded val album: AlbumEntity,
    val favorite: Boolean,
)
