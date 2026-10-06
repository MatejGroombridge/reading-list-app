package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Shelf
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.theme.ShelfIcons

/** Icon for authors; null for books (they use their genre's icon). */
fun ItemKind.icon(): ImageVector? = when (this) {
    ItemKind.Book -> null
    ItemKind.Author -> Icons.Outlined.Person
}

/**
 * Book-shaped (3:4) leading tile used on every card and in the overview.
 * Shows the cover when there is one and covers are enabled; otherwise the
 * genre's icon on its accent — or, for authors, a person icon, so they
 * read differently from books at a glance.
 * A failed cover load falls back to the icon tile.
 */
@Composable
fun BookTile(
    book: Book,
    shelf: Shelf?,
    showCovers: Boolean,
    width: Dp = 48.dp,
    height: Dp = 64.dp,
    muted: Boolean = false,
) {
    val color = ShelfColors.entry(shelf?.colorKey)
    val shape = RoundedCornerShape(width * 0.22f)
    var coverFailed by remember(book.coverUrl) { mutableStateOf(false) }
    val showCover = showCovers && book.coverUrl.isNotBlank() && !coverFailed

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(if (muted) color.accent.copy(alpha = 0.55f) else color.accent),
        contentAlignment = Alignment.Center,
    ) {
        if (showCover) {
            AsyncImage(
                model = coverModel(book.coverUrl),
                contentDescription = "Cover of ${book.title}",
                contentScale = ContentScale.Crop,
                onError = { coverFailed = true },
                modifier = Modifier.size(width, height),
            )
        } else {
            Icon(
                imageVector = book.kind.icon() ?: ShelfIcons.entry(shelf?.iconKey).icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = if (muted) 0.55f else 0.8f),
                modifier = Modifier.size(width * 0.5f),
            )
        }
    }
}

/**
 * Five stars. Read-only when [onRate] is null. Tapping the current rating
 * again clears it, so "unrated" is always reachable.
 */
@Composable
fun RatingStars(
    rating: Int,
    onRate: ((Int) -> Unit)? = null,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (onRate != null) "$i star${if (i == 1) "" else "s"}" else null,
                tint = if (filled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .then(
                        if (onRate != null) Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onRate(if (i == rating) 0 else i) }
                        else Modifier,
                    )
                    .size(size),
            )
        }
    }
}

/**
 * Open Library answers a missing cover with a blank placeholder image rather
 * than an error; `default=false` makes it a 404 so the genre-icon fallback
 * shows instead of an empty tile.
 */
fun coverModel(url: String): String =
    if ("covers.openlibrary.org" in url && "default=" !in url) url + (if ("?" in url) "&" else "?") + "default=false" else url
