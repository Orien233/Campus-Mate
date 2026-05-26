package com.example.campusmate.ui.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.focus.FaceDownDetector
import com.example.campusmate.domain.focus.FocusEvent
import com.example.campusmate.domain.focus.FocusState
import com.example.campusmate.domain.focus.FocusStateMachine
import com.example.campusmate.util.PermissionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/** Focus entry screen for task selection, face-down start, and service state display. */
class FocusActivity : AppCompatActivity(), FaceDownDetector.Listener {
    private lateinit var taskRepository: TaskRepository
    private lateinit var stateMachine: FocusStateMachine
    private var detector: FaceDownDetector? = null
    private var tasks: List<StudyTask> = emptyList()
    private var serviceRunning = false

    private lateinit var rootView: View
    private lateinit var taskSpinner: Spinner
    private lateinit var durationSpinner: Spinner
    private lateinit var countdownText: TextView
    private lateinit var stateText: TextView
    private lateinit var pauseCountText: TextView
    private lateinit var hintText: TextView
    private lateinit var prepareButton: MaterialButton
    private lateinit var manualStartButton: MaterialButton
    private lateinit var pauseResumeButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FocusService.ACTION_FOCUS_STATE_CHANGED -> handleServiceState(intent)
                FocusService.ACTION_FOCUS_SENSOR_UNAVAILABLE -> showSensorUnavailable()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focus)
        taskRepository = TaskRepository(this)
        stateMachine = FocusStateMachine()
        bindViews()
        setupToolbar()
        setupSpinners()
        setupActions()
        updateIdleUi()
        maybeRequestNotificationPermission()
        applyImmersivePreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersivePreference()
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter().apply {
                addAction(FocusService.ACTION_FOCUS_STATE_CHANGED)
                addAction(FocusService.ACTION_FOCUS_SENSOR_UNAVAILABLE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        detector?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        detector?.stop()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onFaceDownStable() {
        if (stateMachine.state == FocusState.READY_WAITING_FACE_DOWN) {
            stateMachine.dispatch(FocusEvent.FACE_DOWN_STABLE)
            detector?.stop()
            startFocusService()
        }
    }

    override fun onPickedUp() = Unit

    private fun bindViews() {
        rootView = findViewById(R.id.focusRoot)
        taskSpinner = findViewById(R.id.focusTaskSpinner)
        durationSpinner = findViewById(R.id.focusDurationSpinner)
        countdownText = findViewById(R.id.focusCountdownText)
        stateText = findViewById(R.id.focusStateText)
        pauseCountText = findViewById(R.id.focusPauseCountText)
        hintText = findViewById(R.id.focusHintText)
        prepareButton = findViewById(R.id.focusPrepareButton)
        manualStartButton = findViewById(R.id.focusManualStartButton)
        pauseResumeButton = findViewById(R.id.focusPauseResumeButton)
        finishButton = findViewById(R.id.focusFinishButton)
        cancelButton = findViewById(R.id.focusCancelButton)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.focusToolbar)
        toolbar.title = getString(R.string.focus_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupSpinners() {
        tasks = taskRepository.getAllTasks().filter { it.status == StudyTask.STATUS_TODO }
        val taskLabels = listOf(getString(R.string.focus_free_task)) + tasks.map { it.title }
        taskSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, taskLabels)
        durationSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            DURATION_OPTIONS_MINUTES.map { getString(R.string.focus_duration_minutes, it) }
        )
        durationSpinner.setSelection(1)
        durationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!serviceRunning) {
                    countdownText.text = formatDuration(selectedDurationMinutes() * 60)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupActions() {
        prepareButton.setOnClickListener { prepareForFaceDown() }
        manualStartButton.setOnClickListener { startFocusService() }
        pauseResumeButton.setOnClickListener { toggleManualPause() }
        finishButton.setOnClickListener { sendServiceAction(FocusService.ACTION_FINISH) }
        cancelButton.setOnClickListener { sendServiceAction(FocusService.ACTION_CANCEL) }
    }

    private fun prepareForFaceDown() {
        if (serviceRunning) return
        stateMachine.dispatch(FocusEvent.PREPARE)
        hintText.text = getString(R.string.focus_face_down_hint)
        stateText.text = getString(R.string.focus_state_ready)
        detector?.stop()
        detector = FaceDownDetector(this, this)
        val started = detector?.start() == true
        manualStartButton.visibility = if (started) View.GONE else View.VISIBLE
        if (!started) {
            showSensorUnavailable()
        }
    }

    private fun startFocusService() {
        detector?.stop()
        val task = selectedTask()
        val durationSec = selectedDurationMinutes() * 60
        val title = task?.title ?: getString(R.string.focus_free_task)
        val intent = Intent(this, FocusService::class.java).apply {
            action = FocusService.ACTION_START
            putExtra(FocusService.EXTRA_TASK_ID, task?.id ?: 0L)
            putExtra(FocusService.EXTRA_COURSE_ID, task?.courseId ?: 0L)
            putExtra(FocusService.EXTRA_TITLE, title)
            putExtra(FocusService.EXTRA_DURATION_SEC, durationSec)
        }
        ContextCompat.startForegroundService(this, intent)
        serviceRunning = true
        updateControlsForServiceRunning()
    }

    private fun toggleManualPause() {
        val currentState = stateText.tag as? String
        val action = if (currentState == FocusState.PAUSED_MANUAL.name) {
            FocusService.ACTION_RESUME_MANUAL
        } else {
            FocusService.ACTION_PAUSE_MANUAL
        }
        sendServiceAction(action)
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, FocusService::class.java).setAction(action))
    }

    private fun handleServiceState(intent: Intent) {
        val stateName = intent.getStringExtra(FocusService.EXTRA_STATE) ?: FocusState.IDLE.name
        val remainingSec = intent.getIntExtra(FocusService.EXTRA_REMAINING_SEC, 0)
        val pauseCount = intent.getIntExtra(FocusService.EXTRA_PAUSE_COUNT, 0)
        val title = intent.getStringExtra(FocusService.EXTRA_TITLE).orEmpty()
        serviceRunning = stateName != FocusState.FINISHED.name && stateName != FocusState.CANCELLED.name
        countdownText.text = formatDuration(remainingSec)
        stateText.text = focusStateText(stateName)
        stateText.tag = stateName
        pauseCountText.text = getString(R.string.focus_pause_count_format, pauseCount)
        hintText.text = if (serviceRunning) {
            getString(R.string.focus_running_hint, title)
        } else {
            getString(R.string.focus_finish_hint)
        }
        updateControlsForServiceRunning()
    }

    private fun updateIdleUi() {
        countdownText.text = formatDuration(selectedDurationMinutes() * 60)
        stateText.text = getString(R.string.focus_state_idle)
        stateText.tag = FocusState.IDLE.name
        pauseCountText.text = getString(R.string.focus_pause_count_format, 0)
        hintText.text = getString(R.string.focus_idle_hint)
        manualStartButton.visibility = View.GONE
        pauseResumeButton.visibility = View.GONE
        finishButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
    }

    private fun updateControlsForServiceRunning() {
        prepareButton.isEnabled = !serviceRunning
        taskSpinner.isEnabled = !serviceRunning
        durationSpinner.isEnabled = !serviceRunning
        manualStartButton.visibility = View.GONE
        pauseResumeButton.visibility = if (serviceRunning) View.VISIBLE else View.GONE
        finishButton.visibility = if (serviceRunning) View.VISIBLE else View.GONE
        cancelButton.visibility = if (serviceRunning) View.VISIBLE else View.GONE
        val isManualPaused = stateText.tag == FocusState.PAUSED_MANUAL.name
        pauseResumeButton.setText(if (isManualPaused) R.string.focus_resume else R.string.focus_pause)
    }

    private fun showSensorUnavailable() {
        manualStartButton.visibility = View.VISIBLE
        Snackbar.make(rootView, R.string.focus_sensor_unavailable, Snackbar.LENGTH_LONG).show()
    }

    private fun selectedTask(): StudyTask? {
        val position = taskSpinner.selectedItemPosition
        return if (position > 0) tasks.getOrNull(position - 1) else null
    }

    private fun selectedDurationMinutes(): Int {
        return DURATION_OPTIONS_MINUTES.getOrElse(durationSpinner.selectedItemPosition) { 25 }
    }

    private fun focusStateText(stateName: String): String {
        return when (stateName) {
            FocusState.RUNNING.name -> getString(R.string.focus_state_running)
            FocusState.PAUSED_BY_PICKUP.name -> getString(R.string.focus_state_paused_pickup)
            FocusState.PAUSED_MANUAL.name -> getString(R.string.focus_state_paused_manual)
            FocusState.FINISHED.name -> getString(R.string.focus_state_finished)
            FocusState.CANCELLED.name -> getString(R.string.focus_state_cancelled)
            FocusState.READY_WAITING_FACE_DOWN.name -> getString(R.string.focus_state_ready)
            else -> getString(R.string.focus_state_idle)
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun maybeRequestNotificationPermission() {
        if (!PermissionUtils.hasPostNotificationsPermission(this)) {
            PermissionUtils.requestPostNotificationsPermission(this)
        }
    }

    private fun applyImmersivePreference() {
        if (!SettingsRepository(this).isImmersiveModeEnabled()) return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private val DURATION_OPTIONS_MINUTES = listOf(1, 25, 45, 60)
    }
}
