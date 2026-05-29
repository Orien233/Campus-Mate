package com.example.campusmate.ui.course

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.ui.import_.ImportScheduleActivity
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

/** Shows the user's course list and weekday filter. */
class CourseListFragment : Fragment(R.layout.fragment_course_list) {
    private lateinit var repository: CourseRepository
    private lateinit var adapter: CourseAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: View
    private lateinit var totalCountText: TextView
    private lateinit var todayCountText: TextView
    private lateinit var visibleCountText: TextView
    private var selectedWeekday: Int = WEEKDAY_ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = CourseRepository(requireContext())
        emptyStateView = view.findViewById(R.id.courseEmptyState)
        totalCountText = view.findViewById(R.id.courseTotalCountText)
        todayCountText = view.findViewById(R.id.courseTodayCountText)
        visibleCountText = view.findViewById(R.id.courseVisibleCountText)
        recyclerView = view.findViewById(R.id.courseRecyclerView)
        adapter = CourseAdapter(
            onCourseClick = { openDetail(it.id) },
            onEditClick = { openEdit(it.id) },
            onDeleteClick = { confirmDelete(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.addCourseFab).setOnClickListener {
            openEdit()
        }
        view.findViewById<MaterialButton>(R.id.courseEmptyActionButton).setOnClickListener {
            openEdit()
        }
        view.findViewById<MaterialButton>(R.id.manualAddCourseButton).setOnClickListener {
            openEdit()
        }
        view.findViewById<MaterialButton>(R.id.importCourseButton).setOnClickListener {
            startActivity(Intent(requireContext(), ImportScheduleActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.semesterSettingsButton).setOnClickListener {
            Snackbar.make(requireView(), R.string.placeholder_next_stage, Snackbar.LENGTH_SHORT).show()
        }

        view.findViewById<ChipGroup>(R.id.weekdayFilterGroup).setOnCheckedStateChangeListener { _, checkedIds ->
            selectedWeekday = checkedIds.firstOrNull()?.let { weekdayForChipId(it) } ?: WEEKDAY_ALL
            loadCourses()
        }
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.courseFilterHeader,
            contentId = R.id.courseFilterContent,
            indicatorId = R.id.courseFilterIndicator
        )
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.courseActionsHeader,
            contentId = R.id.courseActionsContent,
            indicatorId = R.id.courseActionsIndicator
        )
    }

    override fun onResume() {
        super.onResume()
        loadCourses()
    }

    private fun loadCourses() {
        val allCourses = repository.getAllCourses()
        val courses = if (selectedWeekday == WEEKDAY_ALL) {
            allCourses
        } else {
            allCourses.filter { it.weekday == selectedWeekday }
        }
        totalCountText.text = allCourses.size.toString()
        todayCountText.text = allCourses.count { it.weekday == DateTimeUtils.currentWeekday() }.toString()
        visibleCountText.text = courses.size.toString()
        adapter.submitList(courses)
        val isEmpty = courses.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openDetail(courseId: Long) {
        startActivity(
            Intent(requireContext(), CourseDetailActivity::class.java)
                .putExtra(CourseDetailActivity.EXTRA_COURSE_ID, courseId)
        )
    }

    private fun openEdit(courseId: Long? = null) {
        val intent = Intent(requireContext(), CourseEditActivity::class.java)
        courseId?.let { intent.putExtra(CourseEditActivity.EXTRA_COURSE_ID, it) }
        startActivity(intent)
    }

    private fun confirmDelete(course: Course) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.course_delete_title)
            .setMessage(getString(R.string.course_delete_message, course.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (repository.deleteCourse(course.id)) {
                    Snackbar.make(requireView(), R.string.course_delete_success, Snackbar.LENGTH_SHORT).show()
                    loadCourses()
                } else {
                    Snackbar.make(requireView(), R.string.course_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun weekdayForChipId(chipId: Int): Int {
        return when (chipId) {
            R.id.filterMondayChip -> 1
            R.id.filterTuesdayChip -> 2
            R.id.filterWednesdayChip -> 3
            R.id.filterThursdayChip -> 4
            R.id.filterFridayChip -> 5
            R.id.filterSaturdayChip -> 6
            R.id.filterSundayChip -> 7
            else -> WEEKDAY_ALL
        }
    }

    companion object {
        private const val WEEKDAY_ALL = 0
    }
}
