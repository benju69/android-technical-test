package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.icons.Icon
import com.adevinta.spark.components.icons.IconButton
import com.adevinta.spark.icons.LeboncoinAnimatedIcons
import com.adevinta.spark.icons.likeHeart
import fr.leboncoin.androidrecruitmenttestapp.R

/** Favorite toggle shared by the list item and the detail screen. */
@Composable
fun FavoriteButton(
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            // Animated heart: the outline and fill shapes are one path that
            // morphs as `favorite` flips (atEnd drives the target state), so
            // the toggle reads as a real animation instead of an icon swap
            sparkIcon = LeboncoinAnimatedIcons.likeHeart(),
            contentDescription = stringResource(
                if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites
            ),
            tint = if (favorite) SparkTheme.colors.accent else SparkTheme.colors.neutral,
            atEnd = favorite,
        )
    }
}
