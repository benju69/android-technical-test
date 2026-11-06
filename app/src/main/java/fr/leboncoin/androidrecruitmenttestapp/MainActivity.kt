package fr.leboncoin.androidrecruitmenttestapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.adevinta.spark.SparkTheme
import dagger.hilt.android.AndroidEntryPoint
import fr.leboncoin.androidrecruitmenttestapp.ui.AlbumsScreen
import fr.leboncoin.androidrecruitmenttestapp.utils.AnalyticsHelper
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Resolved by Hilt from the ViewModel graph (no ViewModelProvider.Factory)
    private val viewModel: AlbumsViewModel by viewModels()

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SparkTheme {
                AlbumsScreen(
                    viewModel = viewModel,
                    onItemSelected = {
                        analyticsHelper.trackSelection(it.id.toString())
                        startActivity(Intent(this, DetailsActivity::class.java))
                    }
                )
            }
        }
    }
}
