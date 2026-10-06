package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.BookPrefill
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.domain.LibraryQueries
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.util.Haptics

/** Which book dialog is showing. One at a time; null = none. */
sealed interface BookDialog {
    data class Create(val prefill: BookPrefill) : BookDialog
    data class Overview(val bookId: String, val picked: Boolean = false) : BookDialog
    data class Edit(val bookId: String) : BookDialog
    data class Finish(val bookId: String) : BookDialog
    data class ConfirmStart(val bookId: String) : BookDialog
}

/**
 * The status moves every surface offers — overview buttons, long-press
 * menus, the Pick for Me dialog — routed through one place so the rules
 * (current-reads limit, finish dialog, archive undo) can't drift apart
 * between screens.
 */
class BookActions(
    private val viewModel: LibraryViewModel,
    private val state: () -> LibraryUiState,
    private val settings: () -> Settings,
    private val haptics: Haptics,
    val open: (BookDialog?) -> Unit,
    private val onArchived: (Book) -> Unit,
) {
    fun overview(book: Book) = open(BookDialog.Overview(book.id))

    fun create(status: ReadingStatus) = open(BookDialog.Create(BookPrefill(status = status)))

    /** Asks first when the current-reads limit is already reached. */
    fun startReading(book: Book, confirmed: Boolean = false) {
        val limit = settings().currentReadsLimit
        val readingNow = state().reading.count { it.id != book.id }
        if (!confirmed && limit > 0 && readingNow >= limit) {
            open(BookDialog.ConfirmStart(book.id))
            return
        }
        haptics.completion()
        viewModel.setStatus(book.id, ReadingStatus.Reading)
    }

    fun finish(book: Book) = open(BookDialog.Finish(book.id))

    fun abandon(book: Book) {
        haptics.light()
        viewModel.setStatus(book.id, ReadingStatus.Abandoned)
    }

    fun backToList(book: Book) {
        haptics.light()
        viewModel.setStatus(book.id, ReadingStatus.WantToRead)
    }

    fun toggleUpNext(book: Book) {
        haptics.light()
        viewModel.toggleUpNext(book.id)
    }

    fun archive(book: Book) {
        viewModel.setArchived(book.id, true)
        onArchived(book)
    }

    fun pickForMe(excludeId: String? = null) {
        val pick = LibraryQueries.pickRandom(state().library, exclude = excludeId) ?: return
        haptics.light()
        open(BookDialog.Overview(pick.id, picked = true))
    }

    /** Long-press menu entries, by where the item currently is. */
    fun quickActions(book: Book): List<QuickAction> = buildList {
        when (book.status) {
            ReadingStatus.WantToRead -> {
                add(QuickAction("Start Reading", Icons.AutoMirrored.Outlined.MenuBook) { startReading(book) })
                add(QuickAction("Already Read", Icons.Outlined.TaskAlt) { finish(book) })
                add(
                    QuickAction(
                        if (book.upNext) "Remove from Up Next" else "Add to Up Next",
                        if (book.upNext) Icons.Outlined.StarOutline else Icons.Filled.Star,
                    ) { toggleUpNext(book) },
                )
            }
            ReadingStatus.Reading -> {
                add(QuickAction("Finished", Icons.Outlined.TaskAlt) { finish(book) })
                add(QuickAction("Didn't Finish", Icons.Outlined.Close) { abandon(book) })
                add(QuickAction("Back to To Read", Icons.AutoMirrored.Outlined.Undo) { backToList(book) })
            }
            ReadingStatus.Read -> {
                add(QuickAction("Read Again", Icons.Outlined.Replay) { startReading(book) })
            }
            ReadingStatus.Abandoned -> {
                add(QuickAction("Resume Reading", Icons.AutoMirrored.Outlined.MenuBook) { startReading(book) })
                add(QuickAction("Back to To Read", Icons.AutoMirrored.Outlined.Undo) { backToList(book) })
            }
        }
        add(QuickAction("Edit", Icons.Outlined.Edit) { open(BookDialog.Edit(book.id)) })
        add(QuickAction("Archive", Icons.Outlined.Archive) { archive(book) })
    }
}

