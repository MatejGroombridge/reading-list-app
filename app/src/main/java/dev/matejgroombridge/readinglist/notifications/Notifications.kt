package dev.matejgroombridge.readinglist.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.matejgroombridge.readinglist.MainActivity
import dev.matejgroombridge.readinglist.R

/** Channel + notification helpers, isolated from the scheduler logic. */
object Notifications {

    const val CHANNEL_ID = "reading_reminder"
    private const val CHANNEL_NAME = "Reading reminder"
    private const val NOTIFICATION_ID = 9_100_001

    /** Creates the reminder channel if it doesn't already exist. Safe to call repeatedly. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily nudge to pick up your current book."
            },
        )
    }

    fun postReminder(context: Context, title: String, body: String) {
        ensureChannel(context)
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_PAGE, MainActivity.PAGE_READING)
        }
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        mgr.notify(NOTIFICATION_ID, notification)
    }
}
