package com.example.fio.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.FocusSessionLog
import com.example.fio.data.model.SessionStatus
import com.example.fio.data.model.formatTimestamp
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.delay

fun calculateStrikesForDuration(minutes: Int): Int {
    return when {
        minutes < 60 -> 1
        minutes < 120 -> 3
        minutes < 180 -> 5
        minutes < 240 -> 7
        else -> 10
    }
}

@Composable
fun TimerLockdownScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val settings by repository.settings.collectAsState()
    val sessionLogs by repository.sessionLogs.collectAsState()

    // Timer Config
    var selectedMinutes by remember { mutableStateOf(50) }
    var isLockdownEnabled by remember { mutableStateOf(false) }

    // Running State
    var isRunning by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableStateOf(selectedMinutes * 60) }
    var isInBreak by remember { mutableStateOf(false) }
    var breakSecondsRemaining by remember { mutableStateOf(settings.breakDurationMinutes * 60) }

    // Strikes State
    val startingStrikes = remember(selectedMinutes) { calculateStrikesForDuration(selectedMinutes) }
    var currentStrikesRemaining by remember { mutableStateOf(startingStrikes) }
    var strikesUsedCount by remember { mutableStateOf(0) }
    var breaksTakenCount by remember { mutableStateOf(0) }

    // Emergency Pause (1 per session, up to 5 min)
    var emergencyPauseUsed by remember { mutableStateOf(false) }
    var isEmergencyPaused by remember { mutableStateOf(false) }
    var emergencyPauseSeconds by remember { mutableStateOf(300) } // 5 mins

    // Completion Dialog
    var showSummaryDialog by remember { mutableStateOf(false) }
    var lastFinishedStatus by remember { mutableStateOf(SessionStatus.COMPLETED) }

    // Keep strikes remaining synced when changing duration before start
    LaunchedEffect(selectedMinutes) {
        if (!isRunning) {
            secondsRemaining = selectedMinutes * 60
            currentStrikesRemaining = calculateStrikesForDuration(selectedMinutes)
        }
    }

    // Timer countdown loop
    LaunchedEffect(isRunning, isEmergencyPaused, isInBreak) {
        while (isRunning) {
            delay(1000L)
            if (isEmergencyPaused) {
                if (emergencyPauseSeconds > 0) {
                    emergencyPauseSeconds--
                } else {
                    isEmergencyPaused = false // resume automatically
                }
            } else if (isInBreak) {
                if (breakSecondsRemaining > 0) {
                    breakSecondsRemaining--
                } else {
                    // Break finished! Award 1 strike back (never above starting number)
                    isInBreak = false
                    breaksTakenCount++
                    currentStrikesRemaining = (currentStrikesRemaining + 1).coerceAtMost(startingStrikes)
                    breakSecondsRemaining = settings.breakDurationMinutes * 60
                }
            } else {
                if (secondsRemaining > 0) {
                    secondsRemaining--
                    // Check if it's time for a scheduled break (every breakFrequencyMinutes)
                    val elapsedSeconds = (selectedMinutes * 60) - secondsRemaining
                    if (selectedMinutes >= 50 && elapsedSeconds > 0 && elapsedSeconds % (settings.breakFrequencyMinutes * 60) == 0 && secondsRemaining > 300) {
                        isInBreak = true
                        breakSecondsRemaining = settings.breakDurationMinutes * 60
                    }
                } else {
                    // Session Completed Successfully!
                    isRunning = false
                    lastFinishedStatus = SessionStatus.COMPLETED
                    repository.logFocusSession(
                        FocusSessionLog(
                            title = "Focus Session",
                            plannedMinutes = selectedMinutes,
                            actualMinutes = selectedMinutes,
                            isLockdownEnabled = isLockdownEnabled,
                            startingStrikes = startingStrikes,
                            strikesUsed = strikesUsedCount,
                            breaksTaken = breaksTakenCount,
                            status = SessionStatus.COMPLETED
                        )
                    )
                    showSummaryDialog = true
                }
            }
        }
    }

    // End / Broken Session Dialog
    if (showSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            containerColor = colors.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (lastFinishedStatus == SessionStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (lastFinishedStatus == SessionStatus.COMPLETED) colors.success else colors.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lastFinishedStatus == SessionStatus.COMPLETED) "Session Finished!" else "Session Broken",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lastFinishedStatus == SessionStatus.COMPLETED)
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
                        Text("• Time Focused: $selectedMinutes min", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Strikes Used: $strikesUsedCount / $startingStrikes", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Breaks Taken: $breaksTakenCount", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Logged into Study & Life Stats", color = colors.primary, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSummaryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Done", color = Color.White)
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
        // --- 1. Header & Title ---
        item {
            Column {
                Text("Focus Timer & Lockdown Mode", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("High-discipline academic time blocking with anti-distraction strikes", color = colors.textMuted, fontSize = 11.sp)
            }
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
                    val statusText = when {
                        isEmergencyPaused -> "EMERGENCY PAUSED (${emergencyPauseSeconds}s left)"
                        isInBreak -> "REST BREAK (Free to leave - no strikes)"
                        isRunning -> if (isLockdownEnabled) "LOCKDOWN ACTIVE" else "FOCUSING"
                        else -> "TIMER READY"
                    }
                    val statusColor = when {
                        isEmergencyPaused -> colors.warning
                        isInBreak -> colors.info
                        isRunning -> if (isLockdownEnabled) colors.error else colors.primary
                        else -> colors.textMuted
                    }
                    FioBadge(text = statusText, color = statusColor)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular Countdown Display
                    val displaySeconds = if (isInBreak) breakSecondsRemaining else secondsRemaining
                    val mins = displaySeconds / 60
                    val secs = displaySeconds % 60
                    val timeString = String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .border(3.dp, if (isInBreak) colors.info else colors.primary, CircleShape),
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
                                text = if (isInBreak) "Break Ends Soon" else "$selectedMinutes min session",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lockdown Strikes Info
                    if (isLockdownEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Strikes Remaining: ", color = colors.textSecondary, fontSize = 12.sp)
                            repeat(startingStrikes) { idx ->
                                val isLost = idx >= currentStrikesRemaining
                                Icon(
                                    imageVector = if (isLost) Icons.Default.Cancel else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isLost) colors.error else colors.primary,
                                    modifier = Modifier.size(16.dp).padding(horizontal = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$currentStrikesRemaining / $startingStrikes",
                                color = if (currentStrikesRemaining <= 1) colors.error else colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Simulation button for testing app exit in emulator
                        if (isRunning && !isInBreak) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    // Trigger a strike for leaving beyond grace period
                                    if (currentStrikesRemaining > 1) {
                                        currentStrikesRemaining--
                                        strikesUsedCount++
                                    } else {
                                        // Out of strikes -> broken session
                                        currentStrikesRemaining = 0
                                        strikesUsedCount++
                                        isRunning = false
                                        lastFinishedStatus = SessionStatus.BROKEN
                                        repository.logFocusSession(
                                            FocusSessionLog(
                                                title = "Focus Session (Broken)",
                                                plannedMinutes = selectedMinutes,
                                                actualMinutes = (selectedMinutes * 60 - secondsRemaining) / 60,
                                                isLockdownEnabled = true,
                                                startingStrikes = startingStrikes,
                                                strikesUsed = strikesUsedCount,
                                                breaksTaken = breaksTakenCount,
                                                status = SessionStatus.BROKEN
                                            )
                                        )
                                        showSummaryDialog = true
                                    }
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
                                    isRunning = true
                                    secondsRemaining = selectedMinutes * 60
                                    currentStrikesRemaining = startingStrikes
                                    strikesUsedCount = 0
                                    breaksTakenCount = 0
                                    emergencyPauseUsed = false
                                    isEmergencyPaused = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(8.dp),
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
                                    if (!emergencyPauseUsed) {
                                        emergencyPauseUsed = true
                                        isEmergencyPaused = !isEmergencyPaused
                                    }
                                },
                                enabled = !emergencyPauseUsed || isEmergencyPaused,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isEmergencyPaused) "Resume" else if (emergencyPauseUsed) "Pause Used" else "1x Pause",
                                    fontSize = 11.sp,
                                    color = if (emergencyPauseUsed && !isEmergencyPaused) colors.textMuted else colors.warning
                                )
                            }

                            // Stop / Abandon
                            Button(
                                onClick = {
                                    isRunning = false
                                    isEmergencyPaused = false
                                    isInBreak = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stop", color = colors.textPrimary)
                            }
                        }
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                                    .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                    .clickable { selectedMinutes = mins }
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
                                        text = "${calculateStrikesForDuration(mins)} strike",
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
                                text = "Leaving app beyond 15s grace costs 1 strike. $startingStrikes strikes available for this ${selectedMinutes}m session.",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isLockdownEnabled,
                            onCheckedChange = { isLockdownEnabled = it },
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

        // --- 4. Focus Session Logs History ---
        item {
            Text("Session History (${sessionLogs.size})", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        items(sessionLogs) { log ->
            val isSuccess = log.status == SessionStatus.COMPLETED
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isSuccess) colors.success else colors.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = log.title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    FioBadge(text = "${log.actualMinutes}m / ${log.plannedMinutes}m", color = colors.primary)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Strikes used: ${log.strikesUsed}/${log.startingStrikes} • Breaks: ${log.breaksTaken} • Logged: ${formatTimestamp(log.loggedAtMillis)}",
                    color = colors.textMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}
