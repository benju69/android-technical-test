package fr.leboncoin.data.repository

import fr.leboncoin.data.network.api.AlbumApiService

class AlbumRepository(
    private val albumApiService: AlbumApiService,
) {
    
    suspend fun getAllAlbums() = albumApiService.getAlbums()
}