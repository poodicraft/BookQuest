package com.poodicraft.bookquest.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.poodicraft.bookquest.R
import com.poodicraft.bookquest.data.AccountState
import com.poodicraft.bookquest.data.AppEvent
import com.poodicraft.bookquest.data.Badges
import com.poodicraft.bookquest.data.Classroom
import com.poodicraft.bookquest.data.Connectivity
import com.poodicraft.bookquest.data.CrashLog
import com.poodicraft.bookquest.data.CloudSync
import com.poodicraft.bookquest.data.UserRole
import com.poodicraft.bookquest.data.LibraryRepository
import com.poodicraft.bookquest.data.Prefs
import com.poodicraft.bookquest.ui.components.AppBackground
import com.poodicraft.bookquest.ui.components.ConfettiBurst
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CLASS = "class"
    const val CLASS_DETAIL = "classDetail"
    const val CLASS_QUIZ = "classQuiz"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val BOOK = "book"
    const val READER = "reader"
    const val CARDS = "cards"
    const val QUIZ = "quiz"
}

private data class Celebration(val emoji: String, val title: String, val message: String)

private data class TabItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val STUDENT_TABS = listOf(
    TabItem(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    TabItem(Routes.LIBRARY, R.string.nav_library, Icons.Rounded.AutoStories),
    TabItem(Routes.CLASS, R.string.nav_class, Icons.Rounded.School),
    TabItem(Routes.STATS, R.string.nav_stats, Icons.Rounded.EmojiEvents),
    TabItem(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings)
)

/** Teachers do not collect XP or badges, so the progress tab is not theirs. */
private val TEACHER_TABS = STUDENT_TABS.filterNot { it.route == Routes.STATS }

@Composable
fun BookQuestRoot(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LibraryRepository.get(context) }
    val prefs = remember { Prefs(context) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val cloud = remember { CloudSync.get(context) }
    val classroom = remember { Classroom.get(context) }
    val connectivity = remember { Connectivity.get(context) }
    val online by connectivity.online.collectAsStateWithLifecycle()
    val account by cloud.account.collectAsStateWithLifecycle()
    val schoolProfile by classroom.profile.collectAsStateWithLifecycle()
    val roleKnown by classroom.ready.collectAsStateWithLifecycle()

    // Pull the account's role and classes once per sign in, so no screen has to
    // fetch them on the way in and none of them can be missing on the way back.
    LaunchedEffect(account) {
        if (account is AccountState.SignedIn) classroom.ensureLoaded()
    }

    // Coming back online is the moment a failed backup can succeed, so it is
    // retried there and then rather than waiting to be asked.
    LaunchedEffect(online, account) {
        if (online && account is AccountState.SignedIn) {
            cloud.pushQuietly()
            classroom.ensureLoaded(force = true)
        }
    }

    // Signed out launches open on the welcome screen. "Not now" dismisses it for
    // this run of the app, so it is not a wall you cannot get past.
    var skippedSignIn by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) repository.importUris(uris)
    }

    val openImporter: () -> Unit = remember {
        {
            try {
                importLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.import_failed))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        repository.events.collect { event ->
            fun toast(message: String) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
            val teaching = classroom.profile.value.role == UserRole.TEACHER
            when (event) {
                is AppEvent.Xp ->
                    if (!teaching) toast(context.getString(R.string.xp_earned, event.amount))
                is AppEvent.Imported -> toast(context.getString(R.string.imported_count, event.count))
                is AppEvent.Failed -> toast(context.getString(R.string.import_failed))
                is AppEvent.LevelUp -> if (!teaching) {
                    confettiTrigger += 1
                    celebration = Celebration(
                        emoji = "🎉",
                        title = context.getString(R.string.level_up),
                        message = context.getString(R.string.level_up_message, event.level)
                    )
                }
                is AppEvent.BadgeUnlocked -> if (!teaching) {
                    val badge = Badges.ALL.firstOrNull { it.id == event.badgeId }
                    if (badge != null) {
                        confettiTrigger += 1
                        celebration = Celebration(
                            emoji = badge.emoji,
                            title = context.getString(badge.titleRes),
                            message = context.getString(badge.descRes)
                        )
                    }
                }
            }
        }
    }

    val isTeacher = schoolProfile.role == UserRole.TEACHER
    val tabs = if (isTeacher) TEACHER_TABS else STUDENT_TABS

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = tabs.any { it.route == currentRoute }

    // A crash the last time round. Shown once, then the report is cleared so it
    // cannot follow someone around; About keeps a copy until the next crash.
    var crashReport by remember { mutableStateOf(CrashLog.read(context)) }
    val pendingCrash = crashReport
    if (pendingCrash != null) {
        AlertDialog(
            onDismissRequest = { crashReport = null },
            icon = { Text(text = "🐞", fontSize = 30.sp) },
            title = { Text(stringResource(R.string.crash_title)) },
            text = { Text(stringResource(R.string.crash_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(context, pendingCrash)
                    crashReport = null
                }) {
                    Text(stringResource(R.string.copy_report))
                }
            },
            dismissButton = {
                TextButton(onClick = { crashReport = null }) {
                    Text(stringResource(R.string.dismiss_report))
                }
            }
        )
    }

    if (account is AccountState.SignedOut && !skippedSignIn) {
        AuthScreen(
            onSkip = { skippedSignIn = true },
            onSignedIn = { skippedSignIn = false }
        )
        return
    }

    // Straight after signing in, once: teacher or student, and who you are.
    //
    // Only ask once the answer is actually known to be missing. Before the
    // account has been read the role reads as UNKNOWN whether or not one was
    // ever chosen, and asking on that basis is what made this screen flash past
    // on every launch.
    var roleAsked by rememberSaveable { mutableStateOf(false) }
    if (account is AccountState.SignedIn && !roleKnown) {
        SettlingScreen()
        return
    }
    if (account is AccountState.SignedIn &&
        schoolProfile.role == UserRole.UNKNOWN &&
        !roleAsked
    ) {
        RoleScreen(onDone = { roleAsked = true })
        return
    }

    // Once, on the first run after installing, and after the account questions
    // so the app has already shown what it is before asking to interrupt you.
    var notifyAsked by rememberSaveable { mutableStateOf(prefs.onboarded) }
    if (!notifyAsked) {
        NotifyIntroScreen(onDone = {
            prefs.onboarded = true
            notifyAsked = true
        })
        return
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (onTab) {
                    BottomBar(
                        navController = navController,
                        currentRoute = currentRoute,
                        tabs = tabs
                    )
                }
            },
            floatingActionButton = {
                if (currentRoute == Routes.HOME || currentRoute == Routes.LIBRARY) {
                    ExtendedFloatingActionButton(
                        onClick = openImporter,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.import_books)) }
                    )
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (!online) {
                    Text(
                        text = stringResource(R.string.offline_banner),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        val books by repository.books.collectAsStateWithLifecycle()
                        val profile by repository.profile.collectAsStateWithLifecycle()
                        val teaching by classroom.profile.collectAsStateWithLifecycle()
                        HomeScreen(
                            books = books,
                            profile = profile,
                            isTeacher = teaching.role == UserRole.TEACHER,
                            onOpenClasses = { navController.navigate(Routes.CLASS) },
                            onOpenBook = { id -> navController.navigate("${Routes.BOOK}/$id") },
                            onRead = { id -> navController.navigate("${Routes.READER}/$id") },
                            onImport = openImporter,
                            onSeeLibrary = { navController.navigate(Routes.LIBRARY) }
                        )
                    }
                    composable(Routes.LIBRARY) {
                        val books by repository.books.collectAsStateWithLifecycle()
                        LibraryScreen(
                            books = books,
                            onOpenBook = { id -> navController.navigate("${Routes.BOOK}/$id") },
                            onImport = openImporter
                        )
                    }
                    composable(Routes.CLASS) {
                        ClassScreen(
                            onOpenClass = { id ->
                                navController.navigate("${Routes.CLASS_DETAIL}/$id")
                            },
                            onRunQuiz = { classId, assignmentId ->
                                navController.navigate(
                                    "${Routes.CLASS_QUIZ}/$classId/$assignmentId"
                                )
                            },
                            onSignIn = { skippedSignIn = false }
                        )
                    }
                    composable("${Routes.CLASS_DETAIL}/{classId}") { entry ->
                        ClassDetailScreen(
                            classId = entry.arguments?.getString("classId"),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("${Routes.CLASS_QUIZ}/{classId}/{assignmentId}") { entry ->
                        ClassQuizScreen(
                            classId = entry.arguments?.getString("classId"),
                            assignmentId = entry.arguments?.getString("assignmentId"),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.STATS) {
                        val teaching by classroom.profile.collectAsStateWithLifecycle()
                        if (teaching.role == UserRole.TEACHER) {
                            ClassScreen(
                                onOpenClass = { id ->
                                    navController.navigate("${Routes.CLASS_DETAIL}/$id")
                                },
                                onRunQuiz = { classId, assignmentId ->
                                    navController.navigate(
                                        "${Routes.CLASS_QUIZ}/$classId/$assignmentId"
                                    )
                                },
                                onSignIn = { skippedSignIn = false }
                            )
                        } else {
                            val books by repository.books.collectAsStateWithLifecycle()
                            val profile by repository.profile.collectAsStateWithLifecycle()
                            StatsScreen(books = books, profile = profile)
                        }
                    }
                    composable(Routes.SETTINGS) {
                        val profile by repository.profile.collectAsStateWithLifecycle()
                        SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            language = language,
                            onLanguageChange = onLanguageChange,
                            profile = profile,
                            repository = repository
                        )
                    }
                    composable("${Routes.BOOK}/{bookId}") { entry ->
                        val id = entry.arguments?.getString("bookId")
                        val books by repository.books.collectAsStateWithLifecycle()
                        BookDetailScreen(
                            book = books.firstOrNull { it.id == id },
                            repository = repository,
                            onBack = { navController.popBackStack() },
                            onRead = { navController.navigate("${Routes.READER}/$id") },
                            onCards = { navController.navigate("${Routes.CARDS}/$id") },
                            onQuiz = { navController.navigate("${Routes.QUIZ}/$id") }
                        )
                    }
                    composable("${Routes.READER}/{bookId}") { entry ->
                        val id = entry.arguments?.getString("bookId")
                        val books by repository.books.collectAsStateWithLifecycle()
                        ReaderScreen(
                            book = books.firstOrNull { it.id == id },
                            repository = repository,
                            prefs = prefs,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("${Routes.CARDS}/{bookId}") { entry ->
                        val id = entry.arguments?.getString("bookId")
                        val books by repository.books.collectAsStateWithLifecycle()
                        CardsScreen(
                            book = books.firstOrNull { it.id == id },
                            repository = repository,
                            onBack = { navController.popBackStack() },
                            onQuiz = { navController.navigate("${Routes.QUIZ}/$id") }
                        )
                    }
                    composable("${Routes.QUIZ}/{bookId}") { entry ->
                        val id = entry.arguments?.getString("bookId")
                        val books by repository.books.collectAsStateWithLifecycle()
                        QuizScreen(
                            book = books.firstOrNull { it.id == id },
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                ConfettiBurst(trigger = confettiTrigger)
                }
            }
        }
    }

    val currentCelebration = celebration
    if (currentCelebration != null) {
        AlertDialog(
            onDismissRequest = { celebration = null },
            confirmButton = {
                TextButton(onClick = { celebration = null }) {
                    Text(stringResource(R.string.awesome))
                }
            },
            icon = { Text(text = currentCelebration.emoji, fontSize = 40.sp) },
            title = { Text(currentCelebration.title) },
            text = { Text(currentCelebration.message) }
        )
    }
}

/**
 * A blank hold, dressed as the app rather than as a spinner, for the moment
 * between the app opening and the account being read. It is usually invisible;
 * the point is that whatever replaces it is correct.
 */
@Composable
private fun SettlingScreen() {
    AppBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BottomBar(
    navController: NavHostController,
    currentRoute: String?,
    tabs: List<TabItem>
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    // Always navigate, even when the bar already draws this tab as
                    // selected: the highlight is read from the back stack, and if
                    // that reading went stale the old "skip when selected" guard
                    // turned the tab into a dead button. launchSingleTop keeps a
                    // re-tap of the tab you are on from stacking a second copy.
                    //
                    // Pop to the graph's own start destination and keep no saved
                    // per-tab back stacks: a restored stack could put the class
                    // screen back on top of Home, which is what made Home look
                    // unreachable.
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
