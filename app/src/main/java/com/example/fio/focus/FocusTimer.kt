package com.example.fio.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.fio.data.model.FocusSessionLog
import com.example.fio.data.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Focus timer that lives outside the screen: a process-wide singleton state
 * plus a foreground service that keeps the countdown running and visible in
 * the notification banner while the app is minimised.
 *
 * The remaining time is always recomputed from the wall clock
 * (`endsAt - now`), so freeing the CPU briefly (Doze) never corrupts the
 * countdown — the next tick just snaps back to the correct value.
 */
object FocusTimer {

    enum class Phase { IDLE, FOCUS, BREAK, EMERGENCY, DONE }

    data class State(
        val phase: Phase = Phase.IDLE,
        val plannedMinutes: Int = 50,
        val lockdown: Boolean = false,
        val alreadyFocusedMinutes: Int = 0,
        val focusEndsAt: Long = 0L,
        val breakEndsAt: Long = 0L,
        val emergencyEndsAt: Long = 0L,
        val startingStrikes: Int = 0,
        val strikesRemaining: Int = 0,
        val strikesUsed: Int = 0,
        val breaksTaken: Int = 0,
        val emergencyUsed: Boolean = false,
        /** Set once when a session finishes or is broken; the screen shows the summary. */
        val finishedStatus: SessionStatus? = null
    ) {
        fun running(): Boolean = phase == Phase.FOCUS || phase == Phase.BREAK || phase == Phase.EMERGENCY
    }

    /** Assigned by MainActivity so finished sessions land in the logs. */
    @Volatile
    var sessionLogger: ((FocusSessionLog) -> Unit)? = null

    /** Assigned from MainActivity so the service can start/stop from controller calls. */
    @Volatile
    var serviceStarter: ((Context) -> Unit)? = null
    @Volatile
    var serviceStopper: ((Context) -> Unit)? = null

    private val stateRef = AtomicReference(State())
    val state: State get() = stateRef.get()

    // Tunables are captured at start so Doze-delayed ticks use stable values.
    private var breakFreqSeconds: Int = 50 * 60
    private var breakDurationSeconds: Int = 10 * 60

    fun start(
        minutes: Int,
        lockdown: Boolean,
        breakFrequencyMinutes: Int,
        breakDurationMinutes: Int,
        context: Context
    ) {
        val now = System.currentTimeMillis()
        breakFreqSeconds = breakFrequencyMinutes.coerceAtLeast(1) * 60
        breakDurationSeconds = breakDurationMinutes.coerceAtLeast(1) * 60
        lastBreakMark.set(-1L)
        val strikes = calculateStrikesForDuration(minutes)
        stateRef.set(
            State(
                phase = Phase.FOCUS,
                plannedMinutes = minutes,
                lockdown = lockdown,
                alreadyFocusedMinutes = 0,
                focusEndsAt = now + minutes * 60_000L,
                startingStrikes = strikes,
                strikesRemaining = strikes
            )
        )
        serviceStarter?.invoke(context)
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        val s = stateRef.get()
        when (s.phase) {
            Phase.FOCUS -> {
                val remaining = s.focusEndsAt - now
                val focusedMinutes = ((s.plannedMinutes * 60_000L - remaining) / 60_000L)
                    .coerceAtLeast(0).toInt()
                if (remaining <= 0) {
                    // Session finished successfully.
                    logSession(
                        FocusSessionLog(
                            title = "Focus Session",
                            plannedMinutes = s.plannedMinutes,
                            actualMinutes = s.plannedMinutes,
                            isLockdownEnabled = s.lockdown,
                            startingStrikes = s.startingStrikes,
                            strikesUsed = s.strikesUsed,
                            breaksTaken = s.breaksTaken,
                            status = SessionStatus.COMPLETED
                        )
                    )
                    stateRef.set(
                        s.copy(
                            phase = Phase.DONE,
                            alreadyFocusedMinutes = s.plannedMinutes,
                            finishedStatus = SessionStatus.COMPLETED
                        )
                    )
                } else {
                    // Time for a scheduled break (every breakFreq of focus time),
                    // only while plenty of session remains. Fired once per mark.
                    val elapsedSeconds = s.plannedMinutes * 60 - remaining / 1000
                    val breakDue = s.plannedMinutes >= breakFreqSeconds / 60 &&
                        elapsedSeconds > 0 &&
                        elapsedSeconds % breakFreqSeconds == 0L &&
                        elapsedSeconds != lastBreakMark.get() &&
                        remaining > 5 * 60_000L
                    if (breakDue) {
                        lastBreakMark.set(elapsedSeconds)
                        stateRef.set(
                            s.copy(
                                phase = Phase.BREAK,
                                breakEndsAt = now + breakDurationSeconds * 1000L,
                                alreadyFocusedMinutes = focusedMinutes
                            )
                        )
                    } else {
                        stateRef.set(s.copy(alreadyFocusedMinutes = focusedMinutes))
                    }
                }
            }
            Phase.BREAK -> {
                val remaining = s.breakEndsAt - now
                if (remaining <= 0) {
                    // Break over: a strike back (never above the starting number).
                    stateRef.set(
                        s.copy(
                            phase = Phase.FOCUS,
                            breaksTaken = s.breaksTaken + 1,
                            strikesRemaining = (s.strikesRemaining + 1).coerceAtMost(s.startingStrikes)
                        )
                    )
                }
            }
            Phase.EMERGENCY -> {
                val remaining = s.emergencyEndsAt - now
                if (remaining <= 0) {
                    // Auto-resume focus after the 5-minute emergency pause.
                    stateRef.set(s.copy(phase = Phase.FOCUS))
                }
            }
            else -> { }
        }
    }

