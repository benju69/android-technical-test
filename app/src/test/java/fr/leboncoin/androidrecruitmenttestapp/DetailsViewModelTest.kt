package fr.leboncoin.androidrecruitmenttestapp

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plumbing note: Dispatchers.Main does not exist in plain JVM tests; the
 * SavedStateHandle-based ViewModel is restored-from-extras just like in the
 * real Android configuration-changes and process-death paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

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

    private fun createViewModel(id: Int) = DetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(DetailsActivity.ALBUM_ID_KEY to id)),
        repository = AlbumRepository(service, dao),
    )

    /** The detail screen observes the album with the id sent by the caller. */
    @Test
    fun `exposes the album matching the id from the saved state`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })

        val viewModel = createViewModel(id = sampleDto.first().id)

        viewModel.album.test {
            // stateIn shares with WhileSubscribed: before subscribing, .value
            // is the initial null; first awaited emission = null until the
            // query completes
            assertNull(awaitItem())
            advanceUntilIdle()
            val album = awaitItem()
            assertEquals(sampleDto.first().id, album?.id)
            assertTrue("Expected favorite=false initially", album?.favorite == false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Unknown id (cache empty): the flow resolves to null, the UI fallback. */
    @Test
    fun `unknown album id resolves to null`() = runTest {
        val viewModel = createViewModel(id = 999)

        viewModel.album.test {
            assertNull(awaitItem())
            advanceUntilIdle()
            // The re-emitted null equals the initial value and is deduplicated
            // by the shared StateFlow, so no second item can be awaited:
            // assert the live value while the subscription (and the sharing
            // coroutine) is still active.
            assertNull(viewModel.album.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Toggle favorite writes through to the persisted favorite state. */
    @Test
    fun `toggleFavorite flips the persisted favorite state`() = runTest {
        dao.insertAll(sampleDto.map { it.toEntity() })
        val viewModel = createViewModel(id = sampleDto.first().id)

        viewModel.album.test {
            assertNull(awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem()?.favorite == false)

            viewModel.toggleFavorite()
            advanceUntilIdle()
            assertTrue("Expected favorite=true after toggle", awaitItem()?.favorite == true)

            // Toggle back
            viewModel.toggleFavorite()
            advanceUntilIdle()
            assertTrue("Expected favorite=false after second toggle", awaitItem()?.favorite == false)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
