package dev.matejgroombridge.readinglist.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.readinglist.BuildConfig
import dev.matejgroombridge.readinglist.R
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.settings.SettingsRepository
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.SettingsViewModel
import dev.matejgroombridge.readinglist.ui.theme.ThemeMode
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Settings in the app family's canonical order (agent.md §10.7.19):
 * Appearance → Reminders → Reading → General → About. No Zen-style mode —
 * there's no single "do the thing" screen to lock down; the current-reads
 * limit covers the focus use case instead.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenShelves: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenNotebook: () -> Unit,
    onOpenBulkAdd: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val enrich by libraryViewModel.enrich.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingImport by remember { mutableStateOf<Library?>(null) }
    var confirmEnrich by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = libraryViewModel.exportJson() ?: return@launch
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
            }.onSuccess { snackbar.showSnackbar("Exported ${countLabel(state.library.books.size)}") }
                .onFailure { snackbar.showSnackbar("Export failed") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val parsed = raw?.let(libraryViewModel::parseImport)
            if (parsed == null) snackbar.showSnackbar("That file isn't a Reading List backup")
            else pendingImport = parsed
        }
    }

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Keep the toggle honest if the user says no.
            if (!granted) viewModel.setReminderEnabled(false)
        }
    } else null

    // Report the end of a Fetch Missing Details run once, then clear it.
    LaunchedEffect(enrich?.finished) {
        val result = enrich ?: return@LaunchedEffect
        if (!result.finished) return@LaunchedEffect
        snackbar.showSnackbar("Updated ${result.updated} of ${countLabel(result.total)}")
        libraryViewModel.clearEnrichResult()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { BackTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Appearance -----------------------------------------------------
            SectionCaption("Appearance")
            SettingsCard {
                Column {
                    ThemePickerRow(selected = settings.themeMode) {
                        haptics.light()
                        viewModel.setThemeMode(it)
                    }
                    Divider()
                    CompactSwitchRow("AMOLED dark mode", settings.amoled) {
                        haptics.light()
                        viewModel.setAmoled(it)
                    }
                    Divider()
                    CompactSwitchRow("Show covers", settings.showCovers) {
                        haptics.light()
                        viewModel.setShowCovers(it)
                    }
                }
            }

            // Reminders ------------------------------------------------------
            SectionCaption("Reminders")
            SettingsCard {
                Column {
                    SwitchRow(
                        label = "Daily reading reminder",
                        subtitle = if (settings.reminder.enabled) "Every day at ${settings.reminder.time}" else "Off",
                        checked = settings.reminder.enabled,
                    ) { wantsOn ->
                        haptics.light()
                        viewModel.setReminderEnabled(wantsOn)
                        if (wantsOn) notificationPermission?.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (settings.reminder.enabled) {
                        Divider()
                        TimeRow(label = "Time", time = settings.reminder.time) {
                            haptics.light()
                            viewModel.setReminderTime(it)
                        }
                    }
                }
            }

            // Reading ----------------------------------------------------------
            SectionCaption("Reading")
            SettingsCard {
                Column {
                    StepperRow(
                        label = "${LocalDate.now().year} goal",
                        value = settings.yearlyGoal,
                        max = SettingsRepository.MAX_YEARLY_GOAL,
                        format = { if (it == 0) "Off" else "$it books" },
                    ) {
                        haptics.light()
                        viewModel.setYearlyGoal(it)
                    }
                    Divider()
                    StepperRow(
                        label = "Current reads limit",
                        value = settings.currentReadsLimit,
                        max = SettingsRepository.MAX_CURRENT_READS_LIMIT,
                        format = { if (it == 0) "None" else it.toString() },
                    ) {
                        haptics.light()
                        viewModel.setCurrentReadsLimit(it)
                    }
                }
            }

            // General ----------------------------------------------------------
            SectionCaption("General")
            SettingsCard {
                Column {
                    CompactSwitchRow("Swipe to navigate", settings.swipeToNavigate) {
                        haptics.light()
                        viewModel.setSwipeToNavigate(it)
                    }
                    Divider()
                    CompactSwitchRow("Online book lookup", settings.onlineLookup) {
                        haptics.light()
                        viewModel.setOnlineLookup(it)
                    }
                    Divider()
                    NavRow("Shelves", value = state.library.shelves.size.toString()) {
                        haptics.light()
                        onOpenShelves()
                    }
                    Divider()
                    NavRow("Archive", value = state.archived.size.takeIf { it > 0 }?.toString()) {
                        haptics.light()
                        onOpenArchive()
                    }
                    Divider()
                    NavRow("Notebook") {
                        haptics.light()
                        onOpenNotebook()
                    }
                    Divider()
                    NavRow("Bulk Add") {
                        haptics.light()
                        onOpenBulkAdd()
                    }
                    Divider()
                    val running = enrich?.let { !it.finished } == true
                    NavRow(
                        label = "Fetch Missing Details",
                        value = enrich?.takeIf { running }?.let { "${it.done} / ${it.total}" },
                    ) {
                        haptics.light()
                        confirmEnrich = true
                    }
                    Divider()
                    NavRow("Export to JSON") {
                        haptics.light()
                        exportLauncher.launch("reading-list-${LocalDate.now()}.json")
                    }
                    Divider()
                    NavRow("Import from JSON") {
                        haptics.light()
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }
            }

            // About ----------------------------------------------------------
            SectionCaption("About")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    pendingImport?.let { incoming ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import Library?") },
            text = {
                Text(
                    "The file has ${countLabel(incoming.books.size)} and ${countLabel(incoming.shelves.size, "shelf", "shelves")}.\n\n" +
                        "Merge adds anything you don't already have. Replace swaps your whole " +
                        "library for the file's contents.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            val s = libraryViewModel.importLibrary(incoming, replace = true)
                            snackbar.showSnackbar("Replaced with ${countLabel(s.books)}")
                        }
                    }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            val s = libraryViewModel.importLibrary(incoming, replace = false)
                            snackbar.showSnackbar("Added ${countLabel(s.books)}" + if (s.shelves > 0) " and ${s.shelves} new shelves" else "")
                        }
                    }) { Text("Merge") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    if (confirmEnrich) {
        val running = enrich?.let { !it.finished } == true
        val candidates = remember(state.library) { libraryViewModel.enrichCandidates(state.library).size }
        AlertDialog(
            onDismissRequest = { confirmEnrich = false },
            title = { Text(if (running) "Stop Fetching?" else "Fetch Missing Details?") },
            text = {
                Text(
                    when {
                        running -> "Details found so far are kept."
                        candidates == 0 -> "Every book already has a cover, page count and year."
                        else -> "Looks up ${countLabel(candidates, "book")} on Open Library and fills in a " +
                            "cover, page count and year wherever there's a confident match. " +
                            "Your titles and authors are kept. Takes about ${(candidates + 59) / 60} min " +
                            "and keeps going if you leave this screen."
                    },
                )
            },
            confirmButton = {
                if (running || candidates > 0) {
                    TextButton(onClick = {
                        confirmEnrich = false
                        if (running) libraryViewModel.cancelEnrich() else libraryViewModel.startEnrich()
                    }) { Text(if (running) "Stop" else "Start") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnrich = false }) { Text(if (candidates == 0 && !running) "OK" else "Cancel") } },
        )
    }
}

// --- Building blocks (same as the rest of the app family) ------------------

/**
 * Shared minimum height for every row in a SettingsCard so switch rows,
 * chevron rows and stepper rows line up exactly.
 */
private val SETTINGS_ROW_MIN_HEIGHT = 56.dp

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(contentPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
private fun ThemePickerRow(selected: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeButton("System", Icons.Outlined.SettingsBrightness, selected == ThemeMode.System, Modifier.weight(1f)) {
                onChange(ThemeMode.System)
            }
            ThemeButton("Light", Icons.Outlined.LightMode, selected == ThemeMode.Light, Modifier.weight(1f)) {
                onChange(ThemeMode.Light)
            }
            ThemeButton("Dark", Icons.Outlined.DarkMode, selected == ThemeMode.Dark, Modifier.weight(1f)) {
                onChange(ThemeMode.Dark)
            }
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
            Icon(icon, null, tint = onContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = onContainer, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CompactSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SwitchRow(label: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** −/+ row. [format] renders the value so 0 can read as "Off". */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    max: Int,
    format: (Int) -> String,
    min: Int = 0,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = format(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TimeRow(label: String, time: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(21, 0))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable {
                TimePickerDialog(context, { _, h, m -> onPick(LocalTime.of(h, m).toString()) },
                    parsed.hour, parsed.minute, true).show()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = parsed.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Chevron row; [value] shows quietly before the chevron (a count, progress). */
@Composable
private fun NavRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 64.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
