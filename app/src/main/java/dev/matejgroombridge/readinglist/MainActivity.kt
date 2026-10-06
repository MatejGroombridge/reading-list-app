package dev.matejgroombridge.readinglist

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.readinglist.data.model.ReadingStatus
import dev.matejgroombridge.readinglist.data.settings.Settings
import dev.matejgroombridge.readinglist.notifications.Notifications
import dev.matejgroombridge.readinglist.notifications.ReminderScheduler
import dev.matejgroombridge.readinglist.ui.LibraryUiState
import dev.matejgroombridge.readinglist.ui.LibraryViewModel
import dev.matejgroombridge.readinglist.ui.SettingsViewModel
import dev.matejgroombridge.readinglist.ui.components.BookActions
import dev.matejgroombridge.readinglist.ui.components.BookDialog
import dev.matejgroombridge.readinglist.ui.components.BookDialogHost
import dev.matejgroombridge.readinglist.ui.components.ConfettiOverlay
import dev.matejgroombridge.readinglist.ui.screens.ArchiveScreen
import dev.matejgroombridge.readinglist.ui.screens.BulkAddScreen
import dev.matejgroombridge.readinglist.ui.screens.DuelScreen
import dev.matejgroombridge.readinglist.ui.screens.NotebookScreen
import dev.matejgroombridge.readinglist.ui.screens.ReadScreen
import dev.matejgroombridge.readinglist.ui.screens.ReadingScreen
import dev.matejgroombridge.readinglist.ui.screens.SearchScreen
import dev.matejgroombridge.readinglist.ui.screens.SettingsScreen
import dev.matejgroombridge.readinglist.ui.screens.ShelvesScreen
import dev.matejgroombridge.readinglist.ui.screens.ToReadScreen
import dev.matejgroombridge.readinglist.ui.screens.countLabel
import dev.matejgroombridge.readinglist.ui.theme.AppTheme
import dev.matejgroombridge.readinglist.ui.util.rememberHaptics
import kotlinx.coroutines.launch

private object Routes {
    /** Single host route for the swipeable Reading / To Read / Read pager. */
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val ARCHIVE = "archive"
    const val SHELVES = "shelves"
    const val BULK_ADD = "bulk_add"
    const val NOTEBOOK = "notebook"
    const val DUEL = "duel"
}

private data class BottomTab(val label: String, val icon: ImageVector, val status: ReadingStatus)

// Pipeline order left to right, landing on To Read in the middle so either
// neighbour is one swipe away (the Habit Tracker "Today in the middle"
// arrangement). Keep in sync with the `when (page)` in MainPager.
private val BOTTOM_TABS = listOf(
    BottomTab("Reading", Icons.Outlined.AutoStories, ReadingStatus.Reading),
    BottomTab("To Read", Icons.Outlined.BookmarkBorder, ReadingStatus.WantToRead),
    BottomTab("Read", Icons.Outlined.LibraryAddCheck, ReadingStatus.Read),
)

/** Something a launching intent asked for: a page (notification) or a book (widget). */
private sealed interface LaunchRequest {
    data class Page(val index: Int) : LaunchRequest
    data class Book(val id: String) : LaunchRequest
}

class MainActivity : ComponentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cheap and idempotent: make sure the channel exists and the alarm
        // matches settings (it's lost on app updates as well as reboots).
        Notifications.ensureChannel(this)
        lifecycleScope.launch { ReminderScheduler.reschedule(applicationContext) }
        publishShortcuts()

        if (savedInstanceState == null) launchRequest = parseLaunch(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(application))
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            AppTheme(themeMode = settings.themeMode, amoled = settings.amoled) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppShell(
                        settingsViewModel = settingsViewModel,
                        launchRequest = launchRequest,
                        onLaunchHandled = { launchRequest = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = parseLaunch(intent)
    }

    private fun parseLaunch(intent: Intent?): LaunchRequest? {
        intent ?: return null
        intent.getStringExtra(EXTRA_OPEN_BOOK)?.let { return LaunchRequest.Book(it) }
        val page = intent.getIntExtra(EXTRA_OPEN_PAGE, -1)
        return if (page in BOTTOM_TABS.indices) LaunchRequest.Page(page) else null
    }

    /**
     * Long-press-the-icon "Add to Reading List". Published dynamically rather
     * than via shortcuts.xml because static shortcuts hard-code the package
     * name, which differs on `.debug` builds.
     */
    private fun publishShortcuts() {
        val add = ShortcutInfoCompat.Builder(this, "add_book")
            .setShortLabel(getString(R.string.shortcut_add_short))
            .setLongLabel(getString(R.string.shortcut_add_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_add))
            .setIntent(Intent(this, QuickAddActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, listOf(add)) }
    }

    companion object {
        const val EXTRA_OPEN_PAGE = "open_page"
        const val EXTRA_OPEN_BOOK = "open_book"
        const val PAGE_READING = 0
        const val PAGE_TO_READ = 1
        const val PAGE_READ = 2
    }
}

