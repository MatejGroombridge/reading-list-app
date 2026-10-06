package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Orders the To Read list by how much you probably want to read each item.
 * Three signals, each worth a bounded number of points so no single one can
 * swamp the rest:
 *
 *  - **Duels** (±~40): your own "this or that" picks, as an Elo rating. The
 *    strongest signal once a book has a few duels behind it.
 *  - **Freshness** (+15 → 0 over [FRESH_DAYS]): something you just added is
 *    something you're excited about right now.
 *  - **Staleness** (0 → −[MAX_AGE_PENALTY] over years): books that have sat
 *    on the list for years drift down. Unknown add dates count as old.
 *
 * "Maybe someday" items take a flat penalty so they settle below the rest.
 */
object Ranking {

    const val FRESH_DAYS = 30.0
    private const val FRESH_BONUS = 15.0
    private const val AGE_PENALTY_PER_YEAR = 6.0
    const val MAX_AGE_PENALTY = 24.0
    /** Imported items with no recorded date are treated as this many years old. */
    private const val UNKNOWN_AGE_YEARS = 3.0
    private const val SOMEDAY_PENALTY = 30.0
    private const val DAY_MS = 86_400_000.0

    /** Elo sensitivity: how far one duel moves a rating. */
    private const val K = 32.0

    fun score(book: Book, nowMillis: Long): Double {
        val duel = (book.duelRating - Book.DEFAULT_DUEL_RATING) / 10.0


        val ageDays = if (book.addedAt > 0) max(0.0, (nowMillis - book.addedAt) / DAY_MS) else UNKNOWN_AGE_YEARS * 365
        val freshness = if (ageDays < FRESH_DAYS) FRESH_BONUS * (1 - ageDays / FRESH_DAYS) else 0.0
        val staleness = -min(MAX_AGE_PENALTY, ageDays / 365.0 * AGE_PENALTY_PER_YEAR)

        val someday = if (book.someday) -SOMEDAY_PENALTY else 0.0
        return duel + freshness + staleness + someday
    }

    /** [books] best-first; ties keep their incoming order. */
    fun rank(books: List<Book>, nowMillis: Long = System.currentTimeMillis()): List<Book> =
        books.sortedByDescending { score(it, nowMillis) }

    /** New ratings for a duel won by [winner] over [loser] (standard Elo). */
    fun afterDuel(winner: Double, loser: Double): Pair<Double, Double> {
        val expectedWin = 1.0 / (1.0 + 10.0.pow((loser - winner) / 400.0))
        val delta = K * (1.0 - expectedWin)
        return (winner + delta) to (loser - delta)
    }

    /** Items eligible for duels: To Read books not already queued as "Now". */
    fun duelPool(library: Library): List<Book> = library.books.filter {
        !it.archived && it.status == ReadingStatus.WantToRead && it.kind == ItemKind.Book && !it.upNext
    }

    /**
     * Picks the next pair. The first book is drawn from the least-duelled
     * items (so everything gets rated), the second from those closest in
     * rating to it (close matches are the informative ones). [avoid] skips
     * the pair just shown.
     */
    fun nextPair(pool: List<Book>, avoid: Set<String> = emptySet(), random: Random = Random.Default): Pair<Book, Book>? {
        if (pool.size < 2) return null
        val minDuels = pool.minOf { it.duels }
        val fresh = pool.filter { it.duels <= minDuels + 1 }
        val first = (fresh.filter { it.id !in avoid }.ifEmpty { fresh }).random(random)
        val rivals = pool.filter { it.id != first.id }
            .sortedBy { kotlin.math.abs(it.duelRating - first.duelRating) }
            .take(RIVAL_WINDOW)
        val second = (rivals.filter { it.id !in avoid }.ifEmpty { rivals }).random(random)
        return if (random.nextBoolean()) first to second else second to first
    }

    private const val RIVAL_WINDOW = 8
}
