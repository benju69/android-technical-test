package fr.leboncoin.data.repository

import fr.leboncoin.data.network.api.AlbumApiService
import javax.inject.Inject

class AlbumRepository @Inject constructor(
    private val albumApiService: AlbumApiService,
) {
    
    suspend fun getAllAlbums() = albumApiService.getAlbums()
}
