package fr.leboncoin.androidrecruitmenttestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adevinta.spark.ExperimentalSparkApi
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.appbar.NavigationBar
import com.adevinta.spark.components.appbar.NavigationBarItem
import com.adevinta.spark.components.appbar.TopAppBar
import com.adevinta.spark.components.image.Illustration
import com.adevinta.spark.components.icons.Icon
import com.adevinta.spark.components.icons.IconButton
import com.adevinta.spark.components.scaffold.Scaffold
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.icons.HeartOutline
import com.adevinta.spark.icons.ImageOutline
import com.adevinta.spark.icons.LeboncoinIcons
import dagger.hilt.android.AndroidEntryPoint
import fr.leboncoin.androidrecruitmenttestapp.feature.albums.AlbumsScreen
import fr.leboncoin.androidrecruitmenttestapp.feature.albums.AlbumsViewModel
import fr.leboncoin.androidrecruitmenttestapp.feature.detail.DetailsScreen
import fr.leboncoin.androidrecruitmenttestapp.feature.detail.DetailsViewModel
import fr.leboncoin.androidrecruitmenttestapp.feature.favorites.FavoritesScreen
import fr.leboncoin.androidrecruitmenttestapp.feature.favorites.FavoritesViewModel
import fr.leboncoin.androidrecruitmenttestapp.ui.FavoriteButton
import fr.leboncoin.androidrecruitmenttestapp.ui.LocalNavAnimatedVisibilityScope
import fr.leboncoin.androidrecruitmenttestapp.ui.LocalSharedTransitionScope
import fr.leboncoin.androidrecruitmenttestapp.ui.theme.AppTheme
import fr.leboncoin.androidrecruitmenttestapp.utils.AnalyticsHelper
import javax.inject.Inject

/**
 * Single-activity host: the NavHost owns all destinations (albums list,
 * favorites list, album detail). A single top bar is always visible — its
 * title follows the destination, and on the detail screen it carries the
 * back arrow and the favorite heart (state from [DetailsViewModel], scoped
 * to the same back-stack entry as the detail content). The bottom bar is
 * shown only on the two top-level destinations.
 *
 * Transitions: cross-fade between tabs, lateral slide for the detail, with
 * the thumbnail -> image shared-element (keyed per source tab).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                MainApp(analyticsHelper)
            }
        }
    }
}

@OptIn(ExperimentalSparkApi::class)
@Composable
private fun MainApp(
    analyticsHelper: AnalyticsHelper,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Screen analytics follow the destinations (replaces the per-Activity
    // trackScreenView calls of the two-Activity setup)
    LaunchedEffect(currentRoute) {
        analyticsHelper.trackScreenView(
            when (currentRoute) {
                Routes.FAVORITES -> "Favorites"
                Routes.ALBUM_DETAIL -> "Details"
                else -> "Albums"
            }
        )
    }

    val isTopLevel = currentRoute in Routes.topLevel

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MainTopBar(
                currentRoute = currentRoute,
                backStackEntry = backStackEntry,
                onBackClick = navController::navigateUp,
            )
        },
        bottomBar = {
            if (isTopLevel) {
                MainNavigationBar(
                    selectedRoute = currentRoute,
                    onTopLevelSelect = { route -> selectTopLevel(navController, route) },
                )
            }
        },
    ) { innerPadding ->
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.ALBUMS,
                ) {
                    composable(
                        route = Routes.ALBUMS,
                        enterTransition = { fadeIn(tween(220)) },
                        exitTransition = { fadeOut(tween(220)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { fadeOut(tween(220)) },
                    ) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val viewModel: AlbumsViewModel = hiltViewModel()
                            AlbumsScreen(
                                viewModel = viewModel,
                                onItemSelected = { album ->
                                    analyticsHelper.trackSelection(album.id.toString())
                                    navController.navigate(
                                        Routes.albumDetail(album.id, source = Routes.ALBUMS)
                                    )
                                },
                                sharedKeyPrefix = Routes.ALBUMS,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }

                    composable(
                        route = Routes.FAVORITES,
                        enterTransition = { fadeIn(tween(220)) },
                        exitTransition = { fadeOut(tween(220)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { fadeOut(tween(220)) },
                    ) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val viewModel: FavoritesViewModel = hiltViewModel()
                            FavoritesScreen(
                                viewModel = viewModel,
                                onItemSelected = { album ->
                                    analyticsHelper.trackSelection(album.id.toString())
                                    navController.navigate(
                                        Routes.albumDetail(album.id, source = Routes.FAVORITES)
                                    )
                                },
                                sharedKeyPrefix = Routes.FAVORITES,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }

                    composable(
                        route = Routes.ALBUM_DETAIL,
                        arguments = listOf(
                            navArgument(Routes.ALBUM_ID_ARG) { type = NavType.IntType },
                            navArgument(Routes.SRC_ARG) {
                                type = NavType.StringType
                                defaultValue = Routes.ALBUMS
                            },
                        ),
                        enterTransition = {
                            slideInHorizontally(animationSpec = tween(300)) { it }
                        },
                        exitTransition = { fadeOut(tween(220)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = {
                            slideOutHorizontally(animationSpec = tween(300)) { it }
                        },
                    ) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            val viewModel: DetailsViewModel = hiltViewModel()
                            val album by viewModel.album.collectAsStateWithLifecycle()
                            val sharedKey = "${
                                entry.arguments?.getString(Routes.SRC_ARG) ?: Routes.ALBUMS
                            }-${viewModel.albumId}"

                            if (album != null) {
                                DetailsScreen(
                                    album = album!!,
                                    imageSharedKey = sharedKey,
                                    modifier = Modifier.padding(innerPadding),
                                )
                            } else {
                                // Unknown id (fresh install without network...):
                                // no crash, just the placeholder artwork
                                Illustration(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                    painter = painterResource(id = R.drawable.work_in_progress),
                                    contentDescription = null,
                                    contentScale = ContentScale.Inside,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The always-visible top bar: tab title on the two tab destinations,
 * "Album details" with a back arrow and the favorite heart on the detail.
 * The heart reads [DetailsViewModel] from the detail's back-stack entry —
 * the same instance the detail content observes, so the two stay in sync.
 */
