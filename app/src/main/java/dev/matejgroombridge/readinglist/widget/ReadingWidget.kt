package dev.matejgroombridge.readinglist.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.matejgroombridge.readinglist.MainActivity
import dev.matejgroombridge.readinglist.QuickAddActivity
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.repository.LibraryRepository
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget: what's being read right now, with progress, and a +
 * that opens the quick-add dialog straight over the home screen. Tapping a
 * book opens its overview in the app. No configuration — it always shows
 * current reads, most recently started first.
 */
class ReadingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val library = LibraryRepository(context).library.first()
        val reading = library.books
            .filter { !it.archived && it.status == ReadingStatus.Reading }
            .sortedByDescending { it.startedOn ?: Long.MIN_VALUE }
        val waiting = library.books.count { !it.archived && it.status == ReadingStatus.WantToRead }

        provideContent {
            GlanceTheme {
                WidgetBody(context = context, library = library, reading = reading, waiting = waiting)
            }
        }
    }
}

private const val MAX_ROWS = 3

@Composable
private fun WidgetBody(context: Context, library: Library, reading: List<Book>, waiting: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Reading",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openPage(context, MainActivity.PAGE_READING))),
            )
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity<QuickAddActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))

        if (reading.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity(openPage(context, MainActivity.PAGE_TO_READ))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (waiting > 0) "Nothing on the go.\n$waiting waiting on your list." else "Nothing on the go.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            return@Column
        }

        reading.take(MAX_ROWS).forEachIndexed { index, book ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            BookRow(context, book, library)
        }
        if (reading.size > MAX_ROWS) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "+${reading.size - MAX_ROWS} more",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(openPage(context, MainActivity.PAGE_READING))),
            )
        }
    }
}

@Composable
private fun BookRow(context: Context, book: Book, library: Library) {
    val palette = ShelfColors.entry(library.shelf(book.shelfId)?.colorKey)
    val subtitle = book.author
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(day = palette.light, night = palette.dark))
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_BOOK, book.id)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // Distinct data per book so the system doesn't
                        // collapse every row's PendingIntent into one.
                        .setData(android.net.Uri.parse("readinglist://book/${book.id}")),
                ),
            ),
    ) {
        Text(
            text = book.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(day = palette.onColor, night = Color.White),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(day = palette.onColor.copy(alpha = 0.7f), night = Color.White.copy(alpha = 0.7f)),
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

private fun openPage(context: Context, page: Int): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_PAGE, page)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .setData(android.net.Uri.parse("readinglist://page/$page"))
