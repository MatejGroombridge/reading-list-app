package dev.matejgroombridge.readinglist.data.model

/**
 * One shelf section. [colorKey] indexes the shared palette in
 * `ui.theme.BookColors`; [key] is what gets persisted on a [Book].
 *
 * Genres are addressed by string key rather than by enum ordinal or list
 * index so this catalogue can be re-ordered or extended later without
 * invalidating anyone's saved shelf — unknown keys fall back to [Genres.unsorted].
 */
data class Genre(
    val key: String,
    val label: String,
    val colorKey: String,
)

/**
 * The curated genre catalogue.
 *
 * Sized deliberately: broad enough that a mixed fiction/non-fiction list
 * lands somewhere sensible, small enough that the shelf doesn't fragment
 * into twenty one-book sections. Order here is the order sections appear on
 * the Library screen — fiction first, then non-fiction, then Unsorted last.
 */
object Genres {

    const val UNSORTED_KEY = "unsorted"

    val catalog: List<Genre> = listOf(
        // ── Fiction ──────────────────────────────────────────────
        Genre("literary-fiction", "Literary Fiction", "blush"),
        Genre("sci-fi", "Science Fiction", "sky"),
        Genre("fantasy", "Fantasy", "lavender"),
        Genre("mystery-thriller", "Mystery & Thriller", "fog"),
        Genre("horror", "Horror", "fog"),
        Genre("romance", "Romance", "blush"),
        Genre("historical-fiction", "Historical Fiction", "peach"),
        Genre("classics", "Classics", "butter"),
        Genre("comics", "Comics & Graphic Novels", "peach"),
        Genre("poetry", "Poetry", "lavender"),

        // ── Non-fiction ──────────────────────────────────────────
        Genre("biography", "Biography & Memoir", "peach"),
        Genre("history", "History", "butter"),
        Genre("science", "Science & Nature", "mint"),
        Genre("technology", "Technology", "sky"),
        Genre("philosophy", "Philosophy", "fog"),
        Genre("psychology", "Psychology", "lavender"),
        Genre("self-improvement", "Self Improvement", "mint"),
        Genre("business", "Business & Economics", "teal"),
        Genre("politics", "Politics & Society", "teal"),
        Genre("true-crime", "True Crime", "fog"),
        Genre("health", "Health & Fitness", "mint"),
        Genre("religion", "Religion & Spirituality", "butter"),
        Genre("art", "Art & Design", "blush"),
        Genre("travel", "Travel", "teal"),
        Genre("cooking", "Food & Cooking", "peach"),

        // ── Fallback ─────────────────────────────────────────────
        Genre(UNSORTED_KEY, "Unsorted", "fog"),
    )

    private val byKey: Map<String, Genre> = catalog.associateBy { it.key }

    val unsorted: Genre get() = byKey.getValue(UNSORTED_KEY)

    fun entry(key: String): Genre = byKey[key] ?: unsorted

    /** Catalogue order, used to sort the Library screen's sections. */
    fun sortIndex(key: String): Int =
        catalog.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: catalog.size
}
