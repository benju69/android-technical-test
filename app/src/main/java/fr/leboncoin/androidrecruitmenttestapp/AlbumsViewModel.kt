package fr.leboncoin.androidrecruitmenttestapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.leboncoin.data.network.model.AlbumDto
import fr.leboncoin.data.repository.AlbumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Success(val albums: List<AlbumDto>) : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
}

/**
 * Constructed by Hilt: [AlbumRepository] itself is constructor-injected
 * through the data module's network graph, so no hand-written Factory is
 * needed anymore.
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: AlbumRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        viewModelScope.launch {
            try {
                val albums = repository.getAllAlbums()
                _uiState.value = AlbumsUiState.Success(albums)
            } catch (_: Exception) {
                _uiState.value = AlbumsUiState.Error("Failed to load albums")
            }
        }
    }
}
