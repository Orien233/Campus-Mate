package com.example.campusmate.domain.focus

import android.os.SystemClock

/** ElapsedRealtime-based timer engine; Handler ticks are only display triggers. */
class FocusTimerEngine(
    private val plannedDurationSec: Int,
    private val nowProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var runningSinceElapsed: Long = 0L
    private var accumulatedRunningMillis: Long = 0L
    var startedWallTimeMillis: Long = 0L
        private set
    var endedWallTimeMillis: Long = 0L
        private set

    val isRunning: Boolean
        get() = runningSinceElapsed > 0L

    fun start(startWallTimeMillis: Long = System.currentTimeMillis()) {
        if (isRunning) return
        if (startedWallTimeMillis == 0L) {
            startedWallTimeMillis = startWallTimeMillis
        }
        runningSinceElapsed = nowProvider()
    }

    fun pause() {
        if (!isRunning) return
        accumulatedRunningMillis += nowProvider() - runningSinceElapsed
        runningSinceElapsed = 0L
    }

    fun resume() = start()

    fun finish(endWallTimeMillis: Long = System.currentTimeMillis()) {
        pause()
        endedWallTimeMillis = endWallTimeMillis
    }

    fun elapsedSec(): Int = (elapsedMillis() / 1000L).toInt()

    fun remainingSec(): Int = (plannedDurationSec - elapsedSec()).coerceAtLeast(0)

    fun isComplete(): Boolean = elapsedSec() >= plannedDurationSec

    private fun elapsedMillis(): Long {
        return accumulatedRunningMillis + if (isRunning) nowProvider() - runningSinceElapsed else 0L
    }
}
