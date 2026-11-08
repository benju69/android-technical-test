package fr.leboncoin.data.model

/**
 * Domain model used by the UI layer, decoupled from both the network DTO and
 * the Room entity. `favorite` reflects the local (persisted) user state.
 *
 * navigation passes only the album *id* (see DetailsActivity / DetailsViewModel), the detail screen re-observes it from
 * the repository so the UI always reads the freshest state from the cache.
 */
data class Album(
    val id: Int,
    val albumId: Int,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val favorite: Boolean,
)
