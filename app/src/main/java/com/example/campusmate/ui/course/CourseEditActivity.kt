package com.example.campusmate.ui.course

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.CourseRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

/** Screen for creating and editing courses with validation and conflict warnings. */
class CourseEditActivity : AppCompatActivity() {
    private lateinit var repository: CourseRepository
    private var editingCourseId: Long = 0L
    private var editingCourse: Course? = null

    private lateinit var rootView: View
    private lateinit var nameInput: TextInputEditText
    private lateinit var teacherInput: TextInputEditText
    private lateinit var classroomInput: TextInputEditText
    private lateinit var weekdaySpinner: Spinner
    private lateinit var startSectionInput: TextInputEditText
    private lateinit var endSectionInput: TextInputEditText
    private lateinit var startWeekInput: TextInputEditText
    private lateinit var endWeekInput: TextInputEditText
    private lateinit var weekTypeSpinner: Spinner
    private lateinit var colorSpinner: Spinner
    private lateinit var noteInput: TextInputEditText
    private lateinit var timeSlotSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_edit)

        repository = CourseRepository(this)
        editingCourseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L)
        bindViews()
        setupToolbar()
        setupSpinners()

        if (editingCourseId > 0L) {
            editingCourse = repository.getCourseById(editingCourseId)
            val course = editingCourse
            if (course == null) {
                showMessage(getString(R.string.course_not_found))
                finish()
                return
            }
            bindCourse(course)
        }

        findViewById<MaterialButton>(R.id.saveCourseButton).setOnClickListener {
            saveWithConflictCheck()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        rootView = findViewById(R.id.courseEditRoot)
        nameInput = findViewById(R.id.courseNameInput)
        teacherInput = findViewById(R.id.courseTeacherInput)
        classroomInput = findViewById(R.id.courseClassroomInput)
        weekdaySpinner = findViewById(R.id.courseWeekdaySpinner)
        startSectionInput = findViewById(R.id.courseStartSectionInput)
        endSectionInput = findViewById(R.id.courseEndSectionInput)
        startWeekInput = findViewById(R.id.courseStartWeekInput)
        endWeekInput = findViewById(R.id.courseEndWeekInput)
        weekTypeSpinner = findViewById(R.id.courseWeekTypeSpinner)
        colorSpinner = findViewById(R.id.courseColorSpinner)
        noteInput = findViewById(R.id.courseNoteInput)
        timeSlotSpinner = findViewById(R.id.courseTimeSlotSpinner)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.courseEditToolbar)
        toolbar.title = getString(if (editingCourseId > 0L) R.string.course_edit_title else R.string.course_add_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupSpinners() {
        weekdaySpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.course_weekdays,
            android.R.layout.simple_spinner_dropdown_item
        )
        timeSlotSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            timeSlotLabels
        )
        weekTypeSpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.course_week_types,
            android.R.layout.simple_spinner_dropdown_item
        )
        colorSpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.course_color_labels,
            android.R.layout.simple_spinner_dropdown_item
        )
    }

    private fun bindCourse(course: Course) {
        nameInput.setText(course.name)
        teacherInput.setText(course.teacher.orEmpty())
        classroomInput.setText(course.classroom.orEmpty())
        weekdaySpinner.setSelection((course.weekday - 1).coerceIn(0, 6))
        timeSlotSpinner.setSelection((course.startSection - 1).coerceIn(0, 6))
        endSectionInput.setText(course.endSection.toString())
        startWeekInput.setText(course.startWeek.toString())
        endWeekInput.setText(course.endWeek.toString())
        weekTypeSpinner.setSelection(course.weekType.coerceIn(0, 2))
        colorSpinner.setSelection(colorValues.indexOf(course.color).takeIf { it >= 0 } ?: 0)
        noteInput.setText(course.note.orEmpty())
    }

    private fun saveWithConflictCheck() {
        val course = collectCourseOrShowError() ?: return
        if (repository.hasTimeConflict(course)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.course_conflict_title)
                .setMessage(R.string.course_conflict_message)
                .setNegativeButton(R.string.course_conflict_back, null)
                .setPositiveButton(R.string.course_conflict_save_anyway) { _, _ ->
                    persistCourse(course)
                }
                .show()
        } else {
            persistCourse(course)
        }
    }

    private fun collectCourseOrShowError(): Course? {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            nameInput.error = getString(R.string.course_name_required)
            return null
        }

        val startSection = timeSlotSpinner.selectedItemPosition + 1
        val endSection = startSection
        val startWeek = parsePositiveInt(startWeekInput, R.string.course_start_week)
        val endWeek = parsePositiveInt(endWeekInput, R.string.course_end_week)
        if (startSection == null || endSection == null || startWeek == null || endWeek == null) {
            return null
        }
        if (endSection < startSection) {
            showMessage(getString(R.string.course_section_range_error))
            return null
        }
        if (endWeek < startWeek) {
            showMessage(getString(R.string.course_week_range_error))
            return null
        }

        return Course(
            id = editingCourseId,
            name = name,
            teacher = teacherInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            classroom = classroomInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            weekday = weekdaySpinner.selectedItemPosition + 1,
            startSection = startSection,
            endSection = endSection,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekTypeSpinner.selectedItemPosition,
            color = colorValues[colorSpinner.selectedItemPosition],
            note = noteInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            createdAt = editingCourse?.createdAt ?: 0L
        )
    }

    private fun parsePositiveInt(input: TextInputEditText, labelRes: Int): Int? {
        val value = input.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value <= 0) {
            input.error = getString(R.string.course_positive_number_required, getString(labelRes))
            return null
        }
        return value
    }

    private fun persistCourse(course: Course) {
        try {
            val success = if (course.id > 0L) {
                repository.updateCourse(course)
            } else {
                repository.addCourse(course) > 0L
            }
            if (success) {
                setResult(RESULT_OK)
                finish()
            } else {
                showMessage(getString(R.string.course_save_failed))
            }
        } catch (error: IllegalArgumentException) {
            showMessage(error.message ?: getString(R.string.course_save_failed))
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"

        private val colorValues = listOf(
            "#1B6B5F",
            "#2F5D9E",
            "#E8A23A",
            "#B54848",
            "#6B5BA7",
            "#3C7D4E"
        )
        private val timeSlotLabels = listOf(
            "1  8:00-9:50",
            "2  10:10-12:00",
            "3  12:10-13:50",
            "4  14:10-16:00",
            "5  16:20-18:10",
            "6  19:00-20:50",
            "7  21:00-21:50"
        )
    }
}
