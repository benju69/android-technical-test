package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.adevinta.spark.components.text.Text
import fr.leboncoin.androidrecruitmenttestapp.FavoritesViewModel
import fr.leboncoin.androidrecruitmenttestapp.R
import fr.leboncoin.data.model.Album

/**
 * Favorites tab: same list item as the Albums tab ([AlbumItem]); an explicit
 * empty state covers the "nothing marked yet" case instead of a blank screen.
 * No nested Scaffold either — the host shell (MainActivity) already owns the
 * bars and their insets.
 */
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onItemSelected: (Album) -> Unit,
    modifier: Modifier = Modifier,
    /** Prefix of the shared-element key of thumbnails (source tab name). */
    sharedKeyPrefix: String? = null,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    FavoritesContent(
        favorites = favorites,
        onItemSelected = onItemSelected,
        onFavoriteToggle = viewModel::toggleFavorite,
        sharedKeyPrefix = sharedKeyPrefix,
        modifier = modifier,
    )
}

@Composable
fun FavoritesContent(
    favorites: List<Album>,
    onItemSelected: (Album) -> Unit,
    onFavoriteToggle: (Album) -> Unit,
    modifier: Modifier = Modifier,
    sharedKeyPrefix: String? = null,
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.favorites_empty))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = favorites,
                key = { album -> album.id },
            ) { album ->
                AlbumItem(
                    album = album,
                    onItemSelected = onItemSelected,
                    onFavoriteToggle = onFavoriteToggle,
                    imageSharedKey = sharedKeyPrefix?.let { "$it-${album.id}" },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesContentPreview() {
    SparkTheme {
        FavoritesContent(
            favorites = listOf(
                Album(
                    id = 2,
                    albumId = 1,
                    title = "omnis laboriosam repellendus commodi",
                    url = "https://picsum.photos/600/600",
                    thumbnailUrl = "https://picsum.photos/150/150",
                    favorite = true,
                ),
            ),
            onItemSelected = {},
            onFavoriteToggle = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesEmptyPreview() {
    SparkTheme {
        FavoritesContent(
            favorites = emptyList(),
            onItemSelected = {},
            onFavoriteToggle = {},
        )
    }
}
