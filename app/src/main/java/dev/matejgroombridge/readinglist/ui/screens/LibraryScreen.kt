package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.data.model.RecSource
import dev.matejgroombridge.readinglist.ui.GenreSection
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.ShelfFilter
import dev.matejgroombridge.readinglist.ui.components.BookRow
import dev.matejgroombridge.readinglist.ui.components.CardCorner
import dev.matejgroombridge.readinglist.ui.components.EmptyState
import dev.matejgroombridge.readinglist.ui.components.SectionCaption
import dev.matejgroombridge.readinglist.ui.theme.BookColors
import dev.matejgroombridge.readinglist.ui.theme.GenreIcons
import dev.matejgroombridge.readinglist.ui.theme.RecSourceIcons
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * The shelf: everything the user wants to read, filed into collapsible genre
 * sections, with anything in progress pinned above it.
 *
 * This is the screen that answers the problem the app exists for — a single
 * unsorted document of book titles — so it leads with structure: section
 * headings, counts, and colour, rather than one long undifferentiated list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Which sections the user has collapsed. Saveable so a rotation or a trip
    // through the app switcher doesn't silently re-expand everything.
    val collapsed = rememberSaveable(saver = collapsedSaver) { mutableStateOf(emptySet()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search books")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
        )

        // An empty shelf and an over-filtered shelf look identical without
        // this split, and the fix for each is completely different.
        if (state.wantToReadCount == 0 && state.reading.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                title = "Your shelf is empty",
                message = "Search for a book and tap Want to Read, or paste a list " +
                    "you already have and import it all at once.",
                action = {
                    Button(onClick = onOpenImport) { Text("Import a List") }
                },
            )
            return@Column
        }

        ShelfFilterBar(
            filter = state.filter,
            availableSources = state.availableSources,
            onQueryChange = viewModel::setFilterQuery,
            onSourceClick = viewModel::setSourceFilter,
        )

        if (state.sections.isEmpty() && state.reading.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Search,
                title = "No matches",
                message = "Nothing on your shelf matches that filter.",
                action = {
                    TextButton(onClick = viewModel::clearFilter) { Text("Clear filter") }
                },
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
            if (state.reading.isNotEmpty()) {
                item(key = "reading-caption") {
                    SectionCaption("Reading Now", modifier = Modifier.padding(horizontal = 0.dp))
                }
                items(state.reading, key = { "reading-${it.id}" }) { book ->
                    BookRow(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        showGenre = true,
                    )
                }
                item(key = "reading-gap") { Spacer(Modifier.size(8.dp)) }
            }

            item(key = "shelf-caption") {
                SectionCaption(
                    text = if (state.filter.isActive) {
                        "Want to Read · ${state.filteredCount} of ${state.wantToReadCount}"
                    } else {
                        "Want to Read · ${state.wantToReadCount}"
                    },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }

            state.sections.forEach { section ->
                item(key = "section-${section.genre.key}") {
                    GenreHeader(
                        section = section,
                        collapsed = section.genre.key in collapsed.value,
                        onToggle = {
                            collapsed.value = collapsed.value.toMutableSet().apply {
                                if (!add(section.genre.key)) remove(section.genre.key)
                            }
                        },
                    )
                }
                if (section.genre.key !in collapsed.value) {
                    items(section.books, key = { "${section.genre.key}-${it.id}" }) { book ->
                        BookRow(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            showGenre = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter row: a compact text field over title / author / recommendation note,
 * plus one chip per recommendation source actually present on the shelf.
 *
 * The source chips are the feature that makes "where did I hear about this"
 * more than a label — tapping YouTube answers "what did I add off YouTube
 * that I still haven't read?".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfFilterBar(
    filter: ShelfFilter,
    availableSources: List<RecSource>,
    onQueryChange: (String) -> Unit,
    onSourceClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Filter your shelf") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear filter")
                    }
                }
            },
            singleLine = true,
        )

        // Only worth showing once there's more than one source to tell apart.
        if (availableSources.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableSources.forEach { source ->
                    FilterChip(
                        selected = filter.sourceKind == source.key,
                        onClick = { onSourceClick(source.key) },
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
        }
    }
}

/**
 * A collapsible genre heading: glyph, label, and count.
 *
 * Sections collapse because a long list is the exact problem being solved —
 * being able to fold away "Fantasy: 23 books" to see the rest of the shelf is
 * what makes a hundred-book list navigable on a phone.
 */
@Composable
private fun GenreHeader(
    section: GenreSection,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = rememberHaptics()
    val accent = BookColors.entry(section.genre.colorKey).accent
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "chevron",
    )

    Surface(
        shape = RoundedCornerShape(CardCorner),
        color = accent.copy(alpha = 0.18f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .clickable {
                haptics.light()
                onToggle()
            }
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GenreIcons.icon(section.genre.key),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = section.genre.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = section.books.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }
    }
}

/**
 * Persists collapsed section keys across configuration changes. Sets aren't
 * saveable by default, so this round-trips through a list.
 */
private val collapsedSaver = androidx.compose.runtime.saveable.Saver<
    androidx.compose.runtime.MutableState<Set<String>>, List<String>,
    >(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) },
)
