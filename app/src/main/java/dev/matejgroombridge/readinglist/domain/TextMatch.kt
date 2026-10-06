package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Book
import java.text.Normalizer

/**
 * Fuzzy-but-predictable text comparison shared by search, duplicate
 * detection, sorting and the online lookup. Everything goes through
 * [normalize] so "Gödel", "godel" and "GÖDEL" behave the same, and a
 * lowercase title typed in a hurry still matches its tidy counterpart.
 */
object TextMatch {

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val LEADING_ARTICLE = Regex("^(the|a|an) ")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace("&", " and ")
            .replace(NON_ALNUM, " ")
            .trim()

    /** [normalize] minus a leading "the/a/an", for title comparison and A–Z sorting. */
    fun titleKey(title: String): String = normalize(title).replace(LEADING_ARTICLE, "")

    /** Sort key for authors: surname first, so "Frank Herbert" files under H. */
    fun authorKey(author: String): String {
        val first = author.split("&", ",", " and ").first()
        val tokens = normalize(first).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return "￿" // blank authors sink to the end
        return (listOf(tokens.last()) + tokens.dropLast(1)).joinToString(" ")
    }

    /**
     * Whether two titles name the same work. Exact after normalising, or
     * equal once a subtitle after ':' is dropped from either side —
     * "Endurance: Shackleton's Incredible Voyage" agrees with "Endurance".
     * Deliberately no prefix matching: "Dune" must not agree with
     * "Dune Messiah".
     */
    fun titlesAgree(a: String, b: String): Boolean {
        val ka = titleKey(a)
        val kb = titleKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        val mainA = titleKey(a.substringBefore(':'))
        val mainB = titleKey(b.substringBefore(':'))
        return mainA == kb || ka == mainB || (mainA.isNotEmpty() && mainA == mainB && (':' in a || ':' in b))
    }

    /** True when the two author strings share a name token of 3+ letters (usually the surname). */
    fun authorsOverlap(a: String, b: String): Boolean {
        val ta = normalize(a).split(' ').filter { it.length >= 3 }.toSet()
        val tb = normalize(b).split(' ').filter { it.length >= 3 }.toSet()
        return ta.isNotEmpty() && ta.any { it in tb }
    }

    /** Every query word appears somewhere in the item's text fields. */
    fun matches(book: Book, query: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = normalize(
            listOf(book.title, book.author, book.recommendedBy, book.reason, book.notes, book.review)
                .joinToString(" "),
        )
        return words.all { it in haystack }
    }

    /** An existing, non-archived item with the same title, ignoring [excludeId]. */
    fun findDuplicate(books: List<Book>, title: String, excludeId: String? = null): Book? {
        if (titleKey(title).length < 2) return null
        return books.firstOrNull { it.id != excludeId && !it.archived && titlesAgree(it.title, title) }
    }
}
