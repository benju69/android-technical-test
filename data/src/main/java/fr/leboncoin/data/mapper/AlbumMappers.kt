package fr.leboncoin.data.mapper

import fr.leboncoin.data.database.AlbumEntity
import fr.leboncoin.data.database.AlbumWithFavorite
import fr.leboncoin.data.model.Album
import fr.leboncoin.data.network.model.AlbumDto

fun AlbumDto.toEntity() = AlbumEntity(
    id = id,
    albumId = albumId,
    title = title,
    url = url,
    thumbnailUrl = thumbnailUrl,
)

fun AlbumWithFavorite.toAlbum() = Album(
    id = album.id,
    albumId = album.albumId,
    title = album.title,
    url = album.url,
    thumbnailUrl = album.thumbnailUrl,
    favorite = favorite,
)
