package com.example.campusmate.ui.course

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.CourseRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Read-only course detail screen with edit and soft-delete actions. */
class CourseDetailActivity : AppCompatActivity() {
    private lateinit var repository: CourseRepository
    private lateinit var rootView: View
    private var courseId: Long = 0L
    private var currentCourse: Course? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_detail)
        repository = CourseRepository(this)
        courseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L)
        rootView = findViewById(R.id.courseDetailRoot)

        setupToolbar()
        findViewById<MaterialButton>(R.id.editCourseButton).setOnClickListener {
            currentCourse?.let { course ->
                startActivity(
                    Intent(this, CourseEditActivity::class.java)
                        .putExtra(CourseEditActivity.EXTRA_COURSE_ID, course.id)
                )
            }
        }
        findViewById<MaterialButton>(R.id.deleteCourseButton).setOnClickListener {
            currentCourse?.let { confirmDelete(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCourse()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.courseDetailToolbar)
        toolbar.title = getString(R.string.course_detail_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadCourse() {
        val course = repository.getCourseById(courseId)
        if (course == null) {
            Snackbar.make(rootView, R.string.course_not_found, Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        currentCourse = course
        bindCourse(course)
    }

    private fun bindCourse(course: Course) {
        findViewById<TextView>(R.id.courseDetailNameText).text = course.name
        findViewById<TextView>(R.id.courseDetailTeacherText).text = course.teacher.orEmpty().ifBlank { getString(R.string.course_empty_value) }
        findViewById<TextView>(R.id.courseDetailClassroomText).text = course.classroom.orEmpty().ifBlank { getString(R.string.course_empty_value) }
        findViewById<TextView>(R.id.courseDetailTimeText).text = CourseUiFormatter.timeSummary(course)
        findViewById<TextView>(R.id.courseDetailNoteText).text = course.note.orEmpty().ifBlank { getString(R.string.course_no_note) }
        findViewById<View>(R.id.courseDetailColorView).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.getDimension(R.dimen.space_s)
            setColor(CourseUiFormatter.parseColorOrDefault(course.color))
        }
    }

    private fun confirmDelete(course: Course) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.course_delete_title)
            .setMessage(getString(R.string.course_delete_message, course.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (repository.deleteCourse(course.id)) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Snackbar.make(rootView, R.string.course_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
    }
}
