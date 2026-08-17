package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.Genres
import dev.matejgroombridge.readinglist.data.model.RecSources
import dev.matejgroombridge.readinglist.data.model.ShelfStatus
import dev.matejgroombridge.readinglist.ui.theme.BookColors
import dev.matejgroombridge.readinglist.ui.theme.RecSourceIcons
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import kotlinx.coroutines.delay

/** Everything the sheet can change about a book, funnelled through the caller. */
data class BookDetailActions(
    val onSetStatus: (ShelfStatus) -> Unit,
    val onSetRating: (Int) -> Unit,
    val onSetRecSource: (String, String) -> Unit,
    val onSetNotes: (String) -> Unit,
    val onSetGenre: (String?) -> Unit,
    val onSetPrioritised: (Boolean) -> Unit,
    val onRemove: () -> Unit,
)

/**
 * The book detail sheet — shelf status, rating, queue slot, recommendation
 * source, notes, and genre, all editable in one place.
 *
 * [book] must be re-read from live state by the caller on each recomposition
 * rather than captured once when the sheet opened, so an edit made here is
 * reflected immediately by the controls above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailSheet(
    book: Book,
    sheetState: SheetState,
    actions: BookDetailActions,
    onDismiss: () -> Unit,
    onNeedDescription: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }

    // Fetch the blurb the first time this book is opened; cached afterwards.
    LaunchedEffect(book.id) { onNeedDescription() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HeaderBlock(book)
            StatusBlock(book, actions)
            if (book.status == ShelfStatus.Read) RatingBlock(book, actions)
            if (book.status == ShelfStatus.WantToRead) QueueBlock(book, actions)
            RecSourceBlock(book, actions)
            NotesBlock(book, actions)
            GenreBlock(book, actions)
            if (book.description.isNotBlank()) DescriptionBlock(book)

            TextButton(
                onClick = { confirmRemove = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Remove from List", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${book.title}?") },
            text = {
                Text(
                    "This clears its rating, notes, and where you heard about it. " +
                        "You can always search for it again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        actions.onRemove()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HeaderBlock(book: Book) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(book = book, width = 96.dp)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authorLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                book.firstPublishYear?.let { InfoPill(text = it.toString()) }
                book.pageCount?.let { InfoPill(text = "$it pages") }
            }
        }
    }
}

@Composable
private fun StatusBlock(book: Book, actions: BookDetailActions) {
    val haptics = rememberHaptics()
    EditorSection("Shelf") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShelfStatus.entries.forEach { status ->
                val selected = book.status == status
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (status == ShelfStatus.Read) haptics.completion() else haptics.light()
                        actions.onSetStatus(status)
                    },
                    label = {
                        Text(
                            text = status.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BookColors.entry(book.genre.colorKey).accent,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RatingBlock(book: Book, actions: BookDetailActions) {
    EditorSection("Your Rating") {
        StarRating(
            rating = book.rating,
            starSize = 34.dp,
            onRate = actions.onSetRating,
        )
    }
}

@Composable
private fun QueueBlock(book: Book, actions: BookDetailActions) {
    val haptics = rememberHaptics()
    EditorSection("Up Next") {
        Surface(
            shape = RoundedCornerShape(ChipCorner),
            color = if (book.isPrioritised) {
                BookColors.entry(book.genre.colorKey).accent.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            onClick = {
                haptics.light()
                actions.onSetPrioritised(!book.isPrioritised)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (book.isPrioritised) "On your Up Next queue" else "Add to Up Next",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * The recommendation-source editor: a bucket to tag it with, plus free text
 * for the detail. Both write through a short debounce so a DataStore write
 * doesn't fire on every keystroke, with a flush on dispose so an edit is
 * never lost by dismissing the sheet mid-type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecSourceBlock(book: Book, actions: BookDetailActions) {
    var text by remember(book.id) { mutableStateOf(book.recSource) }
    var kind by remember(book.id) { mutableStateOf(book.recSourceKind) }

    val latestText by rememberUpdatedState(text)
    val latestKind by rememberUpdatedState(kind)
    val commit by rememberUpdatedState(actions.onSetRecSource)
    val stored by rememberUpdatedState(book.recSource to book.recSourceKind)

    LaunchedEffect(text, kind) {
        if (text == stored.first && kind == stored.second) return@LaunchedEffect
        delay(WRITE_DEBOUNCE_MS)
        commit(text, kind)
    }
    DisposableEffect(book.id) {
        onDispose {
            if (latestText != stored.first || latestKind != stored.second) {
                commit(latestText, latestKind)
            }
        }
    }

    EditorSection("Where It Came From") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecSources.catalog.forEach { source ->
                    FilterChip(
                        selected = kind == source.key,
                        onClick = { kind = source.key },
                        label = { Text(source.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = RecSourceIcons.icon(source.key),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Who or what recommended it?") },
                placeholder = { Text("e.g. Struthless video on reading more") },
                minLines = 2,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun NotesBlock(book: Book, actions: BookDetailActions) {
    var notes by remember(book.id) { mutableStateOf(book.notes) }
    val latestNotes by rememberUpdatedState(notes)
    val commit by rememberUpdatedState(actions.onSetNotes)
    val stored by rememberUpdatedState(book.notes)

    LaunchedEffect(notes) {
        if (notes == stored) return@LaunchedEffect
        delay(WRITE_DEBOUNCE_MS)
        commit(notes)
    }
    DisposableEffect(book.id) {
        onDispose { if (latestNotes != stored) commit(latestNotes) }
    }

    EditorSection("Notes") {
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Anything you want to remember") },
            minLines = 2,
            maxLines = 6,
        )
    }
}

/**
 * Genre override. The classifier's pick is shown as "Auto" and stays
 * selectable, so a user who re-files a book by mistake can hand it back
 * rather than being stuck with a manual choice forever.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreBlock(book: Book, actions: BookDetailActions) {
    EditorSection("Genre") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = book.genreOverride == null,
                onClick = { actions.onSetGenre(null) },
                label = { Text("Auto — ${Genres.entry(book.autoGenreKey).label}") },
            )
            Genres.catalog.forEach { genre ->
                FilterChip(
                    selected = book.genreOverride == genre.key,
                    onClick = { actions.onSetGenre(genre.key) },
                    label = { Text(genre.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BookColors.entry(genre.colorKey).accent,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DescriptionBlock(book: Book) {
    var expanded by remember(book.id) { mutableStateOf(false) }
    EditorSection("About") {
        Column {
            Text(
                text = book.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
            )
            // Open Library blurbs run from one line to several pages, so the
            // toggle only appears once there's plausibly something hidden.
            if (book.description.length > DESCRIPTION_CLAMP) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show less" else "Show more")
                }
            }
        }
    }
}

/** Section header plus body, matching the family's editor layout. */
@Composable
private fun EditorSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Box { content() }
    }
}

/** Human-readable shelf labels; kept next to the UI that renders them. */
private val ShelfStatus.label: String
    get() = when (this) {
        ShelfStatus.WantToRead -> "Want to Read"
        ShelfStatus.Reading -> "Reading"
        ShelfStatus.Read -> "Read"
    }

/** Debounce before a text edit reaches DataStore. */
private const val WRITE_DEBOUNCE_MS = 400L

/** Description length past which the show-more toggle appears. */
private const val DESCRIPTION_CLAMP = 280
