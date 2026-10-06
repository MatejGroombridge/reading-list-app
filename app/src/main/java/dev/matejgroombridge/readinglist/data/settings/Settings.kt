package dev.matejgroombridge.readinglist.data.settings

import dev.matejgroombridge.readinglist.ui.theme.ThemeMode

/** How the To Read list is split into sections. */
enum class GroupBy(val label: String) {
    None("None"),
    Shelf("Genre"),
    MonthAdded("Month Added"),
    Kind("Type"),
}

enum class SortOrder(val label: String) {
    /** Duels + community rating + freshness − staleness; see domain/Ranking. */
    Ranked("Top Ranked"),
    Recent("Recently Added"),
    Oldest("Oldest First"),
    Title("Title"),
    Author("Author"),
}

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a
 * mapping in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** When [ThemeMode] resolves to dark, render with pure black backgrounds. */
    val amoled: Boolean = false,
    /** Show cover art on cards and in the overview when an item has one. */
    val showCovers: Boolean = true,
    val reminder: ReminderSettings = ReminderSettings(),
    /** Books to finish this calendar year. 0 = no goal (the card is hidden). */
    val yearlyGoal: Int = 0,
    /**
     * Soft cap on books in progress at once. Starting another one past the
     * cap asks first. 0 = no limit.
     */
    val currentReadsLimit: Int = 0,
    /** Same meaning as in Habit Tracker — bottom-bar taps still work when off. */
    val swipeToNavigate: Boolean = true,
    /** Query Open Library for suggestions while typing a title. */
    val onlineLookup: Boolean = true,
    val groupBy: GroupBy = GroupBy.None,
    val sortOrder: SortOrder = SortOrder.Ranked,
)

/**
 * A single daily nudge to read. Local only — scheduled on the device via
 * AlarmManager, see `ReminderScheduler`.
 *
 * @param time "HH:MM" local time.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: String = "21:00",
)
