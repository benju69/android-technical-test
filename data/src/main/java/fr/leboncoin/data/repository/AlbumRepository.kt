package fr.leboncoin.data.repository

import fr.leboncoin.data.database.AlbumDao
import fr.leboncoin.data.database.AlbumWithFavorite
import fr.leboncoin.data.database.FavoriteEntity
import fr.leboncoin.data.mapper.toAlbum
import fr.leboncoin.data.mapper.toEntity
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.network.api.AlbumApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first repository:
 * - reads come from Room only -> the list (and favorites) survive process
 *   death, restart and airplane mode;
 * - [refresh] fetches the network payload and caches it (write-through);
 * - the network can fail freely as long as the cache holds data.
 */
@Singleton
class AlbumRepository @Inject constructor(
    private val albumApiService: AlbumApiService,
    private val albumDao: AlbumDao,
) {

    fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAllWithFavorite().map { it.map(AlbumWithFavorite::toAlbum) }

    /**
     * Single album observation by id; null once the query completes without
     * a match (the cache must be setup before).
     */
    fun observeAlbum(id: Int): Flow<Album?> =
        albumDao.observeAlbum(id).map { it?.toAlbum() }

    /**
     * Only the albums marked as favorite (inner join on the favorites table),
     * re-emitted on any change to either table — used by the Favorites tab.
     */
    fun observeFavorites(): Flow<List<Album>> =
        albumDao.observeFavorites().map { it.map(AlbumWithFavorite::toAlbum) }

    /**
     * Fetches the remote albums and writes them in the cache.
     * Favorites live in their own table, so a refresh never resets them.
     * Exceptions propagate to the caller (the ViewModel decides how to
     * surface them).
     */
    suspend fun refresh() {
        val albums = albumApiService.getAlbums()
        albumDao.insertAll(albums.map { it.toEntity() })
    }

    /**
     * Writes the new favorite state of an album to the favorites table.
     *
     * @param favorite the NEW state, already flipped by the caller.
     */
    suspend fun toggleFavorite(albumId: Int, favorite: Boolean) {
        if (favorite) {
            albumDao.insertFavorite(FavoriteEntity(albumId = albumId))
        } else {
            albumDao.deleteFavorite(albumId)
        }
    }
}
