package com.example.campusmate.ui.course

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
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
    private lateinit var timetableScrollView: View
    private lateinit var timetableContainer: LinearLayout
    private lateinit var recyclerView: View
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
        timetableScrollView = view.findViewById(R.id.courseTimetableScroll)
        timetableContainer = view.findViewById(R.id.courseTimetableContainer)
        recyclerView = view.findViewById(R.id.courseRecyclerView)

        view.findViewById<FloatingActionButton>(R.id.addCourseFab).setOnClickListener { anchor ->
            showAddCourseMenu(anchor)
        }
        view.findViewById<MaterialButton>(R.id.courseEmptyActionButton).setOnClickListener {
            openEdit()
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
        renderTimetable(courses)
        val isEmpty = courses.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        timetableScrollView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun renderTimetable(courses: List<Course>) {
        timetableContainer.removeAllViews()

        val days = (1..7).toList()
        val maxSection = maxOf(7, courses.maxOfOrNull { it.endSection } ?: 7)

        val headerRow = createRow()
        headerRow.addView(createBaseCell(getString(R.string.course_time_header), TIME_CELL_WIDTH_DP, HEADER_CELL_HEIGHT_DP))
        days.forEach { weekday ->
            headerRow.addView(
                createBaseCell(
                    CourseUiFormatter.weekdayLabel(requireContext(), weekday),
                    COURSE_CELL_WIDTH_DP,
                    HEADER_CELL_HEIGHT_DP
                )
            )
        }
        timetableContainer.addView(headerRow)

        for (section in 1..maxSection) {
            val row = createRow()
            row.addView(
                createBaseCell(
                    getString(R.string.course_time_slot_label, section, courseTimeRanges.getOrNull(section - 1).orEmpty()),
                    TIME_CELL_WIDTH_DP,
                    COURSE_CELL_HEIGHT_DP
                )
            )
            days.forEach { weekday ->
                val cellCourses = courses.filter { course ->
                    course.weekday == weekday && section in course.startSection..course.endSection
                }
                row.addView(createCourseCell(cellCourses.firstOrNull(), cellCourses.size > 1))
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

    private fun createCourseCell(course: Course?, conflict: Boolean): View {
        val cell = layoutInflater.inflate(R.layout.item_course_grid, timetableContainer, false)
        cell.layoutParams = LinearLayout.LayoutParams(dp(COURSE_CELL_WIDTH_DP), dp(COURSE_CELL_HEIGHT_DP)).apply {
            rightMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
        }

        val nameText = cell.findViewById<TextView>(R.id.courseNameText)
        val metaText = cell.findViewById<TextView>(R.id.courseMetaText)
        val timeText = cell.findViewById<TextView>(R.id.courseTimeText)

        if (course == null) {
            nameText.text = ""
            metaText.text = ""
            timeText.text = ""
            cell.alpha = EMPTY_CELL_ALPHA
            cell.isClickable = false
            return cell
        }

        nameText.text = course.name
        metaText.text = CourseUiFormatter.teacherAndRoom(requireContext(), course)
        timeText.text = CourseUiFormatter.timeSummary(requireContext(), course)
        if (conflict) {
            timeText.append("\n${getString(R.string.course_conflict_title)}")
        }
        cell.alpha = 1f
        cell.background = CourseGridCellBackgroundFactory.create(requireContext(), course.color)
        cell.setOnClickListener { openDetail(course.id) }
        cell.setOnLongClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(course.name)
                .setItems(arrayOf(getString(R.string.course_action_edit), getString(R.string.course_action_delete))) { _, which ->
                    when (which) {
                        0 -> openEdit(course.id)
                        1 -> confirmDelete(course)
                    }
                }
                .show()
            true
        }
        return cell
    }

    private fun createBaseCell(text: String, widthDp: Int, heightDp: Int): TextView = TextView(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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

    private fun showAddCourseMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(R.string.course_add)
            menu.add(R.string.dashboard_quick_import_schedule)
            menu.add(R.string.course_semester_settings)
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    getString(R.string.course_add) -> openEdit()
                    getString(R.string.dashboard_quick_import_schedule) -> {
                        startActivity(Intent(requireContext(), ImportScheduleActivity::class.java))
                    }
                    getString(R.string.course_semester_settings) -> {
                        Snackbar.make(requireView(), R.string.placeholder_next_stage, Snackbar.LENGTH_SHORT).show()
                    }
                }
                true
            }
            show()
        }
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
        private const val TIME_CELL_WIDTH_DP = 92
        private const val COURSE_CELL_WIDTH_DP = 96
        private const val HEADER_CELL_HEIGHT_DP = 40
        private const val COURSE_CELL_HEIGHT_DP = 120
        private const val EMPTY_CELL_ALPHA = 0.18f
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
