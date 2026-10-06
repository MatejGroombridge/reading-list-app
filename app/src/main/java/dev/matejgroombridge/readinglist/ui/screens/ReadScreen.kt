package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.domain.LibraryQueries
import dev.matejgroombridge.readinglist.domain.ReadingStats
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.components.BookActions
import dev.matejgroombridge.readinglist.ui.components.BookCard
import dev.matejgroombridge.readinglist.ui.components.StatTile
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import java.time.LocalDate
import kotlin.math.floor

/**
 * Finished books, grouped by the year they were finished, under a small
 * stats header: the yearly goal (with whether you're on pace), headline
 * counts and what shelves the reading came from. Abandoned books live in a
 * collapsed "Didn't Finish" section at the bottom rather than vanishing.
 */
@Composable
fun ReadScreen(
    state: LibraryUiState,
    settings: Settings,
    actions: BookActions,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val sections = remember(state.library) { LibraryQueries.readSections(state.library) }
    val stats = remember(state.library) { ReadingStats.from(state.library) }
    val readCount = sections.sumOf { it.books.size }
    var showAbandoned by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PageHeader(title = "Read", subtitle = if (state.loaded && readCount > 0) countLabel(readCount) else null) {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }
        if (state.loaded && readCount == 0 && state.abandoned.isEmpty()) {
            EmptyState("Nothing finished yet.\nFinished books and your stats will show up here.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "stats") { StatsHeader(stats = stats, goal = settings.yearlyGoal) }

            sections.forEach { section ->
                item(key = "header_${section.key}") { ListSectionHeader(section.title.orEmpty(), section.books.size) }
                items(section.books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        shelf = state.library.shelf(book.shelfId),
                        showCovers = settings.showCovers,
                        todayEpochDay = state.todayEpochDay,
                        onClick = { actions.overview(book) },
                        quickActions = { actions.quickActions(book) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (state.abandoned.isNotEmpty()) {
                item(key = "abandoned_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showAbandoned = !showAbandoned },
                    ) {
                        ListSectionHeader("Didn't Finish", state.abandoned.size, modifier = Modifier.weight(1f))
                        Icon(
                            if (showAbandoned) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (showAbandoned) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (showAbandoned) {
                    items(state.abandoned, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            shelf = state.library.shelf(book.shelfId),
                            showCovers = settings.showCovers,
                            todayEpochDay = state.todayEpochDay,
                            onClick = { actions.overview(book) },
                            quickActions = { actions.quickActions(book) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(stats: ReadingStats, goal: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (goal > 0) GoalCard(stats = stats, goal = goal)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val accent = MaterialTheme.colorScheme.primary
            StatTile("In ${stats.year}", stats.readThisYear.toString(), Icons.Outlined.CalendarMonth, accent, Modifier.weight(1f))
            StatTile("All Time", stats.readAllTime.toString(), Icons.AutoMirrored.Outlined.LibraryBooks, accent, Modifier.weight(1f))
            StatTile(
                "Pages in ${stats.year}",
                if (stats.pagesThisYear >= 10_000) "${stats.pagesThisYear / 1000}k" else stats.pagesThisYear.toString(),
                Icons.Outlined.AutoStories,
                accent,
                Modifier.weight(1f),
            )
        }
        if (stats.byShelf.isNotEmpty()) ShelfBreakdown(stats)
    }
}

/**
 * Progress against the yearly goal plus a pace line: where you'd be today
 * if the goal were spread evenly over the year.
 */
@Composable
private fun GoalCard(stats: ReadingStats, goal: Int) {
    val today = LocalDate.now()
    val expected = floor(goal * today.dayOfYear.toDouble() / today.lengthOfYear()).toInt()
    val diff = stats.readThisYear - expected
    val pace = when {
        stats.readThisYear >= goal -> "Goal reached"
        diff > 0 -> "$diff ahead of pace"
        diff == 0 -> "On pace"
        else -> "${-diff} behind pace"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${stats.readThisYear} of $goal books",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(pace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (stats.readThisYear.toFloat() / goal).coerceIn(0f, 1f) },
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${stats.year} reading goal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShelfBreakdown(stats: ReadingStats) {
    val top = stats.byShelf.take(MAX_SHELF_ROWS)
    val max = top.maxOf { it.second }.coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "BY GENRE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            top.forEach { (shelf, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shelf.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.42f),
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.48f)
                            .height(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(count.toFloat() / max)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(ShelfColors.entry(shelf.colorKey).accent),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(0.1f),
                    )
                }
            }
        }
    }
}

private const val MAX_SHELF_ROWS = 6