@Composable
private fun AppShell(
    settingsViewModel: SettingsViewModel,
    launchRequest: LaunchRequest?,
    onLaunchHandled: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
    val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(initialPage = MainActivity.PAGE_TO_READ) { BOTTOM_TABS.size }

    var dialog by remember { mutableStateOf<BookDialog?>(null) }
    var confetti by remember { mutableStateOf(false) }

    val currentState by rememberUpdatedState(state)
    val currentSettings by rememberUpdatedState(settings)
    val actions = remember(libraryViewModel, haptics) {
        BookActions(
            viewModel = libraryViewModel,
            state = { currentState },
            settings = { currentSettings },
            haptics = haptics,
            open = { dialog = it },
            onArchived = { book ->
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = "Archived “${book.title}”",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) libraryViewModel.setArchived(book.id, false)
                }
            },
            onFinished = {
                // Flip through false so a second finish in a row still
                // re-triggers the overlay.
                scope.launch {
                    confetti = false
                    withFrameNanos { }
                    confetti = true
                }
            },
        )
    }

    LaunchedEffect(launchRequest) {
        when (val request = launchRequest ?: return@LaunchedEffect) {
            is LaunchRequest.Page -> {
                navController.popBackStack(Routes.MAIN, inclusive = false)
                pagerState.scrollToPage(request.index)
            }
            is LaunchRequest.Book -> dialog = BookDialog.Overview(request.id)
        }
        onLaunchHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.MAIN) {
                MainPager(
                    pagerState = pagerState,
                    state = state,
                    settings = settings,
                    settingsViewModel = settingsViewModel,
                    actions = actions,
                    snackbar = snackbar,
                    navController = navController,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    state = state,
                    settings = settings,
                    actions = actions,
                    snackbar = snackbar,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    libraryViewModel = libraryViewModel,
                    state = state,
                    snackbar = snackbar,
                    onBack = { navController.popBackStack() },
                    onOpenShelves = { navController.navigate(Routes.SHELVES) },
                    onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                    onOpenNotebook = { navController.navigate(Routes.NOTEBOOK) },
                    onOpenBulkAdd = { navController.navigate(Routes.BULK_ADD) },
                )
            }
            composable(Routes.ARCHIVE) {
                ArchiveScreen(
                    state = state,
                    settings = settings,
                    viewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SHELVES) {
                ShelvesScreen(state = state, viewModel = libraryViewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.DUEL) {
                DuelScreen(
                    state = state,
                    settings = settings,
                    viewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTEBOOK) {
                NotebookScreen(state = state, viewModel = libraryViewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.BULK_ADD) {
                BulkAddScreen(
                    state = state,
                    viewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                    onDone = { added ->
                        navController.popBackStack()
                        scope.launch { snackbar.showSnackbar("Added ${countLabel(added)}") }
                    },
                )
            }
        }

        BookDialogHost(
            dialog = dialog,
            state = state,
            settings = settings,
            viewModel = libraryViewModel,
            actions = actions,
            haptics = haptics,
        )
        ConfettiOverlay(trigger = confetti)
    }
}

/**
 * The three top-level pages in a [HorizontalPager], mirrored by the bottom
 * bar exactly as in Habit Tracker. The FAB is on every page and adds with
 * that page's status — adding from Reading starts a book, adding from Read
 * logs one you've finished.
 */
@Composable
private fun MainPager(
    pagerState: PagerState,
    state: LibraryUiState,
    settings: Settings,
    settingsViewModel: SettingsViewModel,
    actions: BookActions,
    snackbar: SnackbarHostState,
    navController: NavHostController,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // Light buzz when the pager settles on a new page; the snapshot of the
    // previous page keeps the initial composition quiet.
    var lastPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            haptics.light()
            lastPage = pagerState.currentPage
        }
    }

    val openSettings = { navController.navigate(Routes.SETTINGS) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                BOTTOM_TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) scope.launch { pagerState.animateScrollToPage(index) }
                            else haptics.light()
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                haptics.completion()
                actions.create(BOTTOM_TABS[pagerState.currentPage].status)
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add")
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = settings.swipeToNavigate,
        ) { page ->
            when (page) {
                MainActivity.PAGE_READING -> ReadingScreen(
                    state = state,
                    settings = settings,
                    actions = actions,
                    contentPadding = padding,
                    onOpenSettings = openSettings,
                )
                MainActivity.PAGE_TO_READ -> ToReadScreen(
                    state = state,
                    settings = settings,
                    actions = actions,
                    contentPadding = padding,
                    onGroupBy = settingsViewModel::setGroupBy,
                    onSortOrder = settingsViewModel::setSortOrder,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenDuel = { navController.navigate(Routes.DUEL) },
                    onOpenSettings = openSettings,
                )
                MainActivity.PAGE_READ -> ReadScreen(
                    state = state,
                    settings = settings,
                    actions = actions,
                    contentPadding = padding,
                    onOpenSettings = openSettings,
                )
            }
        }
    }
}
