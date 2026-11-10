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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.FocusSessionLog
import com.example.fio.data.model.SessionStatus
import com.example.fio.data.model.formatTimestamp
import com.example.fio.data.storage.FioRepository
import com.example.fio.focus.FocusTimer
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.rememberFioHaptics
import com.example.fio.ui.theme.LocalFioColors
import kotlinx.coroutines.delay

@Composable
fun TimerLockdownScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val settings by repository.settings.collectAsState()
    val sessionLogs by repository.sessionLogs.collectAsState()
    val haptics = rememberFioHaptics()

    // Timer Config
    var selectedMinutes by remember { mutableStateOf(50) }
    var isLockdownEnabled by remember { mutableStateOf(false) }

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

    // Surface the finish event (fires even if the user was on another page).
    LaunchedEffect(ts.phase, ts.finishedStatus) {
        if (ts.phase == FocusTimer.Phase.DONE && ts.finishedStatus != null && !showSummaryDialog) {
            lastFinishedStatus = ts.finishedStatus
            summaryMinutes = ts.alreadyFocusedMinutes
            summaryStrikesUsed = ts.strikesUsed
            summaryStrikesTotal = ts.startingStrikes
            summaryBreaks = ts.breaksTaken
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSummaryDialog = false
                        FocusTimer.consumeFinished()
                        eventPulse++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
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

                    // Circular Countdown Display (wall-clock based, always accurate)
                    val now = System.currentTimeMillis()
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
