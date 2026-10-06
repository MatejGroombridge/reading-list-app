package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.model.Shelf
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.theme.containerColor
import dev.matejgroombridge.readinglist.ui.theme.contentColor
import dev.matejgroombridge.readinglist.ui.util.Dates
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/** One entry in a card's long-press menu. */
data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A list row for one item, tinted with its shelf's pastel. Tap opens the
 * overview; long-press opens a menu of the status moves that make sense for
 * where the item is now (the per-cell long-press pattern from agent.md
 * §10.7.14) — so starting or finishing a book never needs the overview.
 *
 * What sits under the title depends on status: the recommendation on To
 * Read (that's what you decide on), progress on Reading, rating and date on
 * Read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    shelf: Shelf?,
    showCovers: Boolean,
    todayEpochDay: Long,
    onClick: () -> Unit,
    quickActions: () -> List<QuickAction>,
    modifier: Modifier = Modifier,
) {
    val palette = ShelfColors.entry(shelf?.colorKey)
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }

    // Pulled a little toward the page background so a long list of pastel
    // cards stays calm; text uses the full-strength content colour.
    val container = blend(palette.containerColor(), MaterialTheme.colorScheme.background, 0.2f)
    val content = palette.contentColor()
    val muted = book.status == ReadingStatus.Abandoned

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = container,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClick = {
                        haptics.light()
                        onClick()
                    },
                    onLongClick = {
                        haptics.longPress()
                        menuOpen = true
                    },
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookTile(book = book, shelf = shelf, showCovers = showCovers, muted = muted)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = content.copy(alpha = if (muted) 0.7f else 1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val byline = listOfNotNull(
                        book.author.takeIf { it.isNotBlank() },
                        book.kind.takeIf { it != ItemKind.Book }?.label,
                    ).joinToString(" · ")
                    if (byline.isNotEmpty()) {
                        Text(
                            text = byline,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when (book.status) {
                        ReadingStatus.WantToRead -> ToReadMeta(book, content)
                        ReadingStatus.Reading -> ReadingMeta(book, todayEpochDay, content, palette.accent)
                        ReadingStatus.Read -> ReadMeta(book, content)
                        ReadingStatus.Abandoned -> book.finishedOn?.let {
                            MetaText("Stopped ${Dates.monthYear(it)}", content)
                        }
                    }
                }
                if (book.upNext) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Up next",
                        tint = content.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.Top),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            quickActions().forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            action.label,
                            color = if (action.destructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = if (action.destructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        haptics.light()
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun ToReadMeta(book: Book, content: Color) {
    if (book.reason.isNotBlank()) {
        Text(
            text = "“${book.reason}”",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = content.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (book.recommendedBy.isNotBlank() || book.toAcquire) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (book.recommendedBy.isNotBlank()) {
                IconText(Icons.Outlined.Person, book.recommendedBy, content, Modifier.weight(1f, fill = false))
            }
            if (book.toAcquire) IconText(Icons.Outlined.Download, "To get", content)
        }
    }
}

@Composable
private fun ReadingMeta(book: Book, todayEpochDay: Long, content: Color, accent: Color) {
    val progress = book.progress
    if (progress != null) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = content.copy(alpha = 0.75f),
            trackColor = accent.copy(alpha = 0.45f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        Spacer(Modifier.height(4.dp))
        MetaText("p. ${book.currentPage} of ${book.pageCount} · ${(progress * 100).toInt()}%", content)
    } else {
        book.startedOn?.let { MetaText("Started ${Dates.relativeDays(it, todayEpochDay)}", content) }
    }
}

@Composable
private fun ReadMeta(book: Book, content: Color) {
    if (book.rating == 0 && book.finishedOn == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        if (book.rating > 0) RatingStars(rating = book.rating, size = 14.dp, tint = content.copy(alpha = 0.8f))
        book.finishedOn?.let {
            Text(
                text = Dates.monthYear(it),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MetaText(text: String, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = content.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun IconText(icon: ImageVector, text: String, content: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, tint = content.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = content.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
