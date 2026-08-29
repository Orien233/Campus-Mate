package com.example.campusmate.ui.task

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler
import com.example.campusmate.domain.task.TaskDraft
import com.example.campusmate.util.PermissionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preview and confirmation screen for AI-parsed task drafts. */
class TaskImportPreviewActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var adapter: TaskDraftAdapter
    private lateinit var taskRepository: TaskRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var reminderScheduler: AlarmReminderScheduler
    private var warnings: List<String> = emptyList()
    private var draftCount: Int = 0
    private lateinit var importButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_import_preview)

        rootView = findViewById(R.id.taskImportPreviewRoot)
        taskRepository = TaskRepository(this)
        courseRepository = CourseRepository(this)
        settingsRepository = SettingsRepository(this)
        reminderScheduler = AlarmReminderScheduler(this)
        warnings = intent.getStringArrayListExtra(EXTRA_WARNINGS).orEmpty()

        setupToolbar()
        setupList()
        setupActions()
        loadDrafts()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.taskImportPreviewToolbar)
        toolbar.title = getString(R.string.task_import_preview_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupList() {
        adapter = TaskDraftAdapter {
            refreshSummary()
        }
        findViewById<RecyclerView>(R.id.taskDraftRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@TaskImportPreviewActivity)
            adapter = this@TaskImportPreviewActivity.adapter
        }
    }

    private fun setupActions() {
        importButton = findViewById(R.id.importSelectedTasksButton)
        importButton.setOnClickListener {
            importSelected()
        }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun loadDrafts() {
        val drafts = intent.getSerializableExtra(EXTRA_TASK_DRAFTS) as? ArrayList<TaskDraft> ?: arrayListOf()
        val items = drafts.map { TaskDraftItem(it) }
        draftCount = items.size
        adapter.submitList(items)
        refreshSummary()
        findViewById<TextView>(R.id.taskImportPreviewWarningText).apply {
            visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE
            text = if (warnings.isEmpty()) {
                ""
            } else {
                getString(R.string.task_import_preview_warning_info, warnings.size) +
                    "\n" +
                    warnings.take(3).joinToString("\n")
            }
        }
    }

    private fun refreshSummary() {
        findViewById<TextView>(R.id.taskImportPreviewSummaryText).text = getString(
            R.string.task_import_preview_summary,
            draftCount,
            adapter.selectedItems().size
        )
    }

    private fun importSelected() {
        val selected = adapter.selectedItems()
        if (selected.isEmpty()) {
            Snackbar.make(rootView, R.string.task_import_none_selected, Snackbar.LENGTH_SHORT).show()
            return
        }

        importButton.isEnabled = false
        lifecycleScope.launch {
            val (importedCount, reminderSkippedCount) = withContext(Dispatchers.IO) {
                val courses = courseRepository.getAllCourses()
                var imported = 0
                var reminderSkipped = 0
                selected.forEach { item ->
                    val task = item.draft.toTask(resolveCourseId(item.draft.courseName, courses))
                    val taskId = taskRepository.addTask(task)
                    if (taskId > 0L) {
                        imported += 1
                        val savedTask = task.copy(id = taskId)
                        if (!scheduleReminderIfPossible(savedTask)) {
                            reminderSkipped += if (savedTask.remindAt == null) 0 else 1
                        }
                    }
                }
                imported to reminderSkipped
            }

            val message = buildString {
                append(getString(R.string.task_import_success, importedCount))
                if (reminderSkippedCount > 0) {
                    append("\n")
                    append(getString(R.string.task_import_reminder_skipped, reminderSkippedCount))
                }
            }
            Toast.makeText(this@TaskImportPreviewActivity, message, Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun scheduleReminderIfPossible(task: StudyTask): Boolean {
        if (task.remindAt == null) return true
        if (!settingsRepository.isReminderEnabled()) return false
        if (!PermissionUtils.hasPostNotificationsPermission(this)) return false
        return reminderScheduler.scheduleTaskReminder(task).scheduled
    }

    private fun resolveCourseId(courseName: String?, courses: List<com.example.campusmate.data.model.Course>): Long? {
        val normalizedCourseName = courseName?.trim().orEmpty()
        if (normalizedCourseName.isBlank()) return null
        return courses.firstOrNull { course ->
            val normalizedExisting = course.name.trim()
            normalizedExisting.equals(normalizedCourseName, ignoreCase = true) ||
                normalizedCourseName.contains(normalizedExisting, ignoreCase = true) ||
                normalizedExisting.contains(normalizedCourseName, ignoreCase = true)
        }?.id
    }

    private fun TaskDraft.toTask(courseId: Long?): StudyTask {
        return StudyTask(
            courseId = courseId,
            title = title,
            description = description,
            type = type,
            priority = priority,
            dueAt = dueAt,
            remindAt = remindAt,
            status = StudyTask.STATUS_TODO
        )
    }

    companion object {
        const val EXTRA_TASK_DRAFTS = "extra_task_drafts"
        const val EXTRA_WARNINGS = "extra_warnings"
    }
}
