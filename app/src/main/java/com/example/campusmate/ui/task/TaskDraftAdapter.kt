package com.example.campusmate.ui.task

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.domain.task.TaskDraft
import com.example.campusmate.util.DateTimeUtils

/** Adapter for AI-parsed task drafts before import confirmation. */
class TaskDraftAdapter : RecyclerView.Adapter<TaskDraftAdapter.TaskDraftViewHolder>() {
    private val items = mutableListOf<TaskDraftItem>()

    fun submitList(newItems: List<TaskDraftItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun selectedItems(): List<TaskDraftItem> = items.filter { it.selected }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskDraftViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_draft, parent, false)
        return TaskDraftViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskDraftViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TaskDraftViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.taskDraftCheckBox)
        private val titleText: TextView = itemView.findViewById(R.id.taskDraftTitleText)
        private val metaText: TextView = itemView.findViewById(R.id.taskDraftMetaText)
        private val timeText: TextView = itemView.findViewById(R.id.taskDraftTimeText)
        private val warningText: TextView = itemView.findViewById(R.id.taskDraftWarningText)

        fun bind(item: TaskDraftItem) {
            val draft = item.draft
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.selected
            titleText.text = draft.title
            metaText.text = listOfNotNull(
                draft.courseName?.takeIf { it.isNotBlank() },
                TaskUiFormatter.typeLabel(itemView.context, draft.type),
                TaskUiFormatter.priorityLabel(itemView.context, draft.priority)
            ).joinToString(" · ")
            timeText.text = listOfNotNull(
                draft.dueAt?.let { itemView.context.getString(R.string.task_due_time) + " " + DateTimeUtils.formatDateTime(it) },
                draft.remindAt?.let { itemView.context.getString(R.string.task_remind_time) + " " + DateTimeUtils.formatDateTime(it) }
            ).joinToString("\n")
            timeText.visibility = if (timeText.text.isNullOrBlank()) View.GONE else View.VISIBLE
            warningText.visibility = if (draft.warnings.isEmpty()) View.GONE else View.VISIBLE
            warningText.text = draft.warnings.joinToString("\n")
            checkBox.setOnCheckedChangeListener { _, checked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    items[position].selected = checked
                }
            }
            itemView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }
    }
}

data class TaskDraftItem(
    val draft: TaskDraft,
    var selected: Boolean = true
)
