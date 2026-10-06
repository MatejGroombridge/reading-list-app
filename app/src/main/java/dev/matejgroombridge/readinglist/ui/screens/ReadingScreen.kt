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
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.components.BookActions
import dev.matejgroombridge.readinglist.ui.components.BookCard

/**
 * What's on the go — the Notion page's "Current Reads". Most recently
 * started first, since that's usually the one being picked back up.
 */
@Composable
fun ReadingScreen(
    state: LibraryUiState,
    settings: Settings,
    actions: BookActions,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val books = remember(state.library) {
        state.reading.sortedByDescending { it.startedOn ?: Long.MIN_VALUE }
    }
    val limit = settings.currentReadsLimit
    val subtitle = when {
        !state.loaded || books.isEmpty() -> null
        limit > 0 -> "${books.size} of $limit on the go"
        else -> "${books.size} on the go"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PageHeader(title = "Reading", subtitle = subtitle) {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }
        if (state.loaded && books.isEmpty()) {
            EmptyState("Nothing on the go.\nStart something from your To Read list.")
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
    }
}
