package fr.leboncoin.androidrecruitmenttestapp

import app.cash.turbine.test
import fr.leboncoin.data.mapper.toEntity
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
import java.io.IOException

/**
 * Plumbing note: viewModelScope runs on Dispatchers.Main.immediate which only
 * exists on Android. In plain JVM tests we replace it with a
 * StandardTestDispatcher so the test explicitly advances time
 * (advanceUntilIdle) and observes intermediate states like Loading.
 *
 * All tests assert from inside uiState.test{} (Turbine): the ViewModel
 * shares its state with SharingStarted.WhileSubscribed, so the upstream
 * pipeline only runs while a collector is subscribed to the StateFlow —
 * reading .value without a subscription would never leave the initial value.
 *
 * Fakes (FakeAlbumApiService / FakeAlbumDao) live in Fakes.kt, shared with
 * DetailsViewModelTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

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

    private fun createViewModel() = AlbumsViewModel(AlbumRepository(service, dao))

    /**
     * Happy path: init triggers a refresh; once the fake API answered, the
     * Room-backed flow emits the albums and the state goes Loading -> Success.
     */
    @Test
    fun `init exposes Loading then Success with cached albums`() = runTest {
        service.result = Result.success(sampleDto)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // StateFlow always emits its current value first; the refresh is
            // queued but has not run yet
            assertEquals(AlbumsUiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem()
            assertTrue("Expected Success state, got $state", state is AlbumsUiState.Success)
            assertEquals(sampleAlbums, (state as AlbumsUiState.Success).albums)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Error path, first launch: nothing in the cache and the network fails.
     * The ViewModel must surface Error instead of crashing (hard rejection
     * criterion of the assignment).
     */
    @Test
    fun `init exposes Loading then Error when refresh fails and cache is empty`() = runTest {
        service.result = Result.failure(IOException("Airplane mode"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(AlbumsUiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem()
            assertTrue("Expected Error state, got $state", state is AlbumsUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Offline requirement of the assignment: data must survive restarts and
     * stay available without network. Here the cache was pre-seeded (as it
     * would be after a previous session) and the network is down: the UI
     * must still show the cached albums, never the Error state.
     */
    @Test
    fun `cached albums are displayed even when the network is down`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })
        service.result = Result.failure(IOException("Airplane mode"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(AlbumsUiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem()
            assertTrue("Expected Success state, got $state", state is AlbumsUiState.Success)
            assertEquals(sampleAlbums, (state as AlbumsUiState.Success).albums)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Recovery path: same behaviour as the Retry button of AlbumsScreen.
     * Fail first (Error state), then let the network answer and retry.
     */
    @Test
    fun `retry after a failure recovers to Success`() = runTest {
        service.result = Result.failure(IOException("Network down"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(AlbumsUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue("Expected Error state", awaitItem() is AlbumsUiState.Error)

            // Simulates the user tapping Retry on a working network
            service.result = Result.success(sampleDto)
            viewModel.loadAlbums()
            advanceUntilIdle()

            val state = awaitItem()
            assertTrue("Expected Success state after retry, got $state", state is AlbumsUiState.Success)
            assertEquals(sampleAlbums, (state as AlbumsUiState.Success).albums)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Favorites round trip: toggling writes through the repository to the
     * "favorites" table, and the Room flow re-emits the list with the
     * updated favorite flag. This is the persistence requirement.
     */
    @Test
    fun `toggleFavorite updates the persisted favorite state`() = runTest {
        service.result = Result.success(sampleDto)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(AlbumsUiState.Loading, awaitItem())
            advanceUntilIdle()
            val initial = awaitItem()
            assertTrue("Expected Success state, got $initial", initial is AlbumsUiState.Success)
            val album = (initial as AlbumsUiState.Success).albums[0]
            assertTrue("Expected the album not to be favorite initially", !album.favorite)

            viewModel.toggleFavorite(album)
            advanceUntilIdle()

            var state = awaitItem()
            assertTrue("Expected Success state, got $state", state is AlbumsUiState.Success)
            var toggled = (state as AlbumsUiState.Success).albums.first { it.id == album.id }
            assertTrue("Expected the album to become favorite", toggled.favorite)

            // Toggle back
            viewModel.toggleFavorite(toggled)
            advanceUntilIdle()

            state = awaitItem()
            val reverted = (state as AlbumsUiState.Success).albums.first { it.id == album.id }
            assertTrue("Expected the album to no longer be favorite", !reverted.favorite)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
