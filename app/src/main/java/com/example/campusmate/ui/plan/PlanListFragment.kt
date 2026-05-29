package com.example.campusmate.ui.plan

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.domain.plan.StudyPlanGenerator
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PlanListFragment : Fragment(R.layout.fragment_plan_list) {
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var planGenerator: StudyPlanGenerator
    private lateinit var adapter: PlanAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var completionText: TextView
    private lateinit var weekDaySelector: LinearLayout

    private var selectedDate: String = DateTimeUtils.todayDate()
    private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        planRepository = StudyPlanRepository(requireContext())
        planGenerator = StudyPlanGenerator(requireContext())

        recyclerView = view.findViewById(R.id.planRecyclerView)
        emptyStateView = view.findViewById(R.id.planEmptyState)
        summaryText = view.findViewById(R.id.planSummaryText)
        completionText = view.findViewById(R.id.planCompletionText)
        weekDaySelector = view.findViewById(R.id.weekDaySelector)

        adapter = PlanAdapter(
            onPlanClick = { openDetail(it.id) },
            onToggleComplete = { togglePlanComplete(it) },
            onDeleteClick = { confirmDelete(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton>(R.id.generateTodayButton).setOnClickListener {
            generateTodayPlan()
        }

        view.findViewById<MaterialButton>(R.id.generateWeekButton).setOnClickListener {
            generateWeekPlan()
        }
        view.findViewById<MaterialButton>(R.id.generateByCourseButton).setOnClickListener {
            Snackbar.make(requireView(), R.string.placeholder_next_stage, Snackbar.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.generateByExamButton).setOnClickListener {
            Snackbar.make(requireView(), R.string.placeholder_next_stage, Snackbar.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.planEmptyActionButton).setOnClickListener {
            generateTodayPlan()
        }
        view.findViewById<FloatingActionButton>(R.id.addPlanFab).setOnClickListener {
            showAddPlanDialog()
        }
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.planGenerateHeader,
            contentId = R.id.planGenerateContent,
            indicatorId = R.id.planGenerateIndicator
        )

        setupWeekDaySelector()
        loadPlans()
    }

    override fun onResume() {
        super.onResume()
        loadPlans()
    }

    private fun setupWeekDaySelector() {
        weekDaySelector.removeAllViews()
        val calendar = Calendar.getInstance()
        val todayWeekday = DateTimeUtils.currentWeekday()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -(todayWeekday - 1))

        for (i in 0..6) {
            val dayDate = dateFormat.format(calendar.time)
            val dayOfWeek = weekdayNames[i]
            val dayNumber = SimpleDateFormat("d", Locale.US).format(calendar.time)

            val dayView = layoutInflater.inflate(R.layout.item_weekday_tab, weekDaySelector, false)
            val dayButton = dayView.findViewById<MaterialButton>(R.id.weekdayButton)
            dayButton.text = "$dayOfWeek\n$dayNumber"
            dayButton.isChecked = dayDate == selectedDate

            dayButton.setOnClickListener {
                selectedDate = dayDate
                setupWeekDaySelector()
                loadPlans()
            }

            weekDaySelector.addView(dayView)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun loadPlans() {
        val plans = planRepository.getPlansByDate(selectedDate)
        val totalMinutes = plans.sumOf { it.plannedMinutes }
        val completedCount = plans.count { it.status == StudyPlan.STATUS_COMPLETED }
        val totalCount = plans.size

        summaryText.text = getString(R.string.plan_today_summary, totalCount, totalMinutes)
        completionText.text = if (totalCount > 0) {
            val rate = (completedCount * 100) / totalCount
            getString(R.string.plan_completion_rate, rate)
        } else {
            ""
        }

        adapter.submitList(plans.map { PlanListItem(it) })

        val isEmpty = plans.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun generateTodayPlan() {
        val result = planGenerator.generateDailyPlan()

        if (result.success) {
            Snackbar.make(
                requireView(),
                getString(R.string.plan_generate_success, result.message),
                Snackbar.LENGTH_LONG
            ).show()
            loadPlans()
            setupWeekDaySelector()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.plan_title)
                .setMessage(result.message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_clear) { _, _ ->
                    planRepository.deletePlansByDate(selectedDate)
                    val retryResult = planGenerator.generateDailyPlan()
                    Snackbar.make(
                        requireView(),
                        getString(R.string.plan_generate_success, retryResult.message),
                        Snackbar.LENGTH_LONG
                    ).show()
                    loadPlans()
                    setupWeekDaySelector()
                }
                .show()
        }
    }

    private fun generateWeekPlan() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.plan_generate_week)
            .setMessage(R.string.plan_regenerate_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_select) { _, _ ->
                val result = planGenerator.generateWeeklyPlan()
                Snackbar.make(
                    requireView(),
                    getString(R.string.plan_generate_success, result.message),
                    Snackbar.LENGTH_LONG
                ).show()
                loadPlans()
                setupWeekDaySelector()
            }
            .show()
    }

    private fun showAddPlanDialog() {
        val context = requireContext()
        val sidePadding = resources.getDimensionPixelSize(R.dimen.space_m)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sidePadding, 0, sidePadding, 0)
        }
        val titleInput = TextInputEditText(context).apply {
            setText(R.string.plan_manual_default_title)
            selectAll()
        }
        val titleLayout = TextInputLayout(context).apply {
            hint = getString(R.string.plan_title)
            addView(titleInput)
        }
        val minutesInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(DEFAULT_MANUAL_PLAN_MINUTES.toString())
        }
        val minutesLayout = TextInputLayout(context).apply {
            hint = getString(R.string.plan_planned_minutes)
            addView(minutesInput)
        }
        container.addView(titleLayout)
        container.addView(minutesLayout)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.plan_add_title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val minutes = minutesInput.text?.toString()?.toIntOrNull() ?: 0
                if (title.isBlank() || minutes <= 0) {
                    Snackbar.make(requireView(), R.string.plan_add_invalid, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val planId = planRepository.addPlan(
                    StudyPlan(
                        title = title,
                        planDate = selectedDate,
                        plannedMinutes = minutes,
                        sourceType = StudyPlan.SOURCE_MANUAL
                    )
                )
                if (planId > 0L) {
                    Snackbar.make(requireView(), R.string.plan_add_success, Snackbar.LENGTH_SHORT).show()
                    loadPlans()
                } else {
                    Snackbar.make(requireView(), R.string.task_save_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun togglePlanComplete(plan: StudyPlan) {
        val newStatus = if (plan.status == StudyPlan.STATUS_COMPLETED) {
            StudyPlan.STATUS_PENDING
        } else {
            StudyPlan.STATUS_COMPLETED
        }
        if (planRepository.updatePlanStatus(plan.id, newStatus)) {
            loadPlans()
        } else {
            Snackbar.make(requireView(), R.string.task_status_update_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(plan: StudyPlan) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(R.string.plan_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (planRepository.deletePlan(plan.id)) {
                    Snackbar.make(requireView(), R.string.task_delete_success, Snackbar.LENGTH_SHORT).show()
                    loadPlans()
                } else {
                    Snackbar.make(requireView(), R.string.task_delete_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun openDetail(planId: Long) {
        startActivity(
            Intent(requireContext(), PlanDetailActivity::class.java)
                .putExtra(PlanDetailActivity.EXTRA_PLAN_ID, planId)
        )
    }

    companion object {
        private const val DEFAULT_MANUAL_PLAN_MINUTES = 60
    }
}
