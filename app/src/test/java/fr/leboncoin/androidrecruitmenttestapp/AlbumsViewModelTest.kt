package fr.leboncoin.androidrecruitmenttestapp

import fr.leboncoin.data.network.api.AlbumApiService
import fr.leboncoin.data.network.model.AlbumDto
import fr.leboncoin.data.repository.AlbumRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.logging.Logger

class AlbumsViewModelTest {

    @Test
    fun loadsAlbums_emitsNonEmptyList() {
        val fakeService = object : AlbumApiService {
            override suspend fun getAlbums(): List<AlbumDto> = listOf(
                AlbumDto(id = 1, albumId = 1, title = "t", url = "u", thumbnailUrl = "tu")
            )
        }
        val repository = AlbumRepository(fakeService)
        val vm = AlbumsViewModel(Logger.getGlobal(), repository)

        assertTrue("Expected albums to be loaded", vm.albums.value.isNotEmpty())
    }
}

