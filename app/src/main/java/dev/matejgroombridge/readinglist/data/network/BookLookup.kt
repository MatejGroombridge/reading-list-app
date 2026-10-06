package dev.matejgroombridge.readinglist.data.network

import dev.matejgroombridge.readinglist.domain.TextMatch
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A candidate match from Open Library, ready to drop into the editor. */
data class BookSuggestion(
    val title: String,
    val author: String,
    val publishedYear: Int,
    val pageCount: Int,
    val coverUrl: String,
    /** Open Library community rating; 0 when unrated. */
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
)

/**
 * Title/author search against Open Library's public search API — free, no
 * key, and broad enough to cover the philosophy, theology and self-help
 * long tail this list is full of.
 */
class BookLookup(private val client: io.ktor.client.HttpClient = HttpClientProvider.client) {

    /** Returns up to [limit] suggestions, or an empty list on any failure (offline, timeout, bad JSON). */
    suspend fun search(query: String, limit: Int = 6): List<BookSuggestion> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        return try {
            val response = client.get("https://openlibrary.org/search.json") {
                parameter("q", q)
                parameter("limit", limit)
                parameter("fields", FIELDS)
            }
            if (!response.status.isSuccess()) return emptyList()
            response.body<SearchResponse>().docs.map { it.toSuggestion() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * The best suggestion for a title the user already typed, but only when
     * the match is confident enough to apply without asking: the titles must
     * agree (allowing the result to carry a subtitle), and if an author was
     * given the result must share a surname-length token with it. Used by the
     * bulk "Fetch Missing Details" job, where a wrong cover on someone's
     * favourite book is worse than no cover.
     */
    suspend fun confidentMatch(title: String, author: String): BookSuggestion? {
        val results = search(listOf(title, author).filter { it.isNotBlank() }.joinToString(" "), limit = 5)
        return results.firstOrNull { s ->
            TextMatch.titlesAgree(title, s.title) &&
                (author.isBlank() || TextMatch.authorsOverlap(author, s.author))
        }
    }

    @Serializable
    private data class SearchResponse(val docs: List<Doc> = emptyList())

    @Serializable
    private data class Doc(
        val title: String = "",
        @SerialName("author_name") val authorName: List<String> = emptyList(),
        @SerialName("first_publish_year") val firstPublishYear: Int = 0,
        @SerialName("number_of_pages_median") val pages: Int = 0,
        @SerialName("cover_i") val coverId: Long = 0,
        @SerialName("ratings_average") val ratingsAverage: Double = 0.0,
        @SerialName("ratings_count") val ratingsCount: Int = 0,
    ) {
        fun toSuggestion() = BookSuggestion(
            title = title,
            author = authorName.take(2).joinToString(" & "),
            publishedYear = firstPublishYear,
            pageCount = pages,
            coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else "",
            ratingAverage = ratingsAverage,
            ratingCount = ratingsCount,
        )
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        private const val FIELDS =
            "title,author_name,first_publish_year,number_of_pages_median,cover_i,ratings_average,ratings_count"
    }
}
