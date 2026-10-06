package dev.matejgroombridge.readinglist.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextParserTest {

    @Test
    fun `title by author`() {
        val p = SharedTextParser.parse("Dune by Frank Herbert")
        assertEquals("Dune", p.title)
        assertEquals("Frank Herbert", p.author)
    }

    @Test
    fun `last by wins so titles containing by survive`() {
        val (title, author) = SharedTextParser.splitTitleAuthor("Stand by Me by Stephen King")
        assertEquals("Stand by Me", title)
        assertEquals("Stephen King", author)
    }

    @Test
    fun `title dash author matches the notion habit`() {
        val p = SharedTextParser.parse("the stranger - camus")
        assertEquals("the stranger", p.title)
        assertEquals("camus", p.author)
    }

    @Test
    fun `link is pulled out and the rest parsed`() {
        val p = SharedTextParser.parse("Check out Dune by Frank Herbert on Goodreads: https://www.goodreads.com/book/show/44767458-dune")
        assertEquals("Dune", p.title)
        assertEquals("Frank Herbert", p.author)
    }

    @Test
    fun `bare goodreads link guesses title from the slug`() {
        val p = SharedTextParser.parse("https://www.goodreads.com/book/show/44767458-dune")
        assertEquals("dune", p.title)
    }

    @Test
    fun `bare amazon link guesses from the path before dp`() {
        val p = SharedTextParser.parse("https://www.amazon.com/Dune-Frank-Herbert/dp/0441172717/ref=sr_1_1")
        assertEquals("Dune Frank Herbert", p.title)
    }

    @Test
    fun `a long message becomes the reason`() {
        val msg = "You have to read the one about the Antarctic expedition — the leadership lessons " +
            "are incredible and it reads like a thriller, honestly one of my favourites ever."
        val p = SharedTextParser.parse(msg)
        assertEquals("", p.title)
        assertEquals(msg, p.reason)
    }

    @Test
    fun `noise prefixes only strip whole words`() {
        assertEquals("Ready Player One", SharedTextParser.parse("Ready Player One").title)
        assertEquals("Dune", SharedTextParser.parse("You should read: Dune").title)
    }

    @Test
    fun `blank input gives an empty prefill`() {
        assertEquals("", SharedTextParser.parse("  ").title)
        assertEquals("", SharedTextParser.parse(null).title)
    }
}
