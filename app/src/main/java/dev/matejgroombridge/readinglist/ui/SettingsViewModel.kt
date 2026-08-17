package dev.matejgroombridge.readinglist.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.data.settings.SettingsRepository
import dev.matejgroombridge.readinglist.data.settings.ShelfSort
import dev.matejgroombridge.readinglist.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { repository.setAmoled(enabled) }
    }

    fun setSwipeToNavigate(enabled: Boolean) {
        viewModelScope.launch { repository.setSwipeToNavigate(enabled) }
    }

    fun setGroupByGenre(enabled: Boolean) {
        viewModelScope.launch { repository.setGroupByGenre(enabled) }
    }

    fun setShelfSort(sort: ShelfSort) {
        viewModelScope.launch { repository.setShelfSort(sort) }
    }

    fun setMergeSmallSections(enabled: Boolean) {
        viewModelScope.launch { repository.setMergeSmallSections(enabled) }
    }

    fun setCelebrateFinishes(enabled: Boolean) {
        viewModelScope.launch { repository.setCelebrateFinishes(enabled) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
