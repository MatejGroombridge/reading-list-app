package dev.matejgroombridge.readinglist.data.settings

import dev.matejgroombridge.readinglist.ui.theme.ThemeMode

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a mapping
 * in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** When [ThemeMode] resolves to dark, render with pure black backgrounds. */
    val amoled: Boolean = false,
    /**
     * When `true` the user can swipe horizontally between Up Next / Library /
     * Read. When `false` the pager only responds to bottom-bar taps.
     */
    val swipeToNavigate: Boolean = true,
    /**
     * Master switch for genre grouping. On (the default) the Library screen
     * splits into collapsible genre sections; off gives one flat list for
     * users who'd rather scan everything at once.
     */
    val groupByGenre: Boolean = true,
    /** Ordering applied inside each shelf section. */
    val shelfSort: ShelfSort = ShelfSort.Default,
    /**
     * Hide genre sections holding a single book behind one shared "Odds &
     * Ends" heading. Stops a long, varied list from fragmenting into a dozen
     * one-book headings.
     */
    val mergeSmallSections: Boolean = false,
    /** Confetti when a book is marked as read. The app's one celebration. */
    val celebrateFinishes: Boolean = true,
)
