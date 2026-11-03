package com.example.fio.alarms

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import org.json.JSONArray
import java.util.Calendar

/**
 * Phone-alarms for the customizable Timetable. Each enabled period rings its
 * start time on the chosen weekday (weekly repeat). No third-party scheduler —
 * just AlarmManager + a notification, so it stays offline and light.
 */
object TimetableAlarms {
    const val CHANNEL_ID = "timetable_period_alarms"
    private const val PREFS = "fio_timetable"
    private const val KEY = "periods_json"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timetable alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows a reminder when a timetable period starts"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** Mon-first day (1..7) → Calendar.DAY_OF_WEEK constants. */
    private fun javaDay(dayMonFirst: Int): Int = when (dayMonFirst) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        else -> Calendar.SUNDAY
    }

    /** Next time (today-or-later) this weekday+startTime occurs. */
    fun nextOccurrenceMillis(context: Context, dayMonFirst: Int, startMinutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, startMinutes / 60)
        cal.set(Calendar.MINUTE, startMinutes % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val targetDow = javaDay(dayMonFirst)
        val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayMinutes = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 +
            Calendar.getInstance().get(Calendar.MINUTE)
        var daysAhead = (targetDow - todayDow + 7) % 7
        if (daysAhead == 0 && startMinutes <= todayMinutes) daysAhead = 7
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        return cal.timeInMillis
    }

    fun schedule(
        context: Context,
        periodId: String,
        title: String,
        room: String,
        dayMonFirst: Int,
        startMinutes: Int,
        endMinutes: Int
    ) {
        ensureChannel(context)
        val at = nextOccurrenceMillis(context, dayMonFirst, startMinutes)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            periodId.hashCode(),
            receiverIntent(context, periodId, title, room, dayMonFirst, startMinutes, endMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Exact where the system allows; a 10-minute window elsewhere — the
        // notification still arrives, just slightly earlier on locked devices.
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60 * 1000L, pi)
        }
    }

    fun cancel(context: Context, periodId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            periodId.hashCode(),
            Intent(context, TimetableAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }

    private fun receiverIntent(
        context: Context,
        periodId: String,
        title: String,
        room: String,
        dayMonFirst: Int,
        startMinutes: Int,
        endMinutes: Int
    ) = Intent(context, TimetableAlarmReceiver::class.java).apply {
        putExtra("id", periodId)
        putExtra("title", title)
        putExtra("room", room)
        putExtra("dayOfWeek", dayMonFirst)
        putExtra("startMinutes", startMinutes)
        putExtra("endMinutes", endMinutes)
    }

    /** Re-schedules every enabled period (used on app start + after boot). */
    fun rescheduleAll(context: Context) {
        for (p in storedPeriods(context)) {
            if (p.optBoolean("alarmEnabled", false)) {
                schedule(
                    context,
                    p.optString("id"),
                    p.optString("title", "Period"),
                    p.optString("room", ""),
                    p.optInt("dayOfWeek", 1),
                    p.optInt("startMinutes", 540),
                    p.optInt("endMinutes", 585)
                )
            }
        }
    }

    fun storePeriods(context: Context, json: String) {
        prefs(context).edit().putString(KEY, json).apply()
    }

    private fun storedPeriods(context: Context): List<org.json.JSONObject> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Rings when a period starts; then re-arms the next weekly occurrence. */
class TimetableAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: "Period"
        val room = intent.getStringExtra("room") ?: ""
        val day = intent.getIntExtra("dayOfWeek", 1)
        val start = intent.getIntExtra("startMinutes", 540)
        val end = intent.getIntExtra("endMinutes", 585)

        TimetableAlarms.ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, TimetableAlarms.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fio_eye)
            .setContentTitle(title)
            .setContentText(buildString {
                append("%02d:%02d".format(start / 60, start % 60))
                append("–")
                append("%02d:%02d".format(end / 60, end % 60))
                if (room.isNotBlank()) append(" · ").append(room)
            })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied — alarm timing still fires, silently.
        }

        // Repeat the period weekly until the user turns the alarm off.
        runCatching {
            TimetableAlarms.schedule(context, id, title, room, day, start, end)
        }
    }
}

/** Re-arms all enabled periods after a reboot (alarms don't survive restarts). */
class TimetableBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TimetableAlarms.rescheduleAll(context)
        }
    }
}
