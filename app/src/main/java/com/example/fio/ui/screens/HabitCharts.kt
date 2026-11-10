package com.example.fio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.HabitItem
import com.example.fio.data.model.formatTimestamp
import com.example.fio.ui.components.FioBadge
import com.example.fio.ui.components.FioDivider
import com.example.fio.ui.components.rememberFioHaptics
import com.example.fio.ui.theme.FioType
import com.example.fio.ui.theme.LocalFioColors
import java.time.LocalDate

/**
 * The habit statistics engine, forked from the mhabit reference app and
 * rebuilt for Fio: a GitHub-style heatmap with tap-to-fix records, weekly
 * frequency bars, a rolling habit-score curve, and the summary numbers.
 * Everything is plain Canvas drawing — no chart library, so it stays fast
 * and offline.
 */
object HabitMath {

    /** Cell states mirroring mhabit's heatmap map: 0 none, 1 partial, 2 done, 3 overfulfil. */
    const val DAY_NONE = 0
    const val DAY_PARTIAL = 1
    const val DAY_DONE = 2
    const val DAY_OVER = 3

    fun dayStatus(habit: HabitItem, date: LocalDate): Int {
        val goal = habit.dailyGoal.coerceAtLeast(1)
        val count = habit.progressDates[date.toString()] ?: 0
        val complete = habit.completedDates.contains(date.toString())
        return when {
            count > goal -> DAY_OVER
            complete || count >= goal -> DAY_DONE
            count > 0 -> DAY_PARTIAL
            else -> DAY_NONE
        }
    }

    /**
     * Heatmap grid: `weeks` columns, each a Mon..Sun week, oldest first,
     * ending with the current week. Days outside the range are null.
     */
    fun heatmapGrid(habit: HabitItem, weeks: Int = 15): List<List<LocalDate?>> {
        val today = LocalDate.now()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val startMonday = thisMonday.minusDays(((weeks - 1) * 7).toLong())
        return (0 until weeks).map { w ->
            val monday = startMonday.plusDays((w * 7).toLong())
            (0..6).map { d ->
                val date = monday.plusDays(d.toLong())
                if (date.isAfter(today)) null else date
            }
        }
    }

    /** Completions per week (oldest first) for the frequency bars. */
    fun weeklyCounts(habit: HabitItem, weeks: Int = 12): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val startMonday = thisMonday.minusDays(((weeks - 1) * 7).toLong())
        return (0 until weeks).map { w ->
            val monday = startMonday.plusDays((w * 7).toLong())
            var count = 0
            (0..6).forEach { d ->
                val date = monday.plusDays(d.toLong())
                if (!date.isAfter(today) && habit.completedDates.contains(date.toString())) count++
            }
            monday to count
        }
    }

    /**
     * Rolling habit-score curve (0–100), one point per day for the last
     * `days` days — the same blend that feeds the Life Score: today's
     * progress, the trailing 7-day success rate and the streak strength.
     */
    fun scoreSeries(habit: HabitItem, days: Int = 90): List<Pair<LocalDate, Float>> {
        val goal = habit.dailyGoal.coerceAtLeast(1)
        val interval = if (habit.timesPerWeek >= 7) 1 else (7 / habit.timesPerWeek.coerceIn(1, 7)).coerceAtLeast(1)
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong())
        var streak = 0
        val ringDone = IntArray(7)
        val ringDue = IntArray(7)
        var ringPos = 0
        var doneInWindow = 0
        var dueInWindow = 0
        val out = mutableListOf<Pair<LocalDate, Float>>()
        var cursor = start
        repeat(days) { i ->
            val key = cursor.toString()
            val count = habit.progressDates[key] ?: 0
            val done = habit.completedDates.contains(key)
            val due = i % interval == 0
            // Slide the 7-day window.
            ringPos = i % 7
            doneInWindow -= ringDone[ringPos]
            dueInWindow -= ringDue[ringPos]
            ringDone[ringPos] = if (done) 1 else 0
            ringDue[ringPos] = if (due) 1 else 0
            doneInWindow += ringDone[ringPos]
            dueInWindow += ringDue[ringPos]
            // Streak: grows on completed days, pauses on not-due skips,
            // resets when a due day was missed.
            streak = when {
                done -> streak + 1
                due -> 0
                else -> streak
            }
            val todayPart = (count.toDouble() / goal).coerceIn(0.0, 1.0)
            val weekRate = if (dueInWindow > 0) doneInWindow.toDouble() / dueInWindow else 0.0
            val streakPart = streak.coerceAtMost(14).toDouble() / 14.0
            val score = ((0.5 * todayPart) + (0.35 * weekRate) + (0.15 * streakPart)) * 100.0
            out.add(cursor to score.toFloat())
            cursor = cursor.plusDays(1)
        }
        return out
    }

    /** Total days completed since the habit was created. */
    fun totalDone(habit: HabitItem): Int = habit.completedDates.size

    /** 30-day completion rate as a percentage. */
    fun completionRate30(habit: HabitItem): Int {
        val interval = if (habit.timesPerWeek >= 7) 1 else (7 / habit.timesPerWeek.coerceIn(1, 7)).coerceAtLeast(1)
        var done = 0
        var due = 0
        var cursor = LocalDate.now()
        repeat(30) { i ->
            if (i % interval == 0) {
                due++
                if (habit.completedDates.contains(cursor.toString())) done++
            }
            cursor = cursor.minusDays(1)
        }
        return if (due > 0) (done * 100 / due) else 0
    }

    /** Completions this calendar week vs last (Monday-based). */
    fun thisWeekVsLast(habit: HabitItem): Pair<Int, Int> {
        val today = LocalDate.now()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        fun countSince(monday: LocalDate, end: LocalDate): Int {
            var c = 0
            var cursor = monday
            while (!cursor.isAfter(end)) {
                if (habit.completedDates.contains(cursor.toString())) c++
                cursor = cursor.plusDays(1)
            }
            return c
        }
        val thisWeek = countSince(thisMonday, today)
        val lastWeek = countSince(thisMonday.minusDays(7), thisMonday.minusDays(1))
        return thisWeek to lastWeek
    }
}

