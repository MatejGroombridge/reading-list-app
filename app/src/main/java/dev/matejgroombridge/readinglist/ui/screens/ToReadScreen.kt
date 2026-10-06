package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.settings.GroupBy
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.data.settings.SortOrder
import dev.matejgroombridge.readinglist.domain.LibraryQueries
import dev.matejgroombridge.readinglist.domain.ListFilter
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.components.BookActions
import dev.matejgroombridge.readinglist.ui.components.BookCard
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics

/**
 * The landing page and the replacement for the Notion list itself:
 * everything waiting to be read (Up Next items live on the Reading tab),
 * single-select genre/type filter chips, and a sort/group menu.
 */
@Composable
fun ToReadScreen(
    state: LibraryUiState,
    settings: Settings,
    actions: BookActions,
    contentPadding: PaddingValues,
    onGroupBy: (GroupBy) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDuel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    var filterKey by rememberSaveable { mutableStateOf("all") }

    val toRead = state.toRead
    val filters = remember(state.library) { availableFilters(state) }
    val filter = filters.firstOrNull { it.key == filterKey }?.filter ?: ListFilter.All

    val sections = remember(state.library, filter, settings.groupBy, settings.sortOrder) {
        val visible = LibraryQueries.sort(toRead.filter(filter::accepts), settings.sortOrder, state.library)
        LibraryQueries.toReadSections(visible, settings.groupBy, state.library)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PageHeader(
            title = "To Read",
            subtitle = if (state.loaded && toRead.isNotEmpty()) countLabel(toRead.size) else null,
        ) {
            IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
            IconButton(onClick = onOpenDuel, enabled = toRead.size >= 2) {
                Icon(Icons.Outlined.Balance, contentDescription = "This or that")
            }
            IconButton(onClick = { actions.pickForMe() }, enabled = toRead.isNotEmpty()) {
                Icon(Icons.Outlined.Casino, contentDescription = "Pick for me")
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }

        if (state.loaded && toRead.isEmpty()) {
            EmptyState("Nothing on your list yet.\nTap + to add something someone recommended.")
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SortMenuButton(
                groupBy = settings.groupBy,
                sortOrder = settings.sortOrder,
                onGroupBy = {
                    haptics.light()
                    onGroupBy(it)
                },
                onSortOrder = {
                    haptics.light()
                    onSortOrder(it)
                },
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 20.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filters, key = { it.key }) { option ->
                    FilterChip(
                        selected = option.key == filterKey,
                        onClick = {
                            haptics.light()
                            filterKey = if (option.key == filterKey) "all" else option.key
                        },
                        label = { Text("${option.label}  ${option.count}") },
                        leadingIcon = option.dotColorKey?.let { key ->
                            {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ShelfColors.entry(key).accent),
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }

        if (sections.isEmpty()) {
            EmptyState("Nothing matches this filter.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { section ->
                if (section.title != null) {
                    item(key = "header_${section.key}") {
                        ListSectionHeader(section.title, section.books.size)
                    }
                }
                items(section.books, key = { "${section.key}_${it.id}" }) { book ->
                    BookCard(
                        book = book,
                        shelf = state.library.shelf(book.shelfId),
                        showCovers = settings.showCovers,
                        todayEpochDay = state.todayEpochDay,
                        onClick = { actions.overview(book) },
                        quickActions = { actions.quickActions(book) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private data class FilterOption(
    val key: String,
    val label: String,
    val count: Int,
    val filter: ListFilter,
    val dotColorKey: String? = null,
)

/** Only offer chips that would show something, so the row stays short. */
private fun availableFilters(state: LibraryUiState): List<FilterOption> {
    val books = state.toRead
    return buildList {
        add(FilterOption("all", "All", books.size, ListFilter.All))
        state.library.shelves.forEach { shelf ->
            val n = books.count { it.shelfId == shelf.id }
            if (n > 0) add(FilterOption("shelf_${shelf.id}", shelf.name, n, ListFilter.OnShelf(shelf.id), shelf.colorKey))
        }
        ItemKind.entries.filter { it != ItemKind.Book }.forEach { kind ->
            val n = books.count { it.kind == kind }
            if (n > 0) add(FilterOption("kind_${kind.name}", kind.plural, n, ListFilter.OfKind(kind)))
        }
    }
}

@Composable
private fun SortMenuButton(
    groupBy: GroupBy,
    sortOrder: SortOrder,
    onGroupBy: (GroupBy) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(start = 8.dp)) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort and group")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuCaption("Group By")
            GroupBy.entries.forEach { option ->
                CheckItem(option.label, option == groupBy) {
                    onGroupBy(option)
                    open = false
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuCaption("Sort By")
            SortOrder.entries.forEach { option ->
                CheckItem(option.label, option == sortOrder) {
                    onSortOrder(option)
                    open = false
                }
            }
        }
    }
}

@Composable
private fun MenuCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (checked) Icon(Icons.Outlined.Check, contentDescription = "Selected")
            else Spacer(Modifier.width(24.dp))
        },
    )
}

internal fun countLabel(n: Int, noun: String = "item", plural: String = noun + "s"): String =
    "$n ${if (n == 1) noun else plural}"
