package com.example.campusmate.ui.plan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.StudyPlanRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class PlanDetailActivity : AppCompatActivity() {
    private lateinit var planRepository: StudyPlanRepository
    private var planId: Long = 0L
    private var currentPlan: StudyPlan? = null

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_detail)

        planRepository = StudyPlanRepository(this)
        planId = intent.getLongExtra(EXTRA_PLAN_ID, 0L)
        setupToolbar()

        if (planId == 0L) {
            finish()
            return
        }

        loadPlan()
        setupButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.planDetailToolbar)
        toolbar.title = getString(R.string.plan_detail_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadPlan() {
        currentPlan = planRepository.getPlanById(planId)
        currentPlan?.let { plan ->
            val titleView = findViewById<android.widget.TextView>(R.id.planDetailTitle)
            val plannedView = findViewById<android.widget.TextView>(R.id.planDetailPlanned)
            val actualView = findViewById<android.widget.TextView>(R.id.planDetailActual)
            val timeView = findViewById<android.widget.TextView>(R.id.planDetailTime)
            val statusView = findViewById<android.widget.TextView>(R.id.planDetailStatus)
            val sourceView = findViewById<android.widget.TextView>(R.id.planDetailSource)
            val completeButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.markCompleteButton)

            titleView.text = plan.title
            plannedView.text = getString(R.string.plan_item_duration_format, plan.plannedMinutes)
            actualView.text = getString(R.string.plan_item_duration_format, plan.actualMinutes)

            val timeText = if (plan.startTime != null && plan.endTime != null) {
                "${plan.planDate} ${plan.startTime} - ${plan.endTime}"
            } else {
                plan.planDate
            }
            timeView.text = timeText

            val statusText = when (plan.status) {
                StudyPlan.STATUS_PENDING -> getString(R.string.plan_status_pending)
                StudyPlan.STATUS_COMPLETED -> getString(R.string.plan_status_completed)
                StudyPlan.STATUS_EXPIRED -> getString(R.string.plan_status_expired)
                else -> ""
            }
            statusView.text = statusText

            val sourceText = if (plan.sourceType == StudyPlan.SOURCE_AUTO) {
                getString(R.string.plan_source_auto)
            } else {
                getString(R.string.plan_source_manual)
            }
            sourceView.text = sourceText

            if (plan.status == StudyPlan.STATUS_COMPLETED) {
                completeButton.text = getString(R.string.plan_mark_incomplete)
            } else {
                completeButton.text = getString(R.string.plan_mark_complete)
            }

            supportActionBar?.title = getString(R.string.plan_detail_title)
        } ?: run {
            finish()
        }
    }

    private fun setupButtons() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.markCompleteButton).setOnClickListener {
            toggleComplete()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.deleteButton).setOnClickListener {
            confirmDelete()
        }
    }

    private fun toggleComplete() {
        currentPlan?.let { plan ->
            val newStatus = if (plan.status == StudyPlan.STATUS_COMPLETED) {
                StudyPlan.STATUS_PENDING
            } else {
                StudyPlan.STATUS_COMPLETED
            }
            if (planRepository.updatePlanStatus(plan.id, newStatus)) {
                loadPlan()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    if (newStatus == StudyPlan.STATUS_COMPLETED) R.string.plan_mark_complete else R.string.plan_mark_incomplete,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(R.string.plan_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                currentPlan?.let { plan ->
                    if (planRepository.deletePlan(plan.id)) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.task_delete_success,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
            .show()
    }
}
