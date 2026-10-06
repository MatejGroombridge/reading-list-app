package dev.matejgroombridge.readinglist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.matejgroombridge.readinglist.data.repository.LibraryRepository
import dev.matejgroombridge.readinglist.domain.SharedTextParser
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.SettingsViewModel
import dev.matejgroombridge.readinglist.ui.components.BookEditorDialog
import dev.matejgroombridge.readinglist.ui.components.BookEditorResult
import dev.matejgroombridge.readinglist.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The fastest way onto the list: a translucent activity that floats the
 * add dialog over whatever app you were in. Reached from
 *  - the share sheet (a link or text from Goodreads, a browser, a chat),
 *  - the text-selection menu ("Add to Reading List" on highlighted text),
 *  - the launcher shortcut and the widget's + button.
 * Shared text is parsed into a best-guess title/author/link (see
 * [SharedTextParser]); the dialog is the same one the app uses, so online
 * suggestions and the duplicate warning work here too.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefill = SharedTextParser.parse(sharedText(intent))

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(application))
            val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(application))
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
            val repository = remember { LibraryRepository(applicationContext) }

            AppTheme(themeMode = settings.themeMode, amoled = settings.amoled) {
                BookEditorDialog(
                    existing = null,
                    prefill = prefill,
                    library = state.library,
                    onlineLookup = settings.onlineLookup,
                    showCovers = settings.showCovers,
                    onSearch = { libraryViewModel.search(it) },
                    onDismiss = ::finish,
                    onOpenDuplicate = { book ->
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_BOOK, book.id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                        finish()
                    },
                    onResult = { result ->
                        if (result is BookEditorResult.Save) {
                            // Write before finishing: the ViewModel's scope
                            // dies with this activity, so the save has to
                            // complete first.
                            lifecycleScope.launch {
                                repository.addBook(result.book)
                                Toast.makeText(applicationContext, "Added to your reading list", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } else {
                            finish()
                        }
                    },
                )
            }
        }
    }

    /**
     * Pulls the text out of a share or text-selection intent. Some apps share
     * only a link and put the book's title in the subject line, so a bare
     * link is joined with its subject.
     */
    private fun sharedText(intent: Intent?): String? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
                when {
                    subject.isNotEmpty() && text.startsWith("http") && ' ' !in text -> "$subject $text"
                    text.isNotEmpty() -> text
                    else -> subject
                }
            }
            else -> null
        }
    }
}
