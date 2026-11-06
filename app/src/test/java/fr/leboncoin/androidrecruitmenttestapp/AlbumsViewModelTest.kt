package fr.leboncoin.androidrecruitmenttestapp

import app.cash.turbine.test
import fr.leboncoin.data.network.api.AlbumApiService
import fr.leboncoin.data.network.model.AlbumDto
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

// -----------------------------------------------------------------------------
// Sample data shared by the tests.
//
// They mirror the shape of https://static.leboncoin.fr/img/shared/technical-test.json:
// an album "contains" many photos, each photo has its albumId, a display
// `title` and two image URLs (full `url` + `thumbnailUrl`).
// -----------------------------------------------------------------------------
private val sampleAlbums = listOf(
    AlbumDto(id = 1, albumId = 1, title = "accusantium qui exercitationem", url = "u1", thumbnailUrl = "tu1"),
    AlbumDto(id = 2, albumId = 1, title = "omnis laboriosam repellendus", url = "u2", thumbnailUrl = "tu2"),
    AlbumDto(id = 3, albumId = 2, title = "sunt aut facere repellat", url = "u3", thumbnailUrl = "tu3"),
)

/**
 * Hand-written fake of the API.
 *
 * It lives one level below [AlbumRepository] because the ViewModel receives the
 * concrete `AlbumRepository` class (not an interface): faking the Retrofit
 * interface lets us test the whole VM chain — repository included — without
 * needing a mocking library (MockK) or real HTTP (OkHttp MockWebServer).
 *
 * The fake returns a pre-configured `Result`:
 * - `Result.success(albums)` -> the "happy path" network call
 * - `Result.failure(exception)` -> simulates any network/parse error
 */
private class FakeAlbumApiService : AlbumApiService {
    var result: Result<List<AlbumDto>> = Result.success(emptyList())

    override suspend fun getAlbums(): List<AlbumDto> = result.getOrThrow()
}

/**
 * Note on the plumbing: on a real device, `viewModelScope` runs its coroutines
 * on `Dispatchers.Main.immediate`, which only exists on Android. In a plain JVM
 * unit test there is no Main dispatcher, so any coroutine touching the
 * ViewModel would crash with an IllegalStateException. That's why we replace it
 * with a test dispatcher in @Before and restore the real one in @After.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    /**
     * StandardTestDispatcher queues coroutines instead of running them
     * eagerly: the tester explicitly advances time with advanceUntilIdle().
     *
     * This is exactly what we want here: it lets us observe the intermediate
     * `Loading` state before the network call completes, the same way a slow
     * network would on a device.
     */
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var service: FakeAlbumApiService

    @Before
    fun setUp() {
        // Install the test dispatcher as Main for every test
        Dispatchers.setMain(mainDispatcher)
        // Fresh fake per test so tests stay fully isolated from one another
        service = FakeAlbumApiService()
    }

    @After
    fun tearDown() {
        // Restore the real dispatcher so this test never leaks into other suites
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AlbumsViewModel(AlbumRepository(service))

    /**
     * Happy path when entering the screen:
     * the VM constructor triggers loadAlbums() in its init block, so the
     * UI state must go Loading (frame 1, the spinner) then Success (frame 2,
     * the list) without anything else in between.
     *
     * Turbine turns the StateFlow into a queue we can assert on with
     * awaitItem(); it is the canonical way to test flows emitted from
     * "outside" the test coroutine.
     */
    @Test
    fun `init exposes Loading then Success with fetched albums`() = runTest {
        service.result = Result.success(sampleAlbums)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // StateFlow always emits its current value first to a new
            // subscriber: right after init, that value is Loading
            // (the network call is queued but has not run yet).
            assertEquals(AlbumsUiState.Loading, awaitItem())

            // Run the queued coroutine: repository.getAllAlbums() returns
            // our fake albums and the state switches to Success
            advanceUntilIdle()

            val state = awaitItem()
            assertTrue("Expected Success state, got $state", state is AlbumsUiState.Success)
            // Exact equality: no album added, dropped or reordered
            assertEquals(sampleAlbums, (state as AlbumsUiState.Success).albums)
            // StateFlow never completes, so stop the collection here
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Error path: the repository throws (e.g. no network, 5xx, malformed
     * JSON). The ViewModel must surface a declarative Error state instead of
     * crashing — "the app must not crash systematically" is a hard rejection
     * criterion in the assignment.
     */
    @Test
    fun `init exposes Loading then Error when repository fails`() = runTest {
        service.result = Result.failure(IOException("Network down"))

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
     * Recovery path: same behaviour as the Retry button of AlbumsScreen,
     * which calls viewModel::loadAlbums from the Error state.
     *
     * Sequence:
     * 1. list fails -> Error
     * 2. network "comes back" (fake reconfigured) + loadAlbums() called again
     * 3. state must be back to Success with fresh data
     */
    @Test
    fun `retry after a failure recovers to Success`() = runTest {
        service.result = Result.failure(IOException("Network down"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        // Sanity check the precondition: we are actually in the Error state
        assertTrue(viewModel.uiState.value is AlbumsUiState.Error)

        // Simulates the user tapping Retry on a working network
        service.result = Result.success(sampleAlbums)
        viewModel.loadAlbums()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state after retry, got $state", state is AlbumsUiState.Success)
        assertEquals(sampleAlbums, (state as AlbumsUiState.Success).albums)
    }
}
