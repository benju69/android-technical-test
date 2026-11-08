package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.chips.ChipTinted
import com.adevinta.spark.components.text.Text
import fr.leboncoin.androidrecruitmenttestapp.R
import fr.leboncoin.data.model.Album

/**
 * Detail screen content (stateless, préviewable, testable sans activity).
 *
 * The app bar (back arrow + favorite heart) lives in the single top bar of
 * MainActivity — one visible app bar for the whole app, no bar replacement
 * when arriving from the tabs. The favorite state comes from
 * [DetailsViewModel], shared through the same back-stack entry.
 */
@Composable
fun DetailsScreen(
    album: Album,
    modifier: Modifier = Modifier,
    /** Key binding this image to the list thumbnail in a shared-element transition. */
    imageSharedKey: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val context = LocalContext.current
        val imageRequest = remember(album) {
            ImageRequest.Builder(context)
                .data(album.url)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .add("User-Agent", "LeboncoinApp/1.0")
                        .build()
                )
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = album.title,
            modifier = Modifier
                .sharedImageElement(imageSharedKey)
                .fillMaxWidth()
                // Adaptive height: capped so the image never swallows the
                // whole (short) landscape viewport; Fit letterboxes the rest
                .heightIn(max = 350.dp),
            contentScale = ContentScale.Fit
        )

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = album.title,
                style = SparkTheme.typography.headline1,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Informations
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipTinted(
                    text = "Album #${album.albumId}"
                )
                ChipTinted(
                    text = "Track #${album.id}"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Details
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.information),
                    style = SparkTheme.typography.headline2,
                )
                DetailRow(label = stringResource(R.string.album_id_label), value = album.albumId.toString())
            }
        }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = SparkTheme.typography.caption,
            color = SparkTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = SparkTheme.typography.body2,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DetailsScreenPreview() {
    SparkTheme {
        DetailsScreen(
            album = Album(
                albumId = 1,
                id = 1,
                title = "accusamus beatae ad facilis cum similique qui sunt",
                url = "https://placehold.co/600x600/92c952/white/png",
                thumbnailUrl = "https://placehold.co/150x150/92c952/white/png",
                favorite = false
            )
        )
    }
}
