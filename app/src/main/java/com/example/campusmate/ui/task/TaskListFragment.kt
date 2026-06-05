package com.example.campusmate.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler
import com.example.campusmate.domain.reminder.TaskReminderPolicy
import com.example.campusmate.domain.task.TaskDraft
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

/** Shows study tasks, filters, and quick complete toggles. */
class TaskListFragment : Fragment(R.layout.fragment_task_list) {
    private lateinit var taskRepository: TaskRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var reminderScheduler: AlarmReminderScheduler
    private lateinit var adapter: TaskAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: View
    private lateinit var todoCountText: TextView
    private lateinit var upcomingCountText: TextView
    private lateinit var overdueCountText: TextView
    private lateinit var todoCountContainer: LinearLayout
    private lateinit var upcomingCountContainer: LinearLayout
    private lateinit var overdueCountContainer: LinearLayout
    private var currentFilter: TaskFilter = TaskFilter.TODO

    private val taskWebParseLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        @Suppress("DEPRECATION")
        val drafts = data.getSerializableExtra(TaskWebViewParseActivity.EXTRA_TASK_DRAFTS) as? ArrayList<TaskDraft>
            ?: return@registerForActivityResult
        if (drafts.isEmpty()) return@registerForActivityResult
        startActivity(
            Intent(requireContext(), TaskImportPreviewActivity::class.java)
                .putExtra(TaskImportPreviewActivity.EXTRA_TASK_DRAFTS, drafts)
                .putStringArrayListExtra(
                    TaskImportPreviewActivity.EXTRA_WARNINGS,
                    data.getStringArrayListExtra(TaskWebViewParseActivity.EXTRA_WARNINGS) ?: arrayListOf()
                )
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        taskRepository = TaskRepository(requireContext())
        courseRepository = CourseRepository(requireContext())
        settingsRepository = SettingsRepository(requireContext())
        reminderScheduler = AlarmReminderScheduler(requireContext())
        emptyStateView = view.findViewById(R.id.taskEmptyState)
        todoCountText = view.findViewById(R.id.taskTodoCountText)
        upcomingCountText = view.findViewById(R.id.taskUpcomingCountText)
        overdueCountText = view.findViewById(R.id.taskOverdueCountText)
        todoCountContainer = view.findViewById(R.id.taskTodoCountContainer)
        upcomingCountContainer = view.findViewById(R.id.taskUpcomingCountContainer)
        overdueCountContainer = view.findViewById(R.id.taskOverdueCountContainer)
        recyclerView = view.findViewById(R.id.taskRecyclerView)
        adapter = TaskAdapter(
            onTaskClick = { openDetail(it.id) },
            onToggleDone = { toggleTaskDone(it) },
            onEditClick = { openEdit(it.id) },
            onDeleteClick = { confirmDelete(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.addTaskFab).setOnClickListener { anchor ->
            showAddTaskMenu(anchor)
        }
        view.findViewById<MaterialButton>(R.id.taskEmptyActionButton).setOnClickListener { openEdit() }
        view.findViewById<ChipGroup>(R.id.taskFilterGroup).setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = filterForChipId(checkedIds.firstOrNull() ?: R.id.filterTodoTasksChip)
            loadTasks()
        }
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    private fun loadTasks() {
        val courseNameById = courseRepository.getAllCourses().associate { it.id to it.name }
        val now = System.currentTimeMillis()
        val allTasks = taskRepository.getAllTasks()
        val todoCount = allTasks.count { it.status == StudyTask.STATUS_TODO }
        val upcomingCount = allTasks.count {
            it.status == StudyTask.STATUS_TODO && it.dueAt?.let { dueAt -> dueAt >= now } == true
        }
        val overdueCount = allTasks.count {
            it.status == StudyTask.STATUS_TODO && it.dueAt?.let { dueAt -> dueAt < now } == true
        }
        todoCountText.text = todoCount.toString()
        upcomingCountText.text = upcomingCount.toString()
        overdueCountText.text = overdueCount.toString()
        highlightCountContainer(todoCountContainer, todoCount)
        highlightCountContainer(upcomingCountContainer, upcomingCount)
        highlightCountContainer(overdueCountContainer, overdueCount)

        val tasks = allTasks.filter { task ->
            when (currentFilter) {
                TaskFilter.ALL -> true
                TaskFilter.TODO -> task.status == StudyTask.STATUS_TODO
                TaskFilter.DONE -> task.status == StudyTask.STATUS_DONE
                TaskFilter.UPCOMING -> task.status == StudyTask.STATUS_TODO && task.dueAt?.let { it >= now } == true
                TaskFilter.OVERDUE -> task.status == StudyTask.STATUS_TODO && task.dueAt?.let { it < now } == true
            }
        }.map { task ->
            TaskListItem(task, task.courseId?.let { courseNameById[it] })
        }

        adapter.submitList(tasks)
        val isEmpty = tasks.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun toggleTaskDone(task: StudyTask) {
        val updatedStatus = if (task.status == StudyTask.STATUS_DONE) StudyTask.STATUS_TODO else StudyTask.STATUS_DONE
        if (TaskReminderPolicy.shouldCancelWhenCompleted(task.status, updatedStatus)) {
            reminderScheduler.cancelTaskReminder(task.id)
        }
        val success = if (updatedStatus == StudyTask.STATUS_TODO) {
            taskRepository.markTodo(task.id)
        } else {
            taskRepository.markDone(task.id)
        }
        if (success) {
            scheduleReminderIfReopened(task, updatedStatus)
            loadTasks()
        } else {
            Snackbar.make(requireView(), R.string.task_status_update_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun scheduleReminderIfReopened(previousTask: StudyTask, updatedStatus: Int) {
        val reopenedTask = previousTask.copy(status = updatedStatus)
        if (
            TaskReminderPolicy.shouldScheduleWhenReopened(
                previousStatus = previousTask.status,
                reopenedTask = reopenedTask,
                remindersEnabled = settingsRepository.isReminderEnabled(),
                nowMillis = DateTimeUtils.nowMillis()
            )
        ) {
            reminderScheduler.scheduleTaskReminder(reopenedTask)
        }
    }

    private fun openDetail(taskId: Long) {
        startActivity(
            Intent(requireContext(), TaskDetailActivity::class.java)
                .putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId)
        )
    }

    private fun openEdit(taskId: Long? = null, taskType: Int? = null) {
        val intent = Intent(requireContext(), TaskEditActivity::class.java)
        taskId?.let { intent.putExtra(TaskEditActivity.EXTRA_TASK_ID, it) }
        taskType?.let { intent.putExtra(TaskEditActivity.EXTRA_TASK_TYPE, it) }
        startActivity(intent)
    }

    private fun showAddTaskMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(R.string.task_action_add_homework)
            menu.add(R.string.task_action_add_exam)
            menu.add(R.string.task_action_add_review)
            menu.add(R.string.task_import_from_web)
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    getString(R.string.task_action_add_homework) -> openEdit(taskType = StudyTask.TYPE_HOMEWORK)
                    getString(R.string.task_action_add_exam) -> openEdit(taskType = StudyTask.TYPE_EXAM)
                    getString(R.string.task_action_add_review) -> openEdit(taskType = StudyTask.TYPE_REVIEW)
                    getString(R.string.task_import_from_web) -> {
                        taskWebParseLauncher.launch(Intent(requireContext(), TaskWebViewParseActivity::class.java))
                    }
                    else -> openEdit()
                }
                true
            }
            show()
        }
    }

    private fun confirmDelete(task: StudyTask) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.task_delete_title)
            .setMessage(getString(R.string.task_delete_message, task.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                reminderScheduler.cancelTaskReminder(task.id)
                if (taskRepository.deleteTask(task.id)) {
                    Snackbar.make(requireView(), R.string.task_delete_success, Snackbar.LENGTH_SHORT).show()
                    loadTasks()
                } else {
                    Snackbar.make(requireView(), R.string.task_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun filterForChipId(chipId: Int): TaskFilter {
        return when (chipId) {
            R.id.filterTodoTasksChip -> TaskFilter.TODO
            R.id.filterDoneTasksChip -> TaskFilter.DONE
            R.id.filterUpcomingTasksChip -> TaskFilter.UPCOMING
            R.id.filterOverdueTasksChip -> TaskFilter.OVERDUE
            else -> TaskFilter.ALL
        }
    }

    private enum class TaskFilter {
        ALL,
        TODO,
        DONE,
        UPCOMING,
        OVERDUE
    }

    private fun highlightCountContainer(container: LinearLayout, count: Int) {
        if (count > 0) {
            container.setBackgroundResource(R.drawable.bg_count_highlight)
        } else {
            container.background = null
        }
    }
}
