package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.BookPrefill
import java.net.URLDecoder

/**
 * Turns whatever another app shares into a best-guess prefill. Sources vary
 * wildly — a bare Goodreads link, "Dune by Frank Herbert", the user's own
 * Notion habit of "title - author", or a whole message from a friend — so
 * the rules are deliberately simple and the result is always shown in the
 * editor for a final check before saving.
 */
object SharedTextParser {

    private val URL = Regex("https?://\\S+")
    private val BY = Regex("\\s+by\\s+", RegexOption.IGNORE_CASE)
    private val DASH = Regex("\\s+[-–—]\\s+")
    private val NOISE_PREFIX = Regex(
        "^(check out|i'?m reading|i just read|you should read|read|have you read)\\b\\s*:?\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_SITE = Regex(
        "\\s*(on|via|from|\\|)\\s*(goodreads|amazon|storygraph|the storygraph|audible|kindle)\\.?\\s*:?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Anything longer than this reads as a message, not a title. */
    private const val MAX_TITLE_LENGTH = 120

    fun parse(text: String?): BookPrefill {
        if (text.isNullOrBlank()) return BookPrefill()
        val url = URL.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'').orEmpty()
        val rest = text.replace(URL, " ")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .replace(NOISE_PREFIX, "")
            .replace(TRAILING_SITE, "")
            .trim()

        if (rest.isEmpty()) return BookPrefill(title = titleFromUrl(url))

        // A long or multi-line share is someone's message about a book: keep
        // it as the reason and let the user type the title.
        if (rest.length > MAX_TITLE_LENGTH || rest.lines().size > 2) {
            return BookPrefill(title = titleFromUrl(url), reason = rest)
        }

        val (title, author) = splitTitleAuthor(rest.lines().joinToString(" "))
        return BookPrefill(title = title, author = author)
    }

    /**
     * "Title by Author" (last " by ", so "Stand by Me by Stephen King" works)
     * or "Title - Author". Returns the whole string as the title otherwise.
     */
    fun splitTitleAuthor(line: String): Pair<String, String> {
        val trimmed = line.trim()
        BY.findAll(trimmed).lastOrNull()?.let { m ->
            val title = trimmed.substring(0, m.range.first).trim()
            val author = trimmed.substring(m.range.last + 1).trim()
            if (title.isNotEmpty() && author.isNotEmpty() && author.split(' ').size <= 5) {
                return title to author
            }
        }
        DASH.find(trimmed)?.let { m ->
            val title = trimmed.substring(0, m.range.first).trim()
            val author = trimmed.substring(m.range.last + 1).trim()
            if (title.isNotEmpty() && author.isNotEmpty()) return title to author
        }
        return trimmed to ""
    }

    /**
     * Pulls a readable guess out of common book URLs:
     * `goodreads.com/book/show/44767458-dune` → "dune",
     * `amazon.com/Dune-Frank-Herbert/dp/…` → "Dune Frank Herbert".
     * The online lookup usually refines it into a proper title from there.
     */
    fun titleFromUrl(url: String): String {
        if (url.isBlank()) return ""
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        val segments = path.split('/').filter { it.isNotBlank() }
        val slug = when {
            "goodreads.com" in url -> segments.lastOrNull()?.substringAfter('-')?.substringBefore('?')
            "/dp/" in path -> segments.getOrNull(segments.indexOf("dp") - 1)
            else -> null
        } ?: return ""
        val decoded = runCatching { URLDecoder.decode(slug, "UTF-8") }.getOrDefault(slug)
        return decoded.replace('-', ' ').replace('_', ' ').replace('.', ' ').trim()
    }
}
