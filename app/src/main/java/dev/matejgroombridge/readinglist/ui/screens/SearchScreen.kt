package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.BookPrefill
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.domain.TextMatch
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.components.BookActions
import dev.matejgroombridge.readinglist.ui.components.BookCard
import dev.matejgroombridge.readinglist.ui.components.BookDialog

/**
 * Searches every status at once — the usual question is "is this already
 * on my list?", whether it's waiting, in progress or finished. Matches
 * title, author, who recommended it, why, notes and takeaways, ignoring
 * case and accents. When nothing matches, offers to add the query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: LibraryUiState,
    settings: Settings,
    actions: BookActions,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val results: Map<ReadingStatus, List<Book>> = remember(query, state.library) {
        if (query.isBlank()) emptyMap()
        else state.active
            .filter { TextMatch.matches(it, query) }
            .groupBy { it.status }
            .toSortedMap(compareBy { it.ordinal })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Title, author, who, why…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (query.isBlank()) {
            EmptyState(
                "Search everything on your list —\ntitles, authors, who recommended it and why.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            results.forEach { (status, books) ->
                item(key = "header_${status.name}") { ListSectionHeader(status.label, books.size) }
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        shelf = state.library.shelf(book.shelfId),
                        showCovers = settings.showCovers,
                        todayEpochDay = state.todayEpochDay,
                        onClick = { actions.overview(book) },
                        quickActions = { actions.quickActions(book) },
                    )
                }
            }
            item(key = "add") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    if (results.isEmpty()) {
                        Text(
                            text = "Nothing on your list matches “${query.trim()}”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            actions.open(
                                BookDialog.Create(BookPrefill(title = query.trim(), status = ReadingStatus.WantToRead)),
                            )
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("Add “${query.trim()}”", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}
