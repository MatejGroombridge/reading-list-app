package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.model.Shelf
import java.time.LocalDate

/** Headline numbers for the top of the Read tab. */
data class ReadingStats(
    val year: Int,
    val readThisYear: Int,
    val readAllTime: Int,
    val pagesThisYear: Int,
    val averageRating: Double?,
    /** Shelf → finished count, biggest first. Unshelved books are left out. */
    val byShelf: List<Pair<Shelf, Int>>,
) {
    companion object {
        /**
         * Only books and series count towards totals — finishing an article
         * or "exploring an author" shouldn't move a books-per-year goal.
         */
        fun from(library: Library, today: LocalDate = LocalDate.now()): ReadingStats {
            val read = library.books.filter {
                !it.archived && it.status == ReadingStatus.Read &&
                    (it.kind == ItemKind.Book || it.kind == ItemKind.Series)
            }
            val thisYear = read.filter { b -> b.finishedOn?.let { LocalDate.ofEpochDay(it).year == today.year } == true }
            val rated = read.filter { it.rating > 0 }
            val counts = read.groupingBy { it.shelfId }.eachCount()
            return ReadingStats(
                year = today.year,
                readThisYear = thisYear.size,
                readAllTime = read.size,
                pagesThisYear = thisYear.sumOf { it.pageCount },
                averageRating = rated.takeIf { it.isNotEmpty() }?.map { it.rating }?.average(),
                byShelf = library.shelves
                    .mapNotNull { shelf -> counts[shelf.id]?.let { shelf to it } }
                    .sortedByDescending { it.second },
            )
        }
    }
}
