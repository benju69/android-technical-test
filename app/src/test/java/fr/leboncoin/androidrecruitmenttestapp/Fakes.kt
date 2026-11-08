package fr.leboncoin.androidrecruitmenttestapp

import fr.leboncoin.data.database.AlbumDao
import fr.leboncoin.data.database.AlbumEntity
import fr.leboncoin.data.database.AlbumWithFavorite
import fr.leboncoin.data.database.FavoriteEntity
import fr.leboncoin.data.mapper.toEntity
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.network.api.AlbumApiService
import fr.leboncoin.data.network.model.AlbumDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

// Sample data mirroring https://static.leboncoin.fr/img/shared/technical-test.json
val sampleDto = listOf(
    AlbumDto(id = 1, albumId = 1, title = "accusantium qui exercitationem", url = "u1", thumbnailUrl = "tu1"),
    AlbumDto(id = 2, albumId = 1, title = "omnis laboriosam repellendus", url = "u2", thumbnailUrl = "tu2"),
    AlbumDto(id = 3, albumId = 2, title = "sunt aut facere repellat", url = "u3", thumbnailUrl = "tu3"),
)

fun AlbumEntity.toAlbum(favorite: Boolean) = Album(
    id = id, albumId = albumId, title = title, url = url,
    thumbnailUrl = thumbnailUrl, favorite = favorite,
)

/** What the UI should observe once these DTOs are cached (no favorite set). */
val sampleAlbums = sampleDto.map { it.toEntity().toAlbum(favorite = false) }

/**
 * Fake of the Retrofit interface: configures success/failure per test, no
 * mocking library nor real HTTP involved.
 */
class FakeAlbumApiService : AlbumApiService {
    var result: Result<List<AlbumDto>> = Result.success(emptyList())

    override suspend fun getAlbums(): List<AlbumDto> = result.getOrThrow()
}

/**
 * In-memory fake of the DAO reproducing the real tables' behaviour:
 * albums (network cache) and favorites kept in their own collections so a
 * refresh never resets favorites, same as the real REPLACE semantics.
 */
class FakeAlbumDao : AlbumDao {
    private val albums = MutableStateFlow<List<AlbumEntity>>(emptyList())
    private val favorites = MutableStateFlow<Set<Int>>(emptySet())

    override fun observeAllWithFavorite(): Flow<List<AlbumWithFavorite>> =
        combine(albums, favorites) { data, favs ->
            data.map { AlbumWithFavorite(album = it, favorite = it.id in favs) }
        }

    override fun observeAlbum(id: Int): Flow<AlbumWithFavorite?> =
        combine(albums, favorites) { data, favs ->
            data.find { it.id == id }?.let { AlbumWithFavorite(album = it, favorite = it.id in favs) }
        }

    override fun observeFavorites(): Flow<List<AlbumWithFavorite>> =
        combine(albums, favorites) { data, favs ->
            data.filter { it.id in favs }.map { AlbumWithFavorite(album = it, favorite = true) }
        }

    override suspend fun insertAll(albums: List<AlbumEntity>) {
        this.albums.value = albums
    }

    override suspend fun insertFavorite(favorite: FavoriteEntity) {
        favorites.value = favorites.value + favorite.albumId
    }

    override suspend fun deleteFavorite(albumId: Int) {
        favorites.value = favorites.value - albumId
    }
}