    /** Marks the current break window as "consumed" so it doesn't re-trigger every tick. */
    private val lastBreakMark = AtomicReference<Long>(-1L)

    fun toggleEmergencyPause(context: Context) {
        val s = stateRef.get()
        if (s.phase == Phase.FOCUS && !s.emergencyUsed) {
            val now = System.currentTimeMillis()
            stateRef.set(
                s.copy(
                    phase = Phase.EMERGENCY,
                    emergencyEndsAt = now + 5 * 60_000L,
                    emergencyUsed = true
                )
            )
        } else if (s.phase == Phase.EMERGENCY) {
            stateRef.set(s.copy(phase = Phase.FOCUS))
        }
    }

    /** One strike for leaving the app beyond the grace period (lockdown only). */
    fun useStrike() {
        val s = stateRef.get()
        if (s.strikesRemaining > 1) {
            stateRef.set(s.copy(strikesRemaining = s.strikesRemaining - 1, strikesUsed = s.strikesUsed + 1))
        } else {
            abandonLocked()
        }
    }

    private fun abandonLocked() {
        val s = stateRef.get()
        val focused = s.alreadyFocusedMinutes.coerceAtLeast(0)
        logSession(
            FocusSessionLog(
                title = "Focus Session (Broken)",
                plannedMinutes = s.plannedMinutes,
                actualMinutes = focused,
                isLockdownEnabled = true,
                startingStrikes = s.startingStrikes,
                strikesUsed = s.strikesUsed + 1,
                breaksTaken = s.breaksTaken,
                status = SessionStatus.BROKEN
            )
        )
        stateRef.set(
            s.copy(
                phase = Phase.DONE,
                strikesRemaining = 0,
                strikesUsed = s.strikesUsed + 1,
                alreadyFocusedMinutes = focused,
                finishedStatus = SessionStatus.BROKEN
            )
        )
    }

    /** Stop pressed by the user: log what happened so far and end. */
    fun stop(context: Context? = null) {
        val s = stateRef.get()
        if (!s.running()) {
            if (context != null) serviceStopper?.invoke(context)
            return
        }
        val focused = when (s.phase) {
            Phase.FOCUS -> s.alreadyFocusedMinutes
            else -> s.alreadyFocusedMinutes
        }.coerceAtLeast(0)
        val status = if (s.lockdown) SessionStatus.BROKEN else SessionStatus.COMPLETED
        logSession(
            FocusSessionLog(
                title = if (s.lockdown) "Focus Session (Broken)" else "Focus Session",
                plannedMinutes = s.plannedMinutes,
                actualMinutes = focused,
                isLockdownEnabled = s.lockdown,
                startingStrikes = s.startingStrikes,
                strikesUsed = s.strikesUsed,
                breaksTaken = s.breaksTaken,
                status = status
            )
        )
        stateRef.set(s.copy(phase = Phase.DONE, alreadyFocusedMinutes = focused, finishedStatus = status))
    }

    /** Clear everything back to the setup state. */
    fun reset(context: Context? = null) {
        if (context != null) serviceStopper?.invoke(context)
        stateRef.set(State())
    }

    /** Summary dialog shown once — the screen consumes and clears it. */
    fun consumeFinished() {
        val s = stateRef.get()
        if (s.phase == Phase.DONE && s.finishedStatus != null) {
            stateRef.set(
                State(
                    plannedMinutes = s.plannedMinutes,
                    lockdown = s.lockdown,
                    startingStrikes = s.startingStrikes
                )
            )
        }
    }

    private fun logSession(log: FocusSessionLog) {
        try { sessionLogger?.invoke(log) } catch (_: Exception) { }
    }

    fun calculateStrikesForDuration(minutes: Int): Int = when {
        minutes < 60 -> 1
        minutes < 120 -> 3
        minutes < 180 -> 5
        minutes < 240 -> 7
        else -> 10
    }
}
