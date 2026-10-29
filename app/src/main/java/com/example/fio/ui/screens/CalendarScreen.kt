package com.example.fio.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.*
import com.example.fio.data.storage.FioRepository
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.theme.LocalFioColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(repository: FioRepository) {
    val colors = LocalFioColors.current
    val context = LocalContext.current

    val events by repository.events.collectAsState()
    val sleepLogs by repository.sleepLogs.collectAsState()
    val mealLogs by repository.mealLogs.collectAsState()

    // Interactive Calendar Navigation State
    var displayedCalendar by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        mutableStateOf(cal)
    }

    var selectedDayTimestamp by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        mutableStateOf(cal.timeInMillis)
    }

    // Dialog States
    var showAddEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<ScheduleEvent?>(null) }
    var showIcsImportDialog by remember { mutableStateOf(false) }

    // Form inputs
    var eventTitle by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(EventType.STUDY) }
    var eventStartHour by remember { mutableStateOf("14") }
    var eventStartMinute by remember { mutableStateOf("00") }
    var eventDurationMinutes by remember { mutableStateOf("60") }

    // .ics input
    var icsContent by remember { mutableStateOf("") }

    // Synchronize to Phone Calendar / Reminders
    fun portEventToDeviceCalendar(event: ScheduleEvent) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.Events.DESCRIPTION, "${event.description}\n[Fio Planner]")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTimeMillis)
                putExtra(CalendarContract.Events.EVENT_LOCATION, "Fio Life Planner")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Porting '${event.title}' to system calendar/reminders", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open system calendar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to open add/edit event dialog
    fun openEventEditor(event: ScheduleEvent?) {
        if (event != null) {
            editingEvent = event
            eventTitle = event.title
            eventDescription = event.description
            eventType = event.type
            val cal = Calendar.getInstance().apply { timeInMillis = event.startTimeMillis }
            eventStartHour = cal.get(Calendar.HOUR_OF_DAY).toString()
            eventStartMinute = String.format(Locale.getDefault(), "%02d", cal.get(Calendar.MINUTE))
            val duration = (event.endTimeMillis - event.startTimeMillis) / (60 * 1000L)
            eventDurationMinutes = duration.toString()
        } else {
            editingEvent = null
            eventTitle = ""
            eventDescription = ""
            eventType = EventType.STUDY
            eventStartHour = "14"
            eventStartMinute = "00"
            eventDurationMinutes = "60"
        }
        showAddEventDialog = true
    }

    // Add or Edit Event Dialog
    if (showAddEventDialog) {
        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = if (editingEvent != null) "Edit Calendar Event" else "Add Event to Calendar",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event Title", color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("calendar_event_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    OutlinedTextField(
                        value = eventDescription,
                        onValueChange = { eventDescription = it },
                        label = { Text("Notes / Details", color = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    Text("Event Type:", color = colors.textSecondary, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(EventType.STUDY, EventType.REST, EventType.GYM, EventType.JOG, EventType.CLASS).forEach { type ->
                            val isSelected = eventType == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) colors.primaryContainer else colors.surfaceElevated)
                                    .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                    .clickable { eventType = type }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = type.name,
                                    color = if (isSelected) colors.primary else colors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = eventStartHour,
                            onValueChange = { eventStartHour = it },
                            label = { Text("Hour (0-23)", color = colors.textMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        OutlinedTextField(
                            value = eventStartMinute,
                            onValueChange = { eventStartMinute = it },
                            label = { Text("Minute", color = colors.textMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        OutlinedTextField(
                            value = eventDurationMinutes,
                            onValueChange = { eventDurationMinutes = it },
                            label = { Text("Mins", color = colors.textMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                    }

                    if (editingEvent != null) {
                        Text(
                            text = "Created: ${formatTimestamp(editingEvent!!.loggedAtMillis)}" +
                                    if (editingEvent!!.editedAtMillis != null) " • Last edited: ${formatTimestamp(editingEvent!!.editedAtMillis!!)}" else "",
                            color = colors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingEvent != null) {
                        TextButton(
                            onClick = {
                                repository.removeEvent(editingEvent!!.id)
                                showAddEventDialog = false
                            }
                        ) {
                            Text("Delete", color = colors.error)
                        }
                    }

                    Button(
                        onClick = {
                            if (eventTitle.isNotBlank()) {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = selectedDayTimestamp
                                    set(Calendar.HOUR_OF_DAY, eventStartHour.toIntOrNull()?.coerceIn(0, 23) ?: 14)
                                    set(Calendar.MINUTE, eventStartMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0)
                                    set(Calendar.SECOND, 0)
                                }
                                val start = cal.timeInMillis
                                val durationMs = (eventDurationMinutes.toLongOrNull() ?: 60L) * 60 * 1000L
                                val end = start + durationMs

                                if (editingEvent != null) {
                                    val updated = editingEvent!!.copy(
                                        title = eventTitle,
                                        description = eventDescription,
                                        startTimeMillis = start,
                                        endTimeMillis = end,
                                        type = eventType
                                    )
                                    repository.updateEvent(updated)
                                } else {
                                    val newEv = ScheduleEvent(
                                        title = eventTitle,
                                        description = eventDescription,
                                        startTimeMillis = start,
                                        endTimeMillis = end,
                                        type = eventType
                                    )
                                    repository.addEvent(newEv)
                                }
                                showAddEventDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(if (editingEvent != null) "Update" else "Save Event", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEventDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // .ICS Import Dialog
    if (showIcsImportDialog) {
        AlertDialog(
            onDismissRequest = { showIcsImportDialog = false },
            containerColor = colors.surface,
            title = { Text("Import School Calendar (.ics)", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste .ics file content or import default university sample schedule:", color = colors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = icsContent,
                        onValueChange = { icsContent = it },
                        placeholder = { Text("BEGIN:VCALENDAR...", color = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            repository.addEvent(ScheduleEvent(title = "Operating Systems Lab", startTimeMillis = now + (2 * 3600 * 1000L), endTimeMillis = now + (4 * 3600 * 1000L), type = EventType.CLASS))
                            repository.addEvent(ScheduleEvent(title = "Cognitive Psychology Seminar", startTimeMillis = now + (26 * 3600 * 1000L), endTimeMillis = now + (28 * 3600 * 1000L), type = EventType.CLASS))
                            showIcsImportDialog = false
                            Toast.makeText(context, "Sample university classes imported", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import University Sample Schedule", color = colors.primary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lines = icsContent.lines()
                        for (line in lines) {
                            if (line.startsWith("SUMMARY:")) {
                                val title = line.removePrefix("SUMMARY:").trim()
                                if (title.isNotBlank()) {
                                    val now = System.currentTimeMillis()
                                    repository.addEvent(ScheduleEvent(title = title, startTimeMillis = now + (3600 * 1000L), endTimeMillis = now + (2 * 3600 * 1000L), type = EventType.CLASS))
                                }
                            }
                        }
                        showIcsImportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Import .ICS", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIcsImportDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            }
        )
    }

    // Filter events for the selected day
    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDayTimestamp }
    val selectedYear = selectedCal.get(Calendar.YEAR)
    val selectedDayOfYear = selectedCal.get(Calendar.DAY_OF_YEAR)

    val eventsOnSelectedDay = events.filter {
        val evCal = Calendar.getInstance().apply { timeInMillis = it.startTimeMillis }
        evCal.get(Calendar.YEAR) == selectedYear && evCal.get(Calendar.DAY_OF_YEAR) == selectedDayOfYear
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Top Header Actions ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Calendar & Scheduler", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Interactive visual calendar & phone reminders sync", color = colors.textMuted, fontSize = 11.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showIcsImportDialog = true },
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.surfaceElevated)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Import .ics", tint = colors.primary)
                    }

                    IconButton(
                        onClick = { openEventEditor(null) },
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primary).testTag("add_calendar_event_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Event", tint = Color.White)
                    }
                }
            }
        }

        // --- 2. Interactive Calendar Month Grid ---
        item {
            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val monthName = monthFormat.format(displayedCalendar.time)

            FioCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                padding = PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("interactive_calendar_card")
            ) {
                // Month Header with Prev / Next Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newCal = displayedCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            displayedCalendar = newCal
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Prev Month", tint = colors.textPrimary)
                    }

                    Text(
                        text = monthName,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val todayCal = Calendar.getInstance()
                                todayCal.set(Calendar.DAY_OF_MONTH, 1)
                                displayedCalendar = todayCal

                                val sel = Calendar.getInstance()
                                sel.set(Calendar.HOUR_OF_DAY, 0)
                                sel.set(Calendar.MINUTE, 0)
                                sel.set(Calendar.SECOND, 0)
                                sel.set(Calendar.MILLISECOND, 0)
                                selectedDayTimestamp = sel.timeInMillis
                            }
                        ) {
                            Text("Today", color = colors.primary, fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = {
                                val newCal = displayedCalendar.clone() as Calendar
                                newCal.add(Calendar.MONTH, 1)
                                displayedCalendar = newCal
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Month", tint = colors.textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Day of Week Headers (Mon, Tue, Wed, Thu, Fri, Sat, Sun)
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { dayLabel ->
                        Text(
                            text = dayLabel,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                FioDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Calculate Days Matrix
                val calCopy = displayedCalendar.clone() as Calendar
                calCopy.set(Calendar.DAY_OF_MONTH, 1)
                val firstDayOfWeek = (calCopy.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Convert Sunday=1 to Monday=0
                val daysInMonth = calCopy.getActualMaximum(Calendar.DAY_OF_MONTH)

                val todayCal = Calendar.getInstance()
                val isCurrentMonthAndYear = displayedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                        displayedCalendar.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
                val currentDayOfMonth = todayCal.get(Calendar.DAY_OF_MONTH)

                val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
                val weeks = totalCells / 7

                for (w in 0 until weeks) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        for (d in 0 until 7) {
                            val cellIndex = w * 7 + d
                            val dayNumber = cellIndex - firstDayOfWeek + 1

                            if (dayNumber in 1..daysInMonth) {
                                val cellCal = displayedCalendar.clone() as Calendar
                                cellCal.set(Calendar.DAY_OF_MONTH, dayNumber)
                                cellCal.set(Calendar.HOUR_OF_DAY, 0)
                                cellCal.set(Calendar.MINUTE, 0)
                                cellCal.set(Calendar.SECOND, 0)
                                cellCal.set(Calendar.MILLISECOND, 0)
                                val cellTime = cellCal.timeInMillis

                                val isSelected = cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                        cellCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                                val isToday = isCurrentMonthAndYear && dayNumber == currentDayOfMonth

                                // Check if there are events on this day
                                val hasEvents = events.any {
                                    val evC = Calendar.getInstance().apply { timeInMillis = it.startTimeMillis }
                                    evC.get(Calendar.YEAR) == cellCal.get(Calendar.YEAR) && evC.get(Calendar.DAY_OF_YEAR) == cellCal.get(Calendar.DAY_OF_YEAR)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                isSelected -> colors.primaryContainer
                                                isToday -> colors.surfaceElevated
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else if (isToday) 1.dp else 0.dp,
                                            color = if (isSelected) colors.primary else if (isToday) colors.border else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedDayTimestamp = cellTime }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$dayNumber",
                                            color = when {
                                                isSelected -> colors.primary
                                                isToday -> colors.warning
                                                else -> colors.textPrimary
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (hasEvents) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) colors.primary else colors.info)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Events On Selected Day with Direct Edit/Delete ---
        val selectedDateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(selectedDayTimestamp))

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedDateStr,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${eventsOnSelectedDay.size} scheduled item(s) on this date",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = { openEventEditor(null) }
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Event", color = colors.primary, fontSize = 12.sp)
                }
            }
        }

        if (eventsOnSelectedDay.isEmpty()) {
            item {
                FioCard(
                    backgroundColor = colors.surfaceElevated,
                    borderColor = colors.border,
                    padding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("No events scheduled for this day.", color = colors.textMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { openEventEditor(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("+ Schedule Study / Rest Block Here", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            items(eventsOnSelectedDay) { event ->
                val typeColor = when (event.type) {
                    EventType.REST -> colors.info
                    EventType.STUDY -> colors.primary
                    EventType.GYM, EventType.JOG -> colors.warning
                    EventType.CLASS -> colors.success
                    else -> colors.textSecondary
                }

                FioCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = event.isCompleted,
                            onCheckedChange = { repository.toggleEventCompleted(event.id) },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { openEventEditor(event) } // Edit directly by tapping
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FioBadge(text = event.type.name, color = typeColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = event.title,
                                    color = if (event.isCompleted) colors.textMuted else colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            if (event.description.isNotBlank()) {
                                Text(text = event.description, color = colors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatTimeOnly(event.startTimeMillis)} - ${formatTimeOnly(event.endTimeMillis)}",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Logged: ${formatTimestamp(event.loggedAtMillis)}" +
                                        if (event.editedAtMillis != null) " • Edited: ${formatTimestamp(event.editedAtMillis)}" else "",
                                color = colors.textMuted,
                                fontSize = 9.sp
                            )
                        }

                        // Port to System Phone Calendar & Reminders action (user request!)
                        IconButton(
                            onClick = { portEventToDeviceCalendar(event) },
                            modifier = Modifier.testTag("port_to_system_reminder_${event.id}")
                        ) {
                            Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = "Port to Phone Reminders", tint = colors.warning, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { openEventEditor(event) }
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // --- 4. Phone Reminders & System Sync Card ---
        item {
            FioCard(
                backgroundColor = colors.surfaceElevated,
                borderColor = colors.border,
                padding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Port Schedule to Phone Reminders", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Syncs your Fio sessions, rest blocks, and deadlines straight into your device's native system Calendar & Reminders app using standard Android CalendarContract.",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val upcoming = events.filter { it.startTimeMillis >= System.currentTimeMillis() }.take(5)
                        if (upcoming.isEmpty()) {
                            Toast.makeText(context, "No upcoming events to port", Toast.LENGTH_SHORT).show()
                        } else {
                            portEventToDeviceCalendar(upcoming.first())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.warning),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("sync_phone_reminders_button")
                ) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Port Upcoming Events to Phone Reminders", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
