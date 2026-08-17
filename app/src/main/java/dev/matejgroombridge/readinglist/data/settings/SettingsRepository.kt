package dev.matejgroombridge.readinglist.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
            themeMode = prefs[KEY_THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.System,
            amoled = prefs[KEY_AMOLED] ?: false,
            swipeToNavigate = prefs[KEY_SWIPE_TO_NAVIGATE] ?: true,
            groupByGenre = prefs[KEY_GROUP_BY_GENRE] ?: true,
            shelfSort = prefs[KEY_SHELF_SORT]?.let(::parseShelfSort) ?: ShelfSort.Default,
            mergeSmallSections = prefs[KEY_MERGE_SMALL_SECTIONS] ?: false,
            celebrateFinishes = prefs[KEY_CELEBRATE_FINISHES] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAmoled(amoled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AMOLED] = amoled }
    }

    suspend fun setSwipeToNavigate(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SWIPE_TO_NAVIGATE] = enabled }
    }

    suspend fun setGroupByGenre(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_GROUP_BY_GENRE] = enabled }
    }

    suspend fun setShelfSort(sort: ShelfSort) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SHELF_SORT] = sort.name }
    }

    suspend fun setMergeSmallSections(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_MERGE_SMALL_SECTIONS] = enabled }
    }

    suspend fun setCelebrateFinishes(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_CELEBRATE_FINISHES] = enabled }
    }

    private fun parseThemeMode(raw: String): ThemeMode = runCatching {
        ThemeMode.valueOf(raw)
    }.getOrDefault(ThemeMode.System)

    private fun parseShelfSort(raw: String): ShelfSort = runCatching {
        ShelfSort.valueOf(raw)
    }.getOrDefault(ShelfSort.Default)

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AMOLED = booleanPreferencesKey("amoled")
        val KEY_SWIPE_TO_NAVIGATE = booleanPreferencesKey("swipe_to_navigate")
        val KEY_GROUP_BY_GENRE = booleanPreferencesKey("group_by_genre")
        val KEY_SHELF_SORT = stringPreferencesKey("shelf_sort")
        val KEY_MERGE_SMALL_SECTIONS = booleanPreferencesKey("merge_small_sections")
        val KEY_CELEBRATE_FINISHES = booleanPreferencesKey("celebrate_finishes")
    }
}
