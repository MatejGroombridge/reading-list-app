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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.model.BookFormat
import dev.matejgroombridge.readinglist.data.model.BookPrefill
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
 * One dialog for adding and editing.
 *
 * Adding opens in a quick mode: title, author, who recommended it, why, and
 * status — the minimum worth capturing while someone is telling you about a
 * book. "More Details" reveals shelf, type, notes, link, pages, format and
 * the status-specific dates. Editing always shows everything.
 *
 * While a new title is typed, Open Library suggestions appear underneath
 * (picking one fills the canonical title, author, cover, pages and year),
 * and an inline warning appears if the title is already on the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var shelfId by remember { mutableStateOf(existing?.shelfId) }
    var recommendedBy by remember { mutableStateOf(existing?.recommendedBy.orEmpty()) }
    var reason by remember { mutableStateOf(existing?.reason ?: prefill.reason) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url ?: prefill.url) }
    var coverUrl by remember { mutableStateOf(existing?.coverUrl.orEmpty()) }
    var pagesText by remember { mutableStateOf(existing?.pageCount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var yearText by remember { mutableStateOf(existing?.publishedYear?.takeIf { it > 0 }?.toString().orEmpty()) }
    var currentPageText by remember { mutableStateOf(existing?.currentPage?.takeIf { it > 0 }?.toString().orEmpty()) }
    var format by remember { mutableStateOf(existing?.format ?: BookFormat.Any) }
    var upNext by remember { mutableStateOf(existing?.upNext ?: false) }
    var toAcquire by remember { mutableStateOf(existing?.toAcquire ?: false) }
    var rating by remember { mutableIntStateOf(existing?.rating ?: 0) }
    var review by remember { mutableStateOf(existing?.review.orEmpty()) }
    var startedOn by remember {
        mutableStateOf(existing?.startedOn ?: if (prefill.status == ReadingStatus.Reading) today else null)
    }
    var finishedOn by remember {
        mutableStateOf(existing?.finishedOn ?: if (prefill.status == ReadingStatus.Read) today else null)
    }

    var expanded by remember { mutableStateOf(isEdit || prefill.status == ReadingStatus.Read) }
    var pickingDate by remember { mutableStateOf<DateField?>(null) }

    // --- Online lookup ------------------------------------------------------
    // Armed automatically while adding; when editing it waits for the
    // search icon so opening an item never fires a request on its own.
    var lookupArmed by remember { mutableStateOf(!isEdit && onlineLookup) }
    var lookupNow by remember { mutableIntStateOf(0) }
    var skipDebounce by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<BookSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(title, lookupArmed, lookupNow) {
        if (!lookupArmed || title.trim().length < BookLookup.MIN_QUERY_LENGTH) {
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
        if (!isEdit) runCatching { titleFocus.requestFocus() }
    }

    fun changeStatus(next: ReadingStatus) {
        status = next
        if (next == ReadingStatus.Reading && startedOn == null) startedOn = today
        if ((next == ReadingStatus.Read || next == ReadingStatus.Abandoned) && finishedOn == null) finishedOn = today
        if (next == ReadingStatus.Read) expanded = true
    }

    fun applySuggestion(s: BookSuggestion) {
        title = s.title
        if (s.author.isNotBlank()) author = s.author
        if (s.pageCount > 0) pagesText = s.pageCount.toString()
        if (s.publishedYear > 0) yearText = s.publishedYear.toString()
        if (s.coverUrl.isNotBlank()) coverUrl = s.coverUrl
        lookupArmed = false
        suggestions = emptyList()
    }

    val canSave = title.isNotBlank()

    fun submit() {
        if (!canSave) return
        val pages = pagesText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val base = existing ?: Book(title = "")
        val book = base.copy(
            title = title.trim(),
            author = author.trim(),
            kind = kind,
            status = status,
            shelfId = shelfId?.takeIf { id -> library.shelf(id) != null },
            recommendedBy = recommendedBy.trim(),
            reason = reason.trim(),
            notes = notes.trim(),
            url = url.trim(),
            coverUrl = coverUrl,
            pageCount = pages,
            publishedYear = yearText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            currentPage = when (status) {
                ReadingStatus.WantToRead -> 0
                ReadingStatus.Read -> if (pages > 0) pages else currentPageText.toIntOrNull() ?: 0
                else -> (currentPageText.toIntOrNull() ?: 0).let { if (pages > 0) it.coerceIn(0, pages) else it }
            },
            format = format,
            upNext = upNext && status == ReadingStatus.WantToRead,
            toAcquire = toAcquire && status != ReadingStatus.Read,
            rating = if (status == ReadingStatus.Read) rating else base.rating,
            review = review.trim(),
            startedOn = if (status == ReadingStatus.WantToRead) null else startedOn,
            finishedOn = if (status == ReadingStatus.Read || status == ReadingStatus.Abandoned) finishedOn else null,
        )
        onResult(BookEditorResult.Save(book))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isEdit) "Edit ${kind.label}" else "Add to List",
                    modifier = Modifier.weight(1f),
                )
                if (existing != null) {
                    IconButton(onClick = { onResult(BookEditorResult.Archive(!existing.archived)) }) {
                        Icon(
                            imageVector = if (existing.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = if (existing.archived) "Restore" else "Archive",
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- Identity ---------------------------------------------
                EditorSection(padding = 12.dp) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (kind == ItemKind.Author) "Author's name" else "Title") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        trailingIcon = if (onlineLookup) {
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
                    if (kind != ItemKind.Author) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Author (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (duplicate != null) {
                        Spacer(Modifier.height(8.dp))
                        DuplicateWarning(duplicate, onOpen = onOpenDuplicate)
                    }
                    if (searching) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
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
                }

                // --- Recommendation ----------------------------------------
                CaptionedSection(
                    caption = "Recommendation",
                    helpText = "Who pointed you to it and why. Months later this is " +
                        "the context that decides whether it's worth reading next.",
                ) {
                    OutlinedTextField(
                        value = recommendedBy,
                        onValueChange = { recommendedBy = it },
                        label = { Text("From") },
                        placeholder = { Text("A friend, a podcast…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RecommenderChips(
                        all = recommenders,
                        current = recommendedBy,
                        onPick = { recommendedBy = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Why") },
                        placeholder = { Text("What made it worth reading?") },
                        minLines = 2,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // --- Status ------------------------------------------------
                CaptionedSection(caption = "Status") {
                    OptionGrid(
                        options = ReadingStatus.entries,
                        selected = status,
                        label = { it.label },
                        onSelect = ::changeStatus,
                    )
                }

                if (!isEdit) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(if (expanded) "Fewer Details" else "More Details")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (!expanded) return@Column

                // --- Shelf ---------------------------------------------------
                CaptionedSection(caption = "Shelf") {
                    ShelfChips(library = library, selectedId = shelfId, onSelect = { shelfId = it })
                }

                // --- Type ----------------------------------------------------
                CaptionedSection(
                    caption = "Type",
                    helpText = "Not everything worth noting is one book: an author to " +
                        "explore, a topic to study, an article, or someone else's list.",
                ) {
                    OptionGrid(
                        options = ItemKind.entries,
                        selected = kind,
                        label = { if (it == ItemKind.List) "List" else it.label },
                        onSelect = { kind = it },
                        columns = 3,
                    )
                }

                // --- Details ---------------------------------------------------
                CaptionedSection(caption = "Details") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes") },
                            placeholder = { Text("Edition, tips, spoilers to avoid…") },
                            minLines = 1,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Link") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pagesText,
                                onValueChange = { pagesText = it.filter(Char::isDigit).take(5) },
                                label = { Text("Pages") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = yearText,
                                onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                                label = { Text("Year") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (coverUrl.isNotBlank()) {
                            CoverRow(coverUrl = coverUrl, onRemove = { coverUrl = "" })
                        }
                        Text(
                            text = "Format",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                        )
                        OptionGrid(
                            options = BookFormat.entries,
                            selected = format,
                            label = { it.label },
                            onSelect = { format = it },
                            columns = 4,
                        )
                        if (status == ReadingStatus.WantToRead) {
                            EditorSwitchRow(
                                label = "Up next",
                                supporting = "Pin it to the top of To Read",
                                checked = upNext,
                                onCheckedChange = { upNext = it },
                            )
                        }
                        if (status != ReadingStatus.Read) {
                            EditorSwitchRow(
                                label = "Need a copy",
                                supporting = "Still to buy, borrow or download",
                                checked = toAcquire,
                                onCheckedChange = { toAcquire = it },
                            )
                        }
                    }
                }

                // --- Status-specific -------------------------------------------
                when (status) {
                    ReadingStatus.WantToRead -> Unit
                    ReadingStatus.Reading -> CaptionedSection(caption = "Progress") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateRow("Started", startedOn) { pickingDate = DateField.Started }
                            OutlinedTextField(
                                value = currentPageText,
                                onValueChange = { currentPageText = it.filter(Char::isDigit).take(5) },
                                label = { Text("Current page") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    ReadingStatus.Read -> CaptionedSection(caption = "Finished") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Rating",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
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
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = canSave) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

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

private const val LOOKUP_DEBOUNCE_MS = 450L
private const val MAX_SUGGESTIONS = 4

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
 * One-tap fill for people who've recommended things before. Filters to
 * names starting with what's been typed; disappears once the field holds
 * an exact match.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommenderChips(all: List<String>, current: String, onPick: (String) -> Unit) {
    val typed = current.trim()
    if (all.any { it.equals(typed, ignoreCase = true) }) return
    val shown = all
        .filter { typed.isEmpty() || it.startsWith(typed, ignoreCase = true) }
        .take(MAX_RECOMMENDER_CHIPS)
    if (shown.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        shown.forEach { name ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(name) },
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private const val MAX_RECOMMENDER_CHIPS = 8

/** Wrap of shelf pills (accent dot + name), plus "None". */
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

@Composable
private fun CoverRow(coverUrl: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = coverUrl,
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp, 50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Cover from Open Library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}
