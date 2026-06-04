package com.example.campusmate.ui.plan

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.plan.LlmPlanGenerateService
import com.example.campusmate.domain.plan.StudyPlanGenerator
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PlanListFragment : Fragment(R.layout.fragment_plan_list) {
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var planGenerator: StudyPlanGenerator
    private lateinit var llmSettingsRepository: LlmSettingsRepository
    private lateinit var llmPlanGenerateService: LlmPlanGenerateService
    private lateinit var adapter: PlanAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var completionText: TextView
    private lateinit var progressBar: View
    private lateinit var weekDaySelector: LinearLayout
    private lateinit var generateTodayButton: MaterialButton
    private lateinit var generateWeekButton: MaterialButton

    private var selectedDate: String = DateTimeUtils.todayDate()
    private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private val llmPlanPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            if (data?.getBooleanExtra(LlmPlanPreviewActivity.EXTRA_USE_LOCAL_FALLBACK, false) == true) {
                val planDate = data.getStringExtra(LlmPlanPreviewActivity.EXTRA_PLAN_DATE) ?: selectedDate
                useLocalFallback(planDate)
            } else {
                loadPlans()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        planRepository = StudyPlanRepository(requireContext())
        planGenerator = StudyPlanGenerator(requireContext())
        llmSettingsRepository = LlmSettingsRepository(requireContext())
        llmPlanGenerateService = LlmPlanGenerateService(llmSettingsRepository, LlmClientFactory)

        recyclerView = view.findViewById(R.id.planRecyclerView)
        emptyStateView = view.findViewById(R.id.planEmptyState)
        summaryText = view.findViewById(R.id.planSummaryText)
        completionText = view.findViewById(R.id.planCompletionText)
        progressBar = view.findViewById(R.id.planProgressBar)
        weekDaySelector = view.findViewById(R.id.weekDaySelector)
        generateTodayButton = view.findViewById(R.id.generateTodayButton)
        generateWeekButton = view.findViewById(R.id.generateWeekButton)

        adapter = PlanAdapter(
            onPlanClick = { openDetail(it.id) },
            onToggleComplete = { togglePlanComplete(it) },
            onDeleteClick = { confirmDelete(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        generateTodayButton.setOnClickListener { handleGenerateToday() }
        generateWeekButton.setOnClickListener { handleGenerateWeek() }
        view.findViewById<MaterialButton>(R.id.planEmptyActionButton).setOnClickListener { handleGenerateToday() }
        view.findViewById<FloatingActionButton>(R.id.addPlanFab).setOnClickListener { showAddPlanDialog() }

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
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -(DateTimeUtils.currentWeekday() - 1))
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNumberFormat = SimpleDateFormat("d", Locale.US)

        for (i in 0..6) {
            val dayDate = dateFormat.format(calendar.time)
            val dayView = layoutInflater.inflate(R.layout.item_weekday_tab, weekDaySelector, false)
            val dayButton = dayView.findViewById<MaterialButton>(R.id.weekdayButton)
            val isSelected = dayDate == selectedDate
            val dayNumber = dayNumberFormat.format(calendar.time)
            dayButton.text = "$dayNumber\n${weekdayNames[i]}"
            dayButton.isChecked = isSelected
            dayButton.isSelected = isSelected
            dayButton.jumpDrawablesToCurrentState()
            dayButton.contentDescription = if (isSelected) {
                getString(R.string.plan_selected_date_content_description, weekdayNames[i], dayNumber)
            } else {
                getString(R.string.plan_date_content_description, weekdayNames[i], dayNumber)
            }
            dayButton.setOnClickListener {
                if (selectedDate != dayDate) {
                    selectedDate = dayDate
                    setupWeekDaySelector()
                    loadPlans()
                }
            }
            weekDaySelector.addView(dayView)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun loadPlans() {
        val plans = planRepository.getPlansByDate(selectedDate)
        val totalCount = plans.size
        val completedCount = plans.count { it.status == StudyPlan.STATUS_COMPLETED }
        val completionRate = if (totalCount > 0) (completedCount * 100 / totalCount) else 0

        summaryText.text = getString(R.string.plan_today_summary, totalCount, 0)
        completionText.text = getString(R.string.plan_completion_rate, completionRate)
        progressBar.post { (progressBar as android.widget.ProgressBar).progress = completionRate }
        adapter.submitList(plans.map { PlanListItem(it) })

        val isEmpty = plans.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun handleGenerateToday() {
        val hasExisting = planRepository.getPlansByDate(selectedDate).isNotEmpty()
        if (hasExisting) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.plan_generate_today)
                .setMessage(R.string.plan_regenerate_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_replace) { _, _ -> executeGenerateToday(true) }
                .show()
        } else {
            executeGenerateToday(false)
        }
    }

    private fun executeGenerateToday(hasExisting: Boolean) {
        if (llmPlanGenerateService.isAvailable() && llmSettingsRepository.hasApiKey()) {
            Snackbar.make(requireView(), R.string.plan_generate_using_ai, Snackbar.LENGTH_SHORT).show()
            launchLlmPlanPreview(selectedDate, hasExisting)
        } else {
            if (!llmSettingsRepository.hasApiKey()) {
                Snackbar.make(requireView(), R.string.plan_local_generation_tip, Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(requireView(), R.string.plan_generate_fallback_to_local, Snackbar.LENGTH_SHORT).show()
            }
            showLoadingHintAndGenerate { generateLocalTodayPlan() }
        }
    }

    private fun handleGenerateWeek() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.plan_generate_week)
            .setMessage(R.string.plan_regenerate_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_replace) { _, _ -> executeGenerateWeek() }
            .show()
    }

    private fun executeGenerateWeek() {
        if (llmPlanGenerateService.isAvailable() && llmSettingsRepository.hasApiKey()) {
            Snackbar.make(requireView(), R.string.plan_generate_using_ai, Snackbar.LENGTH_LONG).show()
            launchLlmWeekPlanPreview()
        } else {
            if (!llmSettingsRepository.hasApiKey()) {
                Snackbar.make(requireView(), R.string.plan_local_generation_tip, Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(requireView(), R.string.plan_generate_fallback_to_local, Snackbar.LENGTH_SHORT).show()
            }
            showLoadingHintAndGenerate { generateLocalWeekPlan() }
        }
    }


    private fun launchLlmWeekPlanPreview() {
        val intent = Intent(requireContext(), LlmWeekPlanPreviewActivity::class.java)
        startActivity(intent)
    }

    private fun showLoadingHintAndGenerate(action: () -> Unit) {
        Snackbar.make(requireView(), getString(R.string.plan_generating_loading), Snackbar.LENGTH_SHORT).show()
        action()
    }


    private fun generateLlmWeekPlan() {
        lifecycleScope.launch {
            try {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, -(DateTimeUtils.currentWeekday() - 1))
                }
                var totalPlans = 0
                var totalMinutes = 0
                for (day in 0..6) {
                    val dayDate = DateTimeUtils.formatDate(calendar.timeInMillis)
                    planRepository.deletePlansByDate(dayDate)
                    launchLlmPlanPreviewSync(dayDate, false)
                    val plans = planRepository.getPlansByDate(dayDate)
                    totalPlans += plans.size
                    totalMinutes += plans.sumOf { it.plannedMinutes }
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                Snackbar.make(requireView(), getString(R.string.plan_generate_success, "已生成本周 $totalPlans 项计划，共 $totalMinutes 分钟"), Snackbar.LENGTH_LONG).show()
                loadPlans()
                setupWeekDaySelector()
            } catch (e: Exception) {
                Snackbar.make(requireView(), R.string.llm_plan_parse_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun launchLlmPlanPreview(planDate: String, hasExisting: Boolean) {
        val intent = Intent(requireContext(), LlmPlanPreviewActivity::class.java).apply {
            putExtra(LlmPlanPreviewActivity.EXTRA_PLAN_DATE, planDate)
            putExtra(LlmPlanPreviewActivity.EXTRA_HAS_EXISTING_PLANS, hasExisting)
        }
        llmPlanPreviewLauncher.launch(intent)
    }

    private fun launchLlmPlanPreviewSync(planDate: String, hasExisting: Boolean) {
        val intent = Intent(requireContext(), LlmPlanPreviewActivity::class.java).apply {
            putExtra(LlmPlanPreviewActivity.EXTRA_PLAN_DATE, planDate)
            putExtra(LlmPlanPreviewActivity.EXTRA_HAS_EXISTING_PLANS, hasExisting)
        }
        startActivity(intent)
    }

    private fun generateLocalTodayPlan() {
        if (hasExistingPlansForDate(selectedDate)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.plan_generate_today)
                .setMessage(R.string.plan_regenerate_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_replace) { _, _ ->
                    planRepository.deletePlansByDate(selectedDate)
                    showGenerationResult(planGenerator.generateDailyPlan())
                }
                .show()
        } else {
            showGenerationResult(planGenerator.generateDailyPlan())
        }
    }

    private fun hasExistingPlansForDate(date: String): Boolean = planRepository.getPlansByDate(date).isNotEmpty()

    private fun generateLocalWeekPlan() {
        showGenerationResult(planGenerator.generateWeeklyPlan())
        loadPlans()
        setupWeekDaySelector()
    }

    private fun showGenerationResult(result: StudyPlanGenerator.PlanGenerationResult) {
        if (result.success) {
            Snackbar.make(requireView(), getString(R.string.plan_generate_success, result.message), Snackbar.LENGTH_LONG).show()
        } else {
            Snackbar.make(requireView(), getString(R.string.plan_generate_failed, result.message), Snackbar.LENGTH_LONG).show()
        }
        loadPlans()
        setupWeekDaySelector()
    }

    private fun useLocalFallback(planDate: String) {
        selectedDate = planDate
        planRepository.deletePlansByDate(planDate)
        showGenerationResult(planGenerator.generateDailyPlan())
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
        val newStatus = if (plan.status == StudyPlan.STATUS_COMPLETED) StudyPlan.STATUS_PENDING else StudyPlan.STATUS_COMPLETED
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
        startActivity(Intent(requireContext(), PlanDetailActivity::class.java).putExtra(PlanDetailActivity.EXTRA_PLAN_ID, planId))
    }

    companion object {
        private const val DEFAULT_MANUAL_PLAN_MINUTES = 60
    }
}
