package dev.matejgroombridge.readinglist.data.settings

/**
 * How books are ordered inside a shelf section.
 *
 * Persisted by `name`, so entries may be appended but never renamed or
 * reordered.
 */
enum class ShelfSort(val label: String) {
    RecentlyAdded("Recently Added"),
    Title("Title"),
    Author("Author"),
    Shortest("Shortest First"),
    ;

    companion object {
        val Default = RecentlyAdded
    }
}
