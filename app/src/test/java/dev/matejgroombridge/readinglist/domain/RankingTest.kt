package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RankingTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `an even duel moves both ratings by half of K, symmetrically`() {
        val (w, l) = Ranking.afterDuel(1000.0, 1000.0)
        assertEquals(1016.0, w, 0.001)
        assertEquals(984.0, l, 0.001)
    }

    @Test
    fun `an upset moves ratings more than an expected win`() {
        val upset = Ranking.afterDuel(900.0, 1100.0).first - 900.0
        val expected = Ranking.afterDuel(1100.0, 900.0).first - 1100.0
        assertTrue(upset > expected)
    }

    @Test
    fun `fresh books outrank identical old ones, and old ones sink with age`() {
        val fresh = Ranking.score(Book(title = "a", addedAt = now - 2 * day), now)
        val year = Ranking.score(Book(title = "b", addedAt = now - 365 * day), now)
        val ancient = Ranking.score(Book(title = "c", addedAt = now - 3650 * day), now)
        assertTrue(fresh > year)
        assertTrue(year > ancient)
        assertEquals(-Ranking.MAX_AGE_PENALTY, ancient, 0.001)
    }

    @Test
    fun `community rating counts more with more ratings`() {
        val few = Ranking.score(Book(title = "a", addedAt = now, publicRating = 4.5, publicRatingCount = 3), now)
        val many = Ranking.score(Book(title = "b", addedAt = now, publicRating = 4.5, publicRatingCount = 5000), now)
        assertTrue(many > few)
    }

    @Test
    fun `someday sits below otherwise identical books`() {
        val a = Book(title = "a", addedAt = now)
        assertTrue(Ranking.score(a, now) > Ranking.score(a.copy(someday = true), now))
    }

    @Test
    fun `duel winners rank above losers`() {
        val books = listOf(Book(id = "x", title = "x", duelRating = 980.0), Book(id = "y", title = "y", duelRating = 1060.0))
        assertEquals("y", Ranking.rank(books, now).first().id)
    }

    @Test
    fun `pairs are two different books from the pool, never Now items`() {
        val lib = Library(
            books = List(6) { Book(id = "b$it", title = "Book $it") } + Book(id = "now", title = "Now", upNext = true),
        )
        val pool = Ranking.duelPool(lib)
        repeat(30) {
            val (a, b) = Ranking.nextPair(pool, random = Random(it))!!
            assertNotEquals(a.id, b.id)
            assertTrue(a.id != "now" && b.id != "now")
        }
    }
}
