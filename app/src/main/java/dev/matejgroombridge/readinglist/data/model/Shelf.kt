package dev.matejgroombridge.readinglist.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A user-defined category ("Fiction", "Theology"). Shelves carry the same
 * identity pair a habit does in Habit Tracker — an icon key and a palette
 * colour key — and every book on the shelf takes on that colour.
 *
 * Shelf order is the order of [Library.shelves].
 */
@Serializable
data class Shelf(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconKey: String = "book",
    val colorKey: String = "fog",
)

object DefaultShelves {
    // Stable ids so a library exported from one install and merged into
    // another lines up shelf-for-shelf instead of duplicating them. Seeded
    // from the categories the Notion "Books I've Read" section already used.
    val all: List<Shelf> = listOf(
        Shelf(id = "shelf_self_improvement", name = "Self Improvement", iconKey = "rocket", colorKey = "peach"),
        Shelf(id = "shelf_business", name = "Business", iconKey = "trending", colorKey = "butter"),
        Shelf(id = "shelf_writing", name = "Writing", iconKey = "edit", colorKey = "mint"),
        Shelf(id = "shelf_fiction", name = "Fiction", iconKey = "stories", colorKey = "lavender"),
        Shelf(id = "shelf_philosophy", name = "Philosophy & Spirituality", iconKey = "psychology", colorKey = "sky"),
        Shelf(id = "shelf_theology", name = "Theology", iconKey = "church", colorKey = "blush"),
        Shelf(id = "shelf_history", name = "History & Biography", iconKey = "history", colorKey = "teal"),
        Shelf(id = "shelf_science", name = "Science", iconKey = "science", colorKey = "mint"),
        Shelf(id = "shelf_other", name = "Other", iconKey = "book", colorKey = "fog"),
    )
}
