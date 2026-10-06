package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.components.BookTile
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.theme.containerColor
import dev.matejgroombridge.readinglist.ui.theme.contentColor
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * Archived items — "no longer interested", without losing who recommended
 * it. Restore, or delete for good behind a confirmation. This is the only
 * place anything can be deleted.
 */
@Composable
fun ArchiveScreen(
    state: LibraryUiState,
    settings: Settings,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Archive", onBack = onBack) },
    ) { padding ->
        if (state.archived.isEmpty()) {
            EmptyState("Nothing archived.\nLong-press an item and choose Archive.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.archived.asReversed(), key = { it.id }) { book ->
                val shelf = state.library.shelf(book.shelfId)
                val palette = ShelfColors.entry(shelf?.colorKey)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.containerColor().copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookTile(book = book, shelf = shelf, showCovers = settings.showCovers, width = 36.dp, height = 48.dp, muted = true)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.contentColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOf(book.author, book.status.label).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contentColor().copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            haptics.light()
                            viewModel.setArchived(book.id, false)
                        }) {
                            Icon(Icons.Outlined.Unarchive, contentDescription = "Restore", tint = palette.contentColor())
                        }
                        IconButton(onClick = { pendingDelete = book }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete for Good?") },
            text = {
                Text(
                    "“${book.title}” will be removed permanently, along with who recommended it " +
                        "and any notes. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(book.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}
