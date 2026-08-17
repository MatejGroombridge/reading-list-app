package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.ui.ImportState
import dev.matejgroombridge.readinglist.ui.ImportViewModel
import dev.matejgroombridge.readinglist.ui.components.BookCover
import dev.matejgroombridge.readinglist.ui.components.CardCorner
import dev.matejgroombridge.readinglist.ui.components.InfoPill
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * Bulk import — paste a reading list, review what matched, add it all.
 *
 * The migration path for anyone arriving with an existing document of titles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportListScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rawText by viewModel.rawText.collectAsStateWithLifecycle()
    val truncatedFrom by viewModel.truncatedFrom.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Import a List") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        when (val current = state) {
            is ImportState.Input -> InputStep(
                rawText = rawText,
                onTextChange = viewModel::onTextChange,
                onStart = viewModel::startMatching,
            )

            is ImportState.Matching -> MatchingStep(done = current.done, total = current.total)

            is ImportState.Review -> ReviewStep(
                state = current,
                truncatedFrom = truncatedFrom,
                onToggle = viewModel::toggle,
                onSetAll = viewModel::setAllSelected,
                onConfirm = viewModel::confirm,
            )

            is ImportState.Done -> DoneStep(
                added = current.added,
                onDone = {
                    viewModel.reset()
                    onFinished()
                },
            )
        }
    }
}

@Composable
private fun InputStep(
    rawText: String,
    onTextChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    val haptics = rememberHaptics()
    val lineCount = ImportViewModel.parseLines(rawText).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Paste your list — one book per line. Bullets, numbering and " +
                "\"Title by Author\" all work.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = rawText,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            placeholder = {
                Text(
                    "The Dispossessed by Ursula K. Le Guin\n" +
                        "1. Sapiens\n" +
                        "- Project Hail Mary",
                )
            },
        )
        Text(
            text = when {
                lineCount == 0 -> "Nothing to import yet."
                lineCount > ImportViewModel.MAX_LINES ->
                    "$lineCount books found — the first ${ImportViewModel.MAX_LINES} will be looked up."
                else -> "$lineCount book${if (lineCount == 1) "" else "s"} found."
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                haptics.completion()
                onStart()
            },
            enabled = lineCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Look Up ${if (lineCount > 0) "$lineCount " else ""}Books")
        }
    }
}

@Composable
private fun MatchingStep(done: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.size(48.dp))
        CircularProgressIndicator()
        Text(
            text = "Looking up $done of $total",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Searching one at a time so Open Library doesn't throttle us.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReviewStep(
    state: ImportState.Review,
    truncatedFrom: Int,
    onToggle: (String) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    val haptics = rememberHaptics()
    val selectedCount = state.candidates.count { it.selected }
    val unmatched = state.candidates.count { it.match == null }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (unmatched > 0) {
                    Text(
                        text = "$unmatched couldn't be matched",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { onSetAll(true) }) { Text("All") }
            TextButton(onClick = { onSetAll(false) }) { Text("None") }
        }

        if (truncatedFrom > 0) {
            Text(
                text = "Your list had $truncatedFrom lines — only the first " +
                    "${ImportViewModel.MAX_LINES} were looked up. Import these, " +
                    "then paste the rest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.candidates, key = { it.line }) { candidate ->
                CandidateRow(
                    line = candidate.line,
                    matchTitle = candidate.match?.title,
                    matchAuthor = candidate.match?.authorLine,
                    genreLabel = candidate.match?.genre?.label,
                    alreadyOnShelf = candidate.alreadyOnShelf,
                    selected = candidate.selected,
                    cover = candidate.match?.let { book ->
                        { BookCover(book = book, width = 36.dp) }
                    },
                    onToggle = { onToggle(candidate.line) },
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Button(
                onClick = {
                    haptics.completion()
                    onConfirm()
                },
                enabled = selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Add $selectedCount to Library")
            }
        }
    }
}

@Composable
private fun CandidateRow(
    line: String,
    matchTitle: String?,
    matchAuthor: String?,
    genreLabel: String?,
    alreadyOnShelf: Boolean,
    selected: Boolean,
    cover: (@Composable () -> Unit)?,
    onToggle: () -> Unit,
) {
    val matched = matchTitle != null
    Surface(
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onToggle,
        enabled = matched,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = matched,
            )
            Spacer(Modifier.width(4.dp))
            if (cover != null) {
                cover()
                Spacer(Modifier.width(10.dp))
            } else {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = matchTitle ?: line,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (matched) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = matchAuthor ?: "No match found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Always show the original line under a match so a wrong
                // pairing is obvious at a glance — this is the whole point of
                // the review step.
                if (matched && !line.equals(matchTitle, ignoreCase = true)) {
                    Text(
                        text = "from: $line",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = TextDecoration.None,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genreLabel?.let { InfoPill(text = it) }
                    if (alreadyOnShelf) InfoPill(text = "Already on your list")
                }
            }
        }
    }
}

@Composable
private fun DoneStep(added: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.size(48.dp))
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Added $added book${if (added == 1) "" else "s"}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "They've been filed into genre sections on your Library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Go to Library")
        }
    }
}
