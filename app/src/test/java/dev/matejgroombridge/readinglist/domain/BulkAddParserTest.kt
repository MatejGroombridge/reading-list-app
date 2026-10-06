package dev.matejgroombridge.readinglist.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BulkAddParserTest {

    @Test
    fun `parses a pasted notion section`() {
        val text = """
            from angela:
                • flowers for algernon
                • babel
            - a void - perec (no letter e)
            1. Dune by Frank Herbert
            2) The Stranger — Albert Camus
            [x] Blindsight

            https://example.com/some-list
        """.trimIndent()

        val entries = BulkAddParser.parse(text)

        assertEquals(
            listOf(
                BulkAddParser.Entry("flowers for algernon", "", ""),
                BulkAddParser.Entry("babel", "", ""),
                BulkAddParser.Entry("a void", "perec", "no letter e"),
                BulkAddParser.Entry("Dune", "Frank Herbert", ""),
                BulkAddParser.Entry("The Stranger", "Albert Camus", ""),
                BulkAddParser.Entry("Blindsight", "", ""),
            ),
            entries,
        )
    }

    @Test
    fun `empty text gives nothing`() {
        assertEquals(emptyList<BulkAddParser.Entry>(), BulkAddParser.parse("\n\n  \n"))
    }
}
