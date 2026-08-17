package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.ui.theme.BookColors
import dev.matejgroombridge.readinglist.ui.theme.GenreIcons

/**
 * A book's cover art at [width], in the standard 2:3 book proportion.
 *
 * Open Library has no cover for a meaningful share of works, and a grid full
 * of grey boxes looks broken. So the fallback isn't a generic placeholder:
 * it's a coloured tile carrying the book's genre glyph, which keeps the shelf
 * scannable by colour even where art is missing and makes the genre grouping
 * visible at a glance.
 *
 * The same tile renders while the image loads, so nothing pops or reflows
 * when it arrives.
 */
@Composable
fun BookCover(
    book: Book,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val height = width * COVER_ASPECT
    val shape = RoundedCornerShape(width.value.coerceIn(6f, 12f).dp)
    val accent = BookColors.entry(book.genre.colorKey).accent
    val url = book.coverUrl(if (width > 96.dp) 'L' else 'M')

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(accent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            GenrePlaceholder(book, width, accent)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "Cover of ${book.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { GenrePlaceholder(book, width, accent) },
                error = { GenrePlaceholder(book, width, accent) },
            )
        }
    }
}

@Composable
private fun GenrePlaceholder(book: Book, width: Dp, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = GenreIcons.icon(book.genreKey),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(4.dp)
                .size((width.value * 0.42f).coerceIn(16f, 48f).dp),
        )
    }
}

/** Standard book cover proportion — height is 1.5x width. */
private const val COVER_ASPECT = 1.5f
