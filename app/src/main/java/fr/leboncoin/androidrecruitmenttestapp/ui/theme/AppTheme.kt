package fr.leboncoin.androidrecruitmenttestapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.tokens.darkSparkColors
import com.adevinta.spark.tokens.lightSparkColors

/**
 * Dark-mode wrapper around the Spark design system.
 *
 * Spark deliberately does NOT auto-switch its colors (`SparkTheme` falls back
 * to light colors, see its docs). Without this wrapper, in system dark mode
 * `enableEdgeToEdge()` draws light system bar icons over Spark's light
 * background — white on white.
 *
 * The same `darkTheme` boolean drives both the Spark color set and the
 * appearance of status/navigation bar icons, so they never disagree.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkSparkColors() else lightSparkColors()

    val view = LocalView.current
    DisposableEffect(view, darkTheme) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            // Edge-to-edge already made the bars transparent: only the icon
            // appearance (light vs dark icons) needs to follow the theme.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose { }
    }

    SparkTheme(colors = colors, content = content)
}
