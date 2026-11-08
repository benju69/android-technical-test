package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Primary
import com.adevinta.spark.components.progress.Spinner
import com.adevinta.spark.components.scaffold.Scaffold
import com.adevinta.spark.components.text.Text
import fr.leboncoin.androidrecruitmenttestapp.AlbumsUiState
import fr.leboncoin.androidrecruitmenttestapp.AlbumsViewModel
import fr.leboncoin.androidrecruitmenttestapp.R
import fr.leboncoin.data.model.Album

@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onItemSelected: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { paddingValues ->
        when (val state = uiState) {
            is AlbumsUiState.Loading -> AlbumsLoading(paddingValues = paddingValues)

            is AlbumsUiState.Success -> AlbumsContent(
                albums = state.albums,
                onItemSelected = onItemSelected,
                onFavoriteToggle = viewModel::toggleFavorite,
                contentPadding = paddingValues,
            )

            is AlbumsUiState.Error -> AlbumsError(
                message = stringResource(R.string.albums_error, state.message),
                onRetry = viewModel::loadAlbums,
                paddingValues = paddingValues,
            )
        }
    }
}

@Composable
private fun AlbumsLoading(paddingValues: PaddingValues, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        Spinner()
    }
}

@Composable
private fun AlbumsContent(
    albums: List<Album>,
    onItemSelected: (Album) -> Unit,
    onFavoriteToggle: (Album) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = contentPadding,
    ) {
        items(
            items = albums,
            key = { album -> album.id },
        ) { album ->
            AlbumItem(
                album = album,
                onItemSelected = onItemSelected,
                onFavoriteToggle = onFavoriteToggle,
            )
        }
    }
}

@Composable
private fun AlbumsError(
    message: String,
    onRetry: () -> Unit,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = message)
            Button.Primary(onClick = onRetry, text = stringResource(R.string.retry))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlbumsLoadingPreview() {
    SparkTheme {
        AlbumsLoading(paddingValues = PaddingValues(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun AlbumsContentPreview() {
    val sampleAlbums = listOf(
        Album(
            id = 1,
            albumId = 1,
            title = "accusantium qui exercitationem molestiae quo ut nemo eum",
            url = "https://picsum.photos/600/600",
            thumbnailUrl = "https://picsum.photos/150/150",
            favorite = false,
        ),
        Album(
            id = 2,
            albumId = 1,
            title = "omnis laboriosam repellendus commodi",
            url = "https://picsum.photos/600/600",
            thumbnailUrl = "https://picsum.photos/150/150",
            favorite = true,
        ),
        Album(
            id = 3,
            albumId = 1,
            title = "sunt aut facere repellat provident occaecati excepturi optio reprehenderit",
            url = "https://picsum.photos/600/600",
            thumbnailUrl = "https://picsum.photos/150/150",
            favorite = false,
        ),
    )

    SparkTheme {
        AlbumsContent(
            albums = sampleAlbums,
            onItemSelected = {},
            onFavoriteToggle = {},
            contentPadding = PaddingValues(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AlbumsErrorPreview() {
    SparkTheme {
        AlbumsError(
            message = "Failed to load albums",
            onRetry = {},
            paddingValues = PaddingValues(16.dp),
        )
    }
}
