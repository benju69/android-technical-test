package fr.leboncoin.androidrecruitmenttestapp

import app.cash.turbine.test
import fr.leboncoin.data.database.FavoriteEntity
import fr.leboncoin.data.mapper.toEntity
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.repository.AlbumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Favorites tab is the favorite-only Room query; these tests mirror the
 * user flow: nothing marked yet -> mark favorites from the database side ->
 * untoggle from the tab itself.
 *
 * Fakes (FakeAlbumApiService / FakeAlbumDao) live in Fakes.kt, shared with
 * AlbumsViewModelTest and DetailsViewModelTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var service: FakeAlbumApiService
    private lateinit var dao: FakeAlbumDao

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        service = FakeAlbumApiService()
        dao = FakeAlbumDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = FavoritesViewModel(AlbumRepository(service, dao))

    /** An empty favorites tab is a valid state, not an error. */
    @Test
    fun `exposes an empty list when nothing is marked as favorite`() = runTest {
        val viewModel = createViewModel()

        viewModel.favorites.test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Marking favorites elsewhere (list, detail) re-emits on this tab. */
    @Test
    fun `reflects favorites marked in the database`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })
        val viewModel = createViewModel()

        viewModel.favorites.test {
            assertEquals(0, awaitItem().size)

            dao.insertFavorite(FavoriteEntity(albumId = sampleDto.first().id))
            advanceUntilIdle()
            val promoted = awaitItem()
            assertEquals(1, promoted.size)
            assertTrue("Expected the album to be favorite", promoted.first().favorite)
            assertEquals(sampleDto.first().id, promoted.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Untoggling from the tab itself removes the album from the list. */
    @Test
    fun `toggleFavorite removes the album from the favorites list`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })
        dao.insertFavorite(FavoriteEntity(albumId = sampleDto.first().id))
        val viewModel = createViewModel()

        viewModel.favorites.test {
            // First awaited item = the stateIn initial value; the shared
            // upstream emissions are delivered once we advance the scheduler
            assertEquals(0, awaitItem().size)
            advanceUntilIdle()
            assertEquals(1, awaitItem().size)

            viewModel.toggleFavorite(sampleDto.first().id.let { id ->
                // Candidate Album as observed by the UI (favorite = true)
                Album(id = id, albumId = 1, title = "t", url = "u", thumbnailUrl = "tu", favorite = true)
            })
            advanceUntilIdle()
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
