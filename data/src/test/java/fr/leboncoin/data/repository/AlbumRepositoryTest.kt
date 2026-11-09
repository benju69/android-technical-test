package fr.leboncoin.data.repository

import app.cash.turbine.test
import fr.leboncoin.data.database.AlbumDao
import fr.leboncoin.data.database.AlbumEntity
import fr.leboncoin.data.database.AlbumWithFavorite
import fr.leboncoin.data.database.FavoriteEntity
import fr.leboncoin.data.mapper.toEntity
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.network.api.AlbumApiService
import fr.leboncoin.data.network.model.AlbumDto
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Sample data mirroring https://static.leboncoin.fr/img/shared/technical-test.json
private val sampleDto = listOf(
    AlbumDto(id = 1, albumId = 1, title = "accusantium qui exercitationem", url = "u1", thumbnailUrl = "tu1"),
    AlbumDto(id = 2, albumId = 1, title = "omnis laboriosam repellendus", url = "u2", thumbnailUrl = "tu2"),
    AlbumDto(id = 3, albumId = 2, title = "sunt aut facere repellat", url = "u3", thumbnailUrl = "tu3"),
)

/**
 * In-memory fake of the DAO reproducing the real tables' behaviour:
 * albums (network cache) and favorites span two collections so a refresh
 * never resets favorites, same as the real REPLACE semantics. insertAll
 * replaces rows by id as Room's OnConflictStrategy.REPLACE would.
 */
private class InMemoryAlbumDao : AlbumDao {
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
        this.albums.value = (this.albums.value + albums).associateBy { it.id }.values.toList()
    }

    override suspend fun insertFavorite(favorite: FavoriteEntity) {
        favorites.value = favorites.value + favorite.albumId
    }

    override suspend fun deleteFavorite(albumId: Int) {
        favorites.value = favorites.value - albumId
    }
}

/**
 * Fake of the Retrofit interface: configures success/failure per test, no
 * mocking library nor real HTTP involved.
 */
private class FakeAlbumApi : AlbumApiService {
    var result: Result<List<AlbumDto>> = Result.success(emptyList())

    override suspend fun getAlbums(): List<AlbumDto> = result.getOrThrow()
}

/**
 * The repository is the seam between the network, Room and the UI: these
 * tests pin its contracts — mapping entity+favorite flag -> domain model,
 * refresh write-through (and failure propagation), favorite round trip —
 * without Android, mocking framework nor device.
 */
class AlbumRepositoryTest {

    private val dao = InMemoryAlbumDao()
    private val api = FakeAlbumApi()
    private val repository = AlbumRepository(api, dao)

    /** The one-time contract: DTO network payload -> Entity cache write. */
    @Test
    fun `refresh caches the network payload`() = runTest {
        api.result = Result.success(sampleDto)

        repository.refresh()

        repository.observeAlbums().test {
            val albums = awaitItem().sortedBy { it.id }
            assertEquals(sampleDto.size, albums.size)
            assertEquals(
                sampleDto.map { Album(it.id, it.albumId, it.title, it.url, it.thumbnailUrl, favorite = false) },
                albums,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Airplane mode before any cache: the failure propagates to the caller. */
    @Test
    fun `refresh propagates a network failure without caching anything`() = runTest {
        api.result = Result.failure(IOException("Airplane mode"))

        val outcome = runCatching { repository.refresh() }

        assertTrue("Expected refresh() to throw, got $outcome", outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is IOException)
        repository.observeAlbums().test {
            assertTrue("Expected an empty cache", awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Domain mapping: entity + favorites table -> Album.favorite flag. */
    @Test
    fun `observeAlbums projects the favorite flag into the domain model`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })
        dao.insertFavorite(FavoriteEntity(albumId = 2))

        repository.observeAlbums().test {
            val albums = awaitItem().sortedBy { it.id }

            assertTrue(albums.first { it.id == 1 }.favorite.not())
            assertTrue(albums.first { it.id == 2 }.favorite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Single-album read: known id -> album, unknown id -> null. */
    @Test
    fun `observeAlbum emits the album or null when the id is unknown`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })

        repository.observeAlbum(id = 2).test {
            val album = awaitItem()
            assertEquals(2, album?.id)
            assertFalse(album?.favorite ?: true)
            cancelAndIgnoreRemainingEvents()
        }

        repository.observeAlbum(id = 99).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Favorites tab contract: only marked albums, live on inserts/deletes. */
    @Test
    fun `observeFavorites exposes only marked albums and follows the toggles`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })

        repository.observeFavorites().test {
            assertTrue(awaitItem().isEmpty())

            repository.toggleFavorite(albumId = 3, favorite = true)
            val marked = awaitItem()
            assertEquals(listOf(3), marked.map { it.id })
            assertTrue(marked.first().favorite)

            // The write-through only adds the favorite, never the entity
            repository.toggleFavorite(albumId = 3, favorite = false)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The offline-first invariant: a refresh that fails must never erase or
     * alter the persisted state (favorites above all).
     */
    @Test
    fun `a failing refresh leaves favorites untouched`() = runTest {
        api.result = Result.success(sampleDto)
        dao.insertAll(sampleDto.map { it.toEntity() })
        dao.insertFavorite(FavoriteEntity(albumId = 1))

        api.result = Result.failure(IOException("Airplane mode"))
        runCatching { repository.refresh() }

        repository.observeFavorites().test {
            assertEquals(listOf(1), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
