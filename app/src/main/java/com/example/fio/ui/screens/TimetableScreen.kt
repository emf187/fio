package com.example.fio.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fio.alarms.TimetableAlarms
import com.example.fio.data.model.TimetablePeriod
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.theme.LocalFioColors
import java.util.Calendar

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun formatTimetableMinutes(minutes: Int): String =
    "%02d:%02d".format((minutes.coerceIn(0, 24 * 60 - 1)) / 60, minutes % 60)

fun todayMonSunIndex(): Int {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return when (dow) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
}

/**
 * Customizable class Timetable. Every row is user-defined: tap a row to edit
 * title, room, day, both times; switch the alarm per row so the phone actually
 * rings at each period's start (weekly repeat, survives reboot).
 */
@Composable
fun TimetableScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current
    val periods by repository.timetable.collectAsState()

    var selectedDay by remember { mutableStateOf(todayMonSunIndex()) }
    var editorPeriod by remember { mutableStateOf<TimetablePeriod?>(null) } // existing → edit
    var editorOpen by remember { mutableStateOf(false) }
    var periodToDelete by remember { mutableStateOf<TimetablePeriod?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) TimetableAlarms.ensureChannel(context)
    }

    // Keep the OS in charge of the real alarms whenever anything changes.
    LaunchedEffect(periods) {
        TimetableAlarms.ensureChannel(context)
        TimetableAlarms.rescheduleAll(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Day selector — Mon..Sun chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DAY_LABELS.forEachIndexed { index, label ->
                val day = index + 1
                val isSelected = day == selectedDay
                val isToday = day == todayMonSunIndex()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.primary else colors.surfaceElevated)
                        .clickable { selectedDay = day }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isToday) "$label ·" else label,
                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dayPeriods = periods.filter { it.dayOfWeek == selectedDay }
            if (dayPeriods.isEmpty()) {
                FioCard(padding = PaddingValues(16.dp)) {
                    Text("Nothing on ${DAY_LABELS[selectedDay - 1]} yet", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add a period and turn on its alarm — this tablet will ring at that time.",
                        color = colors.textMuted, fontSize = 11.sp
                    )
                }
            }
            dayPeriods.forEach { period ->
                FioCard(
                    backgroundColor = if (period.alarmEnabled) colors.surface else colors.surfaceElevated,
                    padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.width(78.dp)) {
                            Text(
                                text = formatTimetableMinutes(period.startMinutes),
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatTimetableMinutes(period.endMinutes),
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f).clickable { editorPeriod = period; editorOpen = true }) {
                            Text(
                                text = period.title,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (period.room.isNotBlank()) {
                                Text(text = period.room, color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = {
                            val enabling = !period.alarmEnabled
                            val updated = period.copy(alarmEnabled = enabling)
                            repository.upsertTimetablePeriod(updated)
                            if (enabling) {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                TimetableAlarms.schedule(
                                    context, period.id, period.title, period.room,
                                    period.dayOfWeek, period.startMinutes, period.endMinutes
                                )
                            } else {
                                TimetableAlarms.cancel(context, period.id)
                            }
                        }) {
                            Icon(
                                imageVector = if (period.alarmEnabled) Icons.Default.Alarm else Icons.Default.AlarmOff,
                                contentDescription = if (period.alarmEnabled) "Alarm on" else "Alarm off",
                                tint = if (period.alarmEnabled) colors.primary else colors.textMuted
                            )
                        }
                        IconButton(onClick = { periodToDelete = period }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = colors.textMuted
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(76.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            Button(
                onClick = { editorPeriod = null; editorOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Period", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (editorOpen) {
        PeriodEditorDialog(
            initial = editorPeriod,
            defaultDay = selectedDay,
            onDismiss = { editorOpen = false },
            onSave = { saved ->
                repository.upsertTimetablePeriod(saved)
                // Cancel first so the edited (maybe re-timed) period re-arms cleanly.
                TimetableAlarms.cancel(context, saved.id)
                if (saved.alarmEnabled) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    TimetableAlarms.schedule(
                        context, saved.id, saved.title, saved.room,
                        saved.dayOfWeek, saved.startMinutes, saved.endMinutes
                    )
                }
                editorOpen = false
            }
        )
    }

    periodToDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { periodToDelete = null },
            title = { Text("Delete period?") },
            text = { Text("\"${doomed.title}\" will be removed from the timetable and its alarm cancelled.") },
            confirmButton = {
                TextButton(onClick = {
                    TimetableAlarms.cancel(context, doomed.id)
                    repository.deleteTimetablePeriod(doomed.id)
                    periodToDelete = null
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { periodToDelete = null }) { Text("Keep") } }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PeriodEditorDialog(
    initial: TimetablePeriod?,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (TimetablePeriod) -> Unit
) {
    val colors = LocalFioColors.current

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var day by remember { mutableStateOf(initial?.dayOfWeek ?: defaultDay) }
    var startMinutes by remember { mutableStateOf(initial?.startMinutes ?: 540) }
    var endMinutes by remember { mutableStateOf(initial?.endMinutes ?: 585) }
    var alarmOn by remember { mutableStateOf(initial?.alarmEnabled ?: true) }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (initial == null) "Add period" else "Edit period",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Period title (e.g. Physics)", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tt_title"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room / note (optional)", color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tt_room"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                // Day picker
                Text("Day", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val d = index + 1
                        val isSelected = d == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.primary else colors.surfaceElevated)
                                .clickable { day = d }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.textPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Times
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickingStart = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("tt_start"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null
                    ) {
                        Text("Starts ${formatTimetableMinutes(startMinutes)}", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { pickingEnd = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("tt_end"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary,
                            containerColor = colors.surfaceElevated
                        ),
                        border = null
                    ) {
                        Text("Ends ${formatTimetableMinutes(endMinutes)}", fontSize = 12.sp)
                    }
                }

                // Alarm switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (alarmOn) colors.primary else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ring at start time", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Repeats every week on this day", color = colors.textMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = alarmOn,
                        onCheckedChange = { alarmOn = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.primary)
                    )
                }

                validationError?.let { Text(it, color = colors.error, fontSize = 11.sp) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) }
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                validationError = "Give the period a name first."
                            } else if (endMinutes <= startMinutes) {
                                validationError = "End time must be after the start time."
                            } else {
                                validationError = null
                                onSave(
                                    (initial ?: TimetablePeriod(title = "")).copy(
                                        title = title.trim(),
                                        room = room.trim(),
                                        dayOfWeek = day,
                                        startMinutes = startMinutes,
                                        endMinutes = endMinutes,
                                        alarmEnabled = alarmOn
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Save", color = androidx.compose.ui.graphics.Color.White) }
                }
            }
        }
    }

    if (pickingStart || pickingEnd) {
        val state = rememberTimePickerState(
            initialHour = (if (pickingStart) startMinutes else endMinutes) / 60,
            initialMinute = (if (pickingStart) startMinutes else endMinutes) % 60,
            is24Hour = true
        )
        Dialog(
            onDismissRequest = { pickingStart = false; pickingEnd = false },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(color = colors.surface, shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (pickingStart) "Start time" else "End time",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TimePicker(state = state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingStart = false; pickingEnd = false }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                        Button(
                            onClick = {
                                val picked = state.hour * 60 + state.minute
                                if (pickingStart) startMinutes = picked else endMinutes = picked
                                pickingStart = false
                                pickingEnd = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Set", color = androidx.compose.ui.graphics.Color.White) }
                    }
                }
            }
        }
    }
}

