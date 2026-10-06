package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.theme.ShelfIcons

/**
 * Rounded container that groups related controls — the editor equivalent
 * of the Settings card. Same shape and colour as Habit Tracker's editor.
 */
@Composable
fun EditorSection(
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/**
 * Uppercased caption + optional "?" help popover, then the contained card.
 * Tight 4dp spacing between caption and card so they read as a unit; the
 * parent Column owns inter-section spacing.
 */
@Composable
fun CaptionedSection(
    caption: String,
    helpText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp),
        ) {
            Text(
                text = caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (helpText != null) {
                Spacer(Modifier.width(4.dp))
                HelpIcon(helpText = helpText)
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        EditorSection(padding = 12.dp) { content() }
    }
}

/**
 * Compact "?" icon. Tapping opens a small popover containing [helpText];
 * sized to sit flush with caption text without inflating its row.
 */
@Composable
fun HelpIcon(helpText: String) {
    var showHelp by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable { showHelp = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "What's this?",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (showHelp) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showHelp = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .widthIn(max = 280.dp),
                ) {
                    Text(
                        text = helpText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * One cell in an equal-weight option grid (status, type, format). Filled
 * with primary when selected; plain surface otherwise. Size it with
 * `Modifier.weight(1f)` from the parent Row so labels of different lengths
 * still line up.
 */
@Composable
fun OptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Lays out [options] as equal-weight [OptionCard]s, [columns] per row. */
@Composable
fun <T> OptionGrid(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    columns: Int = 2,
    icon: ((T) -> ImageVector?)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    OptionCard(
                        label = label(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        icon = icon?.invoke(option),
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad a short last row so its cards keep the same width.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Pill-shaped −/+ stepper. [label] renders the value as a phrase
 * ("p. 120 of 340", "24 books") so it reads naturally.
 */
@Composable
fun CompactStepper(
    value: Int,
    onChange: (Int) -> Unit,
    label: (Int) -> String,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Outlined.Remove,
            description = "Decrease",
            enabled = value > min,
            onClick = { onChange((value - step).coerceAtLeast(min)) },
        )
        Text(
            text = label(value),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        StepperButton(
            icon = Icons.Outlined.Add,
            description = "Increase",
            enabled = value < max,
            onClick = { onChange((value + step).coerceAtMost(max)) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Label + supporting text + Switch, the whole row tappable. Used inside editor cards. */
@Composable
fun EditorSwitchRow(
    label: String,
    supporting: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Shelf identity tile: the shelf's icon on its accent colour. */
@Composable
fun ShelfBadge(
    iconKey: String,
    colorKey: String,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null,
) {
    val color = ShelfColors.entry(colorKey)
    val icon = ShelfIcons.entry(iconKey)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.29f))
            .background(color.accent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.icon,
            contentDescription = if (onClick != null) "Change icon" else icon.label,
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Combined colour row + icon grid, exactly as Habit Tracker's "Choose an Icon" dialog. */
@Composable
fun IconAndColorPickerDialog(
    selectedIconKey: String,
    selectedColorKey: String,
    onIconSelected: (String) -> Unit,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an Icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShelfColors.palette.forEach { entry ->
                        val selected = entry.key == selectedColorKey
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(entry.accent)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else Color.Black.copy(alpha = 0.10f),
                                    shape = CircleShape,
                                )
                                .clickable { onColorSelected(entry.key) },
                        )
                    }
                }
                val accent = ShelfColors.entry(selectedColorKey).accent
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(count = ShelfIcons.catalog.size, key = { ShelfIcons.catalog[it].key }) { index ->
                        val entry = ShelfIcons.catalog[index]
                        val selected = entry.key == selectedIconKey
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onIconSelected(entry.key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = entry.label,
                                tint = if (selected) Color.Black.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Linear blend between [a] and [b] in straight RGB (good enough for pastels). */
fun blend(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - u) + b.red * u,
        green = a.green * (1 - u) + b.green * u,
        blue = a.blue * (1 - u) + b.blue * u,
        alpha = a.alpha * (1 - u) + b.alpha * u,
    )
}
