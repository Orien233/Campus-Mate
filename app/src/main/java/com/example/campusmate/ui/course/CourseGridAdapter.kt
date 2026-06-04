package com.example.campusmate.ui.course

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.Course

class CourseGridAdapter(
    private val onCourseClick: (Course) -> Unit
) : RecyclerView.Adapter<CourseGridAdapter.ViewHolder>() {
    private val items = mutableListOf<Course>()

    fun submitList(courses: List<Course>) {
        items.clear()
        items.addAll(courses)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.courseNameText)
        private val metaText: TextView = itemView.findViewById(R.id.courseMetaText)
        private val timeText: TextView = itemView.findViewById(R.id.courseTimeText)

        fun bind(course: Course) {
            nameText.text = course.name
            metaText.text = CourseUiFormatter.teacherAndRoom(itemView.context, course)
            timeText.text = CourseUiFormatter.timeSummary(itemView.context, course)
            itemView.setOnClickListener { onCourseClick(course) }
            itemView.background = CourseGridCellBackgroundFactory.create(itemView.context, course.color)
        }
    }
}
