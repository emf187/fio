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
                repository.addHabit(name, desc)
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
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Life Score Banner ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(FioScreen.STATS) }
                    .testTag("life_score_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Today's Life Score",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val trendColor = if (lifeScore.trend >= 0) colors.success else colors.error
                            val trendIcon = if (lifeScore.trend >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown
                            Icon(
                                imageVector = trendIcon,
                                contentDescription = null,
                                tint = trendColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${if (lifeScore.trend >= 0) "+" else ""}${lifeScore.trend}",
                                color = trendColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lifeScore.note,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                            .border(2.dp, colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${lifeScore.overallScore}",
                            color = colors.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 2. Quick Action Bar (Meal, Sleep, Timer) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { repository.logMeal("Quick meal") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(colors.border)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_meal_button")
                ) {
                    Icon(imageVector = Icons.Default.Restaurant, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("I Ate", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = {
                        if (isSleeping) {
                            repository.quickSleepAwake()
                            isSleeping = false
                        } else {
                            repository.quickSleepStart()
                            isSleeping = true
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(colors.border)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("quick_sleep_button")
                ) {
                    Icon(
                        imageVector = if (isSleeping) Icons.Default.WbSunny else Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSleeping) "I'm Awake" else "Sleep", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = { onNavigate(FioScreen.TIMER) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_focus_button")
                ) {
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Focus", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
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
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wingman AI Agent", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.clickable { onNavigate(FioScreen.SETTINGS) }) {
                        FioBadge(
                            text = if (settings.aiApiKey.isNotBlank()) "Key Set (Edit)" else "+ Set API Key",
                            color = if (settings.aiApiKey.isNotBlank()) colors.success else colors.warning
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Wingman controls the whole app: schedule events, log meals, record sleep, check habits, and analyze uploaded images.",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                // Attached Image preview
                agentAttachedImageUri?.let { uri ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Image Attached for Wingman", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Will be downscaled, stored in app, and inspected by AI", color = colors.textMuted, fontSize = 9.sp)
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
                    // Upload Image Button
                    IconButton(
                        onClick = {
                            agentPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .testTag("wingman_upload_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Upload Image",
                            tint = if (agentAttachedImageUri != null) colors.primary else colors.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = agentInput,
                        onValueChange = { agentInput = it },
                        placeholder = { Text("Ask Wingman to schedule, log, or analyze...", color = colors.textMuted, fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_schedule_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
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
                            .clip(RoundedCornerShape(8.dp))
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
                            Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wingman Response", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = reply, color = colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)

                        // Action execution list
                        if (agentPendingActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FioDivider()
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Wingman Proposed Actions:", color = colors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        shape = RoundedCornerShape(4.dp)
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

        // --- 4. Today's Schedule ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Today's Schedule", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "View Calendar →",
                    color = colors.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onNavigate(FioScreen.CALENDAR) }
                )
            }
        }

        if (events.isEmpty()) {
            item {
                Text("No events scheduled for today. Add one above or in Calendar.", color = colors.textMuted, fontSize = 12.sp)
            }
        } else {
            items(events) { event ->
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
                            Text(
                                text = "Logged: ${formatTimestamp(event.loggedAtMillis)}${if (event.editedAtMillis != null) " • Edited: ${formatTimestamp(event.editedAtMillis)}" else ""}",
                                color = colors.textMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 5. Habits Quick Check ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Daily Habits", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Streaks", color = colors.textMuted, fontSize = 12.sp)
            }
        }

        items(habits) { habit ->
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val isCompleted = habit.completedDates.contains(todayStr)

            FioCard(
                backgroundColor = colors.surface,
                borderColor = if (isCompleted) colors.primary.copy(alpha = 0.4f) else colors.border,
                padding = PaddingValues(10.dp),
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
                            .background(if (isCompleted) colors.primary else Color.Transparent)
                            .border(1.dp, if (isCompleted) colors.primary else colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = habit.name, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (habit.description.isNotBlank()) {
                            Text(text = habit.description, color = colors.textMuted, fontSize = 10.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.LocalFireDepartment, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                        Text(text = "${habit.streak}d", color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 6. Active Goals ---
        item {
            Text("Active Goals", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        items(goals) { goal ->
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
