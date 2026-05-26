package com.example.campusmate.ui.course

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.Course

/** RecyclerView adapter for course cards and item-level actions. */
class CourseAdapter(
    private val onCourseClick: (Course) -> Unit,
    private val onEditClick: (Course) -> Unit,
    private val onDeleteClick: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {
    private val courses = mutableListOf<Course>()

    fun submitList(items: List<Course>) {
        courses.clear()
        courses.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bind(courses[position])
    }

    override fun getItemCount(): Int = courses.size

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorView: View = itemView.findViewById(R.id.courseColorView)
        private val nameText: TextView = itemView.findViewById(R.id.courseNameText)
        private val metaText: TextView = itemView.findViewById(R.id.courseMetaText)
        private val timeText: TextView = itemView.findViewById(R.id.courseTimeText)
        private val noteText: TextView = itemView.findViewById(R.id.courseNoteText)
        private val moreButton: ImageButton = itemView.findViewById(R.id.courseMoreButton)

        fun bind(course: Course) {
            nameText.text = course.name
            metaText.text = CourseUiFormatter.teacherAndRoom(course)
            timeText.text = CourseUiFormatter.timeSummary(course)
            noteText.text = course.note?.takeIf { it.isNotBlank() } ?: itemView.context.getString(R.string.course_no_note)
            colorView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = itemView.resources.getDimension(R.dimen.space_xs)
                setColor(CourseUiFormatter.parseColorOrDefault(course.color))
            }

            itemView.setOnClickListener { onCourseClick(course) }
            itemView.setOnLongClickListener {
                showItemMenu(course, moreButton)
                true
            }
            moreButton.setOnClickListener { showItemMenu(course, moreButton) }
        }

        private fun showItemMenu(course: Course, anchor: View) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add(R.string.course_action_edit)
                menu.add(R.string.course_action_delete)
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        anchor.context.getString(R.string.course_action_edit) -> onEditClick(course)
                        anchor.context.getString(R.string.course_action_delete) -> onDeleteClick(course)
                    }
                    true
                }
                show()
            }
        }
    }
}
