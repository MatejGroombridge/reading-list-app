package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.RecSources
import dev.matejgroombridge.readinglist.ui.theme.BookColors
import dev.matejgroombridge.readinglist.ui.theme.RecSourceIcons
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * One book in a list: cover, title, author, and a metadata line.
 *
 * A 4dp bar in the genre's accent colour runs down the left edge — a book
 * spine. It carries the genre signal on every row without the noise of fully
 * tinted cards, so the shelf stays scannable by colour even when a section
 * heading has scrolled off.
 *
 * [leading] and [trailing] let each screen bolt on its own controls (queue
 * position, reorder arrows, an add button) without forking the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookRow(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showGenre: Boolean = false,
    showRating: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val accent = BookColors.entry(book.genre.colorKey).accent

    Surface(
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptics.light()
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptics.longPress()
                        it()
                    }
                },
            ),
    ) {
        // Intrinsic min height lets the spine stretch to whatever the content
        // ends up being, however many metadata pills wrap onto the row.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }

                BookCover(book = book, width = 44.dp)
                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.authorLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BookMetaLine(
                        book = book,
                        showGenre = showGenre,
                        showRating = showRating,
                    )
                }

                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        }
    }
}

/**
 * The line of pills under the author: rating, genre, and where the
 * recommendation came from.
 *
 * The recommendation badge is deliberately the most prominent of the three —
 * remembering *why* a book is on the list is the thing a plain document
 * can't do, so it gets the genre accent colour while the rest stay neutral.
 */
@Composable
private fun BookMetaLine(
    book: Book,
    showGenre: Boolean,
    showRating: Boolean,
) {
    val accent = BookColors.entry(book.genre.colorKey).accent
    val hasSource = book.recSource.isNotBlank() || book.recSourceKind != RecSources.UNKNOWN_KEY
    if (!showGenre && !showRating && !hasSource) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        if (showRating && book.rating > 0) {
            StarRating(rating = book.rating, starSize = 13.dp)
        }
        if (showGenre) {
            InfoPill(text = book.genre.label, accent = accent.copy(alpha = 0.28f))
        }
        if (hasSource) {
            RecSourceBadge(book = book, accent = accent)
        }
    }
}

/**
 * "Where did I hear about this?" badge. Falls back to the source-kind label
 * when there's no free-text note, so tagging a book as "YouTube" without
 * typing anything still shows something useful.
 */
@Composable
private fun RecSourceBadge(book: Book, accent: androidx.compose.ui.graphics.Color) {
    val label = book.recSource.ifBlank { RecSources.entry(book.recSourceKind).label }
    Surface(
        shape = RoundedCornerShape(ChipCorner),
        color = accent.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = RecSourceIcons.icon(book.recSourceKind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
