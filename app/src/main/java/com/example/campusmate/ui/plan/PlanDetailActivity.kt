package com.example.campusmate.ui.plan

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.StudyPlanRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlanDetailActivity : AppCompatActivity() {
    private lateinit var planRepository: StudyPlanRepository
    private var planId: Long = 0L
    private var currentPlan: StudyPlan? = null

    private val editPlanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadPlan()
        }
    }

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
        lifecycleScope.launch {
            val plan = withContext(Dispatchers.IO) { planRepository.getPlanById(planId) }
            currentPlan = plan
            if (plan == null) {
                finish()
                return@launch
            }

            findViewById<TextView>(R.id.planDetailTitle).text = plan.title
            findViewById<TextView>(R.id.planDetailPlanned).text =
                getString(R.string.plan_item_duration_format, plan.plannedMinutes)
            findViewById<TextView>(R.id.planDetailActual).text =
                getString(R.string.plan_item_duration_format, plan.actualMinutes)
            findViewById<TextView>(R.id.planDetailTime).text = if (plan.startTime != null && plan.endTime != null) {
                "${plan.planDate} ${getString(R.string.plan_item_time_format, plan.startTime, plan.endTime)}"
            } else {
                plan.planDate
            }
            findViewById<TextView>(R.id.planDetailStatus).text = when (plan.status) {
                StudyPlan.STATUS_PENDING -> getString(R.string.plan_status_pending)
                StudyPlan.STATUS_COMPLETED -> getString(R.string.plan_status_completed)
                StudyPlan.STATUS_EXPIRED -> getString(R.string.plan_status_expired)
                else -> ""
            }
            findViewById<TextView>(R.id.planDetailSource).text = when (plan.sourceType) {
                StudyPlan.SOURCE_LLM -> getString(R.string.plan_source_llm)
                StudyPlan.SOURCE_MANUAL -> getString(R.string.plan_source_manual)
                else -> getString(R.string.plan_source_auto)
            }
            findViewById<MaterialButton>(R.id.markCompleteButton).text = if (plan.status == StudyPlan.STATUS_COMPLETED) {
                getString(R.string.plan_mark_incomplete)
            } else {
                getString(R.string.plan_mark_complete)
            }
        }
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.markCompleteButton).setOnClickListener {
            toggleComplete()
        }
        findViewById<MaterialButton>(R.id.editButton).setOnClickListener {
            openEdit()
        }
        findViewById<MaterialButton>(R.id.deleteButton).setOnClickListener {
            confirmDelete()
        }
    }

    private fun openEdit() {
        val plan = currentPlan ?: return
        val intent = Intent(this, PlanEditActivity::class.java).apply {
            putExtra(PlanEditActivity.EXTRA_PLAN_ID, plan.id)
            putExtra(PlanEditActivity.EXTRA_PLAN_DATE, plan.planDate)
        }
        editPlanLauncher.launch(intent)
    }

    private fun toggleComplete() {
        val plan = currentPlan ?: return
        val newStatus = if (plan.status == StudyPlan.STATUS_COMPLETED) {
            StudyPlan.STATUS_PENDING
        } else {
            StudyPlan.STATUS_COMPLETED
        }
        lifecycleScope.launch {
            if (withContext(Dispatchers.IO) { planRepository.updatePlanStatus(plan.id, newStatus) }) {
                loadPlan()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    if (newStatus == StudyPlan.STATUS_COMPLETED) R.string.plan_mark_complete else R.string.plan_mark_incomplete,
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                Snackbar.make(findViewById(android.R.id.content), R.string.plan_status_update_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(R.string.plan_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val plan = currentPlan ?: return@setPositiveButton
                lifecycleScope.launch {
                    if (withContext(Dispatchers.IO) { planRepository.deletePlan(plan.id) }) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.plan_delete_success, Snackbar.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), R.string.plan_delete_failed, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
}
