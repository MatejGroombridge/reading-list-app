package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.data.model.Shelf
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.components.IconAndColorPickerDialog
import dev.matejgroombridge.readinglist.ui.components.ShelfBadge
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

private sealed interface ShelfDialog {
    data object Create : ShelfDialog
    data class Edit(val shelf: Shelf) : ShelfDialog
    data class ConfirmDelete(val shelf: Shelf) : ShelfDialog
}

/**
 * Add, rename, restyle, reorder and delete genres (stored as "shelves").
 * Reordering is up/down arrows (agent.md §10.7.12) — order drives the
 * filter chips, the editor's genre picker and "Group by Genre".
 */
@Composable
fun ShelvesScreen(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    var dialog by remember { mutableStateOf<ShelfDialog?>(null) }
    val shelves = state.library.shelves
    val counts = remember(state.library) { state.active.groupingBy { it.shelfId }.eachCount() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopBar(title = "Genres", onBack = onBack) {
                IconButton(onClick = { dialog = ShelfDialog.Create }) {
                    Icon(Icons.Outlined.Add, contentDescription = "New genre")
                }
            }
        },
    ) { padding ->
        if (shelves.isEmpty()) {
            EmptyState("No genres.\nTap + to make one.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(shelves, key = { _, s -> s.id }) { index, shelf ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { dialog = ShelfDialog.Edit(shelf) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        ShelfBadge(iconKey = shelf.iconKey, colorKey = shelf.colorKey, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shelf.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = countLabel(counts[shelf.id] ?: 0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(enabled = index > 0, onClick = {
                            haptics.light()
                            viewModel.moveShelf(shelf.id, -1)
                        }) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(enabled = index < shelves.lastIndex, onClick = {
                            haptics.light()
                            viewModel.moveShelf(shelf.id, +1)
                        }) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down") }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        ShelfDialog.Create -> ShelfEditorDialog(
            existing = null,
            onDismiss = { dialog = null },
            onSave = {
                viewModel.addShelf(it)
                dialog = null
            },
            onDelete = null,
        )
        is ShelfDialog.Edit -> ShelfEditorDialog(
            existing = d.shelf,
            onDismiss = { dialog = null },
            onSave = {
                viewModel.updateShelf(it)
                dialog = null
            },
            onDelete = { dialog = ShelfDialog.ConfirmDelete(d.shelf) },
        )
        is ShelfDialog.ConfirmDelete -> {
            val n = counts[d.shelf.id] ?: 0
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Delete “${d.shelf.name}”?") },
                text = {
                    Text(
                        if (n == 0) "Nothing is in this genre."
                        else "Its ${countLabel(n)} stay on your list, just without a genre.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteShelf(d.shelf.id)
                        dialog = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ShelfEditorDialog(
    existing: Shelf?,
    onDismiss: () -> Unit,
    onSave: (Shelf) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "book") }
    var colorKey by remember { mutableStateOf(existing?.colorKey ?: ShelfColors.palette.random().key) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (existing == null) "New Genre" else "Edit Genre", modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete genre", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfBadge(iconKey = iconKey, colorKey = colorKey, onClick = { picking = true })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = existing ?: Shelf(name = "")
                    onSave(base.copy(name = name.trim(), iconKey = iconKey, colorKey = colorKey))
                },
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        IconAndColorPickerDialog(
            selectedIconKey = iconKey,
            selectedColorKey = colorKey,
            onIconSelected = { iconKey = it },
            onColorSelected = { colorKey = it },
            onDismiss = { picking = false },
        )
    }
}
