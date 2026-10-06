package dev.matejgroombridge.readinglist.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.readinglist.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Single source of truth for user preferences. Backed by a Preferences
 * DataStore — one [Preferences.Key] per setting, mapped into a [Settings]
 * snapshot for the UI to consume.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            themeMode = prefs[KEY_THEME_MODE]?.let { parse(it, ThemeMode.System) } ?: ThemeMode.System,
            amoled = prefs[KEY_AMOLED] ?: false,
            showCovers = prefs[KEY_SHOW_COVERS] ?: true,
            reminder = ReminderSettings(
                enabled = prefs[KEY_REMINDER_ENABLED] ?: false,
                time = prefs[KEY_REMINDER_TIME] ?: "21:00",
            ),
            yearlyGoal = (prefs[KEY_YEARLY_GOAL] ?: 0).coerceIn(0, MAX_YEARLY_GOAL),
            currentReadsLimit = (prefs[KEY_CURRENT_READS_LIMIT] ?: 0).coerceIn(0, MAX_CURRENT_READS_LIMIT),
            swipeToNavigate = prefs[KEY_SWIPE_TO_NAVIGATE] ?: true,
            onlineLookup = prefs[KEY_ONLINE_LOOKUP] ?: true,
            groupBy = prefs[KEY_GROUP_BY]?.let { parse(it, GroupBy.None) } ?: GroupBy.None,
            sortOrder = prefs[KEY_SORT_ORDER]?.let { parse(it, SortOrder.Recent) } ?: SortOrder.Recent,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME_MODE] = mode.name }
    suspend fun setAmoled(enabled: Boolean) = edit { it[KEY_AMOLED] = enabled }
    suspend fun setShowCovers(enabled: Boolean) = edit { it[KEY_SHOW_COVERS] = enabled }
    suspend fun setReminderEnabled(enabled: Boolean) = edit { it[KEY_REMINDER_ENABLED] = enabled }
    suspend fun setReminderTime(time: String) = edit { it[KEY_REMINDER_TIME] = time }
    suspend fun setYearlyGoal(goal: Int) = edit { it[KEY_YEARLY_GOAL] = goal.coerceIn(0, MAX_YEARLY_GOAL) }
    suspend fun setCurrentReadsLimit(limit: Int) =
        edit { it[KEY_CURRENT_READS_LIMIT] = limit.coerceIn(0, MAX_CURRENT_READS_LIMIT) }
    suspend fun setSwipeToNavigate(enabled: Boolean) = edit { it[KEY_SWIPE_TO_NAVIGATE] = enabled }
    suspend fun setOnlineLookup(enabled: Boolean) = edit { it[KEY_ONLINE_LOOKUP] = enabled }
    suspend fun setGroupBy(groupBy: GroupBy) = edit { it[KEY_GROUP_BY] = groupBy.name }
    suspend fun setSortOrder(order: SortOrder) = edit { it[KEY_SORT_ORDER] = order.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    private inline fun <reified T : Enum<T>> parse(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

    companion object {
        const val MAX_YEARLY_GOAL = 200
        const val MAX_CURRENT_READS_LIMIT = 10

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_AMOLED = booleanPreferencesKey("amoled")
        private val KEY_SHOW_COVERS = booleanPreferencesKey("show_covers")
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_TIME = stringPreferencesKey("reminder_time")
        private val KEY_YEARLY_GOAL = intPreferencesKey("yearly_goal")
        private val KEY_CURRENT_READS_LIMIT = intPreferencesKey("current_reads_limit")
        private val KEY_SWIPE_TO_NAVIGATE = booleanPreferencesKey("swipe_to_navigate")
        private val KEY_ONLINE_LOOKUP = booleanPreferencesKey("online_lookup")
        private val KEY_GROUP_BY = stringPreferencesKey("group_by")
        private val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
    }
}
