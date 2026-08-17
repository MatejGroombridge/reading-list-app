package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.ReadStats
import dev.matejgroombridge.readinglist.ui.components.BookRow
import dev.matejgroombridge.readinglist.ui.components.CardCorner
import dev.matejgroombridge.readinglist.ui.components.EmptyState
import dev.matejgroombridge.readinglist.ui.components.SectionCaption
import java.time.LocalDate

/**
 * The finished shelf — a record of what's actually been read, with a stats
 * strip on top.
 *
 * Ordered most-recent-first so it reads as a diary of the reading year
 * rather than a static inventory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Read") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
        )

        if (state.read.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.CheckCircle,
                title = "No finished books yet",
                message = "Open a book and set its shelf to Read. " +
                    "Everything you finish collects here with your rating.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "stats") { StatsStrip(state.stats) }
            item(key = "finished-caption") { SectionCaption("Finished") }
            items(state.read, key = { it.id }) { book ->
                BookRow(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                    showGenre = true,
                    showRating = true,
                )
            }
        }
    }
}

/**
 * Three headline numbers plus the user's most-read genre.
 *
 * Pages is included alongside book count because a year of short novels and a
 * year of doorstops are very different reading years, and the count alone
 * flatters the former.
 */
@Composable
private fun StatsStrip(stats: ReadStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = stats.booksRead.toString(),
                label = "Books read",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stats.readThisYear.toString(),
                label = "In ${LocalDate.now().year}",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = if (stats.pagesRead > 0) formatPages(stats.pagesRead) else "—",
                label = "Pages",
                modifier = Modifier.weight(1f),
            )
        }
        if (stats.topGenre != null || stats.averageRating > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.topGenre?.let { genre ->
                    StatTile(
                        value = genre.label,
                        label = "Most read",
                        modifier = Modifier.weight(1f),
                        valueStyle = ValueStyle.Compact,
                    )
                }
                if (stats.averageRating > 0) {
                    StatTile(
                        value = String.format(java.util.Locale.US, "%.1f", stats.averageRating),
                        label = "Avg rating",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private enum class ValueStyle { Large, Compact }

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueStyle: ValueStyle = ValueStyle.Large,
) {
    Surface(
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = when (valueStyle) {
                    ValueStyle.Large -> MaterialTheme.typography.headlineMedium
                    ValueStyle.Compact -> MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 12,430 → "12.4k", so the tile never has to shrink its type to fit. */
private fun formatPages(pages: Int): String =
    if (pages < 10_000) pages.toString()
    else String.format(java.util.Locale.US, "%.1fk", pages / 1000.0)
