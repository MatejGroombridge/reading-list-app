package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Genres
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the genre rules against realistic Open Library subject arrays.
 *
 * The subject lists below are representative of what the search API actually
 * returns — a couple of real genre tags buried in plot nouns and cataloguing
 * debris — because that noise is the whole reason the classifier exists.
 * These cases are the regression net for the weight table: adding one keyword
 * can quietly tip an unrelated book into the wrong section.
 */
class GenreClassifierTest {

    private fun assertGenre(expected: String, subjects: List<String>) {
        assertEquals(
            "subjects=$subjects",
            expected,
            GenreClassifier.classify(subjects),
        )
    }

    @Test
    fun `science fiction beats the generic science and fiction tags`() {
        assertGenre(
            "sci-fi",
            listOf("Science fiction", "Fiction", "Desert ecology", "Space colonies"),
        )
    }

    @Test
    fun `fantasy fiction files under fantasy not literary fiction`() {
        assertGenre(
            "fantasy",
            listOf("Fantasy fiction", "Fiction", "Middle Earth (Imaginary place)", "Dwarves"),
        )
    }

    @Test
    fun `suspense and mystery tags outvote a leading plain fiction tag`() {
        assertGenre(
            "mystery-thriller",
            listOf("Fiction", "Suspense fiction", "Mystery fiction", "Married people"),
        )
    }

    @Test
    fun `an explicit self-help shelf label beats psychology qualifiers`() {
        // The hard case: Open Library tags habit books heavily with
        // "(Psychology)" qualifiers, which would otherwise outnumber the one
        // tag that actually says which shelf it belongs on.
        assertGenre(
            "self-improvement",
            listOf(
                "Self-actualization (Psychology)",
                "Habit",
                "Change (Psychology)",
                "Self-Help",
            ),
        )
    }

    @Test
    fun `memoirs file under biography`() {
        assertGenre("biography", listOf("Biography", "Memoirs", "Women", "Education"))
    }

    @Test
    fun `popular science files under science not technology`() {
        assertGenre(
            "science",
            listOf("Evolution (Biology)", "Genetics", "Natural selection", "Science"),
        )
    }

    @Test
    fun `programming books file under technology`() {
        assertGenre(
            "technology",
            listOf("Computer software", "Agile software development", "Computer programming"),
        )
    }

    @Test
    fun `history beats historical fiction when there is no fiction marker`() {
        assertGenre(
            "history",
            listOf("Civilization", "Human beings", "History", "Prehistoric peoples"),
        )
    }

    @Test
    fun `historical fiction wins over plain history`() {
        assertGenre(
            "historical-fiction",
            listOf("Historical fiction", "World War, 1939-1945", "Fiction", "Germany"),
        )
    }

    @Test
    fun `true crime wins over the generic crime rule`() {
        assertGenre("true-crime", listOf("True crime", "Murder", "Criminals", "Case studies"))
    }

    @Test
    fun `a bare fiction tag still lands somewhere better than unsorted`() {
        assertGenre("literary-fiction", listOf("Fiction", "American fiction"))
    }

    @Test
    fun `plot nouns alone classify as unsorted rather than guessing`() {
        // Nothing here names a genre. Unsorted is the honest answer — a wrong
        // guess buries the book under a heading the user would never check.
        assertGenre(
            Genres.UNSORTED_KEY,
            listOf("Large type books", "Accessible book", "Protected DAISY"),
        )
    }

    @Test
    fun `an empty subject list is unsorted`() {
        assertGenre(Genres.UNSORTED_KEY, emptyList())
    }

    @Test
    fun `word boundaries stop art matching heart`() {
        // A naive `contains` check would see "art" inside "Heart" and file
        // this under Art & Design. Asserting the positive result as well as
        // the absence keeps the test honest if the medical vocabulary moves.
        val subjects = listOf("Heart", "Cardiovascular system", "Physiology", "Human anatomy")
        val result = GenreClassifier.classify(subjects)
        assertEquals("health", result)
    }

    @Test
    fun `word boundaries stop novel matching novelist`() {
        assertGenre(
            "biography",
            listOf("Novelists, American", "Biography", "Authors"),
        )
    }

    @Test
    fun `punctuation and casing do not affect the result`() {
        assertEquals(
            GenreClassifier.classify(listOf("Science fiction")),
            GenreClassifier.classify(listOf("SCIENCE-FICTION.")),
        )
    }
}
