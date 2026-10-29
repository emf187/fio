package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.storage.FioRepository
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

        setContent {
            val settings by repository.settings.collectAsState()

            FioTheme(
                themeName = settings.theme,
                reduceMotion = settings.reduceMotion
            ) {
                FioAppContent(
                    repository = repository,
                    aiClient = aiClient
                )
            }
        }
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

    var currentScreen by remember { mutableStateOf(FioScreen.DASHBOARD) }

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
                FioTopBar(
                    title = currentScreen.title,
                    onOpenNav = { coroutineScope.launch { drawerState.open() } },
                    actions = {
                        IconButton(
                            onClick = { currentScreen = FioScreen.SETTINGS }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API Keys & Settings",
                                tint = colors.textSecondary
                            )
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
                    FioScreen.STUDY -> StudyCompanionScreen(
                        repository = repository,
                        aiClient = aiClient
                    )
                    FioScreen.TUTOR -> TutorScreen(
                        repository = repository,
                        aiClient = aiClient
                    )
                    FioScreen.TIMER -> TimerLockdownScreen(
                        repository = repository
                    )
                    FioScreen.NOTES -> NotesScreen(
                        repository = repository
                    )
                    FioScreen.HEALTH -> HealthTrackingScreen(
                        repository = repository
                    )
                    FioScreen.STATS -> StatsLifeScoreScreen(
                        repository = repository,
                        aiClient = aiClient
                    )
                    FioScreen.SETTINGS -> SettingsScreen(
                        repository = repository
                    )
                }
            }
        }
    }
}
