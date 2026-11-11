package com.unofficialpixstudio.extras

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * The stopwatch's and countdown's state, kept here so they carry on while other tabs are
 * open. Times are on [SystemClock.elapsedRealtime], which keeps counting while the phone
 * sleeps. The backpack counts by itself from the frames it was sent ([running steps][stopwatchSteps]);
 * this is the phone's own record of the same thing.
 */
class TimersViewModel : ViewModel() {

    // ---- Stopwatch ----

    /** Time counted by earlier runs, not including the one going now. */
    var stopwatchBankedMs by mutableLongStateOf(0L)
        private set

    /** When the current run started, or null while stopped. */
    var stopwatchRunningSince by mutableStateOf<Long?>(null)
        private set

    /** What the backpack was sent for the current run. */
    var stopwatchSteps: List<TimeStep> = emptyList()
        private set

    fun stopwatchElapsedMs(now: Long = SystemClock.elapsedRealtime()): Long =
        stopwatchBankedMs + (stopwatchRunningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** Call when the backpack has started playing [steps]. */
    fun stopwatchStarted(steps: List<TimeStep>) {
        stopwatchSteps = steps
        stopwatchRunningSince = SystemClock.elapsedRealtime()
    }

    fun stopStopwatch() {
        stopwatchBankedMs = stopwatchElapsedMs()
        stopwatchRunningSince = null
    }

    fun resetStopwatch() {
        stopwatchBankedMs = 0
        stopwatchRunningSince = null
    }

    // ---- Countdown ----

    /** What the countdown is set to, in seconds. */
    var countdownSeconds by mutableIntStateOf(5 * 60)
        private set

    /** When the countdown started, or null while it isn't going. */
    var countdownStartedAt by mutableStateOf<Long?>(null)
        private set

    /** What the backpack was sent for the countdown that's going. */
    var countdownSteps: List<TimeStep> = emptyList()
        private set

    fun setCountdown(seconds: Int) {
        countdownSeconds = seconds.coerceIn(1, TimeSchedules.MAX_SECONDS)
    }

    /** Milliseconds left of a countdown that's going (0 once it has finished), or null. */
    fun countdownLeftMs(now: Long = SystemClock.elapsedRealtime()): Long? =
        countdownStartedAt?.let { (countdownSeconds * 1000L - (now - it)).coerceIn(0, countdownSeconds * 1000L) }

    /** Call when the backpack has started playing [steps]. */
    fun countdownStarted(steps: List<TimeStep>) {
        countdownSteps = steps
        countdownStartedAt = SystemClock.elapsedRealtime()
    }

    fun clearCountdown() {
        countdownStartedAt = null
    }

    // ---- Clock ----

    /** Whether the clock shows 1–12 rather than 0–23. */
    var twelveHour by mutableStateOf(false)
}
