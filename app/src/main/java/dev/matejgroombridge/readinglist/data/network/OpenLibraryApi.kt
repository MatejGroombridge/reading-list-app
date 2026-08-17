package dev.matejgroombridge.readinglist.data.network

import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.domain.GenreClassifier
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments

/**
 * Read-only client for the Open Library API.
 *
 * Open Library rather than Google Books, for two reasons that matter for an
 * app distributed as a personal APK: it needs no API key (Google Books now
 * refuses keyless traffic, and a key can't be committed to a public repo or
 * shipped in an APK safely), and it has no per-day quota. The trade-off is
 * messier genre metadata, which [GenreClassifier] exists to clean up.
 *
 * Docs: https://openlibrary.org/dev/docs/api/search
 */
object OpenLibraryApi {

    private const val BASE_URL = "https://openlibrary.org"

    /**
     * Only the fields the app actually renders. The unfiltered search
     * response includes every edition, IA identifier and full-text blob Open
     * Library holds for a work — hundreds of KB per page — so narrowing this
     * is the difference between a snappy search and a visible stall.
     */
    private const val SEARCH_FIELDS =
        "key,title,author_name,first_publish_year,cover_i,subject," +
            "number_of_pages_median,ratings_average,ratings_count,edition_count"

    /**
     * Searches works matching [query].
     *
     * Results come back as [Book]s already run through the genre classifier,
     * so the shelf section is known before the user taps Add and the card can
     * show its genre in the search list.
     *
     * Throws on network/HTTP failure; callers surface that as a retryable
     * error state.
     */
    suspend fun search(query: String, limit: Int = 25): List<Book> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val response: SearchResponseDto = HttpClientProvider.client
            .get(BASE_URL) {
                url { appendPathSegments("search.json") }
                parameter("q", trimmed)
                parameter("limit", limit)
                parameter("fields", SEARCH_FIELDS)
            }
            .body()

        return response.docs.mapNotNull { it.toBookOrNull() }
    }

    /**
     * Fetches the long description and full subject list for a work. Returns
     * null on any failure — the description is a nice-to-have, so a flaky
     * network should leave the detail sheet showing everything else rather
     * than an error.
     */
    suspend fun workDetail(workId: String): WorkDetailDto? = runCatching {
        HttpClientProvider.client
            .get(BASE_URL) {
                url { appendPathSegments("works", "$workId.json") }
            }
            .body<WorkDetailDto>()
    }.getOrNull()

    /**
     * Maps a search hit onto a [Book], dropping records too thin to show —
     * a missing work key or title means there's nothing to display or store.
     */
    private fun SearchDocDto.toBookOrNull(): Book? {
        val id = workId ?: return null
        val name = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val subjects = subject.orEmpty().take(Book.MAX_SUBJECTS)

        return Book(
            id = id,
            title = name,
            authors = authorName.orEmpty().filter { it.isNotBlank() },
            coverId = coverId,
            firstPublishYear = firstPublishYear,
            pageCount = pageCount?.takeIf { it > 0 },
            subjects = subjects,
            autoGenreKey = GenreClassifier.classify(subjects),
        )
    }
}
