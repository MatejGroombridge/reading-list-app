package dev.matejgroombridge.readinglist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the paste parser against the shapes people actually write reading
 * lists in. This is the migration path off a plain document, so mangling a
 * line here means a book silently fails to import.
 */
class ImportParseTest {

    @Test
    fun `strips bullets, numbering and quotes`() {
        val raw = """
            - The Dispossessed
            * Sapiens
            • Project Hail Mary
            1. Dune
            2) Neuromancer
            3 - Blindsight
            "The Left Hand of Darkness"
        """.trimIndent()

        assertEquals(
            listOf(
                "The Dispossessed",
                "Sapiens",
                "Project Hail Mary",
                "Dune",
                "Neuromancer",
                "Blindsight",
                "The Left Hand of Darkness",
            ),
            ImportViewModel.parseLines(raw),
        )
    }

    @Test
    fun `keeps title and author together as one query`() {
        // Open Library's search handles the combined string well, and the
        // author disambiguates common titles — so this must not be split.
        assertEquals(
            listOf("The Dispossessed by Ursula K. Le Guin"),
            ImportViewModel.parseLines("The Dispossessed by Ursula K. Le Guin"),
        )
    }

    @Test
    fun `drops blank lines and stray punctuation`() {
        val raw = "Dune\n\n   \n-\n.\nSapiens\n"
        assertEquals(listOf("Dune", "Sapiens"), ImportViewModel.parseLines(raw))
    }

    @Test
    fun `deduplicates case-insensitively`() {
        val raw = "Dune\ndune\nDUNE\nSapiens"
        assertEquals(listOf("Dune", "Sapiens"), ImportViewModel.parseLines(raw))
    }

    @Test
    fun `handles a messy real-world document`() {
        val raw = """
            Books to read:

            1. Sapiens - Yuval Noah Harari
            2. "Atomic Habits"
            - Project Hail Mary by Andy Weir

            * The Body Keeps the Score
        """.trimIndent()

        val parsed = ImportViewModel.parseLines(raw)
        // The "Books to read:" heading is indistinguishable from a title, so
        // it survives parsing — the review step is where the user drops it.
        assertTrue(parsed.contains("Sapiens - Yuval Noah Harari"))
        assertTrue(parsed.contains("Atomic Habits"))
        assertTrue(parsed.contains("Project Hail Mary by Andy Weir"))
        assertTrue(parsed.contains("The Body Keeps the Score"))
    }

    @Test
    fun `empty input yields nothing`() {
        assertEquals(emptyList<String>(), ImportViewModel.parseLines(""))
        assertEquals(emptyList<String>(), ImportViewModel.parseLines("   \n\n  "))
    }

    @Test
    fun `a numbered title starting with a digit is not eaten`() {
        // "1984" must survive the leading-number strip, which only removes a
        // number followed by a separator.
        assertEquals(listOf("1984"), ImportViewModel.parseLines("1984"))
        assertEquals(listOf("1984"), ImportViewModel.parseLines("1. 1984"))
    }
}
