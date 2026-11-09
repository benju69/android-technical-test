package fr.leboncoin.androidrecruitmenttestapp.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.repository.AlbumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Success(val albums: List<Album>) : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
}

/**
 * Offline-first ViewModel: the source of truth is the Room flow. The network
 * only refreshes the cache (see [loadAlbums]); as long as the cache holds
 * albums the UI shows them, network failures are only ever surfaced when
 * there is nothing to display.
 *
 * The UI state is a pure reduction of three inputs (cache + two refresh
 * flags) — the state is never "assigned imperatively", it always follows
 * from whether there is content, a request in flight, or a failure:
 *
 *   albums.isNotEmpty() -> Success (the cache wins, even mid-refetch)
 *   refreshing          -> Loading (first load AND retry in flight)
 *   refreshFailed       -> Error (nothing displayed + last attempt failed)
 *   else                -> Loading (initial)
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: AlbumRepository,
) : ViewModel() {

    private val refreshFailed = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<AlbumsUiState> =
        combine(repository.observeAlbums(), refreshFailed, refreshing) { albums, failed, refreshing ->
            when {
                albums.isNotEmpty() -> AlbumsUiState.Success(albums)
                refreshing -> AlbumsUiState.Loading
                failed -> AlbumsUiState.Error("Failed to load albums")
                else -> AlbumsUiState.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumsUiState.Loading,
        )

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        // Guard against double-taps on Retry: a load is already in flight
        if (refreshing.value) return

        refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh()
                refreshFailed.value = false
            } catch (_: Exception) {
                refreshFailed.value = true
            } finally {
                refreshing.value = false
            }
        }
    }

    fun toggleFavorite(album: Album) {
        viewModelScope.launch {
            repository.toggleFavorite(album.id, !album.favorite)
        }
    }
}
