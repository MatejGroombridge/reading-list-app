package dev.matejgroombridge.readinglist.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ShelfStatus
import dev.matejgroombridge.readinglist.domain.GenreClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.booksDataStore: DataStore<Preferences> by preferencesDataStore(name = "books")

/**
 * Single source of truth for the user's list. Backed by a Preferences
 * DataStore holding the whole shelf as one JSON-encoded string.
 *
 * Same intentionally-simple shape as the rest of the app family: no Room, no
 * migrations, one blob. Adding fields to [Book] stays safe because the parser
 * sets `ignoreUnknownKeys = true` and every field has a default.
 */
class BookRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val listSerializer = ListSerializer(Book.serializer())

    val books: Flow<List<Book>> = context.booksDataStore.data.map { prefs ->
        load(prefs[KEY_BOOKS_JSON])
    }

    /**
     * Adds [book] to the shelf as Want to Read. No-op if the work is already
     * there — the search screen shows an "On your list" state instead, so
     * re-adding must never clobber an existing note, rating, or queue slot.
     */
    suspend fun add(book: Book, todayEpochDay: Long) {
        update { current ->
            if (current.any { it.id == book.id }) current
            else current + book.copy(addedAtEpochDay = todayEpochDay)
        }
    }

    suspend fun remove(bookId: String) {
        update { current -> current.filterNot { it.id == bookId } }
    }

    /**
     * Moves a book between shelves, stamping the matching date as it goes.
     *
     * Start/finish dates are only written the first time so that toggling a
     * book back and forth doesn't rewrite history the user cares about. A
     * book that leaves Want to Read also leaves the Up Next queue — the queue
     * means "next to pick up", so something in progress or already finished
     * has no business sitting in it.
     */
    suspend fun setStatus(bookId: String, status: ShelfStatus, todayEpochDay: Long) {
        update { current ->
            current.map { book ->
                if (book.id != bookId) return@map book
                book.copy(
                    status = status,
                    startedAtEpochDay = when (status) {
                        ShelfStatus.Reading -> book.startedAtEpochDay ?: todayEpochDay
                        ShelfStatus.Read -> book.startedAtEpochDay
                        ShelfStatus.WantToRead -> book.startedAtEpochDay
                    },
                    finishedAtEpochDay = when (status) {
                        ShelfStatus.Read -> book.finishedAtEpochDay ?: todayEpochDay
                        else -> null
                    },
                    priorityRank = if (status == ShelfStatus.WantToRead) book.priorityRank else null,
                )
            }
        }
    }

    /** Sets a 1..5 star rating, or 0 to clear it. */
    suspend fun setRating(bookId: String, rating: Int) {
        update { current ->
            current.map { book ->
                if (book.id == bookId) book.copy(rating = rating.coerceIn(0, 5)) else book
            }
        }
    }

    /** Records where the recommendation came from — the app's whole reason for existing. */
    suspend fun setRecSource(bookId: String, source: String, kind: String) {
        update { current ->
            current.map { book ->
                if (book.id == bookId) book.copy(recSource = source.trim(), recSourceKind = kind) else book
            }
        }
    }

    /**
     * Stores the long description fetched from the work endpoint the first
     * time a book's detail sheet is opened, so re-opening it later — on a
     * train, on a plane, anywhere without signal — still shows the blurb.
     * Ignores blanks so a failed fetch can't erase a description we already
     * have.
     */
    suspend fun cacheDescription(bookId: String, description: String) {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            current.map { book ->
                if (book.id == bookId && book.description != trimmed) {
                    book.copy(description = trimmed)
                } else {
                    book
                }
            }
        }
    }

    /**
     * Fills in subject tags for a book whose search result arrived without
     * any, and re-runs the classifier over them.
     *
     * Search hits usually carry subjects, but sparse or newly-added Open
     * Library records sometimes don't — and without subjects a book has no
     * genre and lands in Unsorted. The work endpoint holds the fuller record,
     * so opening the book once is enough to file it properly.
     */
    suspend fun backfillSubjects(bookId: String, subjects: List<String>) {
        val capped = subjects.filter { it.isNotBlank() }.take(Book.MAX_SUBJECTS)
        if (capped.isEmpty()) return
        update { current ->
            current.map { book ->
                if (book.id != bookId) book
                else book.copy(
                    subjects = capped,
                    autoGenreKey = GenreClassifier.classify(capped),
                )
            }
        }
    }

    suspend fun setNotes(bookId: String, notes: String) {
        update { current ->
            current.map { book -> if (book.id == bookId) book.copy(notes = notes.trim()) else book }
        }
    }

    /**
     * Overrides the auto-assigned genre, or clears the override with null so
     * the classifier's pick applies again.
     */
    suspend fun setGenreOverride(bookId: String, genreKey: String?) {
        update { current ->
            current.map { book -> if (book.id == bookId) book.copy(genreOverride = genreKey) else book }
        }
    }

    /**
     * Adds to / removes from the Up Next queue. New entries land at the
     * bottom; the user promotes them from the Up Next screen.
     */
    suspend fun setPrioritised(bookId: String, prioritised: Boolean) {
        update { current ->
            val nextRank = (current.mapNotNull { it.priorityRank }.maxOrNull() ?: -1) + 1
            current.map { book ->
                if (book.id != bookId) book
                else book.copy(priorityRank = if (prioritised) book.priorityRank ?: nextRank else null)
            }
        }
    }

    /**
     * Shifts a queued book by [delta] slots (-1 = up, +1 = down). Swaps ranks
     * with the neighbour rather than renumbering the whole queue, so repeated
     * taps stay stable and can't drift. Out-of-range moves are ignored, which
     * makes the top/bottom arrows safe to leave enabled.
     */
    suspend fun movePriority(bookId: String, delta: Int) {
        update { current ->
            val queue = current.filter { it.priorityRank != null }.sortedBy { it.priorityRank }
            val index = queue.indexOfFirst { it.id == bookId }
            if (index < 0) return@update current
            val target = index + delta
            if (target !in queue.indices) return@update current

            val moving = queue[index]
            val displaced = queue[target]
            current.map { book ->
                when (book.id) {
                    moving.id -> book.copy(priorityRank = displaced.priorityRank)
                    displaced.id -> book.copy(priorityRank = moving.priorityRank)
                    else -> book
                }
            }
        }
    }

    /** Jumps a queued book straight to the front — the "read this next" button. */
    suspend fun movePriorityToTop(bookId: String) {
        update { current ->
            if (current.none { it.id == bookId && it.priorityRank != null }) return@update current
            // Everything else shifts down one; normalisation in `update`
            // collapses the resulting gap back to a dense 0..n-1 range.
            current.map { book ->
                when {
                    book.id == bookId -> book.copy(priorityRank = -1)
                    book.priorityRank != null -> book.copy(priorityRank = book.priorityRank + 1)
                    else -> book
                }
            }
        }
    }

    /**
     * Re-runs the genre classifier across the whole shelf and returns how many
     * books changed section. Manual overrides are left alone. Exposed in
     * Settings so an improved rule table can be applied to books that were
     * added under the old one.
     */
    suspend fun reclassifyAll(): Int {
        var changed = 0
        update { current ->
            current.map { book ->
                if (book.subjects.isEmpty()) return@map book
                val fresh = GenreClassifier.classify(book.subjects)
                if (fresh == book.autoGenreKey) book
                else {
                    // Only counts as a visible change when no override is
                    // masking it — otherwise the reported number wouldn't
                    // match what the user sees move on the shelf.
                    if (book.genreOverride == null) changed++
                    book.copy(autoGenreKey = fresh)
                }
            }
        }
        return changed
    }

    /** Serialises the shelf for export to a file. */
    suspend fun exportJson(): String {
        val prefs = context.booksDataStore.data.first()
        return json.encodeToString(listSerializer, load(prefs[KEY_BOOKS_JSON]))
    }

    /**
     * Replaces the shelf with [rawJson], returning the number of books
     * imported, or null if it couldn't be parsed — in which case the existing
     * shelf is left untouched.
     */
    suspend fun importJson(rawJson: String): Int? {
        val parsed = runCatching { json.decodeFromString(listSerializer, rawJson) }.getOrNull()
            ?: return null
        update { parsed }
        return parsed.size
    }

    private suspend fun update(block: (List<Book>) -> List<Book>) {
        context.booksDataStore.edit { prefs ->
            val existing = load(prefs[KEY_BOOKS_JSON])
            val updated = normalisePriorities(block(existing))
            prefs[KEY_BOOKS_JSON] = json.encodeToString(listSerializer, updated)
        }
    }

    /**
     * Collapses queue positions back to a dense, gap-free 0..n-1 range after
     * every mutation. Callers are then free to write sentinel ranks (see
     * [movePriorityToTop]'s -1) or leave holes behind after a removal without
     * having to think about the invariant themselves.
     */
    private fun normalisePriorities(books: List<Book>): List<Book> {
        val ranked = books.filter { it.priorityRank != null }
            .sortedBy { it.priorityRank }
            .mapIndexed { index, book -> book.id to index }
            .toMap()
        if (ranked.isEmpty()) return books
        return books.map { book ->
            val rank = ranked[book.id]
            if (rank != null && rank != book.priorityRank) book.copy(priorityRank = rank) else book
        }
    }

    private fun load(raw: String?): List<Book> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_BOOKS_JSON = stringPreferencesKey("books_json")
    }
}
