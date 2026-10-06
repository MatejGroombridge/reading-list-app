package dev.matejgroombridge.readinglist.domain

/**
 * Parses a pasted block of text — a friend's list, a section copied out of
 * Notes or Notion — into title/author pairs, one per line.
 *
 * Tolerates the formatting people actually paste: bullets, numbering,
 * checkboxes, blank lines. Lines ending in ':' are treated as headings
 * ("from angela:") and skipped, as are lines that are only a link. A
 * trailing "(…)" aside — "a void - perec (no letter e)" — becomes the
 * item's note rather than part of the author's name.
 */
object BulkAddParser {

    data class Entry(val title: String, val author: String, val note: String)

    private val LIST_MARKER = Regex("^\\s*(?:[-*•·–—]|\\d+[.)]|\\[[ xX]?])\\s*")
    private val BARE_URL = Regex("^https?://\\S+$")
    private val TRAILING_ASIDE = Regex("\\s*\\(([^()]*)\\)\\s*$")

    fun parse(text: String): List<Entry> = text.lines()
        .map { it.replace(LIST_MARKER, "").trim() }
        .filter { it.isNotEmpty() && !it.endsWith(':') && !BARE_URL.matches(it) }
        .mapNotNull { line ->
            val aside = TRAILING_ASIDE.find(line)
            val note = aside?.groupValues?.get(1)?.trim().orEmpty()
            val main = if (aside != null) line.substring(0, aside.range.first).trim() else line
            if (main.isEmpty()) return@mapNotNull null
            val (title, author) = SharedTextParser.splitTitleAuthor(main)
            Entry(title = title, author = author, note = note)
        }
}
