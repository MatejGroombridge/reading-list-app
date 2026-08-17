package dev.matejgroombridge.readinglist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The shared pastel palette used to colour genre sections and book cards.
 * Each entry has a light variant (card background in light mode), a dark
 * variant (dark mode), a stronger accent for badges and spine marks, and a
 * legible foreground for use over [light].
 *
 * Referenced by [key] from the genre catalogue so the palette can be
 * re-ordered or extended without breaking persisted data — unknown keys fall
 * back to [BookColors.defaultEntry].
 */
data class BookColorEntry(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    val accent: Color,
    val onColor: Color,
)

object BookColors {

    // The family's canonical 8-colour palette, ordered along the colour wheel
    // (warm → green → cyan → blue → cool) so adjacent genre sections look
    // related rather than randomly assigned.
    val palette: List<BookColorEntry> = listOf(
        BookColorEntry(
            key = "blush",
            label = "Blush",
            light = Color(0xFFFFE0E6),
            dark = Color(0xFF5A3A42),
            accent = Color(0xFFF7A6B5),
            onColor = Color(0xFF3A1F25),
        ),
        BookColorEntry(
            key = "peach",
            label = "Peach",
            light = Color(0xFFFFE3D1),
            dark = Color(0xFF5A3F30),
            accent = Color(0xFFFFB48A),
            onColor = Color(0xFF3A2418),
        ),
        BookColorEntry(
            key = "butter",
            label = "Butter",
            light = Color(0xFFFFF4C2),
            dark = Color(0xFF55502B),
            accent = Color(0xFFFFE066),
            onColor = Color(0xFF3A330A),
        ),
        BookColorEntry(
            key = "mint",
            label = "Mint",
            light = Color(0xFFD1F0DA),
            dark = Color(0xFF2E4D3A),
            accent = Color(0xFF8DD6A4),
            onColor = Color(0xFF143222),
        ),
        BookColorEntry(
            key = "teal",
            label = "Teal",
            light = Color(0xFFCFE8E4),
            dark = Color(0xFF2F4D49),
            accent = Color(0xFF8DCDC4),
            onColor = Color(0xFF143230),
        ),
        BookColorEntry(
            key = "sky",
            label = "Sky",
            light = Color(0xFFD3E8F5),
            dark = Color(0xFF2F4756),
            accent = Color(0xFF8FC4E0),
            onColor = Color(0xFF12303F),
        ),
        BookColorEntry(
            key = "lavender",
            label = "Lavender",
            light = Color(0xFFE3DAF5),
            dark = Color(0xFF3F354F),
            accent = Color(0xFFB7A5DD),
            onColor = Color(0xFF231A38),
        ),
        BookColorEntry(
            key = "fog",
            label = "Fog",
            light = Color(0xFFE2E5EA),
            dark = Color(0xFF40454D),
            accent = Color(0xFFB6BCC6),
            onColor = Color(0xFF22262D),
        ),
    )

    private val byKey: Map<String, BookColorEntry> = palette.associateBy { it.key }

    val defaultEntry: BookColorEntry get() = palette.first()

    fun entry(key: String): BookColorEntry = byKey[key] ?: defaultEntry
}

/** True when the active Material scheme is a dark one. */
@Composable
@ReadOnlyComposable
private fun isDarkScheme(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    // Cheap luminance proxy — dark themes have a low-luminance background.
    return (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.5f
}

/** Resolves the appropriate background colour for the current theme. */
@Composable
@ReadOnlyComposable
fun BookColorEntry.containerColor(): Color = if (isDarkScheme()) dark else light

/**
 * Foreground colour suitable for text over [containerColor]. Light mode uses
 * the entry's own [BookColorEntry.onColor]; dark mode defers to the theme's
 * onSurface so contrast stays comfortable against the darker container.
 */
@Composable
@ReadOnlyComposable
fun BookColorEntry.contentColor(): Color =
    if (isDarkScheme()) MaterialTheme.colorScheme.onSurface else onColor

/** Linear blend between [a] and [b] in straight RGB (good enough for pastels). */
fun blendColors(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - u) + b.red * u,
        green = a.green * (1 - u) + b.green * u,
        blue = a.blue * (1 - u) + b.blue * u,
        alpha = a.alpha * (1 - u) + b.alpha * u,
    )
}
