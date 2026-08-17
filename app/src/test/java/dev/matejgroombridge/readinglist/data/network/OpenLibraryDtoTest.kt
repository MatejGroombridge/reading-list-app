package dev.matejgroombridge.readinglist.data.network

import dev.matejgroombridge.readinglist.domain.GenreClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes a **real, unmodified** Open Library search response against the
 * DTOs, using the same [HttpClientProvider.json] configuration the app runs.
 *
 * The payload below was captured from
 * `openlibrary.org/search.json?q=dune herbert&fields=...` — only the docs
 * array was trimmed to one entry and its subject list shortened. Nothing was
 * reshaped, so a field rename upstream fails here rather than silently
 * returning an empty shelf on someone's phone.
 */
class OpenLibraryDtoTest {

    private val realResponse = """
        {
          "numFound": 258,
          "start": 0,
          "numFoundExact": true,
          "num_found": 258,
          "documentation_url": "https://openlibrary.org/dev/docs/api/search",
          "q": "dune herbert",
          "offset": null,
          "docs": [
            {
              "author_name": ["Frank Herbert", "Френк Герберт"],
              "cover_i": 11481354,
              "edition_count": 160,
              "first_publish_year": 1965,
              "key": "/works/OL893414W",
              "number_of_pages_median": 605,
              "title": "Dune",
              "subject": [
                "Dune (Imaginary place)",
                "Fiction",
                "Fiction, science fiction, general",
                "Dune (imaginary place), fiction",
                "New York Times reviewed",
                "Science fiction",
                "Science-fiction",
                "American literature"
              ],
              "ratings_average": 4.3061223,
              "ratings_count": 441
            }
          ]
        }
    """.trimIndent()

    private fun decode(json: String): SearchResponseDto =
        HttpClientProvider.json.decodeFromString(SearchResponseDto.serializer(), json)

    @Test
    fun `decodes a real search response`() {
        val response = decode(realResponse)

        assertEquals(258, response.numFound)
        assertEquals(1, response.docs.size)

        val doc = response.docs.first()
        assertEquals("Dune", doc.title)
        assertEquals("/works/OL893414W", doc.key)
        assertEquals(listOf("Frank Herbert", "Френк Герберт"), doc.authorName)
        assertEquals(1965, doc.firstPublishYear)
        assertEquals(11481354L, doc.coverId)
        assertEquals(605, doc.pageCount)
        assertEquals(160, doc.editionCount)
        assertEquals(441, doc.ratingsCount)
        assertEquals(8, doc.subject?.size)
    }

    @Test
    fun `unknown top-level fields do not break decoding`() {
        // The live response carries num_found, documentation_url, q, offset
        // and a null — none of which the DTO declares. Decoding above proves
        // ignoreUnknownKeys and explicitNulls are configured correctly; this
        // pins it against someone "tidying up" the Json config later.
        val withNewField = realResponse.replace(
            "\"numFound\": 258,",
            "\"numFound\": 258, \"some_future_field\": {\"nested\": true},",
        )
        assertEquals(258, decode(withNewField).numFound)
    }

    @Test
    fun `work key is stripped to a bare id`() {
        assertEquals("OL893414W", decode(realResponse).docs.first().workId)
    }

    @Test
    fun `a sparse record still decodes`() {
        // Plenty of Open Library works have nothing but a key and a title.
        val sparse = """{"numFound":1,"docs":[{"key":"/works/OL1W","title":"Untitled"}]}"""
        val doc = decode(sparse).docs.first()

        assertEquals("Untitled", doc.title)
        assertNull(doc.authorName)
        assertNull(doc.coverId)
        assertNull(doc.subject)
        assertEquals("OL1W", doc.workId)
    }

    @Test
    fun `an empty result set decodes to no docs`() {
        assertEquals(0, decode("""{"numFound":0,"start":0,"docs":[]}""").docs.size)
    }

    @Test
    fun `real subject tags classify as science fiction`() {
        // End-to-end over genuine catalogue noise: "Dune (Imaginary place)"
        // and "New York Times reviewed" sit alongside the real genre tags.
        val subjects = decode(realResponse).docs.first().subject.orEmpty()
        assertEquals("sci-fi", GenreClassifier.classify(subjects))
    }

    @Test
    fun `work detail description decodes from both shapes`() {
        // Open Library returns description as a bare string on some records
        // and a {type, value} object on others, depending on how the record
        // was last edited.
        val asString = """{"description":"A desert planet."}"""
        val asObject = """{"description":{"type":"/type/text","value":"A desert planet."}}"""
        val missing = """{"subjects":["Science fiction"]}"""

        fun detail(json: String) =
            HttpClientProvider.json.decodeFromString(WorkDetailDto.serializer(), json)

        assertEquals("A desert planet.", detail(asString).descriptionText)
        assertEquals("A desert planet.", detail(asObject).descriptionText)
        assertTrue(detail(missing).descriptionText.isEmpty())
        assertEquals(listOf("Science fiction"), detail(missing).subjects)
    }
}
