@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.CourseSection
import com.example.fio.data.model.FocusSessionLog
import com.example.fio.data.model.SectionType
import com.example.fio.data.model.SessionStatus
import com.example.fio.data.model.formatTimestamp
import com.example.fio.data.storage.FioRepository
import com.example.fio.focus.FocusTimer
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.FioSelectionBar
import com.example.fio.ui.components.rememberFioHaptics
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun TimerLockdownScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val settings by repository.settings.collectAsState()
    val sessionLogs by repository.sessionLogs.collectAsState()
    val courses by repository.courses.collectAsState()
    val haptics = rememberFioHaptics()

    // Timer Config
    var selectedMinutes by remember { mutableStateOf(50) }
    var isLockdownEnabled by remember { mutableStateOf(false) }
    // What this session is for: Study / Leisure / Research + optional course.
    var selectedSectionType by remember { mutableStateOf(SectionType.STUDY) }
    var selectedCourseId by remember { mutableStateOf<String?>(null) }

    // A one-second pulse: while a session runs, the screen drives the
    // singleton's tick and recomposes from its wall-clock-accurate state.
    // Closing the page changes nothing — the foreground service keeps the
    // countdown and the notification banner alive.
    var tickPulse by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            if (FocusTimer.state.running()) FocusTimer.tick()
            tickPulse++
        }
    }
    // Instant refresh when something happens without waiting a second
    // (start, stop, reset, strike) — recompose immediately after events.
    var eventPulse by remember { mutableStateOf(0) }

    val ts = remember(tickPulse, eventPulse) { FocusTimer.state }
    val isRunning = ts.running()

    // Summary dialog: shown once a session finishes, then cleared from the singleton.
    var showSummaryDialog by remember { mutableStateOf(false) }
    var lastFinishedStatus by remember { mutableStateOf<SessionStatus?>(null) }
    var summaryMinutes by remember { mutableStateOf(0) }
    var summaryStrikesUsed by remember { mutableStateOf(0) }
    var summaryStrikesTotal by remember { mutableStateOf(0) }
    var summaryBreaks by remember { mutableStateOf(0) }
    // Editable context of the session that just ended: purpose, course, typed details.
    var summaryLogId by remember { mutableStateOf<String?>(null) }
    var summarySectionType by remember { mutableStateOf(SectionType.STUDY) }
    var summaryCourseId by remember { mutableStateOf<String?>(null) }
    var summaryDetails by remember { mutableStateOf("") }

    // Editing an old log from the history list (purpose / course / details).
    var historyEditTarget by remember { mutableStateOf<FocusSessionLog?>(null) }
    var historyEditType by remember { mutableStateOf(SectionType.STUDY) }
    var historyEditCourseId by remember { mutableStateOf<String?>(null) }
    var historyEditDetails by remember { mutableStateOf("") }

    // Multi-select for history: long-press a log to select, tap toggles.
    var selectedLogIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmLogBulkDelete by remember { mutableStateOf(false) }

    // Surface the finish event (fires even if the user was on another page).
    LaunchedEffect(ts.phase, ts.finishedStatus) {
        if (ts.phase == FocusTimer.Phase.DONE && ts.finishedStatus != null && !showSummaryDialog) {
            lastFinishedStatus = ts.finishedStatus
            summaryMinutes = ts.alreadyFocusedMinutes
            summaryStrikesUsed = ts.strikesUsed
            summaryStrikesTotal = ts.startingStrikes
            summaryBreaks = ts.breaksTaken
            summaryLogId = FocusTimer.lastLoggedSessionId
            summarySectionType = ts.sectionType
            summaryCourseId = ts.courseId
            summaryDetails = ""
            showSummaryDialog = true
        }
    }

    // End / Broken Session Dialog
    if (showSummaryDialog) {
        val finished = lastFinishedStatus ?: SessionStatus.COMPLETED
        AlertDialog(
            onDismissRequest = {
                showSummaryDialog = false
                FocusTimer.consumeFinished()
            },
            containerColor = colors.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (finished == SessionStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (finished == SessionStatus.COMPLETED) colors.success else colors.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (finished == SessionStatus.COMPLETED) "Session Finished!" else "Session Broken",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (finished == SessionStatus.COMPLETED)
                            "Great work staying in deep focus."
                        else "You ran out of strikes during lockdown mode.",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    FioCard(
                        backgroundColor = colors.surfaceElevated,
                        borderColor = colors.border,
                        padding = PaddingValues(10.dp)
                    ) {
                        Text("• Time Focused: $summaryMinutes min", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Strikes Used: $summaryStrikesUsed / $summaryStrikesTotal", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Breaks Taken: $summaryBreaks", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Logged into Study & Life Stats", color = colors.primary, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text("What was it for?", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FocusPurposeCoursePicker(
                        selectedType = summarySectionType,
                        selectedCourseId = summaryCourseId,
                        courses = courses,
                        onTypeSelected = { type ->
                            summarySectionType = type
                            // Keep the course only if it belongs to the new section.
                            val stillValid = courses.any { it.id == summaryCourseId && it.sectionType == type }
                            if (!stillValid) summaryCourseId = null
                        },
                        onCourseSelected = { summaryCourseId = it }
                    )

                    Text("Details (saved with this session)", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = summaryDetails,
                        onValueChange = { summaryDetails = it },
                        placeholder = { Text("e.g. Chapter 4 problems, past papers…", color = colors.textMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val logId = summaryLogId
                        if (logId != null) {
                            repository.updateFocusSessionLog(
                                id = logId,
                                sectionType = summarySectionType,
                                courseId = summaryCourseId,
                                details = summaryDetails
                            )
                        }
                        showSummaryDialog = false
                        FocusTimer.consumeFinished()
                        eventPulse++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Done", color = Color.White)
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
        // --- 1. Header ---
        item {
            com.example.fio.ui.components.FioScreenHeader(
                title = "Focus",
                subtitle = "One session at a time. It keeps counting even when you minimise Fio."
            )
        }

        // --- 2. Main Clock Card ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = if (isLockdownEnabled && isRunning) colors.primary else colors.border,
                padding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth().testTag("timer_clock_card")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Status Badge
                    val statusText = when (ts.phase) {
                        FocusTimer.Phase.EMERGENCY -> "EMERGENCY PAUSED (${((ts.emergencyEndsAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)}s left)"
                        FocusTimer.Phase.BREAK -> "REST BREAK (Free to leave - no strikes)"
                        FocusTimer.Phase.FOCUS -> if (ts.lockdown) "LOCKDOWN ACTIVE" else "FOCUSING"
                        FocusTimer.Phase.DONE -> "SESSION OVER"
                        else -> "TIMER READY"
                    }
                    val statusColor = when (ts.phase) {
                        FocusTimer.Phase.EMERGENCY -> colors.warning
                        FocusTimer.Phase.BREAK -> colors.info
                        FocusTimer.Phase.FOCUS -> if (ts.lockdown) colors.error else colors.primary
                        else -> colors.textMuted
                    }
                    FioBadge(text = statusText, color = statusColor)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular Countdown Display (wall-clock based, always accurate).
                    // The pulse keys are read here, inside this item's scope, so the
                    // clock re-evaluates every second even when FocusTimer's state
                    // value is unchanged between ticks — otherwise Compose skips the
                    // value-equal recomposition and the display freezes after 1s.
                    val now = remember(tickPulse, eventPulse) { System.currentTimeMillis() }
                    val displaySeconds = when (ts.phase) {
                        FocusTimer.Phase.FOCUS -> ((ts.focusEndsAt - now) / 1000L).coerceAtLeast(0)
                        FocusTimer.Phase.BREAK -> ((ts.breakEndsAt - now) / 1000L).coerceAtLeast(0)
                        FocusTimer.Phase.EMERGENCY -> ((ts.emergencyEndsAt - now) / 1000L).coerceAtLeast(0)
                        else -> 0L
                    }.toInt()
                    val mins = displaySeconds / 60
                    val secs = displaySeconds % 60
                    val timeString = String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .border(3.dp, if (ts.phase == FocusTimer.Phase.BREAK) colors.info else colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeString,
                                color = colors.textPrimary,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (ts.phase == FocusTimer.Phase.BREAK) "Break Ends Soon" else "${ts.plannedMinutes} min session",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                            if (isRunning) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val courseName = ts.courseId?.let { id -> courses.firstOrNull { it.id == id }?.name }
                                Text(
                                    text = focusPurposeLabel(ts.sectionType) + (courseName?.let { " • $it" } ?: ""),
                                    color = colors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lockdown Strikes Info
                    if (ts.lockdown && isRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Strikes Remaining: ", color = colors.textSecondary, fontSize = 12.sp)
                            repeat(ts.startingStrikes) { idx ->
                                val isLost = idx >= ts.strikesRemaining
                                Icon(
                                    imageVector = if (isLost) Icons.Default.Cancel else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isLost) colors.error else colors.primary,
                                    modifier = Modifier.size(16.dp).padding(horizontal = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${ts.strikesRemaining} / ${ts.startingStrikes}",
                                color = if (ts.strikesRemaining <= 1) colors.error else colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Simulation button for testing app exit in emulator
                        if (ts.phase == FocusTimer.Phase.FOCUS) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    haptics.warn()
                                    FocusTimer.useStrike()
                                    eventPulse++
                                }
                            ) {
                                Text("Test: Simulate Leaving App Beyond 15s Grace", color = colors.textMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isRunning) {
                            Button(
                                onClick = {
                                    haptics.confirm()
                                    FocusTimer.start(
                                        minutes = selectedMinutes,
                                        lockdown = isLockdownEnabled,
                                        breakFrequencyMinutes = settings.breakFrequencyMinutes,
                                        breakDurationMinutes = settings.breakDurationMinutes,
                                        sectionType = selectedSectionType,
                                        courseId = selectedCourseId,
                                        context = context
                                    )
                                    eventPulse++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("start_timer_button")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Session", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Emergency Pause (1 per session, max 5 minutes)
                            OutlinedButton(
                                onClick = {
                                    haptics.tick()
                                    FocusTimer.toggleEmergencyPause(context)
                                    eventPulse++
                                },
                                enabled = !ts.emergencyUsed || ts.phase == FocusTimer.Phase.EMERGENCY,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.textPrimary,
                                    containerColor = colors.surfaceElevated
                                ),
                                border = null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (ts.phase == FocusTimer.Phase.EMERGENCY) "Resume" else if (ts.emergencyUsed) "Pause Used" else "1x Pause",
                                    fontSize = 11.sp,
                                    color = if (ts.emergencyUsed && ts.phase != FocusTimer.Phase.EMERGENCY) colors.textMuted else colors.textPrimary
                                )
                            }

                            // Stop / Abandon
                            Button(
                                onClick = {
                                    haptics.warn()
                                    FocusTimer.stop(context)
                                    eventPulse++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stop", color = colors.textPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            FocusTimer.reset(context)
                            eventPulse++
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Timer", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 3. Session Length & Lockdown Setup ---
        if (!isRunning) {
            item {
                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(14.dp)
                ) {
                    Text("Select Session Length", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 50, 90, 120, 180).forEach { mins ->
                            val isSelected = selectedMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                                    .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptics.tick()
                                        selectedMinutes = mins
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (isSelected) colors.primary else colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${FocusTimer.calculateStrikesForDuration(mins)} strike",
                                        color = colors.textMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    FioDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Session purpose: Study / Leisure / Research + optional course.
                    Text("Session Purpose", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FocusPurposeCoursePicker(
                        selectedType = selectedSectionType,
                        selectedCourseId = selectedCourseId,
                        courses = courses,
                        onTypeSelected = { type ->
                            haptics.tick()
                            selectedSectionType = type
                            // Keep the course only if it belongs to the new section.
                            val stillValid = courses.any { it.id == selectedCourseId && it.sectionType == type }
                            if (!stillValid) selectedCourseId = null
                        },
                        onCourseSelected = { id ->
                            haptics.tick()
                            selectedCourseId = id
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    FioDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Lockdown Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lockdown Mode (Off by default)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Leaving app beyond 15s grace costs 1 strike. ${FocusTimer.calculateStrikesForDuration(selectedMinutes)} strikes available for this ${selectedMinutes}m session.",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isLockdownEnabled,
                            onCheckedChange = {
                                haptics.tick()
                                isLockdownEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.primary
                            ),
                            modifier = Modifier.testTag("lockdown_mode_switch")
                        )
                    }
                }
            }
        }

        // --- 4. Focus Stats & Graph (minutes, purposes, 7-day chart) ---
        item {
            FocusStatsCard(sessionLogs = sessionLogs)
        }

        // --- 5. Focus Session Logs History ---
        if (selectedLogIds.isNotEmpty()) {
            item {
                FioSelectionBar(
                    count = selectedLogIds.size,
                    onClear = { selectedLogIds = emptySet() },
                    onSelectAll = { selectedLogIds = sessionLogs.map { it.id }.toSet() }
                ) {
                    TextButton(onClick = { confirmLogBulkDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = colors.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Text("Session History (${sessionLogs.size})", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        items(sessionLogs) { log ->
            val isSuccess = log.status == SessionStatus.COMPLETED
            val courseName = log.courseId?.let { id -> courses.firstOrNull { it.id == id }?.name }
            val isChosen = log.id in selectedLogIds
            FioCard(
                backgroundColor = colors.surface,
                borderColor = if (isChosen) colors.primary else colors.border,
                padding = PaddingValues(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selectedLogIds.isNotEmpty()) {
                                selectedLogIds = if (isChosen) selectedLogIds - log.id else selectedLogIds + log.id
                            } else {
                                haptics.tick()
                                historyEditTarget = log
                                historyEditType = log.sectionType
                                historyEditCourseId = log.courseId
                                historyEditDetails = log.details
                            }
                        },
                        onLongClick = {
                            selectedLogIds = if (isChosen) selectedLogIds - log.id else selectedLogIds + log.id
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isSuccess) colors.success else colors.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = log.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            // Purpose • course line
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = focusPurposeIcon(log.sectionType),
                                    contentDescription = null,
                                    tint = focusPurposeColor(colors, log.sectionType),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = focusPurposeLabel(log.sectionType) + (courseName?.let { " • $it" } ?: ""),
                                    color = colors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    FioBadge(text = "${log.actualMinutes}m / ${log.plannedMinutes}m", color = colors.primary)
                    if (isChosen) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Edit purpose / course / details after the fact.
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.tick()
                                historyEditTarget = log
                                historyEditType = log.sectionType
                                historyEditCourseId = log.courseId
                                historyEditDetails = log.details
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit session details",
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (log.details.isNotBlank()) {
                    Text(
                        text = log.details,
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "Strikes used: ${log.strikesUsed}/${log.startingStrikes} • Breaks: ${log.breaksTaken} • Logged: ${formatTimestamp(log.loggedAtMillis)}",
                    color = colors.textMuted,
                    fontSize = 9.sp
                )
            }
        }
    }

    // Bulk delete confirmation (multi-select)
    if (confirmLogBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmLogBulkDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete ${selectedLogIds.size} session(s)?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "These focus logs will be removed. Your Life Score recalculates without them.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteFocusSessionLogs(selectedLogIds)
                    selectedLogIds = emptySet()
                    confirmLogBulkDelete = false
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogBulkDelete = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // --- Edit an old session's purpose / course / details ---
    historyEditTarget?.let { log ->
        AlertDialog(
            onDismissRequest = { historyEditTarget = null },
            containerColor = colors.surface,
            title = { Text("Edit session", color = colors.textPrimary) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "${log.actualMinutes} min • ${formatTimestamp(log.loggedAtMillis)}",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    FocusPurposeCoursePicker(
                        selectedType = historyEditType,
                        selectedCourseId = historyEditCourseId,
                        courses = courses,
                        onTypeSelected = { type ->
                            historyEditType = type
                            val stillValid = courses.any { it.id == historyEditCourseId && it.sectionType == type }
                            if (!stillValid) historyEditCourseId = null
                        },
                        onCourseSelected = { historyEditCourseId = it }
                    )
                    OutlinedTextField(
                        value = historyEditDetails,
                        onValueChange = { historyEditDetails = it },
                        placeholder = { Text("Details…", color = colors.textMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.updateFocusSessionLog(
                        id = log.id,
                        sectionType = historyEditType,
                        courseId = historyEditCourseId,
                        details = historyEditDetails
                    )
                    historyEditTarget = null
                }) {
                    Text("Save", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { historyEditTarget = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Shared Focus helpers: the timer screen and the Habits tab both use these.
// ---------------------------------------------------------------------------

internal fun focusPurposeLabel(type: SectionType): String = when (type) {
    SectionType.STUDY -> "Study"
    SectionType.LEISURE -> "Leisure"
    SectionType.RESEARCH -> "Research"
}

internal fun focusPurposeIcon(type: SectionType) = when (type) {
    SectionType.STUDY -> Icons.Default.MenuBook
    SectionType.LEISURE -> Icons.Default.SportsEsports
    SectionType.RESEARCH -> Icons.Default.Science
}

internal fun focusPurposeColor(colors: com.example.fio.ui.theme.FioCustomColors, type: SectionType): Color =
    when (type) {
        SectionType.STUDY -> colors.primary
        SectionType.LEISURE -> colors.info
        SectionType.RESEARCH -> colors.warning
    }

/** Purpose chips + matching course chips, shared by setup, summary and edit dialogs. */
@Composable
internal fun FocusPurposeCoursePicker(
    selectedType: SectionType,
    selectedCourseId: String?,
    courses: List<CourseSection>,
    onTypeSelected: (SectionType) -> Unit,
    onCourseSelected: (String?) -> Unit
) {
    val colors = LocalFioColors.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionType.values().forEach { type ->
                val isSelected = selectedType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                        .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(12.dp))
                        .clickable { onTypeSelected(type) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = focusPurposeIcon(type),
                            contentDescription = null,
                            tint = if (isSelected) colors.primary else colors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = focusPurposeLabel(type),
                            color = if (isSelected) colors.primary else colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Courses belonging to the chosen section, plus "None".
        val sectionCourses = courses.filter { it.sectionType == selectedType }
        if (sectionCourses.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedCourseId == null) colors.primaryContainer else colors.surfaceElevated)
                        .border(1.dp, if (selectedCourseId == null) colors.primary else colors.border, RoundedCornerShape(10.dp))
                        .clickable { onCourseSelected(null) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "No course",
                        color = if (selectedCourseId == null) colors.primary else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selectedCourseId == null) FontWeight.Bold else FontWeight.Normal
                    )
                }
                sectionCourses.forEach { course ->
                    val isSelected = selectedCourseId == course.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                            .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                            .clickable { onCourseSelected(course.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = course.name,
                            color = if (isSelected) colors.primary else colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** Focus stats card: totals, 7-day minutes graph and purpose split. */
@Composable
fun FocusStatsCard(sessionLogs: List<FocusSessionLog>) {
    val colors = LocalFioColors.current

    // Last 7 calendar days (today last) with the focus minutes logged in each.
    val stats = remember(sessionLogs) {
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(day, start, end)
        }
        val perDay = days.map { (_, start, end) ->
            sessionLogs.filter { it.loggedAtMillis in start until end }.sumOf { it.actualMinutes }
        }
        val totalMinutes = sessionLogs.sumOf { it.actualMinutes }
        val weekMinutes = perDay.sum()
        val purposeMinutes = SectionType.values().map { type ->
            type to sessionLogs.filter { it.sectionType == type }.sumOf { it.actualMinutes }
        }
        FocusStats(
            dayLabels = days.map { listOf("M", "T", "W", "T", "F", "S", "S")[it.first.dayOfWeek.value - 1] },
            minutesPerDay = perDay,
            totalMinutes = totalMinutes,
            weekMinutes = weekMinutes,
            sessionCount = sessionLogs.size,
            purposeMinutes = purposeMinutes
        )
    }

    FioCard(
        backgroundColor = colors.surface,
        borderColor = colors.border,
        padding = PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("focus_stats_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Insights, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Focus Stats", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FioBadge(text = "${stats.totalMinutes}m total", color = colors.primary)
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FocusStatCell("This week", "${stats.weekMinutes}m")
            FocusStatCell("Sessions", "${stats.sessionCount}")
            FocusStatCell(
                "Avg / session",
                if (stats.sessionCount > 0) "${stats.totalMinutes / stats.sessionCount}m" else "—"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        FocusWeekBars(
            minutesPerDay = stats.minutesPerDay,
            dayLabels = stats.dayLabels
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Purpose split.
        val maxPurpose = stats.purposeMinutes.maxOfOrNull { it.second } ?: 0
        if (maxPurpose == 0) {
            Text(
                "Start a session to see your Study / Leisure / Research split.",
                color = colors.textMuted,
                fontSize = 10.sp
            )
        } else {
            stats.purposeMinutes.forEach { (type, minutes) ->
                val share = if (stats.totalMinutes > 0) minutes.toFloat() / stats.totalMinutes else 0f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = focusPurposeIcon(type),
                        contentDescription = null,
                        tint = focusPurposeColor(colors, type),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = focusPurposeLabel(type),
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.width(56.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.surfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(share)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(focusPurposeColor(colors, type))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${minutes}m",
                        color = colors.textMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Focus minutes feed your Study stats and Life Score.",
            color = colors.textMuted,
            fontSize = 9.sp
        )
    }
}

private data class FocusStats(
    val dayLabels: List<String>,
    val minutesPerDay: List<Int>,
    val totalMinutes: Int,
    val weekMinutes: Int,
    val sessionCount: Int,
    val purposeMinutes: List<Pair<SectionType, Int>>
)

@Composable
private fun RowScope.FocusStatCell(label: String, value: String) {
    val colors = LocalFioColors.current
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = colors.textMuted, fontSize = 9.sp)
    }
}

/** Rounded bar per day (oldest left), like the habit weekly bars. */
@Composable
private fun FocusWeekBars(
    minutesPerDay: List<Int>,
    dayLabels: List<String>
) {
    val colors = LocalFioColors.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 8.sp, color = colors.textMuted)
    val maxValue = (minutesPerDay.maxOfOrNull { it } ?: 0).coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .testTag("focus_week_bars")
    ) {
        val barW = 18.dp.toPx()
        val gap = (size.width - 7 * barW) / 6f
        val chartH = size.height - 14.dp.toPx()
        drawLine(
            color = colors.border,
            start = Offset(0f, chartH),
            end = Offset(size.width, chartH),
            strokeWidth = 1.dp.toPx()
        )
        minutesPerDay.forEachIndexed { i, minutes ->
            val x = i * (barW + gap)
            val h = if (minutes == 0) 2.dp.toPx() else (minutes.toFloat() / maxValue) * (chartH - 8.dp.toPx())
            drawRoundRect(
                color = if (minutes == 0) colors.surfaceElevated else colors.primary,
                topLeft = Offset(x, chartH - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW * 0.3f)
            )
            val label = dayLabels.getOrElse(i) { "" }
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft = Offset(
                    (x + barW / 2f - measured.size.width / 2f).coerceAtLeast(0f),
                    chartH + 2.dp.toPx()
                )
            )
        }
    }
}
