package dev.matejgroombridge.readinglist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The app family's canonical 8-colour pastel palette (see agent.md §10.7.2),
 * used here to colour shelves — and through them, every book card.
 *
 * Stored on a shelf by [key] so the palette can be re-ordered or extended
 * without breaking persisted data — unknown keys fall back to [ShelfColors.defaultEntry].
 */
data class PaletteEntry(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    val accent: Color,
    val onColor: Color,
)

object ShelfColors {

    val palette: List<PaletteEntry> = listOf(
        PaletteEntry("blush", "Blush", Color(0xFFFFE0E6), Color(0xFF5A3A42), Color(0xFFF7A6B5), Color(0xFF3A1F25)),
        PaletteEntry("peach", "Peach", Color(0xFFFFE3D1), Color(0xFF5A3F30), Color(0xFFFFB48A), Color(0xFF3A2418)),
        PaletteEntry("butter", "Butter", Color(0xFFFFF4C2), Color(0xFF55502B), Color(0xFFFFE066), Color(0xFF3A330A)),
        PaletteEntry("mint", "Mint", Color(0xFFD1F0DA), Color(0xFF2E4D3A), Color(0xFF8DD6A4), Color(0xFF143222)),
        PaletteEntry("teal", "Teal", Color(0xFFCFE8E4), Color(0xFF2F4D49), Color(0xFF8DCDC4), Color(0xFF143230)),
        PaletteEntry("sky", "Sky", Color(0xFFD3E8F5), Color(0xFF2F4756), Color(0xFF8FC4E0), Color(0xFF12303F)),
        PaletteEntry("lavender", "Lavender", Color(0xFFE3DAF5), Color(0xFF3F354F), Color(0xFFB7A5DD), Color(0xFF231A38)),
        PaletteEntry("fog", "Fog", Color(0xFFE2E5EA), Color(0xFF40454D), Color(0xFFB6BCC6), Color(0xFF22262D)),
    )

    private val byKey: Map<String, PaletteEntry> = palette.associateBy { it.key }

    /** Unshelved items use the neutral entry so they read as "uncategorised". */
    val defaultEntry: PaletteEntry get() = byKey.getValue("fog")

    fun entry(key: String?): PaletteEntry = key?.let(byKey::get) ?: defaultEntry
}

/** True when the current theme's background is dark. */
@Composable
@ReadOnlyComposable
private fun isDarkBackground(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    return (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.5f
}

/** Card background for the current theme. */
@Composable
@ReadOnlyComposable
fun PaletteEntry.containerColor(): Color = if (isDarkBackground()) dark else light

/**
 * Foreground colour suitable for text on top of [containerColor]. In light
 * mode we use the entry's [PaletteEntry.onColor]; in dark mode we use the
 * theme's onSurface so contrast stays comfortable.
 */
@Composable
@ReadOnlyComposable
fun PaletteEntry.contentColor(): Color =
    if (isDarkBackground()) MaterialTheme.colorScheme.onSurface else onColor
