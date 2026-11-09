package fr.leboncoin.androidrecruitmenttestapp.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.icons.Icon
import com.adevinta.spark.components.icons.IconButton
import com.adevinta.spark.icons.HeartFill
import com.adevinta.spark.icons.HeartOutline
import com.adevinta.spark.icons.LeboncoinIcons
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
            sparkIcon = if (favorite) LeboncoinIcons.HeartFill else LeboncoinIcons.HeartOutline,
            contentDescription = stringResource(
                if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites
            ),
            tint = if (favorite) SparkTheme.colors.accent else SparkTheme.colors.neutral,
        )
    }
}
