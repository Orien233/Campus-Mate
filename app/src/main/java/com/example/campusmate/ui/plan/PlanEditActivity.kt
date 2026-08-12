package com.example.campusmate.ui.plan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Form-based Activity for manually adding and editing a study plan.
 * Fields mirror those produced by AI plan generation: title, date,
 * planned minutes, optional start/end time, and type (daily/weekly).
 */
class PlanEditActivity : AppCompatActivity() {

    private lateinit var repository: StudyPlanRepository
    private lateinit var rootView: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleInput: TextInputEditText
    private lateinit var dateText: TextView
    private lateinit var minutesInput: TextInputEditText
    private lateinit var startTimeText: TextView
    private lateinit var clearStartTimeButton: MaterialButton
    private lateinit var endTimeText: TextView
    private lateinit var clearEndTimeButton: MaterialButton
    private lateinit var typeValueText: TextView
    private lateinit var saveButton: MaterialButton

    private var editingPlanId: Long = 0L
    private var editingPlan: StudyPlan? = null
    private var selectedDate: String = DateTimeUtils.todayDate()
    private var startTime: String? = null
    private var endTime: String? = null
    private var selectedType: Int = StudyPlan.TYPE_DAILY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_edit)

        repository = StudyPlanRepository(this)
        editingPlanId = intent.getLongExtra(EXTRA_PLAN_ID, 0L)

        bindViews()
        setupToolbar()
        setupSelectionRows()

        selectedDate = intent.getStringExtra(EXTRA_PLAN_DATE) ?: DateTimeUtils.todayDate()
        titleInput.setText(getString(R.string.plan_manual_default_title))
        titleInput.selectAll()
        dateText.text = selectedDate
        startTimeText.text = getString(R.string.plan_no_specific_time)
        endTimeText.text = getString(R.string.plan_no_specific_time)
        updateTypeLabel()

        if (editingPlanId > 0L) {
            saveButton.isEnabled = false
            bindExistingPlan()
        } else {
            supportActionBar?.title = getString(R.string.plan_add_title)
        }

        saveButton.setOnClickListener { savePlan() }
    }

    private fun bindViews() {
        rootView = findViewById(R.id.planEditRoot)
        toolbar = findViewById(R.id.planEditToolbar)
        titleInput = findViewById(R.id.planTitleInput)
        dateText = findViewById(R.id.planDateText)
        minutesInput = findViewById(R.id.planMinutesInput)
        startTimeText = findViewById(R.id.planStartTimeText)
        clearStartTimeButton = findViewById(R.id.clearStartTimeButton)
        endTimeText = findViewById(R.id.planEndTimeText)
        clearEndTimeButton = findViewById(R.id.clearEndTimeButton)
        typeValueText = findViewById(R.id.planTypeValueText)
        saveButton = findViewById(R.id.savePlanButton)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSelectionRows() {
        findViewById<View>(R.id.pickDateRow).setOnClickListener { showDatePicker() }
        findViewById<View>(R.id.pickStartTimeRow).setOnClickListener { showTimePicker(isStart = true) }
        clearStartTimeButton.setOnClickListener {
            startTime = null
            startTimeText.text = getString(R.string.plan_no_specific_time)
        }
        findViewById<View>(R.id.pickEndTimeRow).setOnClickListener { showTimePicker(isStart = false) }
        clearEndTimeButton.setOnClickListener {
            endTime = null
            endTimeText.text = getString(R.string.plan_no_specific_time)
        }
        findViewById<View>(R.id.planTypeRow).setOnClickListener { showTypePicker() }
    }

    private fun bindExistingPlan() {
        lifecycleScope.launch {
            val existingPlan = withContext(Dispatchers.IO) { repository.getPlanById(editingPlanId) }
            if (existingPlan == null) {
                finish()
                return@launch
            }
            editingPlan = existingPlan
            supportActionBar?.title = getString(R.string.plan_edit_title)
            titleInput.setText(existingPlan.title)
            minutesInput.setText(existingPlan.plannedMinutes.toString())
            selectedDate = existingPlan.planDate
            dateText.text = selectedDate
            startTime = existingPlan.startTime
            endTime = existingPlan.endTime
            startTimeText.text = startTime ?: getString(R.string.plan_no_specific_time)
            endTimeText.text = endTime ?: getString(R.string.plan_no_specific_time)
            selectedType = existingPlan.type
            updateTypeLabel()
            saveButton.isEnabled = true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parts = selectedDate.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } catch (_: Exception) {
            // Keep current calendar value.
        }

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
            } catch (_: Exception) {
                // Keep current calendar value.
            }
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

    private fun showTypePicker() {
        val labels = arrayOf(
            getString(R.string.plan_form_type_daily),
            getString(R.string.plan_form_type_weekly)
        )
        val checkedItem = if (selectedType == StudyPlan.TYPE_WEEKLY) 1 else 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.plan_form_type_label)
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                selectedType = if (which == 0) StudyPlan.TYPE_DAILY else StudyPlan.TYPE_WEEKLY
                updateTypeLabel()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateTypeLabel() {
        typeValueText.text = if (selectedType == StudyPlan.TYPE_WEEKLY) {
            getString(R.string.plan_form_type_weekly)
        } else {
            getString(R.string.plan_form_type_daily)
        }
    }

    private fun savePlan() {
        val title = titleInput.text?.toString()?.trim().orEmpty()
        val minutes = minutesInput.text?.toString()?.toIntOrNull() ?: 0

        if (title.isBlank() || minutes <= 0) {
            Snackbar.make(rootView, R.string.plan_add_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }

        val existingPlan = editingPlan
        val plan = StudyPlan(
            id = existingPlan?.id ?: 0L,
            title = title,
            planDate = selectedDate,
            plannedMinutes = minutes,
            actualMinutes = existingPlan?.actualMinutes ?: 0,
            startTime = startTime,
            endTime = endTime,
            type = selectedType,
            status = existingPlan?.status ?: StudyPlan.STATUS_PENDING,
            sourceType = existingPlan?.sourceType ?: StudyPlan.SOURCE_MANUAL,
            createdAt = existingPlan?.createdAt ?: 0L,
            updatedAt = existingPlan?.updatedAt ?: 0L
        )

        saveButton.isEnabled = false
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (plan.id > 0L) repository.updatePlan(plan) else repository.addPlan(plan) > 0L
            }
            if (success) {
                Snackbar.make(rootView, R.string.plan_add_success, Snackbar.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                saveButton.isEnabled = true
                Snackbar.make(rootView, R.string.plan_save_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_PLAN_DATE = "extra_plan_date"
        const val EXTRA_PLAN_ID = "extra_plan_id"
    }
}
