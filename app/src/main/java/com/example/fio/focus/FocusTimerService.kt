package com.example.fio.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A quiet foreground service so the focus session keeps running — and stays
 * visible — while the user minimises Fio. It ticks every second, publishes a
 * low-priority notification with the remaining time and holds a partial wake
 * lock so old devices don't freeze the countdown with the screen off.
 */
class FocusTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFY_ID, buildNotification(), foregroundType())
        acquireWakeLock()
        if (loopActive) return START_STICKY
        loopActive = true
        scope.launch {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (scope.isActive && loopActive) {
                FocusTimer.tick()
                if (!FocusTimer.state.running()) {
                    // Update the notification once more to the finished state,
                    // then drop to the background service-safe state and stop.
                    mgr.notify(NOTIFY_ID, buildNotification())
                    loopActive = false
                    break
                }
                val text = remainingText()
                if (text != lastNotificationText) {
                    lastNotificationText = text
                    runCatching { mgr.notify(NOTIFY_ID, buildNotification()) }
                }
                delay(1000L)
            }
            stopSelf()
        }
        return START_STICKY
    }

    private var loopActive = false

    override fun onDestroy() {
        loopActive = false
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun remainingText(): String {
        val s = FocusTimer.state
        val now = System.currentTimeMillis()
        val secondsLeft = when (s.phase) {
            FocusTimer.Phase.FOCUS -> ((s.focusEndsAt - now) / 1000L).coerceAtLeast(0)
            FocusTimer.Phase.BREAK -> ((s.breakEndsAt - now) / 1000L).coerceAtLeast(0)
            FocusTimer.Phase.EMERGENCY -> ((s.emergencyEndsAt - now) / 1000L).coerceAtLeast(0)
            else -> 0L
        }.toInt()
        val label = when (s.phase) {
            FocusTimer.Phase.BREAK -> "Break time"
            FocusTimer.Phase.EMERGENCY -> "Paused"
            else -> "Focus session"
        }
        val lockdownTag = if (s.lockdown) " · lockdown" else ""
        return label + lockdownTag + " — " +
            String.format(Locale.US, "%d:%02d left", secondsLeft / 60, secondsLeft % 60)
    }

    private fun buildNotification(): Notification {
        val s = FocusTimer.state
        val totalSeconds = s.plannedMinutes * 60
        val now = System.currentTimeMillis()
        val secondsLeft = (((s.focusEndsAt - now) / 1000L).coerceAtLeast(0)).toInt()
        val elapsed = (totalSeconds - secondsLeft).coerceIn(0, totalSeconds)

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 99, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fio_eye)
            .setContentTitle(if (s.phase == FocusTimer.Phase.BREAK) "Break — free to leave" else "Focus session running")
            .setContentText(remainingText())
            .setProgress(totalSeconds, elapsed, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPi)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fio:focus_timer")
        wakeLock?.acquire(6 * 60 * 60 * 1000L) // long safety cap
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Focus timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the focus timer running in the background" }
        )
    }

    companion object {
        const val CHANNEL_ID = "fio_focus_timer"
        const val NOTIFY_ID = 4242

        fun start(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FocusTimerService::class.java)) }
        }
    }
}
