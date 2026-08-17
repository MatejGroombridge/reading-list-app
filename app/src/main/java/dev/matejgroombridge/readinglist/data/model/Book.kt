package dev.matejgroombridge.readinglist.data.model

import kotlinx.serialization.Serializable

/**
 * Where a book currently sits on the shelf.
 *
 * Serialised by name, so entries may be appended but never reordered or
 * renamed — see the data-migration note on [Book].
 */
@Serializable
enum class ShelfStatus {
    /** On the list, not started. The default landing state after a search. */
    WantToRead,

    /** Started but not finished. Surfaces in its own strip above the shelf. */
    Reading,

    /** Finished. Moves to the Read screen and can carry a rating. */
    Read,
}

/**
 * One book on the user's list.
 *
 * Schema notes (mirrors the family convention):
 *  - The repository decodes with `ignoreUnknownKeys = true` and every field
 *    below has a default, so books saved by an older build always load.
 *  - [id] is the Open Library *work* key with the `/works/` prefix stripped
 *    (e.g. `OL45804W`). Work-level rather than edition-level, so the same
 *    novel found via a paperback and a hardback dedupes to one entry.
 *
 * @param id               Open Library work id, e.g. `OL45804W`. Stable + unique.
 * @param title            Book title as returned by the search API.
 * @param authors          Author names; may be empty for obscure records.
 * @param coverId          Open Library cover id, used to build a cover URL.
 * @param firstPublishYear Year of first publication, when known.
 * @param pageCount        Median page count across editions, when known.
 * @param subjects         Raw Open Library subjects, capped at [MAX_SUBJECTS].
 *                         Retained so books can be re-classified if the
 *                         genre rules improve, without re-hitting the network.
 * @param autoGenreKey     Genre the classifier picked at add time.
 * @param genreOverride    Genre the user picked by hand; wins over the auto
 *                         one. Null means "trust the classifier".
 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val coverId: Long? = null,
    val firstPublishYear: Int? = null,
    val pageCount: Int? = null,
    val description: String = "",
    val subjects: List<String> = emptyList(),
    val autoGenreKey: String = Genres.UNSORTED_KEY,
    val genreOverride: String? = null,
    val status: ShelfStatus = ShelfStatus.WantToRead,
    val addedAtEpochDay: Long = 0L,
    val startedAtEpochDay: Long? = null,
    val finishedAtEpochDay: Long? = null,
    /** 1..5 stars, or 0 when the user hasn't rated it. Only meaningful once read. */
    val rating: Int = 0,
    /**
     * Free text for where the recommendation came from — the whole reason
     * this app exists. "Struthless video on procrastination", "Dad", "that
     * Huberman episode". Kept as prose rather than a structured reference
     * because the useful detail is never the same shape twice.
     */
    val recSource: String = "",
    /** Key into [RecSources]; drives the little badge shown next to [recSource]. */
    val recSourceKind: String = RecSources.UNKNOWN_KEY,
    /** The user's own notes on the book. */
    val notes: String = "",
    /**
     * Position on the Up Next list, or null when the book isn't on it.
     * The repository renumbers these to a dense 0..n-1 range after every
     * mutation, so gaps and duplicates can't accumulate.
     */
    val priorityRank: Int? = null,
) {
    /** The genre this book files under — a manual pick beats the classifier. */
    val genreKey: String get() = genreOverride ?: autoGenreKey

    val genre: Genre get() = Genres.entry(genreKey)

    val isPrioritised: Boolean get() = priorityRank != null

    /** "Ursula K. Le Guin" / "Gaiman & Pratchett" / "Unknown author". */
    val authorLine: String
        get() = when (authors.size) {
            0 -> "Unknown author"
            1 -> authors[0]
            2 -> "${authors[0]} & ${authors[1]}"
            else -> "${authors[0]} +${authors.size - 1}"
        }

    /**
     * Cover art URL at the requested [size] (`S`, `M`, or `L`), or null when
     * Open Library has no cover on file — callers render a lettered
     * placeholder in that case.
     */
    fun coverUrl(size: Char = 'M'): String? =
        coverId?.let { "https://covers.openlibrary.org/b/id/$it-$size.jpg" }

    companion object {
        /**
         * Open Library returns up to several hundred subjects per work, most
         * of them long-tail noise ("Dune (Imaginary place)"). The classifier
         * only reads the leading entries — they're ordered by relevance — so
         * storing more than this wastes space in the JSON blob for nothing.
         */
        const val MAX_SUBJECTS = 40
    }
}
