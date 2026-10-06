package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.settings.GroupBy
import dev.matejgroombridge.readinglist.data.settings.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class LibraryQueriesTest {

    private val today = LocalDate.of(2026, 10, 5).toEpochDay()

    @Test
    fun `recent sort uses added time, then list order for unknown dates`() {
        val a = Book(id = "a", title = "A")
        val b = Book(id = "b", title = "B")
        val c = Book(id = "c", title = "C", addedAt = 1_000)
        val lib = Library(books = listOf(a, b, c))
        assertEquals(listOf("c", "b", "a"), LibraryQueries.sort(lib.books, SortOrder.Recent, lib).map { it.id })
        assertEquals(listOf("a", "b", "c"), LibraryQueries.sort(lib.books, SortOrder.Oldest, lib).map { it.id })
    }

    @Test
    fun `title sort ignores leading articles`() {
        val lib = Library(books = listOf(Book(title = "The Stranger"), Book(title = "Dune"), Book(title = "A Void")))
        assertEquals(listOf("Dune", "The Stranger", "A Void"), LibraryQueries.sort(lib.books, SortOrder.Title, lib).map { it.title })
    }

    @Test
    fun `ungrouped list is one headerless section`() {
        val lib = Library(books = listOf(Book(title = "X"), Book(title = "Y")))
        val sections = LibraryQueries.toReadSections(lib.books, GroupBy.None, lib)
        assertEquals(listOf<String?>(null), sections.map { it.title })
        assertEquals(2, sections.single().books.size)
    }

    @Test
    fun `genre grouping follows shelf order with ungenred last`() {
        val lib = Library(
            books = listOf(
                Book(title = "1", shelfId = "shelf_fiction"),
                Book(title = "2"),
                Book(title = "3", shelfId = "shelf_self_improvement"),
            ),
        )
        val sections = LibraryQueries.toReadSections(lib.books, GroupBy.Shelf, lib)
        assertEquals(listOf("Self Improvement", "Fiction", "No Genre"), sections.map { it.title })
    }

    @Test
    fun `read sections group by year with undated last`() {
        val lib = Library(
            books = listOf(
                Book(title = "Old", status = ReadingStatus.Read),
                Book(title = "2025", status = ReadingStatus.Read, finishedOn = LocalDate.of(2025, 3, 1).toEpochDay()),
                Book(title = "2026", status = ReadingStatus.Read, finishedOn = today),
            ),
        )
        assertEquals(listOf("2026", "2025", "Earlier"), LibraryQueries.readSections(lib).map { it.title })
    }

    @Test
    fun `pick for me skips leads and never returns the excluded item when there is a choice`() {
        val lib = Library(
            books = listOf(
                Book(id = "a", title = "A"),
                Book(id = "b", title = "B"),
                Book(id = "author", title = "Some Author", kind = ItemKind.Author),
                Book(id = "done", title = "Done", status = ReadingStatus.Read),
            ),
        )
        repeat(50) {
            val pick = LibraryQueries.pickRandom(lib, exclude = "a", random = Random(it))
            assertEquals("b", pick?.id)
        }
        assertNull(LibraryQueries.pickRandom(Library(books = emptyList())))
    }

    @Test
    fun `status moves keep the dates consistent`() {
        val book = Book(title = "Dune", pageCount = 400)
        val started = book.withStatus(ReadingStatus.Reading, today)
        assertEquals(today, started.startedOn)

        val finished = started.copy(currentPage = 120).withStatus(ReadingStatus.Read, today + 10)
        assertEquals(today + 10, finished.finishedOn)
        assertEquals(400, finished.currentPage)

        val reread = finished.withStatus(ReadingStatus.Reading, today + 20)
        assertEquals(today + 20, reread.startedOn)
        assertNull(reread.finishedOn)
        assertEquals(0, reread.currentPage)

        val shelved = reread.withStatus(ReadingStatus.WantToRead, today + 21)
        assertNull(shelved.startedOn)
    }

    @Test
    fun `recommenders are ordered by count`() {
        val lib = Library(
            books = listOf(
                Book(title = "1", recommendedBy = "Jack"),
                Book(title = "2", recommendedBy = "Angela"),
                Book(title = "3", recommendedBy = "angela"),
            ),
        )
        assertEquals(listOf("angela", "Jack"), lib.recommenders())
    }
}
