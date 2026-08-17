package dev.matejgroombridge.readinglist.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.ui.graphics.vector.ImageVector
import dev.matejgroombridge.readinglist.data.model.Genres
import dev.matejgroombridge.readinglist.data.model.RecSources

/**
 * Genre → glyph mapping for section headings and cover placeholders.
 *
 * Kept out of the [Genres] catalogue itself so the data model stays free of
 * Compose types; look-ups are by the same string key.
 */
object GenreIcons {

    private val byKey: Map<String, ImageVector> = mapOf(
        "literary-fiction" to Icons.Outlined.AutoStories,
        "sci-fi" to Icons.Outlined.RocketLaunch,
        "fantasy" to Icons.Outlined.AutoAwesome,
        "mystery-thriller" to Icons.Outlined.Search,
        "horror" to Icons.Outlined.NightsStay,
        "romance" to Icons.Outlined.Favorite,
        "historical-fiction" to Icons.Outlined.HistoryEdu,
        "classics" to Icons.Outlined.AccountBalance,
        "comics" to Icons.Outlined.Palette,
        "poetry" to Icons.Outlined.FormatQuote,
        "biography" to Icons.Outlined.Person,
        "history" to Icons.Outlined.AccountBalance,
        "science" to Icons.Outlined.Science,
        "technology" to Icons.Outlined.Terminal,
        "philosophy" to Icons.Outlined.Forum,
        "psychology" to Icons.Outlined.Psychology,
        "self-improvement" to Icons.Outlined.SelfImprovement,
        "business" to Icons.Outlined.BusinessCenter,
        "politics" to Icons.Outlined.Gavel,
        "true-crime" to Icons.Outlined.Gavel,
        "health" to Icons.Outlined.FitnessCenter,
        "religion" to Icons.Outlined.Spa,
        "art" to Icons.Outlined.Palette,
        "travel" to Icons.Outlined.Flight,
        "cooking" to Icons.Outlined.Restaurant,
    )

    fun icon(genreKey: String): ImageVector = byKey[genreKey] ?: Icons.AutoMirrored.Outlined.MenuBook
}

/** Recommendation-source → glyph mapping, used by the little source badge. */
object RecSourceIcons {

    private val byKey: Map<String, ImageVector> = mapOf(
        "youtube" to Icons.Outlined.SmartDisplay,
        "podcast" to Icons.Outlined.Headphones,
        "friend" to Icons.AutoMirrored.Outlined.Chat,
        "social" to Icons.Outlined.Language,
        "article" to Icons.AutoMirrored.Outlined.Article,
        "bookshop" to Icons.Outlined.Store,
        "another-book" to Icons.AutoMirrored.Outlined.MenuBook,
        RecSources.UNKNOWN_KEY to Icons.Outlined.Bookmarks,
    )

    fun icon(kindKey: String): ImageVector = byKey[kindKey] ?: Icons.Outlined.Bookmarks
}
