package com.example.campusmate.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

/** Shows study tasks, filters, and quick complete toggles. */
class TaskListFragment : Fragment(R.layout.fragment_task_list) {
    private lateinit var taskRepository: TaskRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var reminderScheduler: AlarmReminderScheduler
    private lateinit var adapter: TaskAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: View
    private var currentFilter: TaskFilter = TaskFilter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        taskRepository = TaskRepository(requireContext())
        courseRepository = CourseRepository(requireContext())
        reminderScheduler = AlarmReminderScheduler(requireContext())
        emptyStateView = view.findViewById(R.id.taskEmptyState)
        recyclerView = view.findViewById(R.id.taskRecyclerView)
        adapter = TaskAdapter(
            onTaskClick = { openDetail(it.id) },
            onToggleDone = { toggleTaskDone(it) },
            onEditClick = { openEdit(it.id) },
            onDeleteClick = { confirmDelete(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.addTaskFab).setOnClickListener { openEdit() }
        view.findViewById<ChipGroup>(R.id.taskFilterGroup).setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = filterForChipId(checkedIds.firstOrNull() ?: R.id.filterAllTasksChip)
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
        val tasks = taskRepository.getAllTasks().filter { task ->
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
        val success = if (task.status == StudyTask.STATUS_DONE) {
            taskRepository.markTodo(task.id)
        } else {
            reminderScheduler.cancelTaskReminder(task.id)
            taskRepository.markDone(task.id)
        }
        if (success) {
            loadTasks()
        } else {
            Snackbar.make(requireView(), R.string.task_status_update_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openDetail(taskId: Long) {
        startActivity(
            Intent(requireContext(), TaskDetailActivity::class.java)
                .putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId)
        )
    }

    private fun openEdit(taskId: Long? = null) {
        val intent = Intent(requireContext(), TaskEditActivity::class.java)
        taskId?.let { intent.putExtra(TaskEditActivity.EXTRA_TASK_ID, it) }
        startActivity(intent)
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
}
