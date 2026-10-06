package dev.matejgroombridge.readinglist.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when the alarm set by [ReminderScheduler] elapses, posts the
 * reminder, then re-arms itself for tomorrow. Also restores the alarm after
 * a reboot ([Intent.ACTION_BOOT_COMPLETED]).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) fireReminder(appContext)
                ReminderScheduler.reschedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The body is built at fire time from the live library so it always
     * names what's actually on the go — or nudges toward the To Read pile
     * when nothing is.
     */
    private suspend fun fireReminder(context: Context) {
        val books = LibraryRepository(context).library.first().books.filterNot { it.archived }
        val reading = books.filter { it.status == ReadingStatus.Reading }
        val waiting = books.count { it.status == ReadingStatus.WantToRead }
        val body = when {
            reading.size == 1 -> {
                val b = reading.first()
                "Pick up ${b.title}."
            }
            reading.size > 1 -> "You have ${reading.size} books on the go: " +
                reading.take(3).joinToString(", ") { it.title } + if (reading.size > 3) "…" else "."
            waiting > 0 -> "Nothing on the go. $waiting ${if (waiting == 1) "book is" else "books are"} waiting on your list."
            else -> "Ten pages is a good start."
        }
        Notifications.postReminder(context, "Time to read", body)
    }
}
