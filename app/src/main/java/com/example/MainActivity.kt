package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.storage.FioRepository
import com.example.fio.focus.FocusTimer
import com.example.fio.focus.FocusTimerService
import com.example.fio.ui.components.FioScreen
import com.example.fio.ui.components.FioSidebarDrawer
import com.example.fio.ui.components.FioTopBar
import com.example.fio.ui.screens.*
import com.example.fio.ui.theme.FioTheme
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: FioRepository
    private val aiClient = FioAiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = FioRepository(applicationContext)
        // Finished focus sessions land straight in the study logs; the
        // service wires itself up through the singleton controller.
        FocusTimer.sessionLogger = { log -> repository.logFocusSession(log) }
        FocusTimer.serviceStarter = { FocusTimerService.start(it) }
        FocusTimer.serviceStopper = { FocusTimerService.stop(it) }
        // Any reminder mirror drift (e.g. events imported without a device clone)
        // gets re-armed on every app start.
        runCatching { com.example.fio.alarms.FioReminderAlarms.rescheduleAll(applicationContext) }

        setContent {
            val settings by repository.settings.collectAsState()

            FioTheme(
                themeName = settings.theme,
                accentColor = settings.accentColor,
                reduceMotion = settings.reduceMotion,
                fontScale = settings.fontScale
            ) {
                FioAppContent(
                    repository = repository,
                    aiClient = aiClient
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Durable snapshot before the OS can kill the process — fixes the
        // "resets like a fresh install" behaviour on low-RAM devices.
        runCatching { repository.flushNow() }
    }
}

@Composable
fun FioAppContent(
    repository: FioRepository,
    aiClient: FioAiClient
) {
    val colors = LocalFioColors.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val profileName by repository.profileName.collectAsState()
    var currentScreen by remember { mutableStateOf(FioScreen.DASHBOARD) }

    // First launch: the guided setup (name → AI key → ready) gates the whole
    // app. Cancel/Skip inside it keeps Fio usable without either.
    val onboarded by repository.onboarded.collectAsState()
    if (!onboarded) {
        OnboardingScreen(repository = repository, aiClient = aiClient)
        return
    }

    // Android Back button handler
    BackHandler(enabled = drawerState.isOpen || currentScreen != FioScreen.DASHBOARD) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (currentScreen != FioScreen.DASHBOARD) {
            currentScreen = FioScreen.DASHBOARD
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FioSidebarDrawer(
                currentScreen = currentScreen,
                profileName = profileName,
                onSelectScreen = { screen ->
                    currentScreen = screen
                    coroutineScope.launch { drawerState.close() }
                },
                onClose = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                // Screens with their own large heading keep the top bar icon-only;
                // two titles on one screen was the biggest source of clutter.
                val hasOwnHeading = currentScreen != FioScreen.STUDY &&
                    currentScreen != FioScreen.CALCULATOR
                val screenTitle = if (hasOwnHeading) "" else currentScreen.title
                FioTopBar(
                    title = screenTitle,
                    onOpenNav = { coroutineScope.launch { drawerState.open() } },
                    actions = {
                        if (currentScreen != FioScreen.SETTINGS) {
                            IconButton(
                                onClick = { currentScreen = FioScreen.SETTINGS }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }
                )
            },
            containerColor = colors.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .background(colors.background)
            ) {
                when (currentScreen) {
                    FioScreen.DASHBOARD -> DashboardScreen(
                        repository = repository,
                        aiClient = aiClient,
                        onNavigate = { screen -> currentScreen = screen }
                    )
                    FioScreen.CALENDAR -> CalendarScreen(
                        repository = repository
                    )
                    FioScreen.TIMETABLE -> TimetableScreen(
                        repository = repository
                    )
                    FioScreen.STUDY -> StudyCompanionScreen(
                        repository = repository,
                        aiClient = aiClient
                    )
                    FioScreen.TUTOR -> TutorScreen(
                        repository = repository,
                        aiClient = aiClient
                    )
                    FioScreen.CALCULATOR -> CalculatorScreen()
                    FioScreen.TIMER -> TimerLockdownScreen(
                        repository = repository
                    )
                    FioScreen.NOTES -> NotesScreen(
                        repository = repository
                    )
                    FioScreen.GALLERY -> GalleryScreen(
                        repository = repository
                    )
                    FioScreen.HEALTH -> HealthTrackingScreen(
                        repository = repository,
                        onOpenHabits = { currentScreen = FioScreen.HABITS }
                    )
                    FioScreen.HABITS -> HabitsScreen(
                        repository = repository
                    )
                    FioScreen.STATS -> StatsLifeScoreScreen(
                        repository = repository,
                        aiClient = aiClient,
                        onOpenHabits = { currentScreen = FioScreen.HABITS }
                    )
                    FioScreen.SETTINGS -> SettingsScreen(
                        repository = repository
                    )
                }
            }
        }
    }
}