// ---------------------------------------------------------------------------
// Charts (pure Canvas)
// ---------------------------------------------------------------------------

/** GitHub-style heatmap: weeks as columns, Mon..Sun rows, month labels on top. */
@Composable
fun HabitHeatmap(
    habit: HabitItem,
    modifier: Modifier = Modifier
) {
    val colors = LocalFioColors.current
    val haptics = rememberFioHaptics()
    val habitColor = Color(habit.colorValue())
    val grid = HabitMath.heatmapGrid(habit, weeks = 15)
    val cell = 15.dp
    val gap = 3.dp
    val headerH = 16.dp
    val leftLabels = 18.dp
    val width = leftLabels + (cell + gap) * 15
    val height = headerH + (cell + gap) * 7
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 7.sp, color = colors.textMuted)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Last 15 weeks — tap a day to fix it",
            style = FioType.caption,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box {
            Canvas(
                modifier = Modifier
                    .width(width)
                    .height(height)
            ) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val headerPx = headerH.toPx()
            val leftPx = leftLabels.toPx()

            // Month labels: above the first column whose Monday starts a new month.
            var lastMonth = -1
            grid.forEachIndexed { w, week ->
                val first = week.firstOrNull { it != null } ?: return@forEachIndexed
                if (first.monthValue != lastMonth) {
                    lastMonth = first.monthValue
                    val label = first.format(java.time.format.DateTimeFormatter.ofPattern("MMM"))
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        measured,
                        topLeft = Offset(
                            leftPx + w * (cellPx + gapPx),
                            (headerPx - measured.size.height) / 2f
                        )
                    )
                }
            }

            // Weekday letters on the left (Mon / Wed / Fri only, mhabit-style).
            listOf(0 to "M", 2 to "W", 4 to "F").forEach { (row, letter) ->
                val measured = textMeasurer.measure(letter, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        0f,
                        headerPx + row * (cellPx + gapPx) + (cellPx - measured.size.height) / 2f
                    )
                )
            }

            // Cells
            grid.forEachIndexed { w, week ->
                week.forEachIndexed { d, date ->
                    if (date == null) return@forEachIndexed
                    val status = HabitMath.dayStatus(habit, date)
                    val topLeft = Offset(
                        leftPx + w * (cellPx + gapPx),
                        headerPx + d * (cellPx + gapPx)
                    )
                    when (status) {
                        HabitMath.DAY_DONE -> drawRoundRect(
                            color = habitColor,
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(cellPx * 0.28f)
                        )
                        HabitMath.DAY_OVER -> {
                            drawRoundRect(
                                color = habitColor,
                                topLeft = topLeft,
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(cellPx * 0.28f)
                            )
                            // Overfulfil badge: bright dot in the centre.
                            drawCircle(
                                color = colors.background,
                                radius = cellPx * 0.14f,
                                center = Offset(topLeft.x + cellPx / 2f, topLeft.y + cellPx / 2f)
                            )
                        }
                        HabitMath.DAY_PARTIAL -> drawRoundRect(
                            color = habitColor.copy(alpha = 0.35f),
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(cellPx * 0.28f)
                        )
                        else -> drawRoundRect(
                            color = colors.surfaceElevated,
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(cellPx * 0.28f)
                        )
                    }
                }
            }
            }

            // Tap targets aligned over the drawn cells (same geometry as the canvas).
            Row(
                modifier = Modifier
                    .offset(y = headerH)
                    .width(width)
            ) {
                Spacer(modifier = Modifier.width(leftLabels))
                grid.forEachIndexed { w, week ->
                    Column {
                        week.forEach { date ->
                            Box(
                                modifier = Modifier
                                    .size(cell + gap)
                                    .clickable(
                                        enabled = date != null && !date.isAfter(LocalDate.now())
                                    ) {
                                        date?.let {
                                            haptics.tick()
                                            onHeatmapCellTap(habit, it)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                "missed" to colors.surfaceElevated,
                "partial" to habitColor.copy(alpha = 0.35f),
                "done" to habitColor
            ).forEach { (label, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, colors.border.copy(alpha = 0.5f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(label, style = FioType.caption, color = colors.textMuted, fontSize = 8.sp)
                }
            }
        }
    }
}

/** Wiring set by HabitDetailDialog so the heatmap tap can reach the repository. */
private var onHeatmapCellTap: (HabitItem, LocalDate) -> Unit = { _, _ -> }

/**
 * Full-screen habit statistics page, opened from the Habits tab: heatmap with
 * tap-to-fix records, weekly frequency bars and the rolling score curve, with
 * a back button in the top bar and the actions pinned at the bottom.
 */
@Composable
fun HabitDetailScreen(
    habit: HabitItem,
    repository: com.example.fio.data.storage.FioRepository,
    onBack: () -> Unit,
    onEdit: (HabitItem) -> Unit,
    onDelete: (HabitItem) -> Unit
) {
    val colors = LocalFioColors.current
    val haptics = rememberFioHaptics()
    BackHandler(enabled = true) { onBack() }
    onHeatmapCellTap = { h, date ->
        val complete = h.completedDates.contains(date.toString())
        val goal = h.dailyGoal.coerceAtLeast(1)
        repository.setHabitDay(h.id, date.toString(), if (complete) 0 else goal)
        if (!complete) haptics.confirm() else haptics.tick()
    }

    val habitColor = Color(habit.colorValue())
    val totalDone = HabitMath.totalDone(habit)
    val rate30 = HabitMath.completionRate30(habit)
    val (thisWeek, lastWeek) = HabitMath.thisWeekVsLast(habit)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // --- Top bar: back button + identity ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    haptics.tick()
                    onBack()
                },
                modifier = Modifier.testTag("habit_detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to habits",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(habitColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (habit.question.isNotBlank()) {
                    Text(habit.question, color = colors.textMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        // --- Scrollable statistics ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Summary pill strip ---
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FioBadge(
                    text = "${habit.streak} streak",
                    color = if (habit.streak > 0) colors.warning else colors.textMuted
                )
                FioBadge(text = "Best ${habit.bestStreak}", color = colors.primary)
                FioBadge(text = "$totalDone done", color = colors.success)
                FioBadge(text = "$rate30% (30d)", color = colors.info)
            }
            Text(
                "This week $thisWeek · last week $lastWeek · reminder " +
                    (habit.reminderMinutes?.let { m ->
                        val h12 = ((m / 60 + 11) % 12) + 1
                        "$h12:${String.format("%02d", m % 60)} ${if (m / 60 < 12) "AM" else "PM"}"
                    } ?: "off"),
                style = FioType.caption,
                color = colors.textMuted
            )

            FioDivider()

            // --- Heatmap ---
            HabitHeatmap(habit = habit)

            FioDivider()

            // --- Weekly frequency bars ---
            Text(
                "Completions per week",
                style = FioType.caption,
                color = colors.textMuted
            )
            HabitWeeklyBars(habit = habit)

            FioDivider()

            // --- Score curve ---
            Text(
                "Habit score (90 days)",
                style = FioType.caption,
                color = colors.textMuted
            )
            HabitScoreLine(habit = habit)

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Created ${formatTimestamp(habit.loggedAtMillis)}",
                style = FioType.caption,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // --- Pinned actions ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = {
                haptics.tick()
                onEdit(habit)
            }) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit", color = colors.primary, fontSize = 12.sp)
            }
            TextButton(onClick = {
                haptics.tick()
                repository.updateHabit(habit.copy(archived = !habit.archived))
            }) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = colors.warning, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (habit.archived) "Restore" else "Archive", color = colors.warning, fontSize = 12.sp)
            }
            TextButton(onClick = {
                haptics.longPress()
                onDelete(habit)
            }) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", color = colors.error, fontSize = 12.sp)
            }
        }
    }
}

