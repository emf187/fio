package com.example.fio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors
import java.util.Locale

@Composable
fun HealthTrackingScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val sleepLogs by repository.sleepLogs.collectAsState()
    val mealLogs by repository.mealLogs.collectAsState()
    val settings by repository.settings.collectAsState()

    val pendingSleepStart = remember { repository.getPendingSleepStart() }
    var isSleeping by remember { mutableStateOf(pendingSleepStart != null) }

    // Dialog states
    var showManualSleepDialog by remember { mutableStateOf(false) }
    var showEditMealDialog by remember { mutableStateOf<MealEntry?>(null) }
    var editedMealHoursAgo by remember { mutableStateOf("1") }

    // Manual Sleep Fields
    var manualSleepType by remember { mutableStateOf(SleepType.NIGHT) }
    var manualSleepDurationHours by remember { mutableStateOf("8.0") }

    // Time stretch since last meal
    val lastMeal = mealLogs.firstOrNull()
    val now = System.currentTimeMillis()
    val hoursSinceLastMeal = if (lastMeal != null) {
        (now - lastMeal.timestampMillis) / (1000.0 * 60 * 60)
    } else 0.0

    // Manual Sleep Dialog
    if (showManualSleepDialog) {
        AlertDialog(
            onDismissRequest = { showManualSleepDialog = false },
            containerColor = colors.surface,
            title = { Text("Log Sleep / Nap", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SleepType.values().forEach { type ->
                            val isSelected = manualSleepType == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                                    .clickable { manualSleepType = type }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (type == SleepType.NIGHT) "Night Sleep" else "Afternoon Nap",
                                    color = if (isSelected) colors.primary else colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = manualSleepDurationHours,
                        onValueChange = { manualSleepDurationHours = it },
                        label = { Text("Hours Slept", color = colors.textMuted) },
                        singleLine = true,
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
                        val hours = manualSleepDurationHours.toDoubleOrNull() ?: 8.0
                        val endTime = System.currentTimeMillis()
                        val startTime = endTime - (hours * 3600 * 1000L).toLong()
                        repository.logSleep(
                            SleepEntry(
                                type = manualSleepType,
                                startTimeMillis = startTime,
                                endTimeMillis = endTime,
                                qualityRating = 4
                            )
                        )
                        showManualSleepDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Save Sleep Log", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualSleepDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Edit Meal Dialog
    showEditMealDialog?.let { meal ->
        AlertDialog(
            onDismissRequest = { showEditMealDialog = null },
            containerColor = colors.surface,
            title = { Text("Adjust Meal Timestamp", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("If you forgot to log at the exact moment, specify how many hours ago it was eaten:", color = colors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editedMealHoursAgo,
                        onValueChange = { editedMealHoursAgo = it },
                        label = { Text("Hours ago", color = colors.textMuted) },
                        singleLine = true,
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
                        val hoursAgo = editedMealHoursAgo.toDoubleOrNull() ?: 1.0
                        val newTime = System.currentTimeMillis() - (hoursAgo * 3600 * 1000L).toLong()
                        repository.updateMealTime(meal.id, newTime)
                        showEditMealDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Update Time", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMealDialog = null }) {
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
        // --- Header ---
        item {
            Column {
                Text("Health, Sleep & Meal Log", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Simple, distraction-free logging with zero calorie or weight tracking", color = colors.textMuted, fontSize = 11.sp)
            }
        }

        // --- 1. Sleep Tracker Card ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("sleep_tracker_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sleep & Naps", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showManualSleepDialog = true }) {
                        Text("+ Manual Log", color = colors.primary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Going to sleep / Awake Shortcut Buttons (Section 11 requirement)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isSleeping) {
                                repository.quickSleepAwake()
                                isSleeping = false
                            } else {
                                repository.quickSleepStart()
                                isSleeping = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSleeping) colors.warning else colors.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("sleep_shortcut_button")
                    ) {
                        Icon(
                            imageVector = if (isSleeping) Icons.Default.WbSunny else Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSleeping) "I'm Awake Now" else "Going To Sleep", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isSleeping) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Currently sleeping... Wake up button will record your total hours slept automatically.", color = colors.warning, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("Recent Sleep Entries", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                sleepLogs.take(3).forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FioBadge(text = s.type.name, color = if (s.type == SleepType.NIGHT) colors.primary else colors.info)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f hours", s.durationHours),
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${formatTimeOnly(s.startTimeMillis)} - ${formatTimeOnly(s.endTimeMillis)} • Logged: ${formatTimestamp(s.loggedAtMillis)}",
                                color = colors.textMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Meal Log Card (One-tap 'I Ate') ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("meal_log_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Restaurant, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Meal Log", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    FioBadge(text = "${mealLogs.size} meals logged", color = colors.warning)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // One-tap "I Ate" button (Section 11 requirement)
                Button(
                    onClick = { repository.logMeal("Meal") },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.warning),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("i_ate_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("I Ate (Record Time Automatically)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Gentle non-judgmental reminder if long stretch without food
                if (hoursSinceLastMeal > 5.5) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.warning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.FavoriteBorder, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gentle reminder: It has been ${hoursSinceLastMeal.toInt()} hours since your last logged meal. Remember to take a break and nourish yourself.",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("Today's Meal Times", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                mealLogs.forEach { meal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Eaten at: ${formatTimeOnly(meal.timestampMillis)}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Recorded: ${formatTimestamp(meal.loggedAtMillis)}" +
                                        if (meal.editedAtMillis != null) " • Edited: ${formatTimestamp(meal.editedAtMillis)}" else "",
                                color = colors.textMuted,
                                fontSize = 9.sp
                            )
                        }

                        Row {
                            IconButton(onClick = { showEditMealDialog = meal }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Time", tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { repository.deleteMeal(meal.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = colors.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Gym, Jogging & Rest Blocks Scheduler ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp)
            ) {
                Text("Activity & Recovery Schedule", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Schedule workouts and dedicated 'do nothing' rest blocks as first-class priorities in your calendar.", color = colors.textMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            repository.addEvent(
                                ScheduleEvent(
                                    title = "Gym & Strength Training",
                                    startTimeMillis = now + (3 * 3600 * 1000L),
                                    endTimeMillis = now + (4 * 3600 * 1000L) + (15 * 60 * 1000L),
                                    type = EventType.GYM
                                )
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Gym Session", fontSize = 11.sp, color = colors.warning)
                    }

                    OutlinedButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            repository.addEvent(
                                ScheduleEvent(
                                    title = "Mindful Rest / Nap Block",
                                    description = "Unplugged rest - do nothing time",
                                    startTimeMillis = now + (1 * 3600 * 1000L),
                                    endTimeMillis = now + (1 * 3600 * 1000L) + (45 * 60 * 1000L),
                                    type = EventType.REST
                                )
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Rest Block", fontSize = 11.sp, color = colors.info)
                    }
                }
            }
        }
    }
}
