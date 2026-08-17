package dev.matejgroombridge.readinglist.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.BuildConfig
import dev.matejgroombridge.readinglist.data.settings.ShelfSort
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.SettingsViewModel
import dev.matejgroombridge.readinglist.ui.components.AppCard
import dev.matejgroombridge.readinglist.ui.components.RowDivider
import dev.matejgroombridge.readinglist.ui.components.RowMinHeight
import dev.matejgroombridge.readinglist.ui.components.SectionCaption
import dev.matejgroombridge.readinglist.ui.theme.ThemeMode
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import kotlinx.coroutines.launch
import java.io.BufferedReader

/**
 * Settings, in the family's canonical order: Appearance → Library → Data →
 * About. Rows carry no decorative icons and share one minimum height so
 * every control lines up down the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = libraryViewModel.exportJson()
            if (json == null) {
                snackbar.showSnackbar("Export failed")
                return@launch
            }
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Reading list exported" else "Export failed")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use(BufferedReader::readText)
                }
            }.getOrNull()
            if (raw == null) {
                snackbar.showSnackbar("Couldn't read that file")
                return@launch
            }
            val count = libraryViewModel.importJson(raw)
            snackbar.showSnackbar(
                if (count == null) "That file isn't a valid reading list"
                else "Imported $count book${if (count == 1) "" else "s"}",
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            SectionCaption("Appearance")
            AppCard {
                Column {
                    ThemeRow(
                        selected = settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                    RowDivider()
                    SwitchRow(
                        label = "AMOLED Dark",
                        checked = settings.amoled,
                        onCheckedChange = viewModel::setAmoled,
                    )
                }
            }

            Spacer(Modifier.size(20.dp))
            SectionCaption("Library")
            AppCard {
                Column {
                    SwitchRow(
                        label = "Group by Genre",
                        checked = settings.groupByGenre,
                        onCheckedChange = viewModel::setGroupByGenre,
                    )
                    RowDivider()
                    SwitchRow(
                        label = "Merge One-Book Sections",
                        checked = settings.mergeSmallSections,
                        onCheckedChange = viewModel::setMergeSmallSections,
                    )
                    RowDivider()
                    SortRow(
                        selected = settings.shelfSort,
                        onSelect = viewModel::setShelfSort,
                    )
                    RowDivider()
                    SwitchRow(
                        label = "Swipe to Navigate",
                        checked = settings.swipeToNavigate,
                        onCheckedChange = viewModel::setSwipeToNavigate,
                    )
                    RowDivider()
                    SwitchRow(
                        label = "Celebrate Finished Books",
                        checked = settings.celebrateFinishes,
                        onCheckedChange = viewModel::setCelebrateFinishes,
                    )
                }
            }

            Spacer(Modifier.size(20.dp))
            SectionCaption("Data")
            AppCard {
                Column {
                    ActionRow(
                        label = "Import from a List",
                        onClick = onOpenImport,
                    )
                    RowDivider()
                    ActionRow(
                        label = "Re-sort Genres",
                        onClick = {
                            scope.launch {
                                val moved = libraryViewModel.reclassifyAll()
                                snackbar.showSnackbar(
                                    if (moved == 0) "Everything is already filed correctly"
                                    else "Re-filed $moved book${if (moved == 1) "" else "s"}",
                                )
                            }
                        },
                    )
                    RowDivider()
                    ActionRow(
                        label = "Export Reading List",
                        onClick = { exportLauncher.launch("reading-list.json") },
                    )
                    RowDivider()
                    ActionRow(
                        label = "Import Reading List",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    )
                }
            }

            Spacer(Modifier.size(20.dp))
            SectionCaption("About")
            AppCard {
                Column {
                    ValueRow(label = "Reading List", value = "v${BuildConfig.VERSION_NAME}")
                    RowDivider()
                    ValueRow(label = "Book Data", value = "Open Library")
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.light()
                onCheckedChange(it)
            },
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = RowMinHeight)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Light / Dark / System as three equal-weight chips. */
@Composable
private fun ThemeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            ThemeMode.entries.forEach { mode ->
                ChipChoice(
                    label = mode.name,
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Shelf ordering as a 2x2 chip grid so the longer labels still line up. */
@Composable
private fun SortRow(selected: ShelfSort, onSelect: (ShelfSort) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Sort Books By",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ShelfSort.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { sort ->
                    ChipChoice(
                        label = sort.label,
                        selected = sort == selected,
                        onClick = { onSelect(sort) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a trailing odd chip at half width instead of letting
                // it stretch across the whole row.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChipChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
        }
    }
}
