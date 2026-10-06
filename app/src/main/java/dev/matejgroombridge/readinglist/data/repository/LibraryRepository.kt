package dev.matejgroombridge.readinglist.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.model.Shelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library")

/** What an import would do, for the confirmation dialog and the snackbar after it. */
data class ImportSummary(val books: Int, val shelves: Int)

/**
 * Single source of truth for books, shelves and the notebook. Like Habit
 * Tracker this is one JSON blob in a Preferences DataStore — a few hundred
 * books is a couple of hundred KB, well within what a full rewrite per edit
 * handles without noticeable cost, and it keeps export/import trivially
 * identical to the stored format.
 */
class LibraryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    private val prettyJson = Json(json) { prettyPrint = true }

    val library: Flow<Library> = context.libraryDataStore.data.map { prefs ->
        load(prefs[KEY_LIBRARY_JSON])
    }

    /** Adds [book] at the end of the list, stamping its added time if unset. */
    suspend fun addBook(book: Book) {
        val stamped = if (book.addedAt > 0) book else book.copy(addedAt = System.currentTimeMillis())
        if (stamped.title.isBlank()) return
        update { it.copy(books = it.books + stamped) }
    }

    suspend fun addBooks(books: List<Book>) {
        if (books.isEmpty()) return
        val now = System.currentTimeMillis()
        // Offset each stamp by a millisecond so a bulk add keeps its pasted
        // order under "Recently added".
        val stamped = books.mapIndexed { i, b -> if (b.addedAt > 0) b else b.copy(addedAt = now + i) }
        update { it.copy(books = it.books + stamped) }
    }

    suspend fun updateBook(id: String, transform: (Book) -> Book) {
        update { lib -> lib.copy(books = lib.books.map { if (it.id == id) transform(it) else it }) }
    }

    suspend fun setStatus(id: String, status: ReadingStatus, finishedOn: Long? = null) {
        val today = LocalDate.now().toEpochDay()
        updateBook(id) { it.withStatus(status, today, finishedOn) }
    }

    suspend fun setProgress(id: String, page: Int) {
        updateBook(id) { b ->
            val max = if (b.pageCount > 0) b.pageCount else Int.MAX_VALUE
            b.copy(currentPage = page.coerceIn(0, max))
        }
    }

    suspend fun deleteBook(id: String) {
        update { lib -> lib.copy(books = lib.books.filterNot { it.id == id }) }
    }

    suspend fun addShelf(shelf: Shelf) {
        if (shelf.name.isBlank()) return
        update { it.copy(shelves = it.shelves + shelf.copy(name = shelf.name.trim())) }
    }

    suspend fun updateShelf(shelf: Shelf) {
        if (shelf.name.isBlank()) return
        update { lib ->
            lib.copy(shelves = lib.shelves.map { if (it.id == shelf.id) shelf.copy(name = shelf.name.trim()) else it })
        }
    }

    /** Removes the shelf. Its books stay on the list, just unshelved. */
    suspend fun deleteShelf(id: String) {
        update { lib ->
            lib.copy(
                shelves = lib.shelves.filterNot { it.id == id },
                books = lib.books.map { if (it.shelfId == id) it.copy(shelfId = null) else it },
            )
        }
    }

    /** Moves a shelf one slot up (-1) or down (+1). No-op at either end. */
    suspend fun moveShelf(id: String, delta: Int) {
        update { lib ->
            val list = lib.shelves.toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@update lib
            list.add(to, list.removeAt(from))
            lib.copy(shelves = list)
        }
    }

    suspend fun setNotes(notes: String) {
        update { it.copy(notes = notes) }
    }

    suspend fun exportJson(): String {
        val current = load(context.libraryDataStore.data.first()[KEY_LIBRARY_JSON])
        return prettyJson.encodeToString(Library.serializer(), current)
    }

    /** Parses an export file, or returns null if it isn't one. */
    fun parseImport(raw: String): Library? = decode(raw)

    /**
     * Applies [incoming].
     *
     * Replace swaps the whole library. Merge adds the books and shelves that
     * aren't already here: books are matched by id (so re-importing the same
     * backup is harmless), shelves by id or name so a merged export lands on
     * the existing "Fiction" shelf instead of creating a second one.
     */
    suspend fun importLibrary(incoming: Library, replace: Boolean): ImportSummary {
        var summary = ImportSummary(0, 0)
        update { current ->
            if (replace) {
                val shelfIds = incoming.shelves.map { it.id }.toSet()
                summary = ImportSummary(incoming.books.size, incoming.shelves.size)
                incoming.copy(
                    version = Library.CURRENT_VERSION,
                    books = incoming.books.map { if (it.shelfId in shelfIds) it else it.copy(shelfId = null) },
                )
            } else {
                val shelves = current.shelves.toMutableList()
                val shelfIdMap = mutableMapOf<String, String>()
                var newShelves = 0
                for (s in incoming.shelves) {
                    val match = shelves.firstOrNull { it.id == s.id }
                        ?: shelves.firstOrNull { it.name.equals(s.name, ignoreCase = true) }
                    if (match != null) {
                        shelfIdMap[s.id] = match.id
                    } else {
                        shelves += s
                        shelfIdMap[s.id] = s.id
                        newShelves++
                    }
                }
                val existingIds = current.books.map { it.id }.toSet()
                val added = incoming.books
                    .filter { it.id !in existingIds }
                    .map { it.copy(shelfId = it.shelfId?.let(shelfIdMap::get)) }
                val notes = when {
                    incoming.notes.isBlank() || current.notes.contains(incoming.notes.trim()) -> current.notes
                    current.notes.isBlank() -> incoming.notes
                    else -> current.notes.trimEnd() + "\n\n" + incoming.notes.trim()
                }
                summary = ImportSummary(added.size, newShelves)
                current.copy(books = current.books + added, shelves = shelves, notes = notes)
            }
        }
        return summary
    }

    private suspend fun update(block: (Library) -> Library) {
        context.libraryDataStore.edit { prefs ->
            val raw = prefs[KEY_LIBRARY_JSON]
            // If the stored blob exists but can't be parsed, refuse to write:
            // building on the empty fallback would silently replace years of
            // recommendations with nothing. Reads still show the fallback.
            val existing = if (raw.isNullOrBlank()) Library() else decode(raw) ?: return@edit
            prefs[KEY_LIBRARY_JSON] = json.encodeToString(Library.serializer(), block(existing))
        }
        // The home-screen widget shows current reads, which almost any edit
        // can change. Cheap broadcast; the receiver re-renders off the main
        // thread.
        runCatching {
            dev.matejgroombridge.readinglist.widget.ReadingWidgetReceiver.broadcastRefresh(context)
        }
    }

    private fun load(raw: String?): Library {
        // No stored value means a fresh install: start with the default
        // shelves. A stored library with zero shelves is the user's choice
        // and is respected.
        if (raw.isNullOrBlank()) return Library()
        return decode(raw) ?: Library()
    }

    private fun decode(raw: String): Library? =
        runCatching { json.decodeFromString(Library.serializer(), raw) }.getOrNull()

    private companion object {
        val KEY_LIBRARY_JSON = stringPreferencesKey("library_json")
    }
}
