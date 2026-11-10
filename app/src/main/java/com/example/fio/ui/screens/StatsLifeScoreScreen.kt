package com.example.fio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.fio.ui.components.AiScheduleProposalDialog
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.FioMarkdown
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.launch

@Composable
fun StatsLifeScoreScreen(
    repository: FioRepository,
    aiClient: FioAiClient,
    onOpenHabits: () -> Unit
) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val events by repository.events.collectAsState()
    val habits by repository.habits.collectAsState()
    val goals by repository.goals.collectAsState()
    val sleepLogs by repository.sleepLogs.collectAsState()
    val mealLogs by repository.mealLogs.collectAsState()
    val sessionLogs by repository.sessionLogs.collectAsState()
    val tutorSessions by repository.tutorSessions.collectAsState()
    val settings by repository.settings.collectAsState()

    var selectedPeriod by remember { mutableStateOf("Weekly") } // Daily, Weekly, Monthly

    // Habit stats picker: up to 3 habits featured on this page, the rest stay in the habit tracker.
    val activeHabits = habits.filter { !it.archived }
    var pickedIds by remember { mutableStateOf(repository.habitStatsSelection()) }
    var showHabitPicker by remember { mutableStateOf(false) }
    val pickedHabits = if (pickedIds.isEmpty()) {
        activeHabits.take(3)
    } else {
        pickedIds.mapNotNull { id -> activeHabits.find { it.id == id } }.take(3)
    }

    val lifeScore = remember(events, habits, goals, sleepLogs, mealLogs, sessionLogs) {
        repository.calculateLifeScore()
    }

    // AI Weekly Review State
    var isReviewLoading by remember { mutableStateOf(false) }
    var aiReviewText by remember { mutableStateOf<String?>(null) }
    var proposedAdjustment by remember { mutableStateOf<ProposedScheduleChange?>(null) }

    // Dialog for confirming schedule adjustment from AI Review
    proposedAdjustment?.let { proposal ->
        AiScheduleProposalDialog(
            proposal = proposal,
            onConfirm = {
                repository.addEvent(
                    ScheduleEvent(
                        title = proposal.title,
                        description = proposal.reason,
                        startTimeMillis = proposal.startTimeMillis,
                        endTimeMillis = proposal.endTimeMillis,
                        type = proposal.eventType
                    )
                )
                proposedAdjustment = null
            },
            onDismiss = { proposedAdjustment = null }
        )
    }

    // --- Habit stats picker: choose up to 3 habits to feature on this page ---
    if (showHabitPicker) {
        AlertDialog(
            onDismissRequest = { showHabitPicker = false },
            containerColor = colors.surface,
            title = {
                Text(
                    "Pick habits to show (max 3)",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (activeHabits.isEmpty()) {
                        Text(
                            "No active habits yet. Create one in the Habits tab first.",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        activeHabits.forEach { h ->
                            val checked = pickedIds.contains(h.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when {
                                            checked -> pickedIds = pickedIds - h.id
                                            pickedIds.size >= 3 -> Toast.makeText(
                                                context,
                                                "Pick up to 3 — the rest stay in the habit tracker",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            else -> pickedIds = pickedIds + h.id
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(h.colorValue()))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(h.name, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.setHabitStatsSelection(pickedIds)
                        showHabitPicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHabitPicker = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header & Period Tabs ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.fio.ui.components.FioScreenHeader(
                    title = "Progress",
                    subtitle = "How your week is going.",
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Daily", "Weekly", "Monthly").forEach { period ->
                        val isSelected = selectedPeriod == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                                .clickable { selectedPeriod = period }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = period,
                                color = if (isSelected) colors.primary else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Overall Life Score Hero Card ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overall Life Score (0-100)", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${lifeScore.overallScore}",
                                color = colors.textPrimary,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val trendColor = if (lifeScore.trend >= 0) colors.success else colors.error
                            val trendIcon = if (lifeScore.trend >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown
                            Icon(imageVector = trendIcon, contentDescription = null, tint = trendColor, modifier = Modifier.size(20.dp))
                            Text(
                                text = "${if (lifeScore.trend >= 0) "+" else ""}${lifeScore.trend} vs last week",
                                color = trendColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = lifeScore.note, color = colors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                            .border(3.dp, colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${lifeScore.overallScore}%",
                            color = colors.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 3. Lightweight Custom Canvas Chart (No heavy chart libraries) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(14.dp)
            ) {
                Text("Score Trend (Last 7 Days)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                val sampleScores = listOf(72, 75, 78, 80, 77, 82, lifeScore.overallScore)
                val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val primaryColor = colors.primary
                val trackColor = colors.surfaceElevated

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val barWidth = width / (sampleScores.size * 2f)

                    sampleScores.forEachIndexed { i, score ->
                        val x = (i * 2 + 0.5f) * barWidth
                        val barHeight = (score / 100f) * (height - 20f)
                        val y = height - 20f - barHeight

                        // Background track
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, height - 20f),
                            cornerRadius = CornerRadius(4f, 4f)
                        )

                        // Filled bar
                        drawRoundRect(
                            color = if (i == sampleScores.lastIndex) primaryColor else primaryColor.copy(alpha = 0.6f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayLabels.forEach { label ->
                        Text(text = label, color = colors.textMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        // --- 4. Category Breakdown Scores (0-100 each) ---
        item {
            Text("Category Breakdown (Weighted)", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryScoreRow("Study (Sessions, Tutor, Lockdown)", lifeScore.studyScore, 25, colors.primary)
                    CategoryScoreRow("Sleep (Hours vs 8h Target)", lifeScore.sleepScore, 20, colors.info)
                    CategoryScoreRow("Meals (Frequency & Regularity)", lifeScore.mealsScore, 15, colors.warning)
                    CategoryScoreRow("Habits (Completions & Streaks)", lifeScore.habitsScore, 15, colors.success)

                    TextButton(
                        onClick = onOpenHabits,
                        modifier = Modifier.fillMaxWidth().testTag("open_habits_button")
                    ) {
                        Icon(imageVector = Icons.Default.Repeat, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Habits", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // --- 4b. Habit stats (pick up to 3 from the habit tracker) ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("habit_stats_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Habit stats", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { showHabitPicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.testTag("choose_habit_stats_button")
                    ) {
                        Text("Choose", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (pickedHabits.isEmpty()) {
                    Text(
                        "No habits to show yet. Create habits in the Habits tab, then pick up to 3 to display here.",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pickedHabits.forEach { h ->
                            val rate = HabitMath.completionRate30(h)
                            val total = HabitMath.totalDone(h)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(h.colorValue()))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        h.name,
                                        color = colors.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "$total days done · $rate% (30d)",
                                        color = colors.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = if (h.streak > 0) colors.warning else colors.textMuted, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("${h.streak}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("The rest stays in the habit tracker.", color = colors.textMuted, fontSize = 10.sp)
                }
            }
        }

        // --- 5. AI Weekly Review & Schedule Optimization ---
        item {
            FioCard(
                backgroundColor = colors.surfaceElevated,
                borderColor = colors.border,
                padding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Weekly Stats Review", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Synthesizes your weak and strong dimensions, provides practical advice, and can propose schedule revisions.",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!isReviewLoading) {
                            isReviewLoading = true
                            aiReviewText = null
                            coroutineScope.launch {
                                val prompt = """
Analyze my weekly stats:
- Overall Life Score: ${lifeScore.overallScore}/100 (trend: ${lifeScore.trend})
- Study: ${lifeScore.studyScore}/100
- Sleep: ${lifeScore.sleepScore}/100
- Meals: ${lifeScore.mealsScore}/100
- Habits: ${lifeScore.habitsScore}/100

Give me a concise 3-point review with practical suggestions, and suggest 1 specific schedule change (e.g. an extra 45m study session or rest block) to fix the weakest area.
""".trimIndent()
                                when (val res = aiClient.sendPrompt(prompt, "You are Fio, a wise and supportive personal mentor.", settings)) {
                                    is AiResult.Success -> {
                                        aiReviewText = res.data
                                        // Also generate a proposed calendar adjustment
                                        val now = System.currentTimeMillis()
                                        proposedAdjustment = ProposedScheduleChange(
                                            title = "Focus & Recovery Session",
                                            action = "ADD",
                                            reason = "AI Weekly Review recommendation to boost ${lifeScore.note.take(25)}",
                                            startTimeMillis = now + (2 * 3600 * 1000L),
                                            endTimeMillis = now + (3 * 3600 * 1000L) + (15 * 60 * 1000L),
                                            eventType = EventType.STUDY
                                        )
                                    }
                                    is AiResult.Error -> aiReviewText = "Failed: ${res.message}"
                                }
                                isReviewLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_weekly_review_button")
                ) {
                    if (isReviewLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Generate AI Weekly Review", color = Color.White, fontSize = 12.sp)
                }

                aiReviewText?.let { review ->
                    Spacer(modifier = Modifier.height(10.dp))
                    FioCard(
                        backgroundColor = colors.surface,
                        borderColor = colors.border,
                        padding = PaddingValues(10.dp)
                    ) {
                        FioMarkdown(text = review, baseFontSize = 12.sp)
                    }
                }
            }
        }

        // --- 6. Timestamp Audit Trail (Section 12 requirement) ---
        item {
            Text("Logged Entry Timestamps (Audit Trail)", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        items(sessionLogs.take(5)) { log ->
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Focus Session: ${log.actualMinutes}m", color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    FioBadge(text = log.status.name, color = if (log.status == SessionStatus.COMPLETED) colors.success else colors.error)
                }
                Text(text = "Logged: ${formatTimestamp(log.loggedAtMillis)}", color = colors.textMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun CategoryScoreRow(
    title: String,
    score: Int,
    weightPercent: Int,
    color: Color
) {
    val colors = LocalFioColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = colors.textPrimary, fontSize = 12.sp)
            Text(text = "$score / 100 ($weightPercent%)", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = colors.surfaceElevated
        )
    }
}
