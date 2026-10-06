package dev.matejgroombridge.readinglist.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wires [ReadingWidget] into the AppWidgetManager lifecycle and handles the
 * app's own "refresh" broadcast, fired by the repository after every write.
 */
class ReadingWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = ReadingWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val ids = GlanceAppWidgetManager(context).getGlanceIds(ReadingWidget::class.java)
                    for (id in ids) glanceAppWidget.update(context, id)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.matejgroombridge.readinglist.WIDGET_REFRESH"

        fun broadcastRefresh(context: Context) {
            context.sendBroadcast(Intent(context, ReadingWidgetReceiver::class.java).setAction(ACTION_REFRESH))
        }
    }
}
