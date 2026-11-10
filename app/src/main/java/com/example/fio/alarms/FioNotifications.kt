package com.example.fio.alarms

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * App-wide reminders on the phone's notification tab: calendar events
 * (notified at their start time) and habit reminders (daily at a chosen
 * time). Just AlarmManager + a notification — offline, light, same pattern
 * as the timetable alarms.
 *
 * A tiny prefs mirror ("fio_reminder_mirror") lets the boot receiver re-arm
 * everything after a restart without needing the repository.
 */
object FioReminderAlarms {
    const val CHANNEL_ID = "fio_reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Fio reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Calendar events, habits and other reminders from Fio"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    // --- Events: notify at their start time ---

    fun scheduleEvent(context: Context, event: com.example.fio.data.model.ScheduleEvent) {
        cancelEvent(context, event.id)
        val at = event.startTimeMillis
        if (at <= System.currentTimeMillis()) return
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            Intent(context, FioReminderReceiver::class.java).apply {
                putExtra("kind", "EVENT")
                putExtra("id", event.id)
                putExtra("title", event.title.ifBlank { "Event" })
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60 * 1000L, pi)
        }
    }

    fun cancelEvent(context: Context, eventId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            Intent(context, FioReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }

    // --- Habits: daily reminder at minutes since midnight ---

    fun scheduleHabit(
        context: Context,
        habitId: String,
        name: String,
        question: String,
        reminderMinutes: Int,
        timesPerWeek: Int = 7
    ) {
        cancelHabit(context, habitId)
        ensureChannel(context)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderMinutes / 60)
            set(Calendar.MINUTE, reminderMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 30_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            (habitId.hashCode() + 7777),
            Intent(context, FioReminderReceiver::class.java).apply {
                putExtra("kind", "HABIT")
                putExtra("id", habitId)
                putExtra("name", name)
                putExtra("question", question.ifBlank { "Time for: $name" })
                putExtra("reminderMinutes", reminderMinutes)
                putExtra("timesPerWeek", timesPerWeek)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 5 * 60 * 1000L, pi)
        }
    }

    fun cancelHabit(context: Context, habitId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            (habitId.hashCode() + 7777),
            Intent(context, FioReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }

    // --- Boot: re-arm every habit reminder + future event ---

    fun rescheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("fio_reminder_mirror", Context.MODE_PRIVATE)
        runCatching {
            val habits = JSONArray(prefs.getString("habits_json", "[]") ?: "[]")
            for (i in 0 until habits.length()) {
                val h = habits.getJSONObject(i)
                scheduleHabit(
                    context,
                    h.optString("id"),
                    h.optString("name", "Habit"),
                    h.optString("question", ""),
                    h.optInt("reminderMinutes", -1),
                    h.optInt("timesPerWeek", 7)
                )
            }
        }
        runCatching {
            val events = JSONArray(prefs.getString("events_json", "[]") ?: "[]")
            for (i in 0 until events.length()) {
                val e = events.getJSONObject(i)
                val start = e.optLong("start", 0L)
                if (start > System.currentTimeMillis()) {
                    val fake = com.example.fio.data.model.ScheduleEvent(
                        id = e.optString("id"),
                        title = e.optString("title", "Event"),
                        startTimeMillis = start,
                        endTimeMillis = e.optLong("end", start)
                    )
                    scheduleEvent(context, fake)
                }
            }
        }
    }
}

/** Fires a reminder notification; habit reminders re-arm for their next due day. */
class FioReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val id = intent.getStringExtra("id") ?: return

        FioReminderAlarms.ensureChannel(context)

        val title: String
        val body: String
        if (kind == "EVENT") {
            title = intent.getStringExtra("title") ?: "Event"
            body = "Your event is starting now."
        } else {
            title = intent.getStringExtra("name") ?: "Habit"
            body = intent.getStringExtra("question") ?: "Time for your habit."
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, FioReminderAlarms.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fio_eye)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notifications denied — the schedule still exists, silently.
        }

        // Habits repeat daily until changed.
        if (kind == "HABIT") {
            runCatching {
                FioReminderAlarms.scheduleHabit(
                    context,
                    id,
                    intent.getStringExtra("name") ?: "Habit",
                    intent.getStringExtra("question") ?: "",
                    intent.getIntExtra("reminderMinutes", 540),
                    intent.getIntExtra("timesPerWeek", 7)
                )
            }
        }
    }
}
