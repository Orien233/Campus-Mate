package com.example.campusmate.ui.task

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.TaskAttachmentRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler
import com.example.campusmate.domain.task.TaskDraft
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.PermissionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/** Screen for creating and editing tasks, including optional reminder scheduling. */
class TaskEditActivity : AppCompatActivity() {
    private lateinit var taskRepository: TaskRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var attachmentRepository: TaskAttachmentRepository
    private lateinit var reminderScheduler: AlarmReminderScheduler

    private lateinit var rootView: View
    private lateinit var titleInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var courseSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var prioritySpinner: Spinner
    private lateinit var dueTimeText: TextView
    private lateinit var remindTimeText: TextView
    private lateinit var aiPrefillStatusText: TextView
    private lateinit var attachmentStatusText: TextView

    private var editingTaskId: Long = 0L
    private var editingTask: StudyTask? = null
    private var initialTaskType: Int = StudyTask.TYPE_HOMEWORK
    private var courses: List<Course> = emptyList()
    private var selectedDueAt: Long? = null
    private var selectedRemindAt: Long? = null
    private val pendingAttachments = mutableListOf<PendingAttachment>()

    private val taskParseLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        @Suppress("DEPRECATION")
        val draft = data.getSerializableExtra(TaskWebViewParseActivity.EXTRA_TASK_DRAFT) as? TaskDraft
            ?: return@registerForActivityResult
        val warningSummary = data.getStringExtra(TaskWebViewParseActivity.EXTRA_WARNING_SUMMARY).orEmpty()
        applyTaskDraft(draft, warningSummary)
    }

    private val pickAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            addPendingAttachment(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_edit)

        taskRepository = TaskRepository(this)
        courseRepository = CourseRepository(this)
        settingsRepository = SettingsRepository(this)
        attachmentRepository = TaskAttachmentRepository(this)
        reminderScheduler = AlarmReminderScheduler(this)
        editingTaskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        initialTaskType = intent.getIntExtra(EXTRA_TASK_TYPE, StudyTask.TYPE_HOMEWORK)

        bindViews()
        setupToolbar()
        setupSpinners()
        setupDateButtons()
        setupAiParseAction()
        setupAttachmentAction()

        if (editingTaskId > 0L) {
            editingTask = taskRepository.getTaskById(editingTaskId)
            val task = editingTask
            if (task == null) {
                showMessage(getString(R.string.task_not_found))
                finish()
                return
            }
            bindTask(task)
        }

        findViewById<MaterialButton>(R.id.saveTaskButton).setOnClickListener {
            saveTask()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        rootView = findViewById(R.id.taskEditRoot)
        titleInput = findViewById(R.id.taskTitleInput)
        descriptionInput = findViewById(R.id.taskDescriptionInput)
        courseSpinner = findViewById(R.id.taskCourseSpinner)
        typeSpinner = findViewById(R.id.taskTypeSpinner)
        prioritySpinner = findViewById(R.id.taskPrioritySpinner)
        dueTimeText = findViewById(R.id.taskDueTimeText)
        remindTimeText = findViewById(R.id.taskRemindTimeText)
        aiPrefillStatusText = findViewById(R.id.taskAiPrefillStatusText)
        attachmentStatusText = findViewById(R.id.taskEditAttachmentStatusText)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.taskEditToolbar)
        toolbar.title = getString(if (editingTaskId > 0L) R.string.task_edit_title else R.string.task_add_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupSpinners() {
        courses = courseRepository.getAllCourses()
        val courseLabels = listOf(getString(R.string.task_no_course)) + courses.map { it.name }
        courseSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseLabels)
        typeSpinner.adapter = ArrayAdapter.createFromResource(this, R.array.task_type_labels, android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = ArrayAdapter.createFromResource(this, R.array.task_priority_labels, android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.setSelection(initialTaskType.coerceIn(0, 5))
        prioritySpinner.setSelection(StudyTask.PRIORITY_NORMAL)
    }

    private fun setupDateButtons() {
        findViewById<MaterialButton>(R.id.pickDueTimeButton).setOnClickListener {
            pickDateTime(selectedDueAt) {
                selectedDueAt = it
                updateDateLabels()
            }
        }
        findViewById<MaterialButton>(R.id.clearDueTimeButton).setOnClickListener {
            selectedDueAt = null
            updateDateLabels()
        }
        findViewById<MaterialButton>(R.id.pickRemindTimeButton).setOnClickListener {
            pickDateTime(selectedRemindAt) {
                selectedRemindAt = it
                updateDateLabels()
            }
        }
        findViewById<MaterialButton>(R.id.demoReminderButton).setOnClickListener {
            selectedRemindAt = DateTimeUtils.nowMillis() + DEMO_REMINDER_DELAY_MILLIS
            updateDateLabels()
        }
        findViewById<MaterialButton>(R.id.clearRemindTimeButton).setOnClickListener {
            selectedRemindAt = null
            updateDateLabels()
        }
        updateDateLabels()
    }

    private fun setupAiParseAction() {
        findViewById<MaterialButton>(R.id.parseTaskFromWebButton).setOnClickListener {
            taskParseLauncher.launch(Intent(this, TaskWebViewParseActivity::class.java))
        }
    }

    private fun setupAttachmentAction() {
        findViewById<MaterialButton>(R.id.pickTaskAttachmentButton).setOnClickListener {
            pickAttachmentLauncher.launch(arrayOf("image/*"))
        }
        updateAttachmentStatus()
    }

    private fun bindTask(task: StudyTask) {
        titleInput.setText(task.title)
        descriptionInput.setText(task.description.orEmpty())
        val courseIndex = task.courseId?.let { id -> courses.indexOfFirst { it.id == id } } ?: -1
        courseSpinner.setSelection(if (courseIndex >= 0) courseIndex + 1 else 0)
        typeSpinner.setSelection(task.type.coerceIn(0, 5))
        prioritySpinner.setSelection(task.priority.coerceIn(0, 2))
        selectedDueAt = task.dueAt
        selectedRemindAt = task.remindAt
        updateDateLabels()
    }

    private fun applyTaskDraft(draft: TaskDraft, warningSummary: String) {
        titleInput.setText(draft.title)
        descriptionInput.setText(draft.description.orEmpty())
        selectCourseByDraftName(draft.courseName)
        typeSpinner.setSelection(draft.type.coerceIn(0, 5))
        prioritySpinner.setSelection(draft.priority.coerceIn(0, 2))
        selectedDueAt = draft.dueAt
        selectedRemindAt = draft.remindAt
        updateDateLabels()
        showAiPrefillStatus(draft)

        val message = warningSummary.takeIf { it.isNotBlank() }
            ?: getString(R.string.task_ai_parse_prefilled)
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showAiPrefillStatus(draft: TaskDraft) {
        val fields = buildList {
            add(getString(R.string.task_ai_prefill_field_title))
            if (!draft.description.isNullOrBlank()) add(getString(R.string.task_ai_prefill_field_description))
            if (!draft.courseName.isNullOrBlank()) add(getString(R.string.task_ai_prefill_field_course))
            add(getString(R.string.task_ai_prefill_field_type))
            add(getString(R.string.task_ai_prefill_field_priority))
            if (draft.dueAt != null) add(getString(R.string.task_ai_prefill_field_due))
            if (draft.remindAt != null) add(getString(R.string.task_ai_prefill_field_reminder))
        }
        aiPrefillStatusText.text = getString(
            R.string.task_ai_prefill_status_format,
            fields.joinToString("、")
        )
        aiPrefillStatusText.visibility = View.VISIBLE
    }

    private fun selectCourseByDraftName(courseName: String?) {
        val normalizedCourseName = courseName?.trim().orEmpty()
        if (normalizedCourseName.isBlank()) {
            courseSpinner.setSelection(0)
            return
        }
        val index = courses.indexOfFirst { course ->
            val normalizedExisting = course.name.trim()
            normalizedExisting.equals(normalizedCourseName, ignoreCase = true) ||
                normalizedCourseName.contains(normalizedExisting, ignoreCase = true) ||
                normalizedExisting.contains(normalizedCourseName, ignoreCase = true)
        }
        courseSpinner.setSelection(if (index >= 0) index + 1 else 0)
    }

    private fun saveTask() {
        val task = collectTaskOrShowError() ?: return
        if (
            task.remindAt != null &&
            settingsRepository.isReminderEnabled() &&
            !PermissionUtils.hasPostNotificationsPermission(this)
        ) {
            PermissionUtils.requestPostNotificationsPermission(this)
            Snackbar.make(rootView, R.string.notification_permission_required_before_save, Snackbar.LENGTH_LONG).show()
            return
        }

        val savedTaskId = if (task.id > 0L) {
            if (taskRepository.updateTask(task)) task.id else -1L
        } else {
            taskRepository.addTask(task)
        }
        if (savedTaskId <= 0L) {
            showMessage(getString(R.string.task_save_failed))
            return
        }

        val savedTask = task.copy(id = savedTaskId)
        val failedAttachments = savePendingAttachments(savedTaskId)
        handleReminderAfterSave(savedTask)
        if (failedAttachments > 0) {
            Toast.makeText(this, R.string.task_attachment_add_failed, Toast.LENGTH_LONG).show()
        }
        setResult(RESULT_OK)
        finish()
    }

    private fun addPendingAttachment(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers do not support persistable permissions; best effort only.
        }
        pendingAttachments += PendingAttachment(
            uri = uri,
            mimeType = contentResolver.getType(uri),
            title = TaskAttachmentUiUtils.queryDisplayName(this, uri)
        )
        updateAttachmentStatus()
    }

    private fun updateAttachmentStatus() {
        attachmentStatusText.text = if (pendingAttachments.isEmpty()) {
            getString(R.string.task_attachment_pending_empty)
        } else {
            getString(
                R.string.task_attachment_pending_format,
                pendingAttachments.size,
                pendingAttachments.mapIndexed { index, attachment ->
                    attachment.title ?: getString(R.string.task_attachment_pending_default, index + 1)
                }.joinToString("、")
            )
        }
    }

    private fun savePendingAttachments(taskId: Long): Int {
        if (pendingAttachments.isEmpty()) return 0
        var failed = 0
        pendingAttachments.forEach { attachment ->
            val id = attachmentRepository.addAttachment(
                taskId = taskId,
                uri = attachment.uri.toString(),
                mimeType = attachment.mimeType,
                title = attachment.title
            )
            if (id <= 0L) failed += 1
        }
        return failed
    }

    private fun collectTaskOrShowError(): StudyTask? {
        val title = titleInput.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            titleInput.error = getString(R.string.task_title_required)
            return null
        }
        val selectedCourseId = if (courseSpinner.selectedItemPosition > 0) {
            courses[courseSpinner.selectedItemPosition - 1].id
        } else {
            null
        }
        return StudyTask(
            id = editingTaskId,
            courseId = selectedCourseId,
            title = title,
            description = descriptionInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            type = typeSpinner.selectedItemPosition,
            priority = prioritySpinner.selectedItemPosition,
            dueAt = selectedDueAt,
            remindAt = selectedRemindAt,
            status = editingTask?.status ?: StudyTask.STATUS_TODO,
            createdAt = editingTask?.createdAt ?: 0L
        )
    }

    private fun handleReminderAfterSave(task: StudyTask) {
        reminderScheduler.cancelTaskReminder(task.id)
        if (task.remindAt == null || task.status != StudyTask.STATUS_TODO) return
        if (!settingsRepository.isReminderEnabled()) {
            Toast.makeText(this, R.string.reminder_disabled_message, Toast.LENGTH_SHORT).show()
            return
        }
        val result = reminderScheduler.scheduleTaskReminder(task)
        result.message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        if (result.scheduled && result.message == null) {
            Toast.makeText(this, R.string.reminder_scheduled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickDateTime(initialTimeMillis: Long?, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = initialTimeMillis ?: DateTimeUtils.nowMillis()
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onPicked(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateLabels() {
        dueTimeText.text = TaskUiFormatter.dueLabel(this, selectedDueAt)
        remindTimeText.text = TaskUiFormatter.remindLabel(this, selectedRemindAt)
    }

    private fun showMessage(message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TYPE = "extra_task_type"
        private const val DEMO_REMINDER_DELAY_MILLIS = 10_000L
    }

    private data class PendingAttachment(
        val uri: Uri,
        val mimeType: String?,
        val title: String?
    )
}
