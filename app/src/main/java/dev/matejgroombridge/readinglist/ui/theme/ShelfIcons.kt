package dev.matejgroombridge.readinglist.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The curated set of shelf icons. Stored on a shelf by string key so adding
 * or removing icons later doesn't break persisted JSON — unknown keys fall
 * back to [defaultEntry]. Kept to genres and subjects people actually shelve
 * books under; the picker stays scannable at this size.
 */
data class ShelfIconEntry(val key: String, val label: String, val icon: ImageVector)

object ShelfIcons {

    val catalog: List<ShelfIconEntry> = listOf(
        ShelfIconEntry("book", "Book", Icons.Outlined.Book),
        ShelfIconEntry("stories", "Stories", Icons.Outlined.AutoStories),
        ShelfIconEntry("menu_book", "Reading", Icons.AutoMirrored.Outlined.MenuBook),
        ShelfIconEntry("castle", "Fantasy", Icons.Outlined.Castle),
        ShelfIconEntry("magic", "Magic", Icons.Outlined.AutoAwesome),
        ShelfIconEntry("rocket", "Sci-Fi", Icons.Outlined.RocketLaunch),
        ShelfIconEntry("mystery", "Mystery", Icons.Outlined.Fingerprint),
        ShelfIconEntry("love", "Romance", Icons.Outlined.FavoriteBorder),
        ShelfIconEntry("drama", "Drama", Icons.Outlined.TheaterComedy),
        ShelfIconEntry("history", "History", Icons.Outlined.HistoryEdu),
        ShelfIconEntry("politics", "Politics", Icons.Outlined.AccountBalance),
        ShelfIconEntry("world", "World", Icons.Outlined.Public),
        ShelfIconEntry("travel", "Travel", Icons.Outlined.Explore),
        ShelfIconEntry("psychology", "Mind", Icons.Outlined.Psychology),
        ShelfIconEntry("meditation", "Meditation", Icons.Outlined.SelfImprovement),
        ShelfIconEntry("spa", "Wellbeing", Icons.Outlined.Spa),
        ShelfIconEntry("church", "Faith", Icons.Outlined.Church),
        ShelfIconEntry("ideas", "Ideas", Icons.Outlined.Lightbulb),
        ShelfIconEntry("science", "Science", Icons.Outlined.Science),
        ShelfIconEntry("biology", "Biology", Icons.Outlined.Biotech),
        ShelfIconEntry("nature", "Nature", Icons.Outlined.Eco),
        ShelfIconEntry("flower", "Poetry", Icons.Outlined.LocalFlorist),
        ShelfIconEntry("animals", "Animals", Icons.Outlined.Pets),
        ShelfIconEntry("trending", "Business", Icons.AutoMirrored.Outlined.TrendingUp),
        ShelfIconEntry("work", "Career", Icons.Outlined.Work),
        ShelfIconEntry("money", "Money", Icons.Outlined.Savings),
        ShelfIconEntry("people", "People", Icons.Outlined.Groups),
        ShelfIconEntry("edit", "Writing", Icons.Outlined.Edit),
        ShelfIconEntry("art", "Art", Icons.Outlined.Brush),
        ShelfIconEntry("music", "Music", Icons.Outlined.MusicNote),
        ShelfIconEntry("code", "Tech", Icons.Outlined.Code),
        ShelfIconEntry("fitness", "Fitness", Icons.Outlined.FitnessCenter),
        ShelfIconEntry("food", "Food", Icons.Outlined.Restaurant),
        ShelfIconEntry("school", "Study", Icons.Outlined.School),
    )

    private val byKey: Map<String, ShelfIconEntry> = catalog.associateBy { it.key }

    val defaultEntry: ShelfIconEntry get() = catalog.first()

    fun entry(key: String?): ShelfIconEntry = key?.let(byKey::get) ?: defaultEntry
}
