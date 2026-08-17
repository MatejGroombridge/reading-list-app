package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.components.BookRow
import dev.matejgroombridge.readinglist.ui.components.EmptyState
import dev.matejgroombridge.readinglist.ui.theme.BookColors
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * The priority queue — the handful of books the user has decided to read
 * next, in the order they intend to read them.
 *
 * Reordering is up/down/to-top buttons rather than drag-and-drop: on a phone
 * a precise drag inside a scrolling list is fiddly, and the queue is short by
 * design. Buttons are unambiguous, work one-handed, and can't be fumbled into
 * dropping a book in the wrong slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val queue = state.upNext

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Up Next") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
        )

        if (queue.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                title = "Nothing queued yet",
                message = "Open a book on your shelf and add it to Up Next " +
                    "to line up what you're reading after this one.",
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
            itemsIndexed(queue, key = { _, book -> book.id }) { index, book ->
                BookRow(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                    showGenre = true,
                    leading = { QueuePosition(index = index, colorKey = book.genre.colorKey) },
                    trailing = {
                        Column {
                            IconButton(
                                onClick = {
                                    haptics.light()
                                    viewModel.movePriority(book.id, -1)
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = "Move up",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.light()
                                    viewModel.movePriority(book.id, 1)
                                },
                                enabled = index < queue.lastIndex,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDownward,
                                    contentDescription = "Move down",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.completion()
                                    viewModel.movePriorityToTop(book.id)
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerticalAlignTop,
                                    contentDescription = "Move to top",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** The "1", "2", "3" badge showing a book's slot in the queue. */
@Composable
private fun QueuePosition(index: Int, colorKey: String) {
    val accent = BookColors.entry(colorKey).accent
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = if (index == 0) 0.95f else 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
