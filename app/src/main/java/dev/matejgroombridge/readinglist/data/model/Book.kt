package dev.matejgroombridge.readinglist.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Where an item sits in the reading pipeline. Serialised by name, so never
 * rename an entry — only append new ones.
 */
@Serializable
enum class ReadingStatus(val label: String) {
    WantToRead("To Read"),
    Reading("Reading"),
    Read("Read"),
    Abandoned("Didn't Finish"),
}

/**
 * How keen you are on a To Read item. [Now] is the old Up Next star: those
 * items sit on the Reading tab. Stored as two booleans on [Book] ([Book.upNext],
 * [Book.someday]) so older exports keep their Up Next flags.
 */
enum class Interest(val label: String) {
    Now("Now"),
    Interested("Interested"),
    Someday("Someday"),
}

/**
 * A book, or an author to explore ("all robert greene books"). Older exports
 * may contain kinds that have since been removed (series, topics, articles,
 * lists); the decoder coerces those to [Book].
 */
@Serializable
enum class ItemKind(val label: String, val plural: String) {
    Book("Book", "Books"),
    Author("Author", "Authors"),
}

/**
 * One item on the reading list.
 *
 * Schema notes (same contract as the rest of the app family):
 *  - The repository decodes with `ignoreUnknownKeys = true` and every field
 *    except [title] has a default, so older exports keep loading as fields
 *    are added or removed.
 *  - Dates the user thinks about as days ([startedOn], [finishedOn]) are
 *    `LocalDate.toEpochDay()` values. [addedAt] is epoch millis so items
 *    added on the same day still sort by insertion.
 *
 * @param shelfId       The item's genre (stored as a "shelf" for compatibility).
 * @param recommendedBy Who or what suggested it ("Angela", "Modern Wisdom").
 * @param reason        Why it was recommended — the context that's easy to
 *                      forget and the main thing this app exists to keep.
 * @param coverUrl      Filled from Open Library; never typed.
 * @param pageCount     Filled from Open Library; enables page progress.
 * @param addedAt       Epoch millis, or 0 when unknown (imported items whose
 *                      original date was never written down).
 * @param upNext        Interest "Now": queued to read next, shown on the Reading tab.
 * @param someday       Interest "Maybe someday": ranks lower on To Read.
 * @param duelRating    Elo-style rating from "This or That" duels; 1000 = untested.
 * @param duels         How many duels the item has been in.
 * @param publicRating  Open Library community average (0 = unknown).
 * @param rating        0 = unrated, otherwise 1..5.
 * @param review        Takeaways written when finishing.
 */
@Serializable
data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val kind: ItemKind = ItemKind.Book,
    val status: ReadingStatus = ReadingStatus.WantToRead,
    val shelfId: String? = null,
    val recommendedBy: String = "",
    val reason: String = "",
    val coverUrl: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val publishedYear: Int = 0,
    val upNext: Boolean = false,
    val someday: Boolean = false,
    val duelRating: Double = DEFAULT_DUEL_RATING,
    val duels: Int = 0,
    val publicRating: Double = 0.0,
    val publicRatingCount: Int = 0,
    val rating: Int = 0,
    val review: String = "",
    val addedAt: Long = 0L,
    val startedOn: Long? = null,
    val finishedOn: Long? = null,
    val archived: Boolean = false,
) {
    /** 0..1, or null when there's no page count to measure against. */
    val progress: Float?
        get() = if (pageCount > 0) (currentPage.toFloat() / pageCount).coerceIn(0f, 1f) else null

    val hasRecommendation: Boolean get() = recommendedBy.isNotBlank() || reason.isNotBlank()

    val interest: Interest
        get() = when {
            upNext -> Interest.Now
            someday -> Interest.Someday
            else -> Interest.Interested
        }

    fun withInterest(level: Interest): Book =
        copy(upNext = level == Interest.Now, someday = level == Interest.Someday)

    companion object {
        const val DEFAULT_DUEL_RATING = 1000.0
    }

    /**
     * Returns a copy moved to [newStatus] with the date bookkeeping that goes
     * with it, so every entry point (overview, long-press menu, editor,
     * widget) agrees on what "start" or "finish" means.
     */
    fun withStatus(newStatus: ReadingStatus, today: Long, finishedOn: Long? = null): Book = when (newStatus) {
        ReadingStatus.WantToRead -> copy(
            status = newStatus,
            startedOn = null,
            finishedOn = null,
            currentPage = 0,
        )
        ReadingStatus.Reading -> copy(
            status = newStatus,
            // Re-reading a finished book starts a fresh read; resuming an
            // abandoned one keeps the original start date.
            startedOn = if (status == ReadingStatus.Read || startedOn == null) today else startedOn,
            finishedOn = null,
            currentPage = if (status == ReadingStatus.Read) 0 else currentPage,
            upNext = false,
        )
        ReadingStatus.Read -> copy(
            status = newStatus,
            finishedOn = finishedOn ?: today,
            currentPage = if (pageCount > 0) pageCount else currentPage,
            upNext = false,
        )
        ReadingStatus.Abandoned -> copy(
            status = newStatus,
            finishedOn = finishedOn ?: today,
            upNext = false,
        )
    }
}

/**
 * Initial values for a new item — from a share intent, a search query, or
 * the FAB on a particular tab.
 */
data class BookPrefill(
    val title: String = "",
    val author: String = "",
    val reason: String = "",
    val status: ReadingStatus = ReadingStatus.WantToRead,
    val upNext: Boolean = false,
    val someday: Boolean = false,
)
