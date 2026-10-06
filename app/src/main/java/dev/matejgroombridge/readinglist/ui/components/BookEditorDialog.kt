package dev.matejgroombridge.readinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.BookPrefill
import dev.matejgroombridge.readinglist.data.model.Interest
import dev.matejgroombridge.readinglist.data.model.ItemKind
import dev.matejgroombridge.readinglist.data.model.Library
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.network.BookLookup
import dev.matejgroombridge.readinglist.data.network.BookSuggestion
import dev.matejgroombridge.readinglist.domain.TextMatch
import dev.matejgroombridge.readinglist.ui.theme.ShelfColors
import dev.matejgroombridge.readinglist.ui.util.Dates
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Result emitted by [BookEditorDialog]. */
sealed interface BookEditorResult {
    data class Save(val book: Book) : BookEditorResult

    /**
     * Delete isn't offered here — like Habit Tracker, items are archived
     * first and only deleted from the Archive screen.
     */
    data class Archive(val archived: Boolean) : BookEditorResult
}

private enum class DateField { Started, Finished }

/**
 * Full-screen page for adding and editing, layered over the app (or shown on
 * its own when reached from the share sheet). One scrolling
 * column, every field visible: what it is, who recommended it and why, its
 * status and its genre — plus dates and rating once it's being read.
 *
 * While a new title is typed, Open Library suggestions appear underneath
 * (picking one fills the canonical title, author and cover), and an inline
 * warning appears if the title is already on the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookEditorDialog(
    existing: Book?,
    library: Library,
    onlineLookup: Boolean,
    showCovers: Boolean,
    onSearch: suspend (String) -> List<BookSuggestion>,
    onDismiss: () -> Unit,
    onResult: (BookEditorResult) -> Unit,
    prefill: BookPrefill = BookPrefill(),
    /** When set, the duplicate warning offers a jump to the existing item. */
    onOpenDuplicate: ((Book) -> Unit)? = null,
) {
    val isEdit = existing != null
    val today = remember { LocalDate.now().toEpochDay() }

    var title by remember { mutableStateOf(existing?.title ?: prefill.title) }
    var author by remember { mutableStateOf(existing?.author ?: prefill.author) }
    var kind by remember { mutableStateOf(existing?.kind ?: ItemKind.Book) }
    var status by remember { mutableStateOf(existing?.status ?: prefill.status) }
    var interest by remember {
        mutableStateOf(
            existing?.interest ?: when {
                prefill.upNext -> Interest.Now
                prefill.someday -> Interest.Someday
                else -> Interest.Interested
            },
        )
    }
    var shelfId by remember { mutableStateOf(existing?.shelfId) }
    var recommendedBy by remember { mutableStateOf(existing?.recommendedBy.orEmpty()) }
    var reason by remember { mutableStateOf(existing?.reason ?: prefill.reason) }
    var coverUrl by remember { mutableStateOf(existing?.coverUrl.orEmpty()) }
    var pageCount by remember { mutableIntStateOf(existing?.pageCount ?: 0) }
    var publishedYear by remember { mutableIntStateOf(existing?.publishedYear ?: 0) }
    var rating by remember { mutableIntStateOf(existing?.rating ?: 0) }
    var review by remember { mutableStateOf(existing?.review.orEmpty()) }
    var startedOn by remember {
        mutableStateOf(existing?.startedOn ?: if (prefill.status == ReadingStatus.Reading) today else null)
    }
    var finishedOn by remember {
        mutableStateOf(existing?.finishedOn ?: if (prefill.status == ReadingStatus.Read) today else null)
    }
    var pickingDate by remember { mutableStateOf<DateField?>(null) }

    // --- Online lookup ------------------------------------------------------
    // Armed automatically while adding; when editing it waits for the
    // search icon so opening an item never fires a request on its own.
    var lookupArmed by remember { mutableStateOf(!isEdit && onlineLookup) }
    var lookupNow by remember { mutableIntStateOf(0) }
    var skipDebounce by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<BookSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(title, lookupArmed, lookupNow, kind) {
        if (!lookupArmed || kind != ItemKind.Book || title.trim().length < BookLookup.MIN_QUERY_LENGTH) {
            suggestions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        // Debounce typing; an explicit tap on the search icon skips it once.
        if (!skipDebounce) delay(LOOKUP_DEBOUNCE_MS)
        skipDebounce = false
        searching = true
        suggestions = onSearch(listOf(title, author).filter { it.isNotBlank() }.joinToString(" "))
            .take(MAX_SUGGESTIONS)
        searching = false
    }

    val recommenders = remember(library) { library.recommenders() }
    val duplicate = remember(title) { TextMatch.findDuplicate(library.books, title, existing?.id) }

    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!isEdit && title.isEmpty()) runCatching { titleFocus.requestFocus() }
    }

    fun changeStatus(next: ReadingStatus) {
        status = next
        if (next == ReadingStatus.Reading && startedOn == null) startedOn = today
        if ((next == ReadingStatus.Read || next == ReadingStatus.Abandoned) && finishedOn == null) finishedOn = today
    }

    fun applySuggestion(s: BookSuggestion) {
        title = s.title
        if (s.author.isNotBlank()) author = s.author
        if (s.pageCount > 0) pageCount = s.pageCount
        if (s.publishedYear > 0) publishedYear = s.publishedYear
        if (s.coverUrl.isNotBlank()) coverUrl = s.coverUrl
        lookupArmed = false
        suggestions = emptyList()
    }

    val canSave = title.isNotBlank()

    fun submit() {
        if (!canSave) return
        val base = existing ?: Book(title = "")
        val book = base.copy(
            title = title.trim(),
            author = if (kind == ItemKind.Author) "" else author.trim(),
            kind = kind,
            status = status,
            upNext = interest == Interest.Now && status == ReadingStatus.WantToRead,
            someday = interest == Interest.Someday && status == ReadingStatus.WantToRead,
            shelfId = shelfId?.takeIf { id -> library.shelf(id) != null },
            recommendedBy = recommendedBy.trim(),
            reason = reason.trim(),
            coverUrl = coverUrl,
            pageCount = pageCount,
            publishedYear = publishedYear,
            rating = if (status == ReadingStatus.Read) rating else base.rating,
            review = review.trim(),
            startedOn = if (status == ReadingStatus.WantToRead) null else startedOn,
            finishedOn = if (status == ReadingStatus.Read || status == ReadingStatus.Abandoned) finishedOn else null,
        )
        onResult(BookEditorResult.Save(book))
    }

    // Drawn as a full-screen layer in the host window rather than a dialog
    // window, so the status bar and keyboard insets behave like any other
    // screen. Back closes it.
    BackHandler(onBack = onDismiss)
    run {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                    },
                    title = {
                        Text(
                            text = if (isEdit) "Edit ${kind.label}" else "New ${kind.label}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        if (existing != null) {
                            IconButton(onClick = { onResult(BookEditorResult.Archive(!existing.archived)) }) {
                                Icon(
                                    imageVector = if (existing.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                    contentDescription = if (existing.archived) "Restore" else "Archive",
                                )
                            }
                        }
                        TextButton(onClick = ::submit, enabled = canSave) {
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // --- What it is ---------------------------------------------
                EditorSection(padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionGrid(
                            options = ItemKind.entries,
                            selected = kind,
                            label = { it.label },
                            onSelect = { kind = it },
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(if (kind == ItemKind.Author) "Name" else "Title") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                            trailingIcon = if (onlineLookup && kind == ItemKind.Book) {
                                {
                                    IconButton(onClick = {
                                        lookupArmed = true
                                        skipDebounce = true
                                        lookupNow++
                                    }) {
                                        Icon(Icons.Outlined.TravelExplore, contentDescription = "Find details online")
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(titleFocus),
                        )
                        if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        if (suggestions.isNotEmpty()) {
                            SuggestionList(
                                suggestions = suggestions,
                                showCovers = showCovers,
                                onPick = ::applySuggestion,
                                onClose = {
                                    lookupArmed = false
                                    suggestions = emptyList()
                                },
                            )
                        }
                        if (duplicate != null) DuplicateWarning(duplicate, onOpen = onOpenDuplicate)
                        if (kind == ItemKind.Book) {
                            OutlinedTextField(
                                value = author,
                                onValueChange = { author = it },
                                label = { Text("Author") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // --- Recommendation -------------------------------------------
                CaptionedSection(caption = "Recommended By") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            OutlinedTextField(
                                value = recommendedBy,
                                onValueChange = { recommendedBy = it },
                                label = { Text("Name") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            RecommenderSuggestions(
                                all = recommenders,
                                typed = recommendedBy,
                                onPick = { recommendedBy = it },
                            )
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Why") },
                            minLines = 2,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // --- Status ---------------------------------------------------
                CaptionedSection(caption = "Status") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionGrid(
                            options = ReadingStatus.entries,
                            selected = status,
                            label = { it.label },
                            onSelect = ::changeStatus,
                        )
                        if (status == ReadingStatus.WantToRead) {
                            Text(
                                text = "How keen?",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                            )
                            OptionGrid(
                                options = Interest.entries,
                                selected = interest,
                                label = { it.label },
                                onSelect = { interest = it },
                                columns = 3,
                            )
                        }
                    }
                }

                // --- Genre ------------------------------------------------------
                CaptionedSection(caption = "Genre") {
                    ShelfChips(library = library, selectedId = shelfId, onSelect = { shelfId = it })
                }

                // --- Status-specific --------------------------------------------
                when (status) {
                    ReadingStatus.WantToRead -> Unit
                    ReadingStatus.Reading -> CaptionedSection(caption = "Reading") {
                        DateRow("Started", startedOn) { pickingDate = DateField.Started }
                    }
                    ReadingStatus.Read -> CaptionedSection(caption = "Finished") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("Rating", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                RatingStars(rating = rating, onRate = { rating = it }, size = 28.dp)
                            }
                            DateRow("Started", startedOn) { pickingDate = DateField.Started }
                            DateRow("Finished", finishedOn) { pickingDate = DateField.Finished }
                            OutlinedTextField(
                                value = review,
                                onValueChange = { review = it },
                                label = { Text("Takeaways") },
                                minLines = 2,
                                maxLines = 8,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    ReadingStatus.Abandoned -> CaptionedSection(caption = "Stopped") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateRow("Started", startedOn) { pickingDate = DateField.Started }
                            DateRow("Stopped", finishedOn) { pickingDate = DateField.Finished }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        pickingDate?.let { field ->
            val initial = when (field) {
                DateField.Started -> startedOn
                DateField.Finished -> finishedOn
            } ?: today
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = Dates.epochDayToPickerMillis(initial))
            fun set(value: Long?) {
                when (field) {
                    DateField.Started -> startedOn = value
                    DateField.Finished -> finishedOn = value
                }
                pickingDate = null
            }
            DatePickerDialog(
                onDismissRequest = { pickingDate = null },
                confirmButton = {
                    TextButton(onClick = { set(pickerState.selectedDateMillis?.let(Dates::pickerMillisToEpochDay)) }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { set(null) }) { Text("Clear") }
                        TextButton(onClick = { pickingDate = null }) { Text("Cancel") }
                    }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

private const val LOOKUP_DEBOUNCE_MS = 450L
private const val MAX_SUGGESTIONS = 4
private const val MAX_RECOMMENDER_SUGGESTIONS = 4

@Composable
private fun DuplicateWarning(duplicate: Book, onOpen: ((Book) -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Already on your list · ${duplicate.status.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (onOpen != null) {
                TextButton(onClick = { onOpen(duplicate) }) { Text("Open") }
            }
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<BookSuggestion>,
    showCovers: Boolean,
    onPick: (BookSuggestion) -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "From Open Library",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Hide suggestions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        suggestions.forEach { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp, 42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    if (showCovers && s.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = s.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(30.dp, 42.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        s.author.takeIf { it.isNotBlank() },
                        s.publishedYear.takeIf { it > 0 }?.toString(),
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * People who've recommended things before, matching what's been typed so
 * far (start of any word in their name). Hidden until something is typed,
 * and once the field holds an exact match.
 */
@Composable
private fun RecommenderSuggestions(all: List<String>, typed: String, onPick: (String) -> Unit) {
    val query = typed.trim()
    if (query.isEmpty() || all.any { it.equals(query, ignoreCase = true) }) return
    val matches = all.filter { name ->
        name.split(' ').any { it.startsWith(query, ignoreCase = true) } || name.startsWith(query, ignoreCase = true)
    }.take(MAX_RECOMMENDER_SUGGESTIONS)
    if (matches.isEmpty()) return
    Column(modifier = Modifier.padding(top = 4.dp)) {
        matches.forEach { name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(name) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Wrap of genre pills (accent dot + name), plus "None". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelfChips(library: Library, selectedId: String?, onSelect: (String?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShelfPill(name = "None", accent = null, selected = selectedId == null) { onSelect(null) }
        library.shelves.forEach { shelf ->
            ShelfPill(
                name = shelf.name,
                accent = ShelfColors.entry(shelf.colorKey).accent,
                selected = selectedId == shelf.id,
            ) { onSelect(shelf.id) }
        }
    }
}

@Composable
private fun ShelfPill(name: String, accent: Color?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = when {
            selected && accent != null -> accent
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            val fg = when {
                selected && accent != null -> Color.Black.copy(alpha = 0.85f)
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            } else if (accent != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(text = name, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@Composable
private fun DateRow(label: String, epochDay: Long?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = epochDay?.let(Dates::full) ?: "Not set",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (epochDay == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
