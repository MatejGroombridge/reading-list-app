package dev.matejgroombridge.readinglist.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.Genre
import dev.matejgroombridge.readinglist.data.model.Genres
import dev.matejgroombridge.readinglist.data.model.RecSource
import dev.matejgroombridge.readinglist.data.model.RecSources
import dev.matejgroombridge.readinglist.data.model.ShelfStatus
import dev.matejgroombridge.readinglist.data.network.OpenLibraryApi
import dev.matejgroombridge.readinglist.data.repository.BookRepository
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.data.settings.SettingsRepository
import dev.matejgroombridge.readinglist.data.settings.ShelfSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One genre heading plus the books filed under it. */
data class GenreSection(
    val genre: Genre,
    val books: List<Book>,
)

/** Headline numbers for the Read screen. */
data class ReadStats(
    val booksRead: Int = 0,
    val pagesRead: Int = 0,
    val readThisYear: Int = 0,
    val averageRating: Double = 0.0,
    val topGenre: Genre? = null,
)

/**
 * Active filters on the Library screen.
 *
 * @param query      Free text matched against title and author.
 * @param sourceKind When set, limits the shelf to books from one
 *                   recommendation source — "show me everything I got from
 *                   YouTube".
 */
data class ShelfFilter(
    val query: String = "",
    val sourceKind: String? = null,
) {
    val isActive: Boolean get() = query.isNotBlank() || sourceKind != null
}

