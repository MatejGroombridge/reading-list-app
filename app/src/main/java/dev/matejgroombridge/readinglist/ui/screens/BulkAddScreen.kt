package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.domain.BulkAddParser
import dev.matejgroombridge.readinglist.domain.TextMatch
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.components.CaptionedSection
import dev.matejgroombridge.readinglist.ui.components.OptionGrid
import dev.matejgroombridge.readinglist.ui.components.ShelfChips
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import java.time.LocalDate

/**
 * Paste a list, add it in one go. Meant for a friend's texted list of
 * recommendations, or for moving a section across from somewhere else.
 * Who it's from, why, status and shelf are set once for the whole batch;
 * titles already on the list are skipped.
 */
@Composable
fun BulkAddScreen(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onDone: (added: Int) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    var text by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var why by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf(ReadingStatus.WantToRead) }
    var shelfId by rememberSaveable { mutableStateOf<String?>(null) }

    val parsed = remember(text) { BulkAddParser.parse(text) }
    val (dupes, fresh) = remember(parsed, state.library) {
        parsed.partition { TextMatch.findDuplicate(state.library.books, it.title) != null }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "Bulk Add", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CaptionedSection(caption = "List") {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Dune - Frank Herbert\nThe Stranger by Albert Camus\n…") },
                    minLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CaptionedSection(caption = "Applies to All") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text("From") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = why,
                        onValueChange = { why = it },
                        label = { Text("Why (optional)") },
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OptionGrid(
                        options = ReadingStatus.entries,
                        selected = status,
                        label = { it.label },
                        onSelect = { status = it },
                    )
                    Spacer(Modifier.height(2.dp))
                    ShelfChips(library = state.library, selectedId = shelfId, onSelect = { shelfId = it })
                }
            }

            if (parsed.isNotEmpty()) {
                CaptionedSection(caption = "Preview") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        fresh.take(PREVIEW_LIMIT).forEach { entry ->
                            Text(
                                text = buildString {
                                    append(entry.title)
                                    if (entry.author.isNotBlank()) append(" — ${entry.author}")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (fresh.size > PREVIEW_LIMIT) {
                            Text(
                                "…and ${fresh.size - PREVIEW_LIMIT} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (dupes.isNotEmpty()) {
                            Text(
                                text = "Skipping ${dupes.size} already on your list: " +
                                    dupes.joinToString(", ") { it.title },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Button(
                enabled = fresh.isNotEmpty(),
                onClick = {
                    haptics.completion()
                    val today = LocalDate.now().toEpochDay()
                    viewModel.addBooks(
                        fresh.map { entry ->
                            Book(
                                title = entry.title,
                                author = entry.author,
                                recommendedBy = from.trim(),
                                // A trailing "(aside)" is the closest thing a
                                // pasted line has to a reason.
                                reason = why.trim().ifEmpty { entry.note },
                                shelfId = shelfId,
                            ).withStatus(status, today).let {
                                // A pasted list of finished books is a log of
                                // the past — "finished today" would be wrong.
                                if (status == ReadingStatus.Read || status == ReadingStatus.Abandoned) {
                                    it.copy(finishedOn = null)
                                } else it
                            }
                        },
                    )
                    onDone(fresh.size)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when (fresh.size) {
                        0 -> "Nothing to Add"
                        1 -> "Add 1 Item"
                        else -> "Add ${fresh.size} Items"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val PREVIEW_LIMIT = 12
