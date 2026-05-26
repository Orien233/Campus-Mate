package com.example.campusmate.ui.focus

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.campusmate.R
import com.example.campusmate.data.model.FocusSession
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.repository.FocusRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.domain.focus.FaceDownDetector
import com.example.campusmate.domain.focus.FocusEvent
import com.example.campusmate.domain.focus.FocusState
import com.example.campusmate.domain.focus.FocusStateMachine
import com.example.campusmate.domain.focus.FocusTimerEngine
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.NotificationUtils

/** Foreground service that owns focus timer truth, sensor pause/resume, and record writes. */
class FocusService : Service(), FaceDownDetector.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var focusRepository: FocusRepository
    private lateinit var studyRecordRepository: StudyRecordRepository
    private var stateMachine = FocusStateMachine(FocusState.IDLE)
    private var timerEngine: FocusTimerEngine? = null
    private var faceDownDetector: FaceDownDetector? = null
    private var currentSession: FocusSession? = null
    private var focusTitle: String = ""
    private var pauseCount: Int = 0
    private var interruptCount: Int = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            val timer = timerEngine ?: return
            if (stateMachine.state == FocusState.RUNNING && timer.isComplete()) {
                finishSession()
                return
            }
            updateForegroundNotification()
            broadcastState()
            handler.postDelayed(this, TICK_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        focusRepository = FocusRepository(this)
        studyRecordRepository = StudyRecordRepository(this)
        NotificationUtils.ensureFocusServiceChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE_MANUAL -> pauseManual()
            ACTION_RESUME_MANUAL -> resumeManual()
            ACTION_FINISH -> finishSession()
            ACTION_CANCEL -> cancelSession()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        faceDownDetector?.stop()
        super.onDestroy()
    }

    override fun onFaceDownStable() {
        if (stateMachine.state == FocusState.PAUSED_BY_PICKUP) {
            stateMachine.dispatch(FocusEvent.FACE_DOWN_STABLE)
            timerEngine?.resume()
            persistSessionStatus(FocusSession.STATUS_RUNNING)
            broadcastState()
        }
    }

    override fun onPickedUp() {
        if (stateMachine.state == FocusState.RUNNING) {
            stateMachine.dispatch(FocusEvent.PICKED_UP)
            timerEngine?.pause()
            pauseCount++
            interruptCount++
            persistSessionStatus(FocusSession.STATUS_PAUSED)
            broadcastState()
        }
    }

    private fun handleStart(intent: Intent) {
        if (stateMachine.state == FocusState.RUNNING || stateMachine.state == FocusState.PAUSED_BY_PICKUP || stateMachine.state == FocusState.PAUSED_MANUAL) {
            broadcastState()
            return
        }

        val plannedDurationSec = intent.getIntExtra(EXTRA_DURATION_SEC, DEFAULT_DURATION_SEC).coerceAtLeast(1)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L).takeIf { it > 0L }
        focusTitle = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: getString(R.string.focus_default_title)
        pauseCount = 0
        interruptCount = 0
        timerEngine = FocusTimerEngine(plannedDurationSec).apply { start() }
        stateMachine = FocusStateMachine(FocusState.READY_WAITING_FACE_DOWN).apply {
            dispatch(FocusEvent.FACE_DOWN_STABLE)
        }

        val sessionId = focusRepository.addFocusSession(
            FocusSession(
                taskId = taskId,
                courseId = courseId,
                plannedDurationSec = plannedDurationSec,
                actualDurationSec = 0,
                startAt = System.currentTimeMillis(),
                status = FocusSession.STATUS_RUNNING
            )
        )
        currentSession = focusRepository.getFocusSessionById(sessionId)
        faceDownDetector = FaceDownDetector(this, this).also { detector ->
            if (!detector.start()) {
                broadcastSensorUnavailable()
            }
        }

        startForeground(FOCUS_NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        broadcastState()
    }

    private fun pauseManual() {
        if (stateMachine.state != FocusState.RUNNING && stateMachine.state != FocusState.PAUSED_BY_PICKUP) return
        stateMachine.dispatch(FocusEvent.PAUSE_MANUAL)
        timerEngine?.pause()
        pauseCount++
        persistSessionStatus(FocusSession.STATUS_PAUSED)
        broadcastState()
    }

    private fun resumeManual() {
        if (stateMachine.state != FocusState.PAUSED_MANUAL) return
        stateMachine.dispatch(FocusEvent.RESUME_MANUAL)
        timerEngine?.resume()
        persistSessionStatus(FocusSession.STATUS_RUNNING)
        broadcastState()
    }

    private fun finishSession() {
        val timer = timerEngine ?: return
        if (stateMachine.state == FocusState.FINISHED || stateMachine.state == FocusState.CANCELLED) return
        stateMachine.dispatch(FocusEvent.FINISH)
        timer.finish()
        val actualDurationSec = timer.elapsedSec().coerceAtLeast(1)
        val endAt = System.currentTimeMillis()
        currentSession = currentSession?.copy(
            actualDurationSec = actualDurationSec,
            endAt = endAt,
            status = FocusSession.STATUS_FINISHED,
            pauseCount = pauseCount,
            interruptCount = interruptCount
        )?.also { focusRepository.updateFocusSession(it) }

        currentSession?.let { session ->
            studyRecordRepository.addStudyRecord(
                StudyRecord(
                    taskId = session.taskId,
                    courseId = session.courseId,
                    focusSessionId = session.id,
                    title = focusTitle,
                    durationSec = actualDurationSec,
                    recordDate = DateTimeUtils.formatDate(endAt),
                    startAt = session.startAt,
                    endAt = endAt,
                    source = StudyRecord.SOURCE_FOCUS_AUTO,
                    note = getString(R.string.focus_record_note)
                )
            )
        }
        broadcastState()
        stopSelfSafely()
    }

    private fun cancelSession() {
        if (stateMachine.state == FocusState.CANCELLED || stateMachine.state == FocusState.FINISHED) return
        stateMachine.dispatch(FocusEvent.CANCEL)
        timerEngine?.finish()
        currentSession = currentSession?.copy(
            actualDurationSec = timerEngine?.elapsedSec() ?: 0,
            endAt = System.currentTimeMillis(),
            status = FocusSession.STATUS_CANCELLED,
            pauseCount = pauseCount,
            interruptCount = interruptCount
        )?.also { focusRepository.updateFocusSession(it) }
        broadcastState()
        stopSelfSafely()
    }

    private fun persistSessionStatus(status: Int) {
        currentSession = currentSession?.copy(
            actualDurationSec = timerEngine?.elapsedSec() ?: 0,
            status = status,
            pauseCount = pauseCount,
            interruptCount = interruptCount
        )?.also { focusRepository.updateFocusSession(it) }
        updateForegroundNotification()
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(FOCUS_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, FocusActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stateText = focusStateText(stateMachine.state)
        val timer = timerEngine
        val remainingText = timer?.remainingSec()?.let { formatDuration(it) } ?: "--:--"
        val builder = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_FOCUS_SERVICE)
            .setSmallIcon(R.drawable.ic_focus)
            .setContentTitle(getString(R.string.focus_notification_title))
            .setContentText(getString(R.string.focus_notification_text, focusTitle, remainingText, stateText))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.focus_notification_text, focusTitle, remainingText, stateText)))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val manualPauseAction = if (stateMachine.state == FocusState.PAUSED_MANUAL) {
            buildServicePendingIntent(ACTION_RESUME_MANUAL, 2) to getString(R.string.focus_resume)
        } else {
            buildServicePendingIntent(ACTION_PAUSE_MANUAL, 1) to getString(R.string.focus_pause)
        }
        builder.addAction(R.drawable.ic_focus, manualPauseAction.second, manualPauseAction.first)
        builder.addAction(R.drawable.ic_delete, getString(R.string.focus_cancel), buildServicePendingIntent(ACTION_CANCEL, 3))
        return builder.build()
    }

    private fun buildServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, FocusService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastState() {
        val timer = timerEngine
        sendBroadcast(
            Intent(ACTION_FOCUS_STATE_CHANGED).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATE, stateMachine.state.name)
                putExtra(EXTRA_TITLE, focusTitle)
                putExtra(EXTRA_REMAINING_SEC, timer?.remainingSec() ?: 0)
                putExtra(EXTRA_ELAPSED_SEC, timer?.elapsedSec() ?: 0)
                putExtra(EXTRA_PAUSE_COUNT, pauseCount)
                putExtra(EXTRA_INTERRUPT_COUNT, interruptCount)
                putExtra(EXTRA_SESSION_ID, currentSession?.id ?: 0L)
            }
        )
    }

    private fun broadcastSensorUnavailable() {
        sendBroadcast(
            Intent(ACTION_FOCUS_SENSOR_UNAVAILABLE).apply {
                setPackage(packageName)
            }
        )
    }

    private fun stopSelfSafely() {
        handler.removeCallbacks(tickRunnable)
        faceDownDetector?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun focusStateText(state: FocusState): String {
        return when (state) {
            FocusState.RUNNING -> getString(R.string.focus_state_running)
            FocusState.PAUSED_BY_PICKUP -> getString(R.string.focus_state_paused_pickup)
            FocusState.PAUSED_MANUAL -> getString(R.string.focus_state_paused_manual)
            FocusState.FINISHED -> getString(R.string.focus_state_finished)
            FocusState.CANCELLED -> getString(R.string.focus_state_cancelled)
            FocusState.READY_WAITING_FACE_DOWN -> getString(R.string.focus_state_ready)
            FocusState.IDLE -> getString(R.string.focus_state_idle)
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val ACTION_START = "com.example.campusmate.focus.START"
        const val ACTION_PAUSE_MANUAL = "com.example.campusmate.focus.PAUSE_MANUAL"
        const val ACTION_RESUME_MANUAL = "com.example.campusmate.focus.RESUME_MANUAL"
        const val ACTION_FINISH = "com.example.campusmate.focus.FINISH"
        const val ACTION_CANCEL = "com.example.campusmate.focus.CANCEL"
        const val ACTION_FOCUS_STATE_CHANGED = "com.example.campusmate.focus.STATE_CHANGED"
        const val ACTION_FOCUS_SENSOR_UNAVAILABLE = "com.example.campusmate.focus.SENSOR_UNAVAILABLE"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_COURSE_ID = "extra_course_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DURATION_SEC = "extra_duration_sec"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_REMAINING_SEC = "extra_remaining_sec"
        const val EXTRA_ELAPSED_SEC = "extra_elapsed_sec"
        const val EXTRA_PAUSE_COUNT = "extra_pause_count"
        const val EXTRA_INTERRUPT_COUNT = "extra_interrupt_count"
        const val EXTRA_SESSION_ID = "extra_session_id"

        private const val FOCUS_NOTIFICATION_ID = 20_001
        private const val TICK_INTERVAL_MILLIS = 1_000L
        private const val DEFAULT_DURATION_SEC = 25 * 60
    }
}