data class LibraryUiState(
    /** Books currently being read — pinned above the shelf. */
    val reading: List<Book> = emptyList(),
    /** Want-to-read books, grouped and sorted for display. */
    val sections: List<GenreSection> = emptyList(),
    /** Total want-to-read books before filtering. */
    val wantToReadCount: Int = 0,
    /** Want-to-read books remaining after the active filter. */
    val filteredCount: Int = 0,
    val filter: ShelfFilter = ShelfFilter(),
    /**
     * Recommendation sources actually present on the shelf, so the filter row
     * only offers buckets that would return something.
     */
    val availableSources: List<RecSource> = emptyList(),
    /** The priority queue, in user order. */
    val upNext: List<Book> = emptyList(),
    val read: List<Book> = emptyList(),
    val stats: ReadStats = ReadStats(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
) {
    val isEmpty: Boolean get() = reading.isEmpty() && sections.isEmpty() && read.isEmpty()
}

/**
 * Owns the user's shelf: the grouping/sorting that turns a flat book list
 * into the Library, Up Next and Read screens, plus every mutation the UI can
 * trigger.
 *
 * Grouping lives here rather than in the composables because it depends on
 * both the shelf and user settings, and recomputing it per recomposition
 * would be wasteful — [uiState] recombines only when one of those actually
 * changes.
 */
class LibraryViewModel(
    private val repository: BookRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val today: Long get() = LocalDate.now().toEpochDay()

    /**
     * Set when a book is marked read, so the screen can fire confetti. The UI
     * consumes it immediately; it exists as state rather than a callback so a
     * finish triggered from the detail sheet still celebrates after the sheet
     * closes.
     */
    private val _celebrate = MutableStateFlow(false)
    val celebrate: StateFlow<Boolean> = _celebrate.asStateFlow()

    private val _filter = MutableStateFlow(ShelfFilter())

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.books, settingsRepository.settings, _filter) { books, settings, filter ->
            buildState(books, settings, filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    private fun buildState(
        books: List<Book>,
        settings: Settings,
        filter: ShelfFilter,
    ): LibraryUiState {
        val wantToRead = books.filter { it.status == ShelfStatus.WantToRead }
        val filtered = wantToRead.filter { it.matches(filter) }
        val read = books.filter { it.status == ShelfStatus.Read }
            // Most recently finished first — the Read screen reads as a diary.
            .sortedWith(compareByDescending<Book> { it.finishedAtEpochDay ?: 0L }.thenBy { it.title })

        return LibraryUiState(
            reading = books.filter { it.status == ShelfStatus.Reading }
                .filter { it.matches(filter) }
                .sortedBy { it.startedAtEpochDay ?: Long.MAX_VALUE },
            sections = buildSections(filtered, settings),
            wantToReadCount = wantToRead.size,
            filteredCount = filtered.size,
            filter = filter,
            availableSources = availableSources(books),
            upNext = books.filter { it.priorityRank != null }.sortedBy { it.priorityRank },
            read = read,
            stats = buildStats(read),
            todayEpochDay = today,
        )
    }

    /**
     * Recommendation sources present on the unfinished shelf, in catalogue
     * order. Offering a chip that filters to nothing is worse than offering
     * no chip, so this is derived from the data rather than the full
     * catalogue.
     */
    private fun availableSources(books: List<Book>): List<RecSource> {
        val present = books
            .filter { it.status != ShelfStatus.Read }
            .map { it.recSourceKind }
            .toSet()
        return RecSources.catalog.filter { it.key in present }
    }

    /**
     * Splits want-to-read books into display sections.
     *
     * With grouping off the whole shelf becomes one unnamed section, which
     * keeps the screen's rendering path identical either way.
     */
    private fun buildSections(books: List<Book>, settings: Settings): List<GenreSection> {
        if (books.isEmpty()) return emptyList()
        val sorted = books.sortedWith(comparatorFor(settings.shelfSort))

        if (!settings.groupByGenre) {
            return listOf(GenreSection(FLAT_GENRE, sorted))
        }

        val grouped = sorted.groupBy { it.genreKey }
            .map { (key, entries) -> GenreSection(Genres.entry(key), entries) }
            .sortedBy { Genres.sortIndex(it.genre.key) }

        if (!settings.mergeSmallSections) return grouped

        // Fold every one-book genre into a single trailing section so a
        // varied list doesn't fragment into a dozen headings with one book
        // under each. Unsorted stays separate — it means something different
        // ("we couldn't tell") and the user acts on it differently.
        val (singles, rest) = grouped.partition {
            it.books.size == 1 && it.genre.key != Genres.UNSORTED_KEY
        }
        if (singles.size < 2) return grouped

        val merged = GenreSection(
            genre = ODDS_AND_ENDS,
            books = singles.flatMap { it.books }.sortedWith(comparatorFor(settings.shelfSort)),
        )
        val unsortedLast = rest.sortedBy { Genres.sortIndex(it.genre.key) }
        return unsortedLast + merged
    }

    private fun comparatorFor(sort: ShelfSort): Comparator<Book> = when (sort) {
        ShelfSort.RecentlyAdded ->
            compareByDescending<Book> { it.addedAtEpochDay }.thenBy { it.title.lowercase() }
        ShelfSort.Title ->
            compareBy { it.title.sortableTitle() }
        ShelfSort.Author ->
            compareBy<Book> { it.authors.firstOrNull()?.lowercase() ?: "￿" }
                .thenBy { it.title.lowercase() }
        // Unknown page counts sort last: "shortest first" is for picking a
        // quick read, and a book of unknown length isn't a candidate.
        ShelfSort.Shortest ->
            compareBy<Book> { it.pageCount ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }
    }

    private fun buildStats(read: List<Book>): ReadStats {
        if (read.isEmpty()) return ReadStats()
        val thisYearStart = LocalDate.now().withDayOfYear(1).toEpochDay()
        val rated = read.filter { it.rating > 0 }
        val topGenreKey = read.groupingBy { it.genreKey }.eachCount()
            .maxByOrNull { it.value }?.key

        return ReadStats(
            booksRead = read.size,
            pagesRead = read.sumOf { it.pageCount ?: 0 },
            readThisYear = read.count { (it.finishedAtEpochDay ?: 0L) >= thisYearStart },
            averageRating = if (rated.isEmpty()) 0.0 else rated.sumOf { it.rating }.toDouble() / rated.size,
            topGenre = topGenreKey?.let { Genres.entry(it) },
        )
    }

    // ── Filtering ────────────────────────────────────────────────

    fun setFilterQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    /** Passing the already-selected source clears the filter, so the chip toggles. */
    fun setSourceFilter(sourceKind: String?) {
        val current = _filter.value.sourceKind
        _filter.value = _filter.value.copy(
            sourceKind = if (sourceKind == current) null else sourceKind,
        )
    }

    fun clearFilter() {
        _filter.value = ShelfFilter()
    }

    // ── Mutations ────────────────────────────────────────────────

    fun add(book: Book) {
        viewModelScope.launch { repository.add(book, today) }
    }

    fun remove(bookId: String) {
        viewModelScope.launch { repository.remove(bookId) }
    }

    fun setStatus(bookId: String, status: ShelfStatus) {
        viewModelScope.launch {
            repository.setStatus(bookId, status, today)
            if (status == ShelfStatus.Read) _celebrate.value = true
        }
    }

    /** Called by the screen once the confetti burst has been kicked off. */
    fun consumeCelebration() {
        _celebrate.value = false
    }

    fun setRating(bookId: String, rating: Int) {
        viewModelScope.launch { repository.setRating(bookId, rating) }
    }

    fun setRecSource(bookId: String, source: String, kind: String) {
        viewModelScope.launch { repository.setRecSource(bookId, source, kind) }
    }

    fun setNotes(bookId: String, notes: String) {
        viewModelScope.launch { repository.setNotes(bookId, notes) }
    }

    /**
     * Tops up a book from the work endpoint when its detail sheet is opened:
     * the description if we don't hold one, and subject tags if the search
     * result arrived without any (which would otherwise leave the book stuck
     * in Unsorted). One request, only when something is actually missing.
     *
     * Silent on failure — the sheet renders fine without a blurb, and an
     * error toast for optional flavour text would be noise.
     */
    fun ensureDetail(book: Book) {
        val needsDescription = book.description.isBlank()
        val needsSubjects = book.subjects.isEmpty()
        if (!needsDescription && !needsSubjects) return
        viewModelScope.launch {
            val detail = OpenLibraryApi.workDetail(book.id) ?: return@launch
            if (needsDescription) repository.cacheDescription(book.id, detail.descriptionText)
            if (needsSubjects) repository.backfillSubjects(book.id, detail.subjects.orEmpty())
        }
    }

    fun setGenreOverride(bookId: String, genreKey: String?) {
        viewModelScope.launch { repository.setGenreOverride(bookId, genreKey) }
    }

    fun setPrioritised(bookId: String, prioritised: Boolean) {
        viewModelScope.launch { repository.setPrioritised(bookId, prioritised) }
    }

    fun movePriority(bookId: String, delta: Int) {
        viewModelScope.launch { repository.movePriority(bookId, delta) }
    }

    fun movePriorityToTop(bookId: String) {
        viewModelScope.launch { repository.movePriorityToTop(bookId) }
    }

    suspend fun reclassifyAll(): Int = repository.reclassifyAll()

    suspend fun exportJson(): String? = runCatching { repository.exportJson() }.getOrNull()

    suspend fun importJson(rawJson: String): Int? = repository.importJson(rawJson)

    companion object {
        /** Stand-in heading used when genre grouping is switched off. */
        private val FLAT_GENRE = Genre("all", "All Books", "fog")

        /** Display-only bucket for merged one-book genres; never persisted. */
        private val ODDS_AND_ENDS = Genre("odds-ends", "Odds & Ends", "fog")

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    repository = BookRepository(application.applicationContext),
                    settingsRepository = SettingsRepository(application.applicationContext),
                )
            }
        }
    }
}

/**
 * Whether a book survives the active filter.
 *
 * The text query also matches the recommendation note, so searching
 * "struthless" surfaces every book from that channel even when the source
 * bucket is just "YouTube".
 */
private fun Book.matches(filter: ShelfFilter): Boolean {
    if (filter.sourceKind != null && recSourceKind != filter.sourceKind) return false
    val query = filter.query.trim()
    if (query.isEmpty()) return true
    return title.contains(query, ignoreCase = true) ||
        authors.any { it.contains(query, ignoreCase = true) } ||
        recSource.contains(query, ignoreCase = true)
}

/**
 * Title with a leading article dropped, so "The Dispossessed" files under D
 * where a reader would look for it rather than under T.
 */
private fun String.sortableTitle(): String {
    val lower = lowercase().trim()
    for (article in listOf("the ", "a ", "an ")) {
        if (lower.startsWith(article)) return lower.removePrefix(article)
    }
    return lower
}
