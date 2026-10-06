package dev.matejgroombridge.readinglist.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.model.Shelf
import dev.matejgroombridge.readinglist.data.network.BookLookup
import dev.matejgroombridge.readinglist.data.network.BookSuggestion
import dev.matejgroombridge.readinglist.data.repository.ImportSummary
import dev.matejgroombridge.readinglist.data.repository.LibraryRepository
import dev.matejgroombridge.readinglist.domain.TextMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Snapshot of the library for the UI. The per-status lists are computed
 * once per emission here so the three pager pages don't each re-filter.
 */
data class LibraryUiState(
    val library: Library = Library(),
    /** False until the first DataStore read lands — avoids an empty-state flash on launch. */
    val loaded: Boolean = false,
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
) {
    val active: List<Book> = library.books.filterNot { it.archived }
    val toRead: List<Book> = active.filter { it.status == ReadingStatus.WantToRead && !it.upNext }
    /** Queued To Read items — shown on the Reading tab, not in To Read. */
    val upNext: List<Book> = active.filter { it.status == ReadingStatus.WantToRead && it.upNext }
    val reading: List<Book> = active.filter { it.status == ReadingStatus.Reading }
    val abandoned: List<Book> = active.filter { it.status == ReadingStatus.Abandoned }
    val archived: List<Book> = library.books.filter { it.archived }
    val recommenders: List<String> = library.recommenders()

    fun book(id: String): Book? = library.books.firstOrNull { it.id == id }
}

/** Progress of the "Fetch Missing Details" job. */
data class EnrichProgress(
    val done: Int,
    val total: Int,
    val updated: Int,
    val finished: Boolean = false,
)

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val lookup: BookLookup = BookLookup(),
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = repository.library
        .map { LibraryUiState(library = it, loaded = true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun addBook(book: Book) {
        viewModelScope.launch { repository.addBook(book) }
    }

    fun addBooks(books: List<Book>) {
        viewModelScope.launch { repository.addBooks(books) }
    }

    fun updateBook(id: String, transform: (Book) -> Book) {
        viewModelScope.launch { repository.updateBook(id, transform) }
    }

    fun setStatus(id: String, status: ReadingStatus) {
        viewModelScope.launch { repository.setStatus(id, status) }
    }

    /** Marks [id] read, recording the optional rating and takeaways from the Finished dialog. */
    fun finish(id: String, finishedOn: Long, rating: Int, review: String) {
        viewModelScope.launch {
            repository.updateBook(id) { b ->
                b.withStatus(ReadingStatus.Read, LocalDate.now().toEpochDay(), finishedOn)
                    .copy(rating = rating.coerceIn(0, 5), review = review.trim().ifEmpty { b.review })
            }
        }
    }

    fun setProgress(id: String, page: Int) {
        viewModelScope.launch { repository.setProgress(id, page) }
    }

    fun setRating(id: String, rating: Int) = updateBook(id) { it.copy(rating = rating.coerceIn(0, 5)) }

    fun toggleUpNext(id: String) = updateBook(id) { it.copy(upNext = !it.upNext, someday = false) }

    fun setInterest(id: String, level: dev.matejgroombridge.readinglist.data.model.Interest) =
        updateBook(id) { it.withInterest(level) }

    fun recordDuel(winnerId: String, loserId: String) {
        viewModelScope.launch { repository.recordDuel(winnerId, loserId) }
    }

    fun setArchived(id: String, archived: Boolean) = updateBook(id) { it.copy(archived = archived, upNext = false) }

    fun deleteBook(id: String) {
        viewModelScope.launch { repository.deleteBook(id) }
    }

    fun addShelf(shelf: Shelf) {
        viewModelScope.launch { repository.addShelf(shelf) }
    }

    fun updateShelf(shelf: Shelf) {
        viewModelScope.launch { repository.updateShelf(shelf) }
    }

    fun deleteShelf(id: String) {
        viewModelScope.launch { repository.deleteShelf(id) }
    }

    fun moveShelf(id: String, delta: Int) {
        viewModelScope.launch { repository.moveShelf(id, delta) }
    }

    fun setNotes(notes: String) {
        viewModelScope.launch { repository.setNotes(notes) }
    }

    /** Online suggestions for the editor. Empty on any failure. */
    suspend fun search(query: String): List<BookSuggestion> = lookup.search(query)

    suspend fun exportJson(): String? = runCatching { repository.exportJson() }.getOrNull()

    fun parseImport(raw: String): Library? = repository.parseImport(raw)

    suspend fun importLibrary(library: Library, replace: Boolean): ImportSummary =
        repository.importLibrary(library, replace)

    // --- Fetch Missing Details --------------------------------------------

    private val _enrich = MutableStateFlow<EnrichProgress?>(null)
    val enrich: StateFlow<EnrichProgress?> = _enrich.asStateFlow()
    private var enrichJob: Job? = null

    /** Books that are missing a cover, page count, year or community rating. */
    fun enrichCandidates(library: Library = uiState.value.library): List<Book> = library.books.filter {
        !it.archived && it.title.isNotBlank() &&
            it.kind == ItemKind.Book &&
            (it.coverUrl.isBlank() || it.pageCount == 0 || it.publishedYear == 0 || it.publicRatingCount == 0)
    }

    /**
     * Walks [enrichCandidates] one at a time, applying only confident Open
     * Library matches (see [BookLookup.confidentMatch]). What the user typed
     * is never overwritten except to tidy a title's capitalisation or fill a
     * blank author. Paced at well under one request a second — this is a
     * free community API.
     *
     * Runs in the ViewModel scope, so it keeps going if the user leaves
     * Settings; the activity-scoped ViewModel owns it.
     */
    fun startEnrich() {
        if (enrichJob?.isActive == true) return
        enrichJob = viewModelScope.launch {
            val candidates = enrichCandidates(repository.library.first())
            var updated = 0
            _enrich.value = EnrichProgress(0, candidates.size, 0)
            candidates.forEachIndexed { i, book ->
                val match = lookup.confidentMatch(book.title, book.author)
                if (match != null) {
                    var changed = false
                    repository.updateBook(book.id) { b ->
                        val next = b.copy(
                            title = if (b.title != match.title && TextMatch.titleKey(b.title) == TextMatch.titleKey(match.title)) match.title else b.title,
                            author = b.author.ifBlank { match.author },
                            coverUrl = b.coverUrl.ifBlank { match.coverUrl },
                            pageCount = if (b.pageCount > 0) b.pageCount else match.pageCount,
                            publishedYear = if (b.publishedYear > 0) b.publishedYear else match.publishedYear,
                            publicRating = if (b.publicRatingCount > 0) b.publicRating else match.ratingAverage,
                            publicRatingCount = if (b.publicRatingCount > 0) b.publicRatingCount else match.ratingCount,
                        )
                        changed = next != b
                        next
                    }
                    if (changed) updated++
                }
                _enrich.value = EnrichProgress(i + 1, candidates.size, updated)
                delay(ENRICH_DELAY_MS)
            }
            _enrich.value = EnrichProgress(candidates.size, candidates.size, updated, finished = true)
        }
    }

    fun cancelEnrich() {
        enrichJob?.cancel()
        _enrich.value = _enrich.value?.copy(finished = true)
    }

    fun clearEnrichResult() {
        if (enrichJob?.isActive != true) _enrich.value = null
    }

    companion object {
        private const val ENRICH_DELAY_MS = 1_100L

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(LibraryRepository(application.applicationContext)) }
        }
    }
}
