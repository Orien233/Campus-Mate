package com.example.campusmate.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Read-only task detail screen with edit, complete, and delete actions. */
class TaskDetailActivity : AppCompatActivity() {
    private lateinit var taskRepository: TaskRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var reminderScheduler: AlarmReminderScheduler
    private lateinit var rootView: View
    private var taskId: Long = 0L
    private var currentTask: StudyTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)
        taskRepository = TaskRepository(this)
        courseRepository = CourseRepository(this)
        reminderScheduler = AlarmReminderScheduler(this)
        rootView = findViewById(R.id.taskDetailRoot)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)

        setupToolbar()
        findViewById<MaterialButton>(R.id.editTaskButton).setOnClickListener {
            currentTask?.let {
                startActivity(Intent(this, TaskEditActivity::class.java).putExtra(TaskEditActivity.EXTRA_TASK_ID, it.id))
            }
        }
        findViewById<MaterialButton>(R.id.toggleTaskDoneButton).setOnClickListener {
            currentTask?.let { toggleDone(it) }
        }
        findViewById<MaterialButton>(R.id.deleteTaskButton).setOnClickListener {
            currentTask?.let { confirmDelete(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTask()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.taskDetailToolbar)
        toolbar.title = getString(R.string.task_detail_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadTask() {
        val task = taskRepository.getTaskById(taskId)
        if (task == null) {
            Snackbar.make(rootView, R.string.task_not_found, Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        currentTask = task
        bindTask(task)
    }

    private fun bindTask(task: StudyTask) {
        val courseName = task.courseId?.let { courseRepository.getCourseById(it)?.name } ?: getString(R.string.task_no_course)
        findViewById<TextView>(R.id.taskDetailTitleText).text = task.title
        findViewById<TextView>(R.id.taskDetailCourseText).text = courseName
        findViewById<TextView>(R.id.taskDetailMetaText).text = getString(
            R.string.task_meta_format,
            TaskUiFormatter.typeLabel(this, task.type),
            TaskUiFormatter.priorityLabel(this, task.priority),
            TaskUiFormatter.statusLabel(this, task.status)
        )
        findViewById<TextView>(R.id.taskDetailDueText).text = TaskUiFormatter.dueLabel(this, task.dueAt)
        findViewById<TextView>(R.id.taskDetailRemindText).text = TaskUiFormatter.remindLabel(this, task.remindAt)
        findViewById<TextView>(R.id.taskDetailDescriptionText).text =
            task.description.orEmpty().ifBlank { getString(R.string.task_no_description) }
        findViewById<MaterialButton>(R.id.toggleTaskDoneButton).setText(
            if (task.status == StudyTask.STATUS_DONE) R.string.task_mark_todo else R.string.task_mark_done
        )
    }

    private fun toggleDone(task: StudyTask) {
        val success = if (task.status == StudyTask.STATUS_DONE) {
            taskRepository.markTodo(task.id)
        } else {
            reminderScheduler.cancelTaskReminder(task.id)
            taskRepository.markDone(task.id)
        }
        if (success) loadTask() else Snackbar.make(rootView, R.string.task_status_update_failed, Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmDelete(task: StudyTask) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_delete_title)
            .setMessage(getString(R.string.task_delete_message, task.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                reminderScheduler.cancelTaskReminder(task.id)
                if (taskRepository.deleteTask(task.id)) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Snackbar.make(rootView, R.string.task_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
