package dev.matejgroombridge.readinglist.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Date formatting shared by cards, the overview and the editor. */
object Dates {
    private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
    private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun monthYear(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(MONTH_YEAR)

    fun full(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(FULL)

    fun epochDayFromMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /** "today", "yesterday", "12 days ago", then month + year past two months. */
    fun relativeDays(epochDay: Long, today: Long): String {
        val diff = today - epochDay
        return when {
            diff <= 0L -> "today"
            diff == 1L -> "yesterday"
            diff < 60L -> "$diff days ago"
            else -> "in ${monthYear(epochDay)}"
        }
    }

    // Material's DatePicker speaks UTC-midnight millis; these convert to and
    // from local epoch days without an off-by-one in negative UTC offsets.
    fun epochDayToPickerMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerMillisToEpochDay(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
}
