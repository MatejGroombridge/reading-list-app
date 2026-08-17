package dev.matejgroombridge.readinglist.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire types for the Open Library API.
 *
 * Every field is nullable with a default. That isn't defensive
 * over-engineering — Open Library records are user-contributed and genuinely
 * incomplete: plenty of works have no author, no cover, no year, and obscure
 * ones carry little beyond a title. Modelling any field as required would
 * turn a normal gap in the catalogue into a parse failure.
 */
@Serializable
data class SearchResponseDto(
    val numFound: Int = 0,
    val docs: List<SearchDocDto> = emptyList(),
)

/**
 * One work-level search hit.
 *
 * @param key Work key in `/works/OL45804W` form — [workId] strips the prefix.
 */
@Serializable
data class SearchDocDto(
    val key: String? = null,
    val title: String? = null,
    @SerialName("author_name") val authorName: List<String>? = null,
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    @SerialName("cover_i") val coverId: Long? = null,
    val subject: List<String>? = null,
    @SerialName("number_of_pages_median") val pageCount: Int? = null,
    @SerialName("ratings_average") val ratingsAverage: Double? = null,
    @SerialName("ratings_count") val ratingsCount: Int? = null,
    @SerialName("edition_count") val editionCount: Int? = null,
) {
    /** `/works/OL45804W` → `OL45804W`, or null when the record has no key. */
    val workId: String? get() = key?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

/**
 * Work detail, fetched lazily when the user opens a book.
 *
 * [description] is the awkward one: Open Library returns it as either a bare
 * string or a `{"type": "/type/text", "value": "..."}` object depending on
 * how the record was edited. [descriptionText] normalises both shapes, which
 * is why the field is typed as a raw [JsonElement].
 */
@Serializable
data class WorkDetailDto(
    val description: JsonElement? = null,
    val subjects: List<String>? = null,
) {
    val descriptionText: String
        get() = when (val d = description) {
            is JsonPrimitive -> d.content
            is JsonObject -> d["value"]?.jsonPrimitive?.content.orEmpty()
            else -> ""
        }
}
