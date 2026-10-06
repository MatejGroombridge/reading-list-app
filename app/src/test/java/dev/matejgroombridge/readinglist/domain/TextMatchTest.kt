package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {

    @Test
    fun `titles agree ignoring case, accents, punctuation and leading articles`() {
        assertTrue(TextMatch.titlesAgree("so good they can't ignore you", "So Good They Can't Ignore You"))
        assertTrue(TextMatch.titlesAgree("Godel Escher Bach", "Gödel, Escher, Bach"))
        assertTrue(TextMatch.titlesAgree("Stranger", "The Stranger"))
    }

    @Test
    fun `subtitles are tolerated on either side`() {
        assertTrue(TextMatch.titlesAgree("Endurance: Shackleton's Incredible Voyage", "Endurance"))
        assertTrue(TextMatch.titlesAgree("Endurance", "Endurance: Shackleton's Incredible Voyage"))
    }

    @Test
    fun `different books with a shared prefix do not agree`() {
        assertFalse(TextMatch.titlesAgree("Dune", "Dune Messiah"))
        assertFalse(TextMatch.titlesAgree("It", "It Ends with Us"))
    }

    @Test
    fun `authors overlap on surname`() {
        assertTrue(TextMatch.authorsOverlap("darwin", "Charles Darwin"))
        assertTrue(TextMatch.authorsOverlap("C.S. Lewis", "C. S. Lewis"))
        assertFalse(TextMatch.authorsOverlap("Cal Newport", "Ryan Holiday"))
    }

    @Test
    fun `author sort key files under surname`() {
        assertEquals("herbert frank", TextMatch.authorKey("Frank Herbert"))
        assertEquals("kim w chan", TextMatch.authorKey("W. Chan Kim & Renée Mauborgne"))
    }

    @Test
    fun `search matches every word across fields`() {
        val book = Book(title = "Babel", author = "R. F. Kuang", recommendedBy = "Angela", reason = "Dark academia")
        assertTrue(TextMatch.matches(book, "angela babel"))
        assertTrue(TextMatch.matches(book, "ACADEMIA"))
        assertFalse(TextMatch.matches(book, "angela dune"))
    }

    @Test
    fun `duplicate detection ignores archived items and the item itself`() {
        val dune = Book(id = "a", title = "Dune")
        val archived = Book(id = "b", title = "Babel", archived = true)
        val books = listOf(dune, archived)
        assertEquals(dune, TextMatch.findDuplicate(books, "dune"))
        assertNull(TextMatch.findDuplicate(books, "dune", excludeId = "a"))
        assertNull(TextMatch.findDuplicate(books, "Babel"))
    }
}
