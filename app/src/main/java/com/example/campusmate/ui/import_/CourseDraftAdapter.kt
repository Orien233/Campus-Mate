package com.example.campusmate.ui.import_

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.domain.import_.CourseDraft
import com.example.campusmate.ui.course.CourseUiFormatter

/** Adapter for parsed course drafts before import confirmation. */
class CourseDraftAdapter : RecyclerView.Adapter<CourseDraftAdapter.CourseDraftViewHolder>() {
    private val items = mutableListOf<CourseDraftItem>()

    fun submitList(newItems: List<CourseDraftItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun selectedItems(): List<CourseDraftItem> = items.filter { it.selected }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseDraftViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course_draft, parent, false)
        return CourseDraftViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseDraftViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CourseDraftViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.courseDraftCheckBox)
        private val nameText: TextView = itemView.findViewById(R.id.courseDraftNameText)
        private val metaText: TextView = itemView.findViewById(R.id.courseDraftMetaText)
        private val timeText: TextView = itemView.findViewById(R.id.courseDraftTimeText)
        private val conflictText: TextView = itemView.findViewById(R.id.courseDraftConflictText)

        fun bind(item: CourseDraftItem) {
            val draft = item.draft
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.selected
            nameText.text = draft.name
            metaText.text = listOfNotNull(draft.teacher, draft.classroom).joinToString(" · ").ifBlank {
                itemView.context.getString(R.string.course_empty_value)
            }
            timeText.text = itemView.context.getString(
                R.string.import_draft_time_format,
                CourseUiFormatter.weekdayLabel(draft.weekday),
                draft.startSection,
                draft.endSection,
                draft.startWeek,
                draft.endWeek,
                weekTypeLabel(draft.weekType)
            )
            conflictText.visibility = if (item.hasConflict) View.VISIBLE else View.GONE
            conflictText.text = item.conflictMessage ?: itemView.context.getString(R.string.import_conflict_badge)
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

        private fun weekTypeLabel(weekType: Int): String {
            return when (weekType) {
                1 -> itemView.context.getString(R.string.course_week_type_odd)
                2 -> itemView.context.getString(R.string.course_week_type_even)
                else -> itemView.context.getString(R.string.course_week_type_every)
            }
        }
    }
}

data class CourseDraftItem(
    val draft: CourseDraft,
    val hasConflict: Boolean,
    val conflictMessage: String? = null,
    var selected: Boolean = !hasConflict
)
