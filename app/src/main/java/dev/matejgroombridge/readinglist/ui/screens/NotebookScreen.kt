package dev.matejgroombridge.readinglist.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import kotlinx.coroutines.delay

/**
 * Free-form notes about reading itself — principles, half-formed plans,
 * advice someone gave about *how* to read. The Notion page kept these
 * between its lists; here they get one page. Saves as you type.
 */
@Composable
fun NotebookScreen(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    // Seed once the stored text has loaded, then the field owns the text.
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loaded) {
        if (state.loaded && text == null) text = state.library.notes
    }
    // Debounced save so typing doesn't rewrite the library on every key.
    LaunchedEffect(text) {
        val current = text ?: return@LaunchedEffect
        if (current == state.library.notes) return@LaunchedEffect
        delay(SAVE_DEBOUNCE_MS)
        viewModel.setNotes(current)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopBar(title = "Notebook", onBack = {
                text?.let { if (it != state.library.notes) viewModel.setNotes(it) }
                onBack()
            })
        },
    ) { padding ->
        OutlinedTextField(
            value = text.orEmpty(),
            onValueChange = { text = it },
            enabled = text != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private const val SAVE_DEBOUNCE_MS = 600L
