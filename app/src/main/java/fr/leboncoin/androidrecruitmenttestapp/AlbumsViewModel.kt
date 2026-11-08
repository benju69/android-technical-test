package fr.leboncoin.androidrecruitmenttestapp

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
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: AlbumRepository,
) : ViewModel() {

    private val refreshFailed = MutableStateFlow(false)

    val uiState: StateFlow<AlbumsUiState> =
        combine(repository.observeAlbums(), refreshFailed) { albums, failed ->
            when {
                albums.isNotEmpty() -> AlbumsUiState.Success(albums)
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
        viewModelScope.launch {
            try {
                repository.refresh()
                refreshFailed.value = false
            } catch (_: Exception) {
                refreshFailed.value = true
            }
        }
    }

    fun toggleFavorite(album: Album) {
        viewModelScope.launch {
            repository.toggleFavorite(album.id, !album.favorite)
        }
    }
}