/**
 * Renders whichever [BookDialog] is active. Each dialog reads its book from
 * the live [state] by id, so edits from anywhere (another dialog, the
 * widget) show immediately, and a book that disappears closes its dialog.
 */
@Composable
fun BookDialogHost(
    dialog: BookDialog?,
    state: LibraryUiState,
    settings: Settings,
    viewModel: LibraryViewModel,
    actions: BookActions,
    haptics: Haptics,
    onCelebrate: () -> Unit,
) {
    val close = { actions.open(null) }
    when (dialog) {
        null -> Unit

        is BookDialog.Create -> BookEditorDialog(
            existing = null,
            prefill = dialog.prefill,
            library = state.library,
            onlineLookup = settings.onlineLookup,
            showCovers = settings.showCovers,
            onSearch = { viewModel.search(it) },
            onDismiss = close,
            onOpenDuplicate = { actions.open(BookDialog.Overview(it.id)) },
            onResult = { result ->
                if (result is BookEditorResult.Save) {
                    haptics.completion()
                    viewModel.addBook(result.book)
                }
                close()
            },
        )

        is BookDialog.Edit -> {
            val book = state.book(dialog.bookId) ?: return CloseNow(close)
            BookEditorDialog(
                existing = book,
                library = state.library,
                onlineLookup = settings.onlineLookup,
                showCovers = settings.showCovers,
                onSearch = { viewModel.search(it) },
                onDismiss = close,
                onResult = { result ->
                    when (result) {
                        is BookEditorResult.Save -> viewModel.updateBook(book.id) { result.book }
                        is BookEditorResult.Archive ->
                            if (result.archived) actions.archive(book) else viewModel.setArchived(book.id, false)
                    }
                    close()
                },
            )
        }

        is BookDialog.Overview -> {
            val book = state.book(dialog.bookId)?.takeUnless { it.archived } ?: return CloseNow(close)
            // Rebuilt every composition on purpose: the lambdas must see the
            // live book and state, not the snapshot from when it first opened.
            val overviewActions =
                OverviewActions(
                    onEdit = { actions.open(BookDialog.Edit(book.id)) },
                    onToggleUpNext = { actions.toggleUpNext(book) },
                    onStartReading = { actions.startReading(book) },
                    onFinish = { actions.finish(book) },
                    onAbandon = { actions.abandon(book) },
                    onBackToList = { actions.backToList(book) },
                    onSetProgress = { page ->
                        haptics.light()
                        viewModel.setProgress(book.id, page)
                    },
                    onSetRating = { rating ->
                        haptics.light()
                        viewModel.setRating(book.id, rating)
                    },
                    onPickAgain = if (dialog.picked) ({ actions.pickForMe(excludeId = book.id) }) else null,
                )
            BookOverviewDialog(
                book = book,
                shelf = state.library.shelf(book.shelfId),
                showCovers = settings.showCovers,
                todayEpochDay = state.todayEpochDay,
                onDismiss = close,
                actions = overviewActions,
            )
        }

        is BookDialog.Finish -> {
            val book = state.book(dialog.bookId) ?: return CloseNow(close)
            FinishBookDialog(
                book = book,
                onDismiss = close,
                onConfirm = { finishedOn, rating, review ->
                    haptics.completion()
                    viewModel.finish(book.id, finishedOn, rating, review)
                    onCelebrate()
                    close()
                },
            )
        }

        is BookDialog.ConfirmStart -> {
            val book = state.book(dialog.bookId) ?: return CloseNow(close)
            val others = state.reading.filter { it.id != book.id }
            AlertDialog(
                onDismissRequest = close,
                title = { Text("Start Another Book?") },
                text = {
                    Text(
                        "You're already reading ${others.size} " +
                            "${if (others.size == 1) "book" else "books"}: " +
                            others.joinToString(", ") { it.title } +
                            ". Your limit is ${settings.currentReadsLimit}.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        actions.startReading(book, confirmed = true)
                        close()
                    }) { Text("Start Anyway") }
                },
                dismissButton = { TextButton(onClick = close) { Text("Not Now") } },
            )
        }
    }
}

/** Closes a dialog whose book has gone (deleted, archived) — after composition, not during it. */
@Composable
private fun CloseNow(close: () -> Unit) {
    SideEffect { close() }
}