/** Weekly frequency bars: one rounded bar per week, oldest on the left. */
@Composable
fun HabitWeeklyBars(
    habit: HabitItem,
    modifier: Modifier = Modifier
) {
    val colors = LocalFioColors.current
    val habitColor = Color(habit.colorValue())
    val weeks = remember(habit) { HabitMath.weeklyCounts(habit, weeks = 12) }
    val maxValue = (weeks.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 7.sp, color = colors.textMuted)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .testTag("habit_weekly_bars")
    ) {
        val barW = 16.dp.toPx()
        val gap = (size.width - 12 * barW) / 11f
        val chartH = size.height - 14.dp.toPx()
        // Baseline + 50% guide.
        drawLine(
            color = colors.border,
            start = Offset(0f, chartH),
            end = Offset(size.width, chartH),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = colors.border.copy(alpha = 0.5f),
            start = Offset(0f, chartH / 2f),
            end = Offset(size.width, chartH / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        weeks.forEachIndexed { i, (monday, count) ->
            val x = i * (barW + gap)
            val h = if (count == 0) 2.dp.toPx() else (count.toFloat() / maxValue) * (chartH - 8.dp.toPx())
            drawRoundRect(
                color = if (count == 0) colors.surfaceElevated else habitColor,
                topLeft = Offset(x, chartH - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW * 0.3f)
            )
            // Label the first, middle and last week.
            if (i == 0 || i == 6 || i == 11) {
                val label = monday.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
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
}

/** Rolling score curve: line + soft area fill, 0–100 with dashed guides. */
@Composable
fun HabitScoreLine(
    habit: HabitItem,
    modifier: Modifier = Modifier
) {
    val colors = LocalFioColors.current
    val habitColor = Color(habit.colorValue())
    val series = remember(habit) { HabitMath.scoreSeries(habit, days = 90) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 7.sp, color = colors.textMuted)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .testTag("habit_score_line")
    ) {
        val chartH = size.height - 14.dp.toPx()
        val n = series.size.coerceAtLeast(2)
        fun y(score: Float): Float = chartH - (score.coerceIn(0f, 100f) / 100f) * (chartH - 4.dp.toPx())

        // Guides at 0 / 50 / 100.
        listOf(0 to "0", 50 to "50", 100 to "100").forEach { (v, label) ->
            val yy = y(v.toFloat())
            drawLine(
                color = colors.border.copy(alpha = if (v == 0) 1f else 0.5f),
                start = Offset(0f, yy),
                end = Offset(size.width, yy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (v == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft = Offset(0f, (yy - measured.size.height - 2f).coerceAtLeast(0f))
            )
        }

        // Area fill.
        val fill = Path().apply {
            var startX = 0f
            series.forEachIndexed { i, (_, score) ->
                val x = i.toFloat() / (n - 1) * size.width
                val yy = y(score)
                if (i == 0) {
                    moveTo(x, yy)
                    startX = x
                } else {
                    lineTo(x, yy)
                }
            }
            lineTo(size.width, chartH)
            lineTo(startX, chartH)
            close()
        }
        drawPath(fill, habitColor.copy(alpha = 0.14f))

        // Line.
        val line = Path().apply {
            series.forEachIndexed { i, (_, score) ->
                val x = i.toFloat() / (n - 1) * size.width
                val yy = y(score)
                if (i == 0) moveTo(x, yy) else lineTo(x, yy)
            }
        }
        drawPath(
            line,
            habitColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
