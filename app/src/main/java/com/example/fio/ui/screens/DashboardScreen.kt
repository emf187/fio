package com.example.fio.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.ai.AiResult
import com.example.fio.data.ai.FioAiClient
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.data.storage.ImageStorage
import com.example.fio.ui.components.*
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: FioRepository,
    aiClient: FioAiClient,
    onNavigate: (FioScreen) -> Unit
) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val profileName by repository.profileName.collectAsState()
    val events by repository.events.collectAsState()
    val habits by repository.habits.collectAsState()
    val goals by repository.goals.collectAsState()
    val settings by repository.settings.collectAsState()
    val lifeScore = remember(events, habits, goals) { repository.calculateLifeScore() }

    // Wingman Agent State (Full App Control & Vision)
    var agentInput by remember { mutableStateOf("") }
    var agentAttachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var agentStoredImagePath by remember { mutableStateOf<String?>(null) }
    var isAgentLoading by remember { mutableStateOf(false) }
    var agentReply by remember { mutableStateOf<String?>(null) }
    var agentPendingActions by remember { mutableStateOf<List<AgentAction>>(emptyList()) }
    var agentErrorMessage by remember { mutableStateOf<String?>(null) }

    val agentPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> agentAttachedImageUri = uri }
    )

    // Camera capture for the agent card (shared gallery + camera everywhere).
    val captureAgentPhoto = rememberCameraCapture { uri -> agentAttachedImageUri = uri }

    val pendingSleepStart = remember { repository.getPendingSleepStart() }
    var isSleeping by remember { mutableStateOf(pendingSleepStart != null) }

    // Execute actions proposed by Wingman to control the whole app
    fun executeAction(action: AgentAction) {
        when (action.type) {
            "ADD_EVENT" -> {
                val title = action.payload["title"] ?: "New Session"
                val offsetMin = action.payload["startMinutesFromNow"]?.toLongOrNull() ?: 60L
                val durationMin = action.payload["durationMinutes"]?.toLongOrNull() ?: 60L
                val catStr = action.payload["category"] ?: "STUDY"
                val cat = try { EventType.valueOf(catStr) } catch (e: Exception) { EventType.STUDY }
                val start = System.currentTimeMillis() + (offsetMin * 60 * 1000L)
                repository.addEvent(
                    ScheduleEvent(
                        title = title,
                        startTimeMillis = start,
                        endTimeMillis = start + (durationMin * 60 * 1000L),
                        type = cat
                    )
                )
                Toast.makeText(context, "Added to schedule: $title", Toast.LENGTH_SHORT).show()
            }
            "LOG_MEAL" -> {
                val note = action.payload["note"] ?: "Meal"
                val hoursAgo = action.payload["hoursAgo"]?.toDoubleOrNull() ?: 0.0
                val time = System.currentTimeMillis() - (hoursAgo * 3600 * 1000L).toLong()
                repository.logMeal(note, time)
                Toast.makeText(context, "Logged meal: $note", Toast.LENGTH_SHORT).show()
            }
            "LOG_SLEEP" -> {
                val hours = action.payload["hours"]?.toDoubleOrNull() ?: 8.0
                val isNap = action.payload["type"].equals("NAP", ignoreCase = true)
                val end = System.currentTimeMillis()
                val start = end - (hours * 3600 * 1000L).toLong()
                repository.logSleep(SleepEntry(type = if (isNap) SleepType.NAP else SleepType.NIGHT, startTimeMillis = start, endTimeMillis = end))
                Toast.makeText(context, "Logged ${if (isNap) "nap" else "sleep"}: ${hours}h", Toast.LENGTH_SHORT).show()
            }
            "TOGGLE_HABIT" -> {
                val name = action.payload["habitName"] ?: ""
                val habit = habits.find { it.name.contains(name, ignoreCase = true) }
                if (habit != null) {
                    repository.toggleHabitToday(habit.id)
                    Toast.makeText(context, "Checked off habit: ${habit.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Habit not found: $name", Toast.LENGTH_SHORT).show()
                }
            }
            "ADD_HABIT" -> {
                val name = action.payload["habitName"] ?: "New Habit"
                val desc = action.payload["description"] ?: ""
                repository.addHabit(HabitItem(name = name, description = desc))
                Toast.makeText(context, "Created new habit: $name", Toast.LENGTH_SHORT).show()
            }
            "CREATE_NOTE" -> {
                val title = action.payload["title"] ?: "Note"
                val content = action.payload["content"] ?: ""
                val imgs = if (agentStoredImagePath != null) listOf(agentStoredImagePath!!) else emptyList()
                repository.saveNote(NoteItem(title = title, content = content, imageUris = imgs))
                Toast.makeText(context, "Created note: $title", Toast.LENGTH_SHORT).show()
            }
            "ADD_GOAL" -> {
                val title = action.payload["title"] ?: "New Goal"
                val target = action.payload["targetValue"]?.toDoubleOrNull() ?: 100.0
                val unit = action.payload["unit"] ?: "%"
                val cat = action.payload["category"] ?: "Study"
                repository.addGoal(GoalItem(title = title, targetValue = target, unit = unit, category = cat))
                Toast.makeText(context, "Added goal: $title", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- 0. Greeting ---
        item(key = "greeting_header") {
            val hour = java.time.LocalTime.now().hour
            val greeting = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
            val firstName = profileName.trim().substringBefore(' ')
            val today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.UK))
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = if (firstName.isBlank()) greeting else "$greeting, $firstName",
                    style = com.example.fio.ui.theme.FioType.display,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = today, style = com.example.fio.ui.theme.FioType.small, color = colors.textMuted)
            }
        }

        // --- 1. Life score ---
        item(key = "life_score_card") {
            FioCard(
                backgroundColor = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(FioScreen.STATS) }
                    .testTag("life_score_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Today's life score",
                            style = com.example.fio.ui.theme.FioType.small,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${lifeScore.overallScore}",
                                color = colors.textPrimary,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-1.2).sp,
                                lineHeight = 48.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val trendColor = if (lifeScore.trend >= 0) colors.success else colors.error
                            Text(
                                text = "${if (lifeScore.trend >= 0) "+" else ""}${lifeScore.trend}",
                                color = trendColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.fio.ui.components.FioProgressLine(
                            fraction = (lifeScore.overallScore / 100f).coerceIn(0f, 1f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = lifeScore.note,
                            style = com.example.fio.ui.theme.FioType.small,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // --- 2. Quick actions ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.fio.ui.components.FioSecondaryButton(
                    text = "I ate",
                    compact = true,
                    onClick = { repository.logMeal("Quick meal") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_meal_button")
                )

                com.example.fio.ui.components.FioSecondaryButton(
                    text = if (isSleeping) "Awake" else "Sleep",
                    compact = true,
                    onClick = {
                        if (isSleeping) {
                            repository.quickSleepAwake()
                            isSleeping = false
                        } else {
                            repository.quickSleepStart()
                            isSleeping = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_sleep_button")
                )

                com.example.fio.ui.components.FioPrimaryButton(
                    text = "Focus",
                    compact = true,
                    onClick = { onNavigate(FioScreen.TIMER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_focus_button")
                )
            }
        }

        // --- 3. Wingman AI Agent (Full App Control & Multimodal Vision) ---
        item {
            FioCard(
                backgroundColor = colors.surfaceElevated,
                borderColor = colors.border,
                padding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask Wingman", style = com.example.fio.ui.theme.FioType.section, color = colors.textPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.clickable { onNavigate(FioScreen.SETTINGS) }) {
                        FioBadge(
                            text = if (settings.aiApiKey.isNotBlank()) "Ready" else "Add key",
                            color = if (settings.aiApiKey.isNotBlank()) colors.success else colors.warning
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Schedule, log meals and sleep, or ask about a photo.",
                    style = com.example.fio.ui.theme.FioType.small,
                    color = colors.textMuted
                )

                // Attached Image preview
                agentAttachedImageUri?.let { uri ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Photo attached", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Wingman will look at it", color = colors.textMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = {
                            agentAttachedImageUri = null
                            agentStoredImagePath = null
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = colors.textMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attach image: gallery or camera.
                    AttachImageButton(
                        hasImage = agentAttachedImageUri != null,
                        onGallery = {
                            agentPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCamera = captureAgentPhoto,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .testTag("wingman_upload_image_button")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = agentInput,
                        onValueChange = { agentInput = it },
                        placeholder = { Text("Ask me to schedule, log, or look at a photo", color = colors.textMuted, fontSize = 12.5.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_schedule_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if ((agentInput.isNotBlank() || agentAttachedImageUri != null) && !isAgentLoading) {
                                isAgentLoading = true
                                agentErrorMessage = null
                                agentReply = null
                                agentPendingActions = emptyList()
                                coroutineScope.launch {
                                    // Process and store image locally if attached
                                    val storedData = if (agentAttachedImageUri != null) {
                                        ImageStorage.processAndStoreImage(context, agentAttachedImageUri!!)
                                    } else null

                                    agentStoredImagePath = storedData?.first
                                    val imageBase64 = storedData?.second

                                    val stateSummary = buildString {
                                        append("Events count: ${events.size}. Next upcoming: ${events.firstOrNull()?.title ?: "None"}.\n")
                                        append("Habits: ${habits.joinToString { it.name }}.\n")
                                        append("Goals: ${goals.joinToString { it.title }}.\n")
                                        append("Sleeping state: $isSleeping.")
                                    }

                                    when (val result = aiClient.executeWingmanAgent(
                                        userPrompt = agentInput.ifBlank { "Analyze this image and decide on any appropriate actions." },
                                        imageBase64 = imageBase64,
                                        stateSummary = stateSummary,
                                        settings = settings
                                    )) {
                                        is AiResult.Success -> {
                                            agentReply = result.data.replyText
                                            agentPendingActions = result.data.actions
                                            agentInput = ""
                                            agentAttachedImageUri = null
                                        }
                                        is AiResult.Error -> {
                                            agentErrorMessage = result.message
                                        }
                                    }
                                    isAgentLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.primaryContainer)
                            .testTag("ai_schedule_submit")
                    ) {
                        if (isAgentLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = colors.primary)
                        }
                    }
                }

                agentErrorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = err, color = colors.error, fontSize = 11.sp)
                }

                // Wingman Reply & Action Cards
                agentReply?.let { reply ->
                    Spacer(modifier = Modifier.height(10.dp))
                    FioCard(
                        backgroundColor = colors.surface,
                        borderColor = colors.border,
                        padding = PaddingValues(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Wingman", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FioMarkdown(
                            text = reply,
                            baseFontSize = 12.5.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Action execution list
                        if (agentPendingActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FioDivider()
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Wingman suggests", color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))

                            agentPendingActions.forEach { action ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        FioBadge(text = action.type, color = colors.primary)
                                        Text(text = action.summary, color = colors.textSecondary, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            executeAction(action)
                                            agentPendingActions = agentPendingActions - action
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Apply", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Today's schedule ---
        item {
            com.example.fio.ui.components.FioSectionHeader(
                title = "Today",
                actionLabel = "Calendar",
                onAction = { onNavigate(FioScreen.CALENDAR) }
            )
        }

        if (events.isEmpty()) {
            item {
                Text("No events scheduled for today. Add one above or in Calendar.", color = colors.textMuted, fontSize = 12.sp)
            }
        } else {
            items(events.take(3), key = { it.id }) { event ->
                val typeColor = when (event.type) {
                    EventType.STUDY -> colors.primary
                    EventType.GYM, EventType.JOG -> colors.warning
                    EventType.REST -> colors.info
                    EventType.CLASS -> colors.success
                    else -> colors.textSecondary
                }

                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_item_${event.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = event.isCompleted,
                            onCheckedChange = { repository.toggleEventCompleted(event.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.primary,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FioBadge(text = event.type.name, color = typeColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = event.title,
                                    color = if (event.isCompleted) colors.textMuted else colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatTimeOnly(event.startTimeMillis)} - ${formatTimeOnly(event.endTimeMillis)}",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 5. Habits ---
        if (habits.any { !it.archived }) {
            item {
                com.example.fio.ui.components.FioSectionHeader(
                    title = "Habits",
                    actionLabel = "All",
                    onAction = { onNavigate(FioScreen.HEALTH) }
                )
            }
            items(habits.filter { !it.archived }.take(4), key = { it.id }) { habit ->
                val todayStr = repository.todayDateString()
                val goal = habit.dailyGoal.coerceAtLeast(1)
                val todayCount = habit.progressDates[todayStr] ?: if (habit.completedDates.contains(todayStr)) goal else 0
                val isCompleted = habit.completedDates.contains(todayStr)

                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = if (isCompleted) colors.primary.copy(alpha = 0.4f) else colors.border,
                    padding = PaddingValues(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { repository.toggleHabitToday(habit.id) }
                        .testTag("habit_item_${habit.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCompleted) colors.accent
                                    else if (goal > 1 && todayCount > 0) colors.accent.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .border(1.5.dp, if (isCompleted || (goal > 1 && todayCount > 0)) colors.accent else colors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(15.dp))
                            } else if (goal > 1 && todayCount > 0) {
                                Text("$todayCount", color = colors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = habit.name, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (goal > 1) {
                                Text(
                                    text = "$todayCount / $goal today",
                                    color = if (isCompleted) colors.success else colors.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocalFireDepartment, contentDescription = null, tint = if (habit.streak > 0) colors.warning else colors.textMuted, modifier = Modifier.size(15.dp))
                            Text(text = "${habit.streak}d", color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // --- 6. Goals ---
        if (goals.isNotEmpty()) {
            item {
                com.example.fio.ui.components.FioSectionHeader(
                    title = "Goals",
                    actionLabel = "All",
                    onAction = { onNavigate(FioScreen.STATS) }
                )
            }
            items(goals.take(3), key = { it.id }) { goal ->
                val progressPercent = ((goal.currentValue / goal.targetValue) * 100).coerceIn(0.0, 100.0)
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = goal.title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "${goal.currentValue.toInt()} / ${goal.targetValue.toInt()} ${goal.unit}", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (progressPercent / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = colors.primary,
                        trackColor = colors.surfaceElevated
                    )
                }
            }
        }
    }
}
