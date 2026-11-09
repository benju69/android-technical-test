package fr.leboncoin.androidrecruitmenttestapp.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.repository.AlbumRepository
import fr.leboncoin.androidrecruitmenttestapp.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Observes a single album by id, restored from [SavedStateHandle] — this is
 * the piece making the detail screen resilient to rotation AND process death
 * (the id survives the process; the album content is re-read from Room).
 *
 * null means "no album with this id (yet)": the UI renders its fallback.
 */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
) : ViewModel() {

    val albumId: Int = checkNotNull(savedStateHandle[Routes.ALBUM_ID_ARG])

    val album: StateFlow<Album?> = repository.observeAlbum(albumId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = album.value ?: return@launch
            repository.toggleFavorite(current.id, !current.favorite)
        }
    }
}
