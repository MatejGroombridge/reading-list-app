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
 * Not everything on a reading list is a single book. The Notion page this
 * app replaces had authors to explore ("all robert greene books"), topics
 * ("choose one 1800s tycoon to study"), articles and other people's lists.
 * Giving them a kind keeps them on the same list without pretending they're
 * books.
 */
@Serializable
enum class ItemKind(val label: String, val plural: String) {
    Book("Book", "Books"),
    Series("Series", "Series"),
    Author("Author", "Authors"),
    Topic("Topic", "Topics"),
    Article("Article", "Articles"),
    List("Reading List", "Reading Lists"),
}

@Serializable
enum class BookFormat(val label: String) {
    Any("Any"),
    Print("Print"),
    Ebook("Ebook"),
    Audio("Audio"),
}

/**
 * One item on the reading list.
 *
 * Schema notes (same contract as the rest of the app family):
 *  - The repository decodes with `ignoreUnknownKeys = true` and every field
 *    except [title] has a default, so older exports keep loading as fields
 *    are added.
 *  - Dates the user thinks about as days ([startedOn], [finishedOn]) are
 *    `LocalDate.toEpochDay()` values. [addedAt] is epoch millis so items
 *    added on the same day still sort by insertion.
 *
 * @param recommendedBy Who or what suggested it ("Angela", "Modern Wisdom").
 * @param reason        Why it was recommended — the context that's easy to
 *                      forget and the main thing this app exists to keep.
 * @param notes         Anything else: edition tips, reading advice, spoilers
 *                      to avoid.
 * @param addedAt       Epoch millis, or 0 when unknown (e.g. imported items
 *                      whose original date was never written down).
 * @param upNext        Pinned to the top of To Read.
 * @param toAcquire     "Need a copy" — still has to be bought or downloaded.
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
    val notes: String = "",
    val url: String = "",
    val coverUrl: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val publishedYear: Int = 0,
    val format: BookFormat = BookFormat.Any,
    val upNext: Boolean = false,
    val toAcquire: Boolean = false,
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
            toAcquire = false,
        )
        ReadingStatus.Abandoned -> copy(
            status = newStatus,
            finishedOn = finishedOn ?: today,
            upNext = false,
        )
    }
}

/**
 * Initial values for a new item — from a share intent, an online lookup
 * pick, or the FAB on a particular tab.
 */
data class BookPrefill(
    val title: String = "",
    val author: String = "",
    val url: String = "",
    val reason: String = "",
    val status: ReadingStatus = ReadingStatus.WantToRead,
)
