package com.example.campusmate.ui.task

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.model.TaskAttachment
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.TaskAttachmentRepository
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
    private lateinit var attachmentRepository: TaskAttachmentRepository
    private lateinit var attachmentAdapter: TaskAttachmentAdapter
    private lateinit var rootView: View
    private var taskId: Long = 0L
    private var currentTask: StudyTask? = null

    private val pickAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            addPickedAttachment(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)
        taskRepository = TaskRepository(this)
        courseRepository = CourseRepository(this)
        reminderScheduler = AlarmReminderScheduler(this)
        attachmentRepository = TaskAttachmentRepository(this)
        rootView = findViewById(R.id.taskDetailRoot)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)

        setupToolbar()
        setupAttachments()
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
        loadAttachments()
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

    private fun setupAttachments() {
        attachmentAdapter = TaskAttachmentAdapter(
            onOpen = { openAttachment(it) },
            onDelete = { confirmDeleteAttachment(it) }
        )
        findViewById<RecyclerView>(R.id.attachmentRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@TaskDetailActivity)
            adapter = attachmentAdapter
        }
        findViewById<MaterialButton>(R.id.addAttachmentButton).setOnClickListener {
            if (taskId <= 0L) {
                Snackbar.make(rootView, R.string.task_not_found, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickAttachmentLauncher.launch(arrayOf("image/*"))
        }
    }

    private fun loadAttachments() {
        if (taskId <= 0L) return
        val attachments = attachmentRepository.getAttachmentsByTask(taskId)
        attachmentAdapter.submitList(attachments)
        findViewById<View>(R.id.attachmentEmptyText).visibility = if (attachments.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addPickedAttachment(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers do not support persistable permissions; best effort only.
        }

        val mimeType = contentResolver.getType(uri)
        val title = TaskAttachmentUiUtils.queryDisplayName(this, uri)
        val id = attachmentRepository.addAttachment(
            taskId = taskId,
            uri = uri.toString(),
            mimeType = mimeType,
            title = title
        )
        if (id > 0L) {
            loadAttachments()
        } else {
            Snackbar.make(rootView, R.string.task_attachment_add_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openAttachment(item: TaskAttachment) {
        val uri = Uri.parse(item.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) {
            Snackbar.make(rootView, R.string.task_attachment_no_viewer, Snackbar.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent.createChooser(intent, getString(R.string.task_attachment_open)))
    }

    private fun confirmDeleteAttachment(item: TaskAttachment) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_attachment_delete_title)
            .setMessage(getString(R.string.task_attachment_delete_message, item.title ?: item.uri))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (attachmentRepository.deleteAttachment(item.id)) {
                    loadAttachments()
                } else {
                    Snackbar.make(rootView, R.string.task_attachment_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
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
