package com.example.campusmate.ui.plan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.Locale

/**
 * Form-based Activity for manually adding a study plan.
 * Fields mirror those produced by AI plan generation: title, date,
 * planned minutes, optional start/end time, and type (daily/weekly).
 */
class PlanEditActivity : AppCompatActivity() {

    private lateinit var repository: StudyPlanRepository

    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleInput: TextInputEditText
    private lateinit var dateText: TextView
    private lateinit var pickDateButton: MaterialButton
    private lateinit var minutesInput: TextInputEditText
    private lateinit var startTimeText: TextView
    private lateinit var pickStartTimeButton: MaterialButton
    private lateinit var clearStartTimeButton: MaterialButton
    private lateinit var endTimeText: TextView
    private lateinit var pickEndTimeButton: MaterialButton
    private lateinit var clearEndTimeButton: MaterialButton
    private lateinit var typeSpinner: Spinner
    private lateinit var saveButton: MaterialButton

    private var selectedDate: String = DateTimeUtils.todayDate()
    private var startTime: String? = null
    private var endTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_edit)

        repository = StudyPlanRepository(this)

        toolbar = findViewById(R.id.planEditToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.plan_add_title)
        }
        toolbar.setNavigationOnClickListener { finish() }

        titleInput = findViewById(R.id.planTitleInput)
        dateText = findViewById(R.id.planDateText)
        pickDateButton = findViewById(R.id.pickDateButton)
        minutesInput = findViewById(R.id.planMinutesInput)
        startTimeText = findViewById(R.id.planStartTimeText)
        pickStartTimeButton = findViewById(R.id.pickStartTimeButton)
        clearStartTimeButton = findViewById(R.id.clearStartTimeButton)
        endTimeText = findViewById(R.id.planEndTimeText)
        pickEndTimeButton = findViewById(R.id.pickEndTimeButton)
        clearEndTimeButton = findViewById(R.id.clearEndTimeButton)
        typeSpinner = findViewById(R.id.planTypeSpinner)
        saveButton = findViewById(R.id.savePlanButton)

        // Default date from intent or today
        selectedDate = intent.getStringExtra(EXTRA_PLAN_DATE) ?: DateTimeUtils.todayDate()
        dateText.text = selectedDate
        titleInput.setText(getString(R.string.plan_manual_default_title))
        titleInput.selectAll()

        // Setup type spinner
        typeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf(getString(R.string.plan_form_type_daily), getString(R.string.plan_form_type_weekly))
        )

        pickDateButton.setOnClickListener { showDatePicker() }
        pickStartTimeButton.setOnClickListener { showTimePicker(isStart = true) }
        clearStartTimeButton.setOnClickListener {
            startTime = null
            startTimeText.text = getString(R.string.plan_no_specific_time)
        }
        pickEndTimeButton.setOnClickListener { showTimePicker(isStart = false) }
        clearEndTimeButton.setOnClickListener {
            endTime = null
            endTimeText.text = getString(R.string.plan_no_specific_time)
        }

        startTimeText.text = getString(R.string.plan_no_specific_time)
        endTimeText.text = getString(R.string.plan_no_specific_time)

        saveButton.setOnClickListener { savePlan() }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parts = selectedDate.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } catch (_: Exception) { /* use today */ }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                dateText.text = selectedDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        val current = if (isStart) startTime else endTime
        if (current != null) {
            try {
                val parts = current.split(":")
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE, parts[1].toInt())
            } catch (_: Exception) { /* use now */ }
        }

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
                if (isStart) {
                    startTime = timeStr
                    startTimeText.text = timeStr
                } else {
                    endTime = timeStr
                    endTimeText.text = timeStr
                }
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun savePlan() {
        val title = titleInput.text?.toString()?.trim().orEmpty()
        val minutes = minutesInput.text?.toString()?.toIntOrNull() ?: 0

        if (title.isBlank()) {
            Snackbar.make(findViewById(android.R.id.content), R.string.plan_add_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (minutes <= 0) {
            Snackbar.make(findViewById(android.R.id.content), R.string.plan_add_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }

        val type = if (typeSpinner.selectedItemPosition == 0) StudyPlan.TYPE_DAILY else StudyPlan.TYPE_WEEKLY

        val plan = StudyPlan(
            title = title,
            planDate = selectedDate,
            plannedMinutes = minutes,
            startTime = startTime,
            endTime = endTime,
            type = type,
            status = StudyPlan.STATUS_PENDING,
            sourceType = StudyPlan.SOURCE_MANUAL
        )

        val planId = repository.addPlan(plan)
        if (planId > 0L) {
            Snackbar.make(findViewById(android.R.id.content), R.string.plan_add_success, Snackbar.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } else {
            Snackbar.make(findViewById(android.R.id.content), R.string.task_save_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Optional: pre-fill the plan date. */
        const val EXTRA_PLAN_DATE = "extra_plan_date"
    }
}