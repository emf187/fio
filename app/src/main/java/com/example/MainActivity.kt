package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                accentColor = settings.accentColor,
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

    val profileName by repository.profileName.collectAsState()
    var currentScreen by remember { mutableStateOf(FioScreen.DASHBOARD) }

    // First-launch Profile Name Prompt
    var promptNameInput by remember { mutableStateOf("") }
    val showProfilePrompt = profileName.isBlank()

    if (showProfilePrompt) {
        AlertDialog(
            onDismissRequest = { /* Force explicit entry on first start */ },
            containerColor = colors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Fio Logo",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Welcome to Fio", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("effio • to realize & know", color = colors.textMuted, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your personal offline life planner & Wingman study companion. All documents, schedules, books, and habits stay exclusively on your device.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "What should Fio & Wingman call you?",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = promptNameInput,
                        onValueChange = { promptNameInput = it },
                        placeholder = { Text("Enter your name...", color = colors.textMuted) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_profile_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = promptNameInput.trim().ifEmpty { "User" }
                        repository.setProfileName(name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("onboarding_save_button")
                ) {
                    Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
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
                val screenTitle = if (currentScreen == FioScreen.DASHBOARD && profileName.isNotBlank()) {
                    profileName
                } else {
                    currentScreen.title
                }
                FioTopBar(
                    title = screenTitle,
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
                    FioScreen.GALLERY -> GalleryScreen(
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
