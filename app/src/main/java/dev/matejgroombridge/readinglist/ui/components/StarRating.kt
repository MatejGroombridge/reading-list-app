package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * Five-star rating.
 *
 * Read-only when [onRate] is null (shelf rows), interactive otherwise (the
 * detail sheet). Tapping the star that's already the current rating clears
 * it — otherwise a misrated book could never be reset to unrated, since
 * there's no zeroth star to tap.
 */
@Composable
fun StarRating(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    onRate: ((Int) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (onRate == null) 1.dp else 4.dp),
    ) {
        (1..5).forEach { star ->
            val filled = star <= rating
            val starModifier = if (onRate == null) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button) {
                    haptics.light()
                    onRate(if (rating == star) 0 else star)
                }
            }
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (onRate == null) null else "Rate $star star${if (star == 1) "" else "s"}",
                tint = if (filled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = starModifier.size(starSize),
            )
        }
    }
}
