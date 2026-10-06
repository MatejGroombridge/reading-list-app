package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.domain.LibraryQueries
import dev.matejgroombridge.readinglist.data.settings.SortOrder
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.components.BookActions
import dev.matejgroombridge.readinglist.ui.components.BookCard

/**
 * What's on the go, and what's queued after it: books being read now (most
 * recently started first), then the Up Next queue. Starting an Up Next book
 * moves it from the second section to the first.
 */
@Composable
fun ReadingScreen(
    state: LibraryUiState,
    settings: Settings,
    actions: BookActions,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val reading = remember(state.library) {
        state.reading.sortedByDescending { it.startedOn ?: Long.MIN_VALUE }
    }
    val upNext = remember(state.library) { LibraryQueries.sort(state.upNext, SortOrder.Oldest, state.library) }
    val limit = settings.currentReadsLimit
    val subtitle = when {
        !state.loaded || reading.isEmpty() -> null
        limit > 0 -> "${reading.size} of $limit on the go"
        else -> "${reading.size} on the go"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PageHeader(title = "Reading", subtitle = subtitle) {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }
        if (state.loaded && reading.isEmpty() && upNext.isEmpty()) {
            EmptyState("Nothing on the go.\nStar a book on your To Read list to queue it up next.")
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
            section("now", if (upNext.isNotEmpty()) "Now Reading" else null, reading, state, settings, actions)
            section("up_next", "Up Next", upNext, state, settings, actions)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: String?,
    books: List<Book>,
    state: LibraryUiState,
    settings: Settings,
    actions: BookActions,
) {
    if (books.isEmpty()) return
    if (title != null) item(key = "header_$key") { ListSectionHeader(title, books.size) }
    items(books, key = { it.id }) { book ->
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
