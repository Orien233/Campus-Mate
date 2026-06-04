package com.example.campusmate.ui.course

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.ui.import_.ImportScheduleActivity
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class CourseListFragment : Fragment(R.layout.fragment_course_list) {
    private lateinit var repository: CourseRepository
    private lateinit var totalCountText: TextView
    private lateinit var todayCountText: TextView
    private lateinit var visibleCountText: TextView
    private lateinit var timetableContainer: LinearLayout
    private lateinit var emptyStateView: View
    private lateinit var recyclerView: View
    private lateinit var weekdayFilterGroup: ChipGroup

    private var selectedWeekday: Int = WEEKDAY_ALL
    private var renderedCourses: List<Course> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = CourseRepository(requireContext())

        totalCountText = view.findViewById(R.id.courseTotalCountText)
        todayCountText = view.findViewById(R.id.courseTodayCountText)
        visibleCountText = view.findViewById(R.id.courseVisibleCountText)
        timetableContainer = view.findViewById(R.id.courseTimetableContainer)
        emptyStateView = view.findViewById(R.id.courseEmptyState)
        recyclerView = view.findViewById(R.id.courseRecyclerView)
        weekdayFilterGroup = view.findViewById(R.id.weekdayFilterGroup)

        view.findViewById<FloatingActionButton>(R.id.addCourseFab).setOnClickListener { openEdit() }
        view.findViewById<MaterialButton>(R.id.courseEmptyActionButton).setOnClickListener { openEdit() }
        view.findViewById<MaterialButton>(R.id.manualAddCourseButton).setOnClickListener { openEdit() }
        view.findViewById<MaterialButton>(R.id.importCourseButton).setOnClickListener {
            startActivity(Intent(requireContext(), ImportScheduleActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.semesterSettingsButton).setOnClickListener {
            Snackbar.make(requireView(), R.string.placeholder_next_stage, Snackbar.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.courseFilterHeader).setOnClickListener {
            toggleSection(view, R.id.courseFilterContent, R.id.courseFilterIndicator)
        }
        view.findViewById<View>(R.id.courseGridHeader).setOnClickListener {
            toggleSection(view, R.id.courseGridContent, R.id.courseGridIndicator)
        }
        view.findViewById<View>(R.id.courseActionsHeader).setOnClickListener {
            toggleSection(view, R.id.courseActionsContent, R.id.courseActionsIndicator)
        }

        setupWeekdayFilter()
        loadCourses()
    }

    override fun onResume() {
        super.onResume()
        loadCourses(selectedWeekday)
    }

    private fun setupWeekdayFilter() {
        weekdayFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedWeekday = when (checkedIds.firstOrNull()) {
                R.id.filterMondayChip -> 1
                R.id.filterTuesdayChip -> 2
                R.id.filterWednesdayChip -> 3
                R.id.filterThursdayChip -> 4
                R.id.filterFridayChip -> 5
                R.id.filterSaturdayChip -> 6
                R.id.filterSundayChip -> 7
                else -> WEEKDAY_ALL
            }
            loadCourses(selectedWeekday)
        }
        weekdayFilterGroup.check(R.id.filterAllChip)
    }

    private fun loadCourses(weekday: Int = WEEKDAY_ALL) {
        selectedWeekday = weekday

        val allCourses = repository.getAllCourses()
        renderedCourses = if (selectedWeekday == WEEKDAY_ALL) {
            allCourses
        } else {
            allCourses.filter { it.weekday == selectedWeekday }
        }

        totalCountText.text = allCourses.size.toString()
        todayCountText.text = allCourses.count { it.weekday == DateTimeUtils.currentWeekday() }.toString()
        visibleCountText.text = renderedCourses.size.toString()

        renderTimetable(renderedCourses)

        val isEmpty = renderedCourses.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        timetableContainer.visibility = if (isEmpty) View.GONE else View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun renderTimetable(courses: List<Course>) {
        timetableContainer.removeAllViews()

        val days = (1..7).toList()
        val maxSection = maxOf(7, courses.maxOfOrNull { it.endSection } ?: 7)

        val headerRow = createRow()
        headerRow.addView(createTimeHeaderCell())
        days.forEach { weekday ->
            headerRow.addView(createHeaderCell(CourseUiFormatter.weekdayLabel(requireContext(), weekday)))
        }
        timetableContainer.addView(headerRow)

        for (section in 1..maxSection) {
            val row = createRow()
            row.addView(createTimeSlotCell(section))
            days.forEach { weekday ->
                val cellCourses = courses.filter { course ->
                    course.weekday == weekday && section in course.startSection..course.endSection
                }
                row.addView(createCourseCell(cellCourses.firstOrNull(), cellCourses.size > 1, section))
            }
            timetableContainer.addView(row)
        }
    }

    private fun createRow(): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun createTimeHeaderCell(): TextView = createBaseCell(getString(R.string.course_time_header), 92)

    private fun createHeaderCell(title: String): TextView = createBaseCell(title, 96)

    private fun createTimeSlotCell(section: Int): TextView = createBaseCell(
        getString(R.string.course_time_slot_label, section, courseTimeRanges.getOrNull(section - 1).orEmpty()),
        92
    )

    private fun createCourseCell(course: Course?, conflict: Boolean, section: Int): View {
        val cell = layoutInflater.inflate(R.layout.item_course_grid, timetableContainer, false)
        cell.layoutParams = LinearLayout.LayoutParams(dp(96), dp(120)).apply {
            rightMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
        }

        if (course == null) {
            cell.findViewById<TextView>(R.id.courseNameText).text = ""
            cell.findViewById<TextView>(R.id.courseMetaText).text = ""
            cell.findViewById<TextView>(R.id.courseTimeText).text = ""
            cell.alpha = 0.18f
            cell.isClickable = false
            return cell
        }

        cell.findViewById<TextView>(R.id.courseNameText).text = course.name
        cell.findViewById<TextView>(R.id.courseMetaText).text = CourseUiFormatter.teacherAndRoom(requireContext(), course)
        cell.findViewById<TextView>(R.id.courseTimeText).text = CourseUiFormatter.timeSummary(requireContext(), course)
        cell.isClickable = true
        cell.alpha = 1f
        cell.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(course.name)
                .setItems(arrayOf(getString(R.string.course_action_edit), getString(R.string.course_action_delete))) { _, which ->
                    when (which) {
                        0 -> openEdit(course.id)
                        1 -> confirmDelete(course)
                    }
                }
                .show()
        }
        cell.background = CourseGridCellBackgroundFactory.create(requireContext(), course.color)

        if (conflict && section == course.startSection) {
            Snackbar.make(requireView(), getString(R.string.course_conflict_message), Snackbar.LENGTH_SHORT).show()
        }

        return cell
    }

    private fun createBaseCell(text: String, widthDp: Int): TextView = TextView(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(40)).apply {
            rightMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
        }
        setPadding(
            resources.getDimensionPixelSize(R.dimen.space_s),
            resources.getDimensionPixelSize(R.dimen.space_s),
            resources.getDimensionPixelSize(R.dimen.space_s),
            resources.getDimensionPixelSize(R.dimen.space_s)
        )
        this.text = text
        textSize = 12f
        setTextColor(resources.getColor(R.color.campus_text_primary, null))
        setBackgroundResource(R.drawable.bg_tag)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toggleSection(root: View, contentId: Int, indicatorId: Int) {
        val content = root.findViewById<View>(contentId)
        val indicator = root.findViewById<TextView>(indicatorId)
        val expanded = content.visibility != View.VISIBLE
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        indicator.text = getString(if (expanded) R.string.ui_collapse else R.string.ui_expand)
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

    companion object {
        private const val WEEKDAY_ALL = 0
        private val courseTimeRanges = listOf(
            "8:00-9:50",
            "10:10-12:00",
            "12:10-13:50",
            "14:10-16:00",
            "16:20-18:10",
            "19:00-20:50",
            "21:00-21:50"
        )
    }
}


