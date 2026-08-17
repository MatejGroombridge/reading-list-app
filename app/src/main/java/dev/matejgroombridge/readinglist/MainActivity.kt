package dev.matejgroombridge.readinglist

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.readinglist.data.model.ShelfStatus
import dev.matejgroombridge.readinglist.ui.ImportViewModel
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.SearchViewModel
import dev.matejgroombridge.readinglist.ui.SettingsViewModel
import dev.matejgroombridge.readinglist.ui.components.BookDetailActions
import dev.matejgroombridge.readinglist.ui.components.BookDetailSheet
import dev.matejgroombridge.readinglist.ui.components.ConfettiOverlay
import dev.matejgroombridge.readinglist.ui.screens.ImportListScreen
import dev.matejgroombridge.readinglist.ui.screens.LibraryScreen
import dev.matejgroombridge.readinglist.ui.screens.ReadScreen
import dev.matejgroombridge.readinglist.ui.screens.SearchScreen
import dev.matejgroombridge.readinglist.ui.screens.SettingsScreen
import dev.matejgroombridge.readinglist.ui.screens.UpNextScreen
import dev.matejgroombridge.readinglist.ui.theme.AppTheme
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import kotlinx.coroutines.launch

private object Routes {
    /** Single host route for the swipeable Up Next / Library / Read pager. */
    const val MAIN = "main"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

// Order is intentional: pager index 0 → Up Next, 1 → Library, 2 → Read.
// Library sits in the middle so it's reachable by a swipe from either side,
// and it's the page the app launches on. Adjust both this list AND the
// `when (page)` switch in MainPager() to add a tab.
private const val LIBRARY_PAGE_INDEX = 1
private val BOTTOM_TABS = listOf(
    BottomTab("Up Next", Icons.AutoMirrored.Outlined.PlaylistAdd),
    BottomTab("Library", Icons.AutoMirrored.Outlined.LibraryBooks),
    BottomTab("Read", Icons.Outlined.CheckCircle),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application),
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            AppTheme(themeMode = settings.themeMode, amoled = settings.amoled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppShell(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun rememberApplication(): Application {
    val ctx = LocalContext.current.applicationContext
    return ctx as Application
}

/**
 * Hosts navigation and owns the book detail sheet.
 *
 * The sheet lives here rather than inside each screen so a book opened from
 * the Library, the queue, the Read shelf, or a search result all get the same
 * sheet — and so it survives a swipe between pages while open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val app = rememberApplication()

    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
    val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var openBookId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val celebrate by libraryViewModel.celebrate.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.MAIN) {
            MainPager(
                libraryViewModel = libraryViewModel,
                swipeEnabled = settings.swipeToNavigate,
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenImport = { navController.navigate(Routes.IMPORT) },
                onOpenBook = { openBookId = it },
            )
        }
        composable(Routes.IMPORT) {
            val importViewModel: ImportViewModel = viewModel(factory = ImportViewModel.factory(app))
            ImportListScreen(
                viewModel = importViewModel,
                onBack = { navController.popBackStack() },
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Routes.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(app))
            SearchScreen(
                viewModel = searchViewModel,
                onBack = { navController.popBackStack() },
                onOpenBook = { openBookId = it },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                libraryViewModel = libraryViewModel,
                onBack = { navController.popBackStack() },
                onOpenImport = { navController.navigate(Routes.IMPORT) },
            )
        }
    }

    // Re-read the open book from live state every recomposition so edits made
    // in the sheet are reflected by its own controls immediately.
    val openBook = openBookId?.let { id ->
        (state.reading + state.read + state.sections.flatMap { it.books } + state.upNext)
            .firstOrNull { it.id == id }
    }

    if (openBook != null) {
        BookDetailSheet(
            book = openBook,
            sheetState = sheetState,
            onDismiss = { openBookId = null },
            onNeedDescription = { libraryViewModel.ensureDetail(openBook) },
            actions = BookDetailActions(
                onSetStatus = { status ->
                    libraryViewModel.setStatus(openBook.id, status)
                    // Finishing a book is a moment — close the sheet so the
                    // confetti has the screen to itself.
                    if (status == ShelfStatus.Read) {
                        scope.launch {
                            sheetState.hide()
                            openBookId = null
                        }
                    }
                },
                onSetRating = { libraryViewModel.setRating(openBook.id, it) },
                onSetRecSource = { source, kind ->
                    libraryViewModel.setRecSource(openBook.id, source, kind)
                },
                onSetNotes = { libraryViewModel.setNotes(openBook.id, it) },
                onSetGenre = { libraryViewModel.setGenreOverride(openBook.id, it) },
                onSetPrioritised = { libraryViewModel.setPrioritised(openBook.id, it) },
                onRemove = {
                    libraryViewModel.remove(openBook.id)
                    openBookId = null
                },
            ),
        )
    } else if (openBookId != null) {
        // The book vanished from under us (removed, or an import replaced the
        // shelf). Drop the reference rather than leaving an empty sheet.
        LaunchedEffect(openBookId) { openBookId = null }
    }

    if (settings.celebrateFinishes) {
        ConfettiOverlay(trigger = celebrate)
        LaunchedEffect(celebrate) {
            if (celebrate) libraryViewModel.consumeCelebration()
        }
    }
}

/**
 * Hosts the three top-level screens in a [HorizontalPager] so the user can
 * swipe between them. The bottom NavigationBar mirrors the pager's selected
 * index — tapping a tab animates the pager, swiping updates the tab.
 *
 * The FAB only appears on Library; "add a book" doesn't mean anything on the
 * Read shelf, and Up Next is populated from books already on the shelf.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPager(
    libraryViewModel: LibraryViewModel,
    swipeEnabled: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = LIBRARY_PAGE_INDEX,
        pageCount = { BOTTOM_TABS.size },
    )
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // Light buzz whenever the pager settles on a new page, whether from a
    // swipe or a tab tap. Snapshotting the previous page keeps the initial
    // composition silent.
    var lastPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            haptics.light()
            lastPage = pagerState.currentPage
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                BOTTOM_TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            } else {
                                haptics.light()
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == LIBRARY_PAGE_INDEX) {
                FloatingActionButton(
                    onClick = {
                        haptics.completion()
                        onOpenSearch()
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add a book")
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = swipeEnabled,
        ) { page ->
            when (page) {
                0 -> UpNextScreen(
                    viewModel = libraryViewModel,
                    contentPadding = padding,
                    onOpenBook = onOpenBook,
                )
                LIBRARY_PAGE_INDEX -> LibraryScreen(
                    viewModel = libraryViewModel,
                    contentPadding = padding,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = onOpenSettings,
                    onOpenImport = onOpenImport,
                    onOpenBook = onOpenBook,
                )
                2 -> ReadScreen(
                    viewModel = libraryViewModel,
                    contentPadding = padding,
                    onOpenBook = onOpenBook,
                )
            }
        }
    }
}
