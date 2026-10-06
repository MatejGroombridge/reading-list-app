package dev.matejgroombridge.readinglist.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.matejgroombridge.readinglist.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the single daily reading reminder. A one-shot exact alarm is
 * set for the next occurrence of the chosen time; [ReminderReceiver]
 * re-arms it after firing, so it repeats without the deprecated inexact
 * repeating alarms. Same exact → inexact fallback ladder as Habit Tracker.
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 7_800_001
    private const val ACTION_REMIND = "dev.matejgroombridge.readinglist.ACTION_REMIND"

    /** Cancels any pending alarm and re-arms it from current settings. Safe to call any time. */
    suspend fun reschedule(context: Context) {
        val mgr = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        pendingIntent(context, create = false)?.let {
            mgr.cancel(it)
            it.cancel()
        }

        val settings = SettingsRepository(context).settings.first().reminder
        if (!settings.enabled) return
        val time = runCatching { LocalTime.parse(settings.time) }.getOrNull() ?: return

        var fireAt = LocalDate.now().atTime(time)
        // A minute's grace so re-arming from inside the receiver never
        // schedules a duplicate for the alarm that's firing right now.
        if (!fireAt.isAfter(LocalDateTime.now().plusMinutes(1))) fireAt = fireAt.plusDays(1)
        val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pi = pendingIntent(context, create = true) ?: return
        runCatching {
            if (canUseExactAlarms(mgr)) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure {
            // Exact-alarm permission revoked at runtime — late beats never.
            mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun canUseExactAlarms(mgr: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || mgr.canScheduleExactAlarms()
}
