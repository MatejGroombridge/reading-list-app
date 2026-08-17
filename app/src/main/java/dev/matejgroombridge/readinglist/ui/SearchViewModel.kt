package dev.matejgroombridge.readinglist.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.readinglist.data.model.Book
import dev.matejgroombridge.readinglist.data.network.OpenLibraryApi
import dev.matejgroombridge.readinglist.data.repository.BookRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the search screen is currently showing. */
sealed interface SearchState {
    /** Nothing typed yet — the screen shows its prompt and tips. */
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val books: List<Book>) : SearchState
    data class Empty(val query: String) : SearchState
    data class Error(val message: String) : SearchState
}

/**
 * Drives book search against Open Library.
 *
 * The query is debounced and the in-flight request cancelled on every
 * keystroke (`flatMapLatest`), so typing a title fires one search at the end
 * rather than one per character — Open Library is a free, unmetered service
 * and hammering it per keystroke would be both slow and rude.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: BookRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Ids already on the shelf, so results can show "On your list" instead of Add. */
    val shelfIds: StateFlow<Set<String>> = repository.books
        .map { books -> books.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val state: StateFlow<SearchState> = _query
        .debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { raw ->
            val trimmed = raw.trim()
            flow {
                if (trimmed.length < MIN_QUERY_LENGTH) {
                    emit(SearchState.Idle)
                    return@flow
                }
                emit(SearchState.Loading)
                val result = runCatching { OpenLibraryApi.search(trimmed) }
                emit(
                    result.fold(
                        onSuccess = { books ->
                            if (books.isEmpty()) SearchState.Empty(trimmed)
                            else SearchState.Results(books)
                        },
                        onFailure = { SearchState.Error(it.toUserMessage()) },
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    /** Re-runs the current search — the Retry button on the error state. */
    fun retry() {
        val current = _query.value
        _query.value = ""
        _query.value = current
    }

    /** Adds a search result to the shelf as Want to Read. */
    fun add(book: Book, todayEpochDay: Long) {
        viewModelScope.launch { repository.add(book, todayEpochDay) }
    }

    fun remove(bookId: String) {
        viewModelScope.launch { repository.remove(bookId) }
    }

    companion object {
        private const val DEBOUNCE_MS = 350L

        /** One letter matches half the catalogue; two is the useful floor. */
        private const val MIN_QUERY_LENGTH = 2

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(BookRepository(application.applicationContext)) }
        }
    }
}

/**
 * Turns a network failure into something worth reading on screen.
 *
 * The distinction that matters is "you're offline" versus "Open Library is
 * having a moment" — the first is the user's to fix, the second is only worth
 * waiting out. The earlier catch-all ("Something went wrong") collapsed those
 * together and made a routine upstream 503 look like a broken app, so the
 * HTTP status is now named explicitly.
 */
internal fun Throwable.toUserMessage(): String = when (this) {
    is ServerResponseException ->
        "Open Library is having problems right now (error ${response.status.value}). " +
            "It's usually short-lived — try again in a minute."

    is ClientRequestException ->
        "Open Library rejected that search (error ${response.status.value})."

    else -> {
        // Transport-level failures don't share a common Ktor supertype across
        // engines, so match on the exception name rather than the class.
        val name = this::class.simpleName.orEmpty()
        when {
            name.contains("UnknownHost") ||
                name.contains("UnresolvedAddress") ||
                name.contains("ConnectException") ->
                "Can't reach Open Library. Check your connection."

            name.contains("Timeout") ->
                "Open Library isn't responding. Try again in a minute."

            else -> "Search failed: ${name.ifBlank { "unknown error" }}. Try again."
        }
    }
}
