package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.Interest
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.model.Shelf
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.util.Dates

/** Everything the overview can ask its host to do. */
class OverviewActions(
    val onEdit: () -> Unit,
    val onSetInterest: (Interest) -> Unit,
    val onStartReading: () -> Unit,
    val onFinish: () -> Unit,
    val onAbandon: () -> Unit,
    val onBackToList: () -> Unit,
    val onSetProgress: (Int) -> Unit,
    val onSetRating: (Int) -> Unit,
    /** Non-null when the dialog was opened by Pick for Me. */
    val onPickAgain: (() -> Unit)? = null,
)

/**
 * The "item overview" pattern from Habit Tracker, for a book: identity at
 * the top, then the recommendation — who suggested it and why is
 * the whole reason this app exists — then whatever's actionable for the
 * item's current status. Tap outside to dismiss; no close button.
 *
 * The host passes the live item on every recomposition, so progress and
 * rating changes made here show immediately.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookOverviewDialog(
    book: Book,
    shelf: Shelf?,
    showCovers: Boolean,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    actions: OverviewActions,
) {
    val palette = ShelfColors.entry(shelf?.colorKey)
    val accent = palette.accent

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                // Status on the left, actions on the right — on their own row
                // so a long title below gets the full width.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Pill(text = book.status.label, background = accent.copy(alpha = 0.3f))
                    Spacer(Modifier.weight(1f))
                    actions.onPickAgain?.let { pickAgain ->
                        IconButton(onClick = pickAgain) {
                            Icon(Icons.Outlined.Casino, contentDescription = "Pick again",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (book.status == ReadingStatus.WantToRead) {
                        IconButton(onClick = actions.onStartReading) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Start reading",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = actions.onFinish) {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = "Already read",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = actions.onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    BookTile(book = book, shelf = shelf, showCovers = showCovers, width = 66.dp, height = 92.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            // Book titles run long; three lines is the
                            // compromise between showing it all and keeping
                            // the header compact. The editor has the rest.
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (book.author.isNotBlank()) {
                            Text(
                                text = book.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (book.kind != ItemKind.Book) Pill(book.kind.label, accent.copy(alpha = 0.18f))
                            shelf?.let { Pill(it.name, accent.copy(alpha = 0.18f)) }
                        }
                    }
                }

                val facts = listOfNotNull(
                    book.publishedYear.takeIf { it > 0 }?.toString(),
                    book.pageCount.takeIf { it > 0 }?.let { "$it pages" },
                    book.publicRating.takeIf { book.publicRatingCount > 0 && it > 0 }?.let {
                        "★ %.1f (%s ratings)".format(it, compactCount(book.publicRatingCount))
                    },
                ).joinToString(" · ")
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (book.hasRecommendation) {
                    Spacer(Modifier.height(14.dp))
                    RecommendationQuote(book = book, accent = accent)
                }

                Spacer(Modifier.height(16.dp))
                when (book.status) {
                    ReadingStatus.WantToRead -> InterestPicker(book.interest, actions)
                    ReadingStatus.Reading -> ReadingSection(book, todayEpochDay, accent, actions)
                    ReadingStatus.Read -> ReadSection(book, accent, actions)
                    ReadingStatus.Abandoned -> AbandonedSection(book, actions)
                }

                if (book.addedAt > 0) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Added ${Dates.full(Dates.epochDayFromMillis(book.addedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationQuote(book: Book, accent: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (book.reason.isNotBlank()) {
                Icon(Icons.Outlined.FormatQuote, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Text(
                    text = book.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (book.recommendedBy.isNotBlank()) {
                Spacer(Modifier.height(if (book.reason.isNotBlank()) 6.dp else 0.dp))
                Text(
                    text = if (book.reason.isNotBlank()) "— ${book.recommendedBy}" else "Recommended by ${book.recommendedBy}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How keen you are on it — "Now" puts it on the Reading tab's Up Next. */
@Composable
private fun InterestPicker(current: Interest, actions: OverviewActions) {
    OptionGrid(
        options = Interest.entries,
        selected = current,
        label = { it.label },
        onSelect = actions.onSetInterest,
        columns = 3,
    )
}

private fun compactCount(n: Int): String = when {
    n >= 1000 -> "%.1fk".format(n / 1000.0).replace(".0k", "k")
    else -> n.toString()
}

@Composable
private fun ReadingSection(book: Book, todayEpochDay: Long, accent: Color, actions: OverviewActions) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val progress = book.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
                Spacer(Modifier.height(12.dp))
                // ±10 pages: the "minimum 10 pages a day" habit from the
                // Notion page, as one tap.
                CompactStepper(
                    value = book.currentPage,
                    onChange = actions.onSetProgress,
                    label = { "p. $it of ${book.pageCount}" },
                    min = 0,
                    max = book.pageCount,
                    step = 10,
                )
            }
            book.startedOn?.let {
                if (progress != null) Spacer(Modifier.height(8.dp))
                Text(
                    text = "Started ${Dates.relativeDays(it, todayEpochDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = actions.onAbandon, modifier = Modifier.weight(1f)) { Text("Didn't Finish") }
        Button(onClick = actions.onFinish, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Finished")
        }
    }
}

@Composable
private fun ReadSection(book: Book, accent: Color, actions: OverviewActions) {
    val days = if (book.startedOn != null && book.finishedOn != null) {
        (book.finishedOn - book.startedOn + 1).coerceAtLeast(1)
    } else null
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            label = "Finished",
            value = book.finishedOn?.let(Dates::monthYear) ?: "—",
            icon = Icons.Outlined.EventAvailable,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Days",
            value = days?.toString() ?: "—",
            icon = Icons.Outlined.Timer,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Your rating", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RatingStars(rating = book.rating, onRate = actions.onSetRating, size = 28.dp)
    }
    if (book.review.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        LabelledText(label = "Takeaways", text = book.review)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = actions.onStartReading, modifier = Modifier.fillMaxWidth()) { Text("Read Again") }
}

@Composable
private fun AbandonedSection(book: Book, actions: OverviewActions) {
    book.finishedOn?.let {
        Text(
            text = "Stopped ${Dates.full(it)}" + if (book.progress != null) " on page ${book.currentPage}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = actions.onBackToList, modifier = Modifier.weight(1f)) { Text("Back to To Read") }
        Button(onClick = actions.onStartReading, modifier = Modifier.weight(1f)) { Text("Resume") }
    }
}

@Composable
private fun Pill(text: String, background: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LabelledText(label: String, text: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}


@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
