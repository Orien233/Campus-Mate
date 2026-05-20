package com.example.campusmate.ui.task

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask

/** RecyclerView adapter for task cards and quick status changes. */
class TaskAdapter(
    private val onTaskClick: (StudyTask) -> Unit,
    private val onToggleDone: (StudyTask) -> Unit,
    private val onEditClick: (StudyTask) -> Unit,
    private val onDeleteClick: (StudyTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {
    private val tasks = mutableListOf<TaskListItem>()

    fun submitList(items: List<TaskListItem>) {
        tasks.clear()
        tasks.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val doneCheckBox: CheckBox = itemView.findViewById(R.id.taskDoneCheckBox)
        private val titleText: TextView = itemView.findViewById(R.id.taskTitleText)
        private val courseText: TextView = itemView.findViewById(R.id.taskCourseText)
        private val metaText: TextView = itemView.findViewById(R.id.taskMetaText)
        private val dueText: TextView = itemView.findViewById(R.id.taskDueText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.taskMoreButton)

        fun bind(item: TaskListItem) {
            val task = item.task
            doneCheckBox.setOnCheckedChangeListener(null)
            doneCheckBox.isChecked = task.status == StudyTask.STATUS_DONE
            titleText.text = task.title
            titleText.paintFlags = if (task.status == StudyTask.STATUS_DONE) {
                titleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                titleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            courseText.text = item.courseName ?: itemView.context.getString(R.string.task_no_course)
            metaText.text = itemView.context.getString(
                R.string.task_meta_format,
                TaskUiFormatter.typeLabel(itemView.context, task.type),
                TaskUiFormatter.priorityLabel(itemView.context, task.priority),
                TaskUiFormatter.statusLabel(itemView.context, task.status)
            )
            dueText.text = TaskUiFormatter.dueLabel(itemView.context, task.dueAt)

            doneCheckBox.setOnCheckedChangeListener { _, _ -> onToggleDone(task) }
            itemView.setOnClickListener { onTaskClick(task) }
            moreButton.setOnClickListener { showMenu(task, moreButton) }
            itemView.setOnLongClickListener {
                showMenu(task, moreButton)
                true
            }
        }

        private fun showMenu(task: StudyTask, anchor: View) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add(R.string.task_action_edit)
                menu.add(R.string.task_action_delete)
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        anchor.context.getString(R.string.task_action_edit) -> onEditClick(task)
                        anchor.context.getString(R.string.task_action_delete) -> onDeleteClick(task)
                    }
                    true
                }
                show()
            }
        }
    }
}

data class TaskListItem(
    val task: StudyTask,
    val courseName: String?
)