@OptIn(ExperimentalSparkApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    currentRoute: String?,
    backStackEntry: NavBackStackEntry?,
    onBackClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(
                    when (currentRoute) {
                        Routes.FAVORITES -> R.string.tab_favorites
                        Routes.ALBUM_DETAIL -> R.string.album_details
                        else -> R.string.tab_albums
                    }
                )
            )
        },
        navigationIcon = {
            if (currentRoute == Routes.ALBUM_DETAIL) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                        tint = SparkTheme.colors.main
                    )
                }
            }
        },
        actions = {
            if (currentRoute == Routes.ALBUM_DETAIL && backStackEntry != null) {
                DetailsFavoriteAction(backStackEntry)
            }
        },
    )
}

@Composable
private fun DetailsFavoriteAction(backStackEntry: NavBackStackEntry) {
    val viewModel: DetailsViewModel =
        hiltViewModel(viewModelStoreOwner = backStackEntry)
    val album by viewModel.album.collectAsStateWithLifecycle()

    if (album != null) {
        FavoriteButton(
            favorite = album!!.favorite,
            onToggle = viewModel::toggleFavorite,
        )
    }
}

/**
 * Bottom bar: Albums and Favorites. Clicking already-selected destination is
 * a no-op (launchSingleTop), and switching back restores the saved tab state.
 */
@OptIn(ExperimentalSparkApi::class)
@Composable
private fun MainNavigationBar(
    selectedRoute: String?,
    onTopLevelSelect: (String) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedRoute == Routes.ALBUMS,
            onClick = { onTopLevelSelect(Routes.ALBUMS) },
            icon = LeboncoinIcons.ImageOutline,
            label = { Text(stringResource(R.string.tab_albums)) },
        )
        NavigationBarItem(
            selected = selectedRoute == Routes.FAVORITES,
            onClick = { onTopLevelSelect(Routes.FAVORITES) },
            icon = LeboncoinIcons.HeartOutline,
            label = { Text(stringResource(R.string.tab_favorites)) },
        )
    }
}

/**
 * Standard "switch tab" behaviour with a twist: the start destination is
 * popped INCLUSIVELY (saved), so the back stack carries the current tab
 * only — pressing Back on the Favorites tab exits the app instead of
 * bouncing to the Albums tab.
 */
private fun selectTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
            inclusive = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
