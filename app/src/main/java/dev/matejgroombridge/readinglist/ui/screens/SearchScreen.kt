package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.ui.SearchState
import dev.matejgroombridge.readinglist.ui.SearchViewModel
import dev.matejgroombridge.readinglist.ui.components.BookRow
import dev.matejgroombridge.readinglist.ui.components.EmptyState
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import java.time.LocalDate

/**
 * Book search against Open Library.
 *
 * Adding is a single tap straight from the result row — the book lands on the
 * shelf, already filed by genre, and the row flips to a "on your list" tick
 * without leaving the search. That matters because the common case is
 * arriving with three or four titles to dump in at once, not one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shelfIds by viewModel.shelfIds.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // The user came here to type; open with the field focused and the
    // keyboard up rather than making them tap again.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Add Books") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Title, author, or ISBN") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )

        when (val current = state) {
            is SearchState.Idle -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "Find your next book",
                message = "Search by title or author. Everything you add is sorted " +
                    "into a genre automatically — you can change it later.",
            )

            is SearchState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CircularProgressIndicator()
            }

            is SearchState.Empty -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "No matches",
                message = "Nothing on Open Library for \"${current.query}\". " +
                    "Try the author's name, or a shorter version of the title.",
            )

            is SearchState.Error -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "Search failed",
                    message = current.message,
                )
                TextButton(onClick = viewModel::retry) { Text("Try again") }
            }

            is SearchState.Results -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current.books, key = { it.id }) { book ->
                    val onShelf = book.id in shelfIds
                    BookRow(
                        book = book,
                        showGenre = true,
                        onClick = {
                            // Already-saved books open their detail sheet;
                            // new ones are added by the trailing button, so a
                            // stray tap on the row can't add something by
                            // accident.
                            if (onShelf) onOpenBook(book.id)
                        },
                        trailing = {
                            IconButton(
                                onClick = {
                                    if (onShelf) {
                                        haptics.light()
                                        viewModel.remove(book.id)
                                    } else {
                                        haptics.completion()
                                        viewModel.add(book, LocalDate.now().toEpochDay())
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (onShelf) {
                                        Icons.Outlined.CheckCircle
                                    } else {
                                        Icons.Outlined.Add
                                    },
                                    contentDescription = if (onShelf) {
                                        "On your list — tap to remove"
                                    } else {
                                        "Add to Want to Read"
                                    },
                                    tint = if (onShelf) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
