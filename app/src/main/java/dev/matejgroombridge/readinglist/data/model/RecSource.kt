package dev.matejgroombridge.readinglist.data.model

/**
 * Where a recommendation came from. The free-text note on [Book.recSource]
 * carries the detail ("Struthless — why you procrastinate"); this is just the
 * coarse bucket, so the shelf can show a recognisable badge and the Library
 * screen can filter by it.
 */
data class RecSource(
    val key: String,
    val label: String,
)

object RecSources {

    const val UNKNOWN_KEY = "unknown"

    val catalog: List<RecSource> = listOf(
        RecSource("youtube", "YouTube"),
        RecSource("podcast", "Podcast"),
        RecSource("friend", "Friend"),
        RecSource("social", "Social Media"),
        RecSource("article", "Article"),
        RecSource("bookshop", "Bookshop"),
        RecSource("another-book", "Another Book"),
        RecSource(UNKNOWN_KEY, "Somewhere Else"),
    )

    private val byKey: Map<String, RecSource> = catalog.associateBy { it.key }

    val unknown: RecSource get() = byKey.getValue(UNKNOWN_KEY)

    fun entry(key: String): RecSource = byKey[key] ?: unknown
}
