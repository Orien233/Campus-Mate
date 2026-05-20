package com.example.campusmate.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.ui.focus.FocusActivity
import com.example.campusmate.ui.import_.ImportScheduleActivity
import com.example.campusmate.ui.task.TaskEditActivity
import com.google.android.material.button.MaterialButton

/** Dashboard entry point for daily course, task, and focus summaries. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private lateinit var courseRepository: CourseRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var studyRecordRepository: StudyRecordRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        courseRepository = CourseRepository(requireContext())
        taskRepository = TaskRepository(requireContext())
        studyRecordRepository = StudyRecordRepository(requireContext())

        view.findViewById<MaterialButton>(R.id.startFocusButton).setOnClickListener {
            startActivity(Intent(requireContext(), FocusActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.addTaskButton).setOnClickListener {
            startActivity(Intent(requireContext(), TaskEditActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.importScheduleButton).setOnClickListener {
            startActivity(Intent(requireContext(), ImportScheduleActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val currentView = view ?: return
        val todayCourses = courseRepository.getTodayCourses()
        val pendingTasks = taskRepository.getAllTasks().count { it.status == StudyTask.STATUS_TODO }
        val todayDurationMinutes = studyRecordRepository.getTodayDuration() / 60
        val weeklyDurationMinutes = studyRecordRepository.getWeeklyDuration() / 60

        currentView.findViewById<TextView>(R.id.todayCoursesValue).text = todayCourses.size.toString()
        currentView.findViewById<TextView>(R.id.pendingTasksValue).text = pendingTasks.toString()
        currentView.findViewById<TextView>(R.id.todayFocusValue).text = getString(R.string.duration_minutes, todayDurationMinutes)
        currentView.findViewById<TextView>(R.id.weekFocusValue).text = getString(R.string.duration_minutes, weeklyDurationMinutes)
        currentView.findViewById<TextView>(R.id.nextCourseValue).text =
            todayCourses.firstOrNull()?.let { getString(R.string.dashboard_next_course_format, it.name, it.startSection, it.endSection) }
                ?: getString(R.string.dashboard_no_next_course)
    }
}
