@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
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
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.rememberFioHaptics
import com.example.fio.ui.theme.LocalFioColors
import java.util.Locale

/**
 * The Habits tab: the mhabit-inspired tracker with its own home. Tap a habit
 * to log today, long-press to edit, and tap the chart icon for the full
 * statistics sheet (heatmap, weekly bars, score curve).
 */
@Composable
fun HabitsScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val habits by repository.habits.collectAsState()
    val haptics = rememberFioHaptics()

    val activeHabits = habits.filter { !it.archived }
    val archivedHabits = habits.filter { it.archived }

    var showHabitDialog by remember { mutableStateOf(false) }
    var habitEditTarget by remember { mutableStateOf<HabitItem?>(null) }
    var habitPendingDelete by remember { mutableStateOf<HabitItem?>(null) }
    var habitStatsTarget by remember { mutableStateOf<HabitItem?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    // Add / Edit Habit Dialog (mhabit-inspired: goal, frequency, colour, reminder)
    if (showHabitDialog) {
        var habitName by remember { mutableStateOf(habitEditTarget?.name ?: "") }
        var habitQuestion by remember { mutableStateOf(habitEditTarget?.question ?: "") }
        var habitGoal by remember { mutableStateOf((habitEditTarget?.dailyGoal ?: 1).coerceAtLeast(1)) }
        var habitPerWeek by remember { mutableStateOf(habitEditTarget?.timesPerWeek ?: 7) }
        var habitColorIndex by remember { mutableStateOf(habitEditTarget?.colorIndex ?: 0) }
        var habitReminderOn by remember { mutableStateOf(habitEditTarget?.reminderMinutes != null) }
        val existingMinutes = habitEditTarget?.reminderMinutes ?: (8 * 60)
        var reminderHour12 by remember { mutableStateOf((((existingMinutes / 60) + 11) % 12) + 1) }
        var reminderMinuteStep by remember { mutableStateOf(0) } // index into 0,15,30,45
        var reminderAm by remember { mutableStateOf((existingMinutes / 60) < 12) }
        var archiveThisHabit by remember { mutableStateOf(habitEditTarget?.archived ?: false) }

        AlertDialog(
            onDismissRequest = { showHabitDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    if (habitEditTarget == null) "New Habit" else "Edit Habit",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = habitName,
                        onValueChange = { habitName = it },
                        label = { Text("Habit name", color = colors.textMuted) },
                        placeholder = { Text("e.g. Drink water", color = colors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    OutlinedTextField(
                        value = habitQuestion,
                        onValueChange = { habitQuestion = it },
                        label = { Text("Reminder question (optional)", color = colors.textMuted) },
                        placeholder = { Text("e.g. Did you drink enough water today?", color = colors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    // Daily goal: how many taps a day count as done (water glasses, pages…)
                    Text("Times per day", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 5, 8).forEach { g ->
                            val selected = habitGoal == g
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.primaryContainer else colors.surfaceElevated)
                                    .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptics.tick()
                                        habitGoal = g
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("$g", color = if (selected) colors.primary else colors.textPrimary, fontSize = 12.sp)
                            }
                        }
                        if (!listOf(1, 2, 3, 5, 8).contains(habitGoal)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primaryContainer)
                                    .border(1.dp, colors.primary, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("${habitGoal}", color = colors.primary, fontSize = 12.sp)
                            }
                        }
                    }

                    // Frequency: how many days a week this habit is expected
                    Text("Days a week", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..7).forEach { d ->
                            val selected = habitPerWeek == d
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.primaryContainer else colors.surfaceElevated)
                                    .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptics.tick()
                                        habitPerWeek = d
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (d == 7) "Every day" else "$d",
                                    color = if (selected) colors.primary else colors.textPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Colour picker
                    Text("Colour", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        val habitPalette = listOf(
                            0xFF34D399L, 0xFF60A5FA, 0xFFF472B6, 0xFFFBBF24, 0xFFA78BFA,
                            0xFF4ADE80, 0xFF22D3EE, 0xFFFB923C, 0xFF94A3B8, 0xFFE879F9
                        )
                        habitPalette.forEachIndexed { idx, colorLong ->
                            val selected = habitColorIndex == idx
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorLong))
                                    .border(
                                        if (selected) 2.dp else 0.dp,
                                        if (selected) colors.textPrimary else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable {
                                        haptics.tick()
                                        habitColorIndex = idx
                                    }
                            )
                        }
                    }

                    // Daily reminder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily reminder", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = habitReminderOn,
                            onCheckedChange = {
                                haptics.tick()
                                habitReminderOn = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.primary)
                        )
                    }
                    if (habitReminderOn) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 12-hour clock chips: hour, minute, AM/PM
                            var hourOpen by remember { mutableStateOf(false) }
                            var minOpen by remember { mutableStateOf(false) }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated)
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                        .clickable { hourOpen = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("$reminderHour12", color = colors.textPrimary, fontSize = 12.sp)
                                }
                                DropdownMenu(expanded = hourOpen, onDismissRequest = { hourOpen = false }, containerColor = colors.surfaceElevated) {
                                    (1..12).forEach { h ->
                                        DropdownMenuItem(
                                            text = { Text("$h", color = colors.textPrimary, fontSize = 12.sp) },
                                            onClick = { reminderHour12 = h; hourOpen = false }
                                        )
                                    }
                                }
                            }
                            Text(":", color = colors.textMuted, fontSize = 12.sp)
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated)
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                        .clickable { minOpen = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(String.format(Locale.getDefault(), "%02d", reminderMinuteStep * 15), color = colors.textPrimary, fontSize = 12.sp)
                                }
                                DropdownMenu(expanded = minOpen, onDismissRequest = { minOpen = false }, containerColor = colors.surfaceElevated) {
                                    listOf("00", "15", "30", "45").forEachIndexed { i, m ->
                                        DropdownMenuItem(
                                            text = { Text(m, color = colors.textPrimary, fontSize = 12.sp) },
                                            onClick = { reminderMinuteStep = i; minOpen = false }
                                        )
                                    }
                                }
                            }
                            listOf(true to "AM", false to "PM").forEach { (am, label) ->
                                val selected = reminderAm == am
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) colors.primaryContainer else colors.surfaceElevated)
                                        .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                        .clickable { reminderAm = am }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(label, color = if (selected) colors.primary else colors.textPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (habitEditTarget != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (archiveThisHabit) "Archived (hidden from daily list)" else "Active",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                            TextButton(onClick = { archiveThisHabit = !archiveThisHabit }) {
                                Text(
                                    if (archiveThisHabit) "Restore" else "Archive",
                                    color = colors.warning,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = habitName.trim()
                        if (name.isBlank()) {
                            Toast.makeText(context, "Give the habit a name first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val reminderMinutes = if (habitReminderOn) {
                            val hour24 = if (reminderAm) {
                                if (reminderHour12 == 12) 0 else reminderHour12
                            } else {
                                if (reminderHour12 == 12) 12 else reminderHour12 + 12
                            }
                            hour24 * 60 + (reminderMinuteStep * 15)
                        } else null
                        val saved = HabitItem(
                            id = habitEditTarget?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            description = habitEditTarget?.description ?: "",
                            dailyGoal = habitGoal.coerceAtLeast(1),
                            progressDates = habitEditTarget?.progressDates ?: emptyMap(),
                            streak = habitEditTarget?.streak ?: 0,
                            bestStreak = habitEditTarget?.bestStreak ?: 0,
                            completedDates = habitEditTarget?.completedDates ?: emptyList(),
                            colorIndex = habitColorIndex,
                            reminderMinutes = reminderMinutes,
                            question = habitQuestion.trim(),
                            timesPerWeek = habitPerWeek.coerceIn(1, 7),
                            archived = archiveThisHabit,
                            loggedAtMillis = habitEditTarget?.loggedAtMillis ?: currentTimestamp(),
                            lastCompletedAtMillis = habitEditTarget?.lastCompletedAtMillis
                        )
                        haptics.confirm()
                        if (habitEditTarget == null) repository.addHabit(saved) else repository.updateHabit(saved)
                        showHabitDialog = false
                        habitEditTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (habitEditTarget == null) "Create Habit" else "Save Changes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHabitDialog = false; habitEditTarget = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Delete habit confirmation
    habitPendingDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitPendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Delete habit?", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "“${habit.name}” and its full history will be permanently deleted. This cannot be undone.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.warn()
                    repository.deleteHabit(habit.id)
                    habitPendingDelete = null
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitPendingDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Full statistics sheet (heatmap, weekly bars, score curve)
    habitStatsTarget?.let { habit ->
        HabitDetailDialog(
            habit = habit,
            repository = repository,
            onDismiss = { habitStatsTarget = null },
            onEdit = {
                habitEditTarget = it
                showHabitDialog = true
            },
            onDelete = { habitPendingDelete = it }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header ---
        item {
            com.example.fio.ui.components.FioScreenHeader(
                title = "Habits",
                subtitle = "Small daily wins. Tap to log, long-press to edit."
            )
        }

        // --- Tracker card ---
        item {
            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("habits_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Repeat, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Today", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            haptics.tick()
                            habitEditTarget = null
                            showHabitDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_habit_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Habit", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (activeHabits.isEmpty()) {
                    Text(
                        "No habits yet. Start small — one glass of water, ten pages, a short walk. Tap a habit each day you do it and watch the streak grow.",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                } else {
                    val today = repository.todayDateString()
                    val todayDone = activeHabits.count { h -> h.completedDates.contains(today) }
                    val bestStreak = activeHabits.maxOf { it.bestStreak }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FioBadge(text = "$todayDone of ${activeHabits.size} done today", color = colors.primary)
                        if (bestStreak > 0) FioBadge(text = "Best streak $bestStreak", color = colors.warning)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    activeHabits.forEach { habit ->
                        val habitColor = Color(habit.colorValue())
                        val goal = habit.dailyGoal.coerceAtLeast(1)
                        val todayCount = habit.progressDates[today] ?: if (habit.completedDates.contains(today)) goal else 0
                        val doneToday = habit.completedDates.contains(today)

                        // Last 7 days of dots (mhabit-style strip, compressed to a week)
                        val weekDots = run {
                            var cursor = java.time.LocalDate.now()
                            val dots = mutableListOf<Pair<Boolean, Boolean>>() // (done, partial)
                            repeat(7) {
                                val d = cursor.toString()
                                val count = habit.progressDates[d] ?: 0
                                val complete = habit.completedDates.contains(d)
                                dots.add(complete to (!complete && count > 0))
                                cursor = cursor.minusDays(1)
                            }
                            dots.reversed()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .combinedClickable(
                                    onClick = {
                                        val willComplete = !doneToday
                                        repository.toggleHabitToday(habit.id)
                                        if (willComplete) haptics.confirm() else haptics.tick()
                                    },
                                    onLongClick = {
                                        haptics.longPress()
                                        habitEditTarget = habit
                                        showHabitDialog = true
                                    }
                                )
                                .padding(start = 10.dp, end = 4.dp, top = 9.dp, bottom = 9.dp)
                                .testTag("habit_item_${habit.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colour dot with today's count
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(habitColor.copy(alpha = if (doneToday) 0.9f else 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (doneToday) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(18.dp))
                                } else {
                                    Text(
                                        "$todayCount",
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = habit.name,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                // 7-day strip: filled = done, half = partial, outline = missed
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    weekDots.forEach { (done, partial) ->
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        done -> habitColor
                                                        partial -> habitColor.copy(alpha = 0.35f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    1.dp,
                                                    if (done || partial) Color.Transparent else colors.textMuted.copy(alpha = 0.4f),
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.LocalFireDepartment, contentDescription = null, tint = if (habit.streak > 0) colors.warning else colors.textMuted, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("${habit.streak}", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    if (goal > 1) "today $todayCount/$goal" else if (doneToday) "done" else "tap when done",
                                    color = if (doneToday) colors.success else colors.textMuted,
                                    fontSize = 9.sp
                                )
                            }

                            // Stats sheet (graphs)
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    habitStatsTarget = habit
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("habit_stats_button_${habit.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Habit statistics",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Tap a habit when you do it · long-press to edit · chart icon for graphs",
                        color = colors.textMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // --- Archived habits (kept for stats and backups, out of the daily list) ---
        if (archivedHabits.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.tick()
                            showArchived = !showArchived
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Archived habits (${archivedHabits.size})",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (showArchived) {
                items(archivedHabits, key = { it.id }) { habit ->
                    val habitColor = Color(habit.colorValue())
                    FioCard(
                        backgroundColor = colors.surface,
                        borderColor = colors.border,
                        padding = PaddingValues(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { habitStatsTarget = habit },
                                onLongClick = {
                                    haptics.longPress()
                                    habitEditTarget = habit
                                    showHabitDialog = true
                                }
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(habitColor.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                habit.name,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            FioBadge(text = "${HabitMath.totalDone(habit)} done", color = colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}
