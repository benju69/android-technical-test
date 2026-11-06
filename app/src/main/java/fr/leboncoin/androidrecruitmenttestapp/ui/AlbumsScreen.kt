package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Primary
import com.adevinta.spark.components.progress.Spinner
import com.adevinta.spark.components.scaffold.Scaffold
import com.adevinta.spark.components.text.Text
import fr.leboncoin.androidrecruitmenttestapp.AlbumsUiState
import fr.leboncoin.androidrecruitmenttestapp.AlbumsViewModel
import fr.leboncoin.data.network.model.AlbumDto

@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onItemSelected: (AlbumDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { paddingValues ->
        when (val state = uiState) {
            is AlbumsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Spinner()
                }
            }

            is AlbumsUiState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = paddingValues,
                ) {
                    items(
                        items = state.albums,
                        key = { album -> album.id }
                    ) { album ->
                        AlbumItem(
                            album = album,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            }

            is AlbumsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = "Error: ${state.message}")
                        Button.Primary(onClick = { viewModel.loadAlbums() }, text = "Retry")
                    }
                }
            }
        }
    }
}