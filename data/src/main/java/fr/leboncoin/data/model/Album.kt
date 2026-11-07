package fr.leboncoin.data.model

/**
 * Domain model used by the UI layer, decoupled from both the network DTO and
 * the Room entity. `favorite` reflects the local (persisted) user state.
 */
data class Album(
    val id: Int,
    val albumId: Int,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val favorite: Boolean,
)
