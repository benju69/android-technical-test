package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Composition locals bridging the single-activity navigation with reusable
 * screens that host shared elements (the album thumbnail → detail image).
 *
 * [SharedTransitionLayout] in MainActivity provides the shared scope, and
 * each NavHost destination its [AnimatedVisibilityScope]. Screens read them
 * through [Modifier.sharedImageElement] and fall back to plain rendering
 * (no scope or no key) so Previews and tests need no navigation plumbing.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks the receiver to participate in the shared-element transition, keyed
 * by [key] (e.g. "albums-42"). Returns the receiver undouched when no scope
 * is provided (Preview/test) or the key is null.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedImageElement(key: String?): Modifier {
    if (key == null) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this

    val sharedContentState = with(sharedTransitionScope) {
        rememberSharedContentState(key)
    }
    return with(sharedTransitionScope) {
        sharedElement(sharedContentState, animatedVisibilityScope)
    }
}
