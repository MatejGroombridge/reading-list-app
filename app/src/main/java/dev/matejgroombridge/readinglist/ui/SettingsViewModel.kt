package dev.matejgroombridge.readinglist.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.readinglist.data.settings.GroupBy
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.data.settings.SettingsRepository
import dev.matejgroombridge.readinglist.data.settings.SortOrder
import dev.matejgroombridge.readinglist.notifications.ReminderScheduler
import dev.matejgroombridge.readinglist.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }
    fun setAmoled(enabled: Boolean) = launch { repository.setAmoled(enabled) }
    fun setShowCovers(enabled: Boolean) = launch { repository.setShowCovers(enabled) }
    fun setYearlyGoal(goal: Int) = launch { repository.setYearlyGoal(goal) }
    fun setCurrentReadsLimit(limit: Int) = launch { repository.setCurrentReadsLimit(limit) }
    fun setSwipeToNavigate(enabled: Boolean) = launch { repository.setSwipeToNavigate(enabled) }
    fun setOnlineLookup(enabled: Boolean) = launch { repository.setOnlineLookup(enabled) }
    fun setGroupBy(groupBy: GroupBy) = launch { repository.setGroupBy(groupBy) }
    fun setSortOrder(order: SortOrder) = launch { repository.setSortOrder(order) }

    fun setReminderEnabled(enabled: Boolean) = launch {
        repository.setReminderEnabled(enabled)
        ReminderScheduler.reschedule(appContext)
    }

    fun setReminderTime(time: String) = launch {
        repository.setReminderTime(time)
        ReminderScheduler.reschedule(appContext)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val ctx = application.applicationContext
                SettingsViewModel(ctx, SettingsRepository(ctx))
            }
        }
    }
}
