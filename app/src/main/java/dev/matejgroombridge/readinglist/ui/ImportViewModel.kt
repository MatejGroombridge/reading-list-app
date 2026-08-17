package dev.matejgroombridge.readinglist.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.network.OpenLibraryApi
import dev.matejgroombridge.readinglist.data.repository.BookRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One line of the pasted list, paired with whatever Open Library matched. */
data class ImportCandidate(
    val line: String,
    val match: Book?,
    val selected: Boolean = true,
    val alreadyOnShelf: Boolean = false,
)

sealed interface ImportState {
    /** Waiting for the user to paste their list. */
    data object Input : ImportState

    data class Matching(val done: Int, val total: Int) : ImportState

    data class Review(val candidates: List<ImportCandidate>) : ImportState

    data class Done(val added: Int) : ImportState
}

/**
 * Bulk import: paste a list of titles, look each one up, review the matches,
 * add them all at once.
 *
 * This is the migration path off a plain document — the situation this app
 * exists to fix. Adding a hundred books one search at a time is enough
 * friction that the list would never get moved across, so the whole document
 * goes in one paste and comes out sorted by genre.
 *
 * The review step is not optional politeness: matching a bare title against a
 * crowd-maintained catalogue gets it wrong often enough that silently adding
 * the top hit would quietly fill the shelf with the wrong editions and
 * same-titled books.
 */
class ImportViewModel(
    private val repository: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Input)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _rawText = MutableStateFlow("")
    val rawText: StateFlow<String> = _rawText.asStateFlow()

    /** Set when the paste exceeded [MAX_LINES] and had to be trimmed. */
    private val _truncatedFrom = MutableStateFlow(0)
    val truncatedFrom: StateFlow<Int> = _truncatedFrom.asStateFlow()

    fun onTextChange(value: String) {
        _rawText.value = value
    }

    fun reset() {
        _rawText.value = ""
        _truncatedFrom.value = 0
        _state.value = ImportState.Input
    }

    /**
     * Parses the pasted text and looks each line up, one request at a time.
     *
     * Sequential rather than parallel, with a small gap between calls: Open
     * Library is a free service with no quota, and firing eighty concurrent
     * searches at it is the kind of thing that gets an IP throttled. A
     * hundred-line list takes a few seconds, which is fine for a one-off
     * migration.
     */
    fun startMatching() {
        val allLines = parseLines(_rawText.value)
        if (allLines.isEmpty()) return

        val lines = allLines.take(MAX_LINES)
        _truncatedFrom.value = if (allLines.size > MAX_LINES) allLines.size else 0

        viewModelScope.launch {
            _state.value = ImportState.Matching(done = 0, total = lines.size)
            val onShelf = repository.books.first().map { it.id }.toSet()

            val candidates = mutableListOf<ImportCandidate>()
            lines.forEachIndexed { index, line ->
                val match = runCatching { OpenLibraryApi.search(line, limit = 1) }
                    .getOrNull()
                    ?.firstOrNull()
                candidates += ImportCandidate(
                    line = line,
                    match = match,
                    // Anything already on the shelf is pre-deselected so a
                    // re-run of the same document doesn't look like it's
                    // about to create duplicates.
                    selected = match != null && match.id !in onShelf,
                    alreadyOnShelf = match != null && match.id in onShelf,
                )
                _state.value = ImportState.Matching(done = index + 1, total = lines.size)
                if (index < lines.lastIndex) delay(REQUEST_GAP_MS)
            }
            _state.value = ImportState.Review(candidates)
        }
    }

    fun toggle(line: String) {
        val current = _state.value as? ImportState.Review ?: return
        _state.value = ImportState.Review(
            current.candidates.map {
                if (it.line == line && it.match != null) it.copy(selected = !it.selected) else it
            },
        )
    }

    fun setAllSelected(selected: Boolean) {
        val current = _state.value as? ImportState.Review ?: return
        _state.value = ImportState.Review(
            current.candidates.map {
                if (it.match != null) it.copy(selected = selected) else it
            },
        )
    }

    /** Adds every selected match to the shelf as Want to Read. */
    fun confirm() {
        val current = _state.value as? ImportState.Review ?: return
        val chosen = current.candidates.mapNotNull { if (it.selected) it.match else null }
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            chosen.forEach { repository.add(it, today) }
            _state.value = ImportState.Done(chosen.size)
        }
    }

    companion object {
        /**
         * Ceiling on one paste. Generous enough for a real backlog document,
         * low enough that a stray paste of an entire file can't fire
         * thousands of requests at a free API.
         */
        const val MAX_LINES = 150

        private const val REQUEST_GAP_MS = 120L

        /**
         * Turns a pasted document into clean search queries.
         *
         * Handles the shapes these lists are actually written in — bullets,
         * numbering, quoted titles, blank lines, and stray whitespace — and
         * drops duplicates case-insensitively so a title listed twice doesn't
         * cost two lookups.
         *
         * `Title by Author` is left intact rather than split: Open Library's
         * search handles the combined string well, and the author is a useful
         * disambiguator for common titles.
         */
        fun parseLines(raw: String): List<String> = raw.lineSequence()
            .map { it.trim() }
            .map { it.trimStart('-', '*', '•', '·', '–', '—').trim() }
            .map { it.replace(LEADING_NUMBER, "") }
            .map { it.trim().trim('"', '\'', '“', '”') }
            .map { it.trim() }
            .filter { it.length >= MIN_LINE_LENGTH }
            .distinctBy { it.lowercase() }
            .toList()

        /** "1. ", "12) ", "3 - " at the start of a line. */
        private val LEADING_NUMBER = Regex("""^\d+\s*[.)\-]\s*""")

        /** Below this a "line" is punctuation or a stray character, not a title. */
        private const val MIN_LINE_LENGTH = 2

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportViewModel(BookRepository(application.applicationContext)) }
        }
    }
}
