package dev.matejgroombridge.readinglist.data.model

import kotlinx.serialization.Serializable

/**
 * Everything the app persists apart from settings, as one serialisable
 * root. This is also the export/import file format, so [version] lets a
 * future build recognise older files if the shape ever changes in a way
 * defaults can't paper over.
 *
 * @param notes Free-form notebook text — the "read as much as you can…"
 *              asides the Notion page kept between its lists.
 */
@Serializable
data class Library(
    val version: Int = CURRENT_VERSION,
    val books: List<Book> = emptyList(),
    val shelves: List<Shelf> = DefaultShelves.all,
    val notes: String = "",
) {
    fun shelf(id: String?): Shelf? = if (id == null) null else shelves.firstOrNull { it.id == id }

    /**
     * Everyone who has recommended something, most-used first (ties broken by
     * whoever recommended most recently). Names are matched case-insensitively
     * so "angela" and "Angela" count as one person; the most recent spelling
     * wins for display.
     */
    fun recommenders(): List<String> {
        data class Tally(var display: String, var count: Int, var lastAdded: Long, var lastIndex: Int)
        val tallies = LinkedHashMap<String, Tally>()
        books.forEachIndexed { index, book ->
            val name = book.recommendedBy.trim()
            if (name.isEmpty()) return@forEachIndexed
            val key = name.lowercase()
            val tally = tallies.getOrPut(key) { Tally(name, 0, book.addedAt, index) }
            tally.count++
            if (book.addedAt >= tally.lastAdded) {
                tally.lastAdded = book.addedAt
                tally.lastIndex = index
                tally.display = name
            }
        }
        return tallies.values
            .sortedWith(
                compareByDescending<Tally> { it.count }
                    .thenByDescending { it.lastAdded }
                    .thenByDescending { it.lastIndex },
            )
            .map { it.display }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
