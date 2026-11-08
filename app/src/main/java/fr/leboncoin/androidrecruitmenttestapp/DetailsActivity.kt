package fr.leboncoin.androidrecruitmenttestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.adevinta.spark.components.image.Illustration
import dagger.hilt.android.AndroidEntryPoint
import fr.leboncoin.androidrecruitmenttestapp.ui.DetailsScreen
import fr.leboncoin.androidrecruitmenttestapp.ui.theme.AppTheme
import fr.leboncoin.androidrecruitmenttestapp.utils.AnalyticsHelper
import javax.inject.Inject

/**
 * Receives only the album id (see [ALBUM_ID_KEY]); the album content comes
 * from the Room cache through [DetailsViewModel], which restores the id on
 * process death. A missing/unknown album renders the placeholder artwork.
 */
@AndroidEntryPoint
class DetailsActivity : ComponentActivity() {

    private val viewModel: DetailsViewModel by viewModels()

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        analyticsHelper.trackScreenView("Details")

        setContent {
            AppTheme {
                val album by viewModel.album.collectAsStateWithLifecycle()

                if (album != null) {
                    DetailsScreen(
                        album = album!!,
                        onBackClick = ::finish,
                        onFavoriteToggle = viewModel::toggleFavorite,
                    )
                } else {
                    Illustration(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.work_in_progress),
                        contentDescription = null,
                        contentScale = ContentScale.Inside,
                    )
                }
            }
        }
    }

    companion object {
        const val ALBUM_ID_KEY = "album_id"
    }
}
