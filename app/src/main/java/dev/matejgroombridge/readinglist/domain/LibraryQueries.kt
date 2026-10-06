package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.settings.GroupBy
import dev.matejgroombridge.readinglist.data.settings.SortOrder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** One filter chip on the To Read page. Single-select, so it's a sealed set. */
sealed interface ListFilter {
    data object All : ListFilter
    data object UpNext : ListFilter
    data object ToGet : ListFilter
    data class OnShelf(val shelfId: String) : ListFilter
    data class OfKind(val kind: ItemKind) : ListFilter

    fun accepts(book: Book): Boolean = when (this) {
        All -> true
        UpNext -> book.upNext
        ToGet -> book.toAcquire
        is OnShelf -> book.shelfId == shelfId
        is OfKind -> book.kind == kind
    }
}

/** A titled run of books, for sectioned lists. A null [title] means "no header". */
data class BookSection(val key: String, val title: String?, val books: List<Book>)

object LibraryQueries {

    private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun active(library: Library, status: ReadingStatus): List<Book> =
        library.books.filter { !it.archived && it.status == status }

    /**
     * Sorts [books] by [order]. [Library.books] is in insertion order, so the
     * original index breaks ties — items with no known added date (imports)
     * then keep the order they were written down in.
     */
    fun sort(books: List<Book>, order: SortOrder, library: Library): List<Book> {
        val index = library.books.withIndex().associate { it.value.id to it.index }
        val byRecency = compareBy<Book>({ it.addedAt }, { index[it.id] ?: 0 })
        return when (order) {
            SortOrder.Recent -> books.sortedWith(byRecency.reversed())
            SortOrder.Oldest -> books.sortedWith(byRecency)
            SortOrder.Title -> books.sortedBy { TextMatch.titleKey(it.title) }
            SortOrder.Author -> books.sortedWith(
                compareBy<Book>({ TextMatch.authorKey(it.author) }, { TextMatch.titleKey(it.title) }),
            )
        }
    }

    /**
     * Builds the To Read sections. With no grouping, Up Next items are lifted
     * into their own section at the top — the replacement for the Notion
     * page's "2026:" priority list. With grouping, Up Next items stay in their
     * group (they still show their star).
     */
    fun toReadSections(
        books: List<Book>,
        groupBy: GroupBy,
        library: Library,
    ): List<BookSection> = when (groupBy) {
        GroupBy.None -> {
            val (pinned, rest) = books.partition { it.upNext }
            buildList {
                if (pinned.isNotEmpty()) add(BookSection("up_next", "Up Next", pinned))
                if (rest.isNotEmpty()) add(BookSection("rest", if (pinned.isNotEmpty()) "Everything Else" else null, rest))
            }
        }
        GroupBy.Shelf -> {
            val byShelf = books.groupBy { it.shelfId?.takeIf { id -> library.shelf(id) != null } }
            library.shelves.mapNotNull { shelf ->
                byShelf[shelf.id]?.let { BookSection(shelf.id, shelf.name, it) }
            } + listOfNotNull(byShelf[null]?.let { BookSection("no_shelf", "No Shelf", it) })
        }
        GroupBy.Recommender -> {
            // Same case-insensitive identity as Library.recommenders(), in
            // that "most recommendations first" order.
            val grouped = books.groupBy { it.recommendedBy.trim().lowercase() }
            val names = library.recommenders()
            names.mapNotNull { name ->
                grouped[name.lowercase()]?.let { BookSection("from_${name.lowercase()}", "From $name", it) }
            } + listOfNotNull(grouped[""]?.let { BookSection("from_none", "No Recommender", it) })
        }
        GroupBy.MonthAdded -> {
            // Section order follows the books' current sort.
            books.groupBy { monthLabel(it.addedAt) }.map { (label, list) -> BookSection("month_$label", label, list) }
        }
        GroupBy.Kind -> ItemKind.entries.mapNotNull { kind ->
            books.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let {
                BookSection("kind_${kind.name}", kind.plural, it)
            }
        }
    }

    /** Finished books grouped by year, most recent first, undated last. */
    fun readSections(library: Library): List<BookSection> {
        val read = active(library, ReadingStatus.Read)
        val index = library.books.withIndex().associate { it.value.id to it.index }
        val (dated, undated) = read.partition { it.finishedOn != null }
        val years = dated
            .sortedWith(compareByDescending<Book> { it.finishedOn }.thenByDescending { index[it.id] ?: 0 })
            .groupBy { LocalDate.ofEpochDay(it.finishedOn!!).year }
            .map { (year, list) -> BookSection("year_$year", year.toString(), list) }
        return years + listOfNotNull(
            undated.takeIf { it.isNotEmpty() }?.let { BookSection("year_unknown", "Earlier", it) },
        )
    }

    /**
     * "Pick for Me": a random To Read item, with Up Next items three times as
     * likely. Authors, topics and other people's lists are skipped — they're
     * leads to explore, not something to start tonight. [exclude] avoids
     * immediately re-picking the one just shown.
     */
    fun pickRandom(library: Library, exclude: String? = null, random: Random = Random.Default): Book? {
        val candidates = active(library, ReadingStatus.WantToRead)
            .filter { it.kind == ItemKind.Book || it.kind == ItemKind.Series }
        val pool = candidates.filter { it.id != exclude }.ifEmpty { candidates }
        if (pool.isEmpty()) return null
        val weights = pool.map { if (it.upNext) 3 else 1 }
        var roll = random.nextInt(weights.sum())
        pool.forEachIndexed { i, book ->
            roll -= weights[i]
            if (roll < 0) return book
        }
        return pool.last()
    }

    fun monthLabel(addedAt: Long): String =
        if (addedAt <= 0) "Date Unknown"
        else Instant.ofEpochMilli(addedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(MONTH_FORMAT)
}
