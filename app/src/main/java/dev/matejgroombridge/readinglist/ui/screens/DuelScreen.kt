package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.domain.Ranking
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.components.BookTile
import dev.matejgroombridge.readinglist.ui.components.blend
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.theme.containerColor
import dev.matejgroombridge.readinglist.ui.theme.contentColor
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * "This or That": two To Read books side by side; tap the one you'd rather
 * read. Each pick adjusts both books' Elo ratings (see [Ranking]), which is
 * the biggest input to the Top Ranked sort. Pairs favour books with the
 * fewest duels, matched against similarly-rated rivals, so a handful a day
 * sorts a long list quickly.
 */
@Composable
fun DuelScreen(
    state: LibraryUiState,
    settings: Settings,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    var round by rememberSaveable { mutableIntStateOf(0) }
    var done by rememberSaveable { mutableIntStateOf(0) }
    var lastPair by remember { mutableStateOf(emptySet<String>()) }

    // Pick a new pair per round. Keyed on the round only, so the library
    // updating after a vote doesn't reshuffle the pair mid-tap.
    val pool = Ranking.duelPool(state.library)
    val pair = remember(round, state.loaded) {
        Ranking.nextPair(pool, avoid = lastPair)?.also { lastPair = setOf(it.first.id, it.second.id) }
    }

    fun next() {
        round++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar(title = "This or That", onBack = onBack) },
    ) { padding ->
        if (pair == null) {
            EmptyState("Add at least two books to To Read to start comparing.", Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Which would you rather read?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = pair,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "duel",
                modifier = Modifier.weight(1f),
            ) { (a, b) ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(a to b, b to a).forEachIndexed { i, (winner, loser) ->
                        if (i == 1) {
                            Text(
                                text = "or",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DuelCard(
                            book = winner,
                            state = state,
                            settings = settings,
                            modifier = Modifier.weight(1f),
                        ) {
                            haptics.completion()
                            viewModel.recordDuel(winner.id, loser.id)
                            done++
                            next()
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = if (done == 0) "${pool.size} books to compare" else "$done compared",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    haptics.light()
                    next()
                }) { Text("Can't Decide") }
            }
        }
    }
}

@Composable
private fun DuelCard(
    book: Book,
    state: LibraryUiState,
    settings: Settings,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    val shelf = state.library.shelf(book.shelfId)
    val palette = ShelfColors.entry(shelf?.colorKey)
    val content = palette.contentColor()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = blend(palette.containerColor(), MaterialTheme.colorScheme.background, 0.1f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onPick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            BookTile(book = book, shelf = shelf, showCovers = settings.showCovers, width = 84.dp, height = 120.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f), maxLines = 1)
                }
                // The context that usually decides it: who suggested it and why.
                if (book.reason.isNotBlank()) {
                    Text(
                        text = "“${book.reason}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = content.copy(alpha = 0.75f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = listOfNotNull(
                    book.recommendedBy.takeIf { it.isNotBlank() }?.let { "From $it" },
                    book.publicRating.takeIf { book.publicRatingCount > 0 && it > 0 }?.let { "★ %.1f".format(it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
                }
            }
        }
    }
}
