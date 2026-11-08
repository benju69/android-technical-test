package fr.leboncoin.androidrecruitmenttestapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.repository.AlbumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Favorites tab: observes the favorite-only Room query, so the list re-emits
 * as the user toggles favorites anywhere in the app (list item or detail
 * screen) — no manual refresh needed.
 *
 * emptyList() while the query has not emitted yet: an empty favorites tab IS
 * a valid state (nothing marked yet), so no separate Loading state.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: AlbumRepository,
) : ViewModel() {

    val favorites: StateFlow<List<Album>> = repository.observeFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun toggleFavorite(album: Album) {
        viewModelScope.launch {
            repository.toggleFavorite(album.id, !album.favorite)
        }
    }
}
