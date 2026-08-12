package com.example.campusmate.ui.plan

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
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
import com.example.campusmate.ui.course.CourseUiFormatter
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
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
    private lateinit var weekDaySelector: LinearLayout
    private lateinit var progressBar: android.widget.ProgressBar

    private var selectedDate: String = DateTimeUtils.todayDate()

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

    private val planEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            setupWeekDaySelector()
            loadPlans()
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

        adapter = PlanAdapter(
            onPlanClick = { openDetail(it.id) },
            onToggleComplete = { togglePlanComplete(it) },
            onEditClick = { openPlanEdit(it.id) },
            onDeleteClick = { confirmDelete(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<MaterialButton>(R.id.planEmptyActionButton).setOnClickListener {
            openPlanEdit()
        }

        view.findViewById<FloatingActionButton>(R.id.addPlanFab).setOnClickListener {
            showPlanActionMenu(it)
        }

        setupWeekDaySelector()
        loadPlans()
    }

    override fun onResume() {
        super.onResume()
        setupWeekDaySelector()
        loadPlans()
    }

    private fun setupWeekDaySelector() {
        val selectableDates = buildSelectableDates()
        if (selectedDate !in selectableDates) {
            selectedDate = selectableDates.first()
        }
        weekDaySelector.removeAllViews()
        selectableDates.forEach { date ->
            val pill = layoutInflater.inflate(R.layout.item_selection_pill, weekDaySelector, false) as TextView
            pill.text = formatDatePillLabel(date)
            pill.tag = date
            pill.isSelected = date == selectedDate
            pill.setOnClickListener {
                if (selectedDate == date) return@setOnClickListener
                selectedDate = date
                updateDatePillSelection()
                loadPlans()
            }
            weekDaySelector.addView(pill)
        }
    }

    private fun updateDatePillSelection() {
        for (index in 0 until weekDaySelector.childCount) {
            val child = weekDaySelector.getChildAt(index)
            child.isSelected = child.tag == selectedDate
        }
    }

    private fun buildSelectableDates(): List<String> {
        val today = DateTimeUtils.todayDate()
        return buildList {
            add(today)
            for (offset in 1..7) {
                add(DateTimeUtils.datePlusDays(today, offset))
            }
        }
    }

    private fun formatDatePillLabel(date: String): String {
        if (date == DateTimeUtils.todayDate()) {
            return getString(R.string.plan_date_today)
        }
        val millis = DateTimeUtils.parseDateMillis(date) ?: return date
        val monthDay = SimpleDateFormat("M/d", Locale.CHINA).format(Date(millis))
        val weekday = CourseUiFormatter.weekdayLabel(requireContext(), DateTimeUtils.weekdayForDate(date))
        return getString(R.string.plan_date_label_format, monthDay, weekday)
    }

    private fun loadPlans() {
        val date = selectedDate
        viewLifecycleOwner.lifecycleScope.launch {
            val plans = withContext(Dispatchers.IO) { planRepository.getPlansByDate(date) }
            if (!isAdded || selectedDate != date) return@launch
            bindPlans(plans, date)
        }
    }

    private fun bindPlans(plans: List<StudyPlan>, date: String) {
        val totalMinutes = plans.sumOf { it.plannedMinutes }
        val completedCount = plans.count { it.status == StudyPlan.STATUS_COMPLETED }
        val totalCount = plans.size

        summaryText.text = if (date == DateTimeUtils.todayDate()) {
            getString(R.string.plan_today_summary, totalCount, totalMinutes)
        } else {
            getString(R.string.plan_day_summary, formatDatePillLabel(date), totalCount, totalMinutes)
        }
        completionText.text = if (totalCount > 0) {
            val rate = (completedCount * 100) / totalCount
            getString(R.string.plan_completion_rate, rate)
        } else {
            ""
        }
        progressBar.progress = if (totalCount > 0) {
            (completedCount * 100) / totalCount
        } else {
            0
        }

        adapter.submitList(plans.map { PlanListItem(it) })

        val isEmpty = plans.isEmpty()
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun handleGenerateToday() {
        val date = selectedDate
        viewLifecycleOwner.lifecycleScope.launch {
            val hasExisting = withContext(Dispatchers.IO) { planRepository.getPlansByDate(date).isNotEmpty() }
            if (!isAdded || selectedDate != date) return@launch
            if (hasExisting) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.plan_generate_today)
                    .setMessage(R.string.plan_regenerate_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_replace) { _, _ ->
                        executeGenerateToday(hasExisting = true)
                    }
                    .show()
            } else {
                executeGenerateToday(hasExisting = false)
            }
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
            generateLocalTodayPlan(replaceExisting = hasExisting)
        }
    }

    private fun handleGenerateWeek() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.plan_generate_week)
            .setMessage(R.string.plan_regenerate_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_replace) { _, _ ->
                executeGenerateWeek()
            }
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
            generateLocalWeekPlan()
        }
    }

    private fun launchLlmWeekPlanPreview() {
        val intent = Intent(requireContext(), LlmWeekPlanPreviewActivity::class.java)
        startActivity(intent)
    }

    private fun launchLlmPlanPreview(planDate: String, hasExisting: Boolean) {
        val intent = Intent(requireContext(), LlmPlanPreviewActivity::class.java).apply {
            putExtra(LlmPlanPreviewActivity.EXTRA_PLAN_DATE, planDate)
            putExtra(LlmPlanPreviewActivity.EXTRA_HAS_EXISTING_PLANS, hasExisting)
        }
        llmPlanPreviewLauncher.launch(intent)
    }

    private fun generateLocalTodayPlan(replaceExisting: Boolean = false) {
        val date = selectedDate
        viewLifecycleOwner.lifecycleScope.launch {
            val hasExisting = withContext(Dispatchers.IO) { planRepository.getPlansByDate(date).isNotEmpty() }
            if (!isAdded || selectedDate != date) return@launch
            if (hasExisting && !replaceExisting) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.plan_generate_today)
                    .setMessage(R.string.plan_regenerate_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_replace) { _, _ ->
                        generateLocalTodayPlan(replaceExisting = true)
                    }
                    .show()
            } else {
                val result = withContext(Dispatchers.IO) {
                    if (replaceExisting) planRepository.deletePlansByDate(date)
                    planGenerator.generateDailyPlan(date)
                }
                showGenerationResult(result)
            }
        }
    }

    private fun generateLocalWeekPlan() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { planGenerator.generateWeeklyPlan() }
            if (!isAdded) return@launch
            showGenerationResult(result)
            loadPlans()
            setupWeekDaySelector()
        }
    }

    private fun showGenerationResult(result: StudyPlanGenerator.PlanGenerationResult) {
        if (result.success) {
            Snackbar.make(
                requireView(),
                getString(R.string.plan_generate_success, result.message),
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            Snackbar.make(
                requireView(),
                getString(R.string.plan_generate_failed, result.message),
                Snackbar.LENGTH_LONG
            ).show()
        }
        loadPlans()
        setupWeekDaySelector()
    }

    private fun useLocalFallback(planDate: String) {
        selectedDate = planDate
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                planRepository.deletePlansByDate(planDate)
                planGenerator.generateDailyPlan(planDate)
            }
            if (!isAdded) return@launch
            showGenerationResult(result)
        }
    }

    private fun showPlanActionMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, MENU_ADD_MANUAL, 0, R.string.plan_add_title)
            menu.add(0, MENU_GENERATE_TODAY, 1, R.string.plan_generate_today)
            menu.add(0, MENU_GENERATE_WEEK, 2, R.string.plan_generate_week)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_ADD_MANUAL -> openPlanEdit()
                    MENU_GENERATE_TODAY -> handleGenerateToday()
                    MENU_GENERATE_WEEK -> handleGenerateWeek()
                }
                true
            }
            show()
        }
    }

    private fun openPlanEdit(planId: Long? = null) {
        val intent = Intent(requireContext(), PlanEditActivity::class.java).apply {
            putExtra(PlanEditActivity.EXTRA_PLAN_DATE, selectedDate)
            planId?.let { putExtra(PlanEditActivity.EXTRA_PLAN_ID, it) }
        }
        planEditLauncher.launch(intent)
    }

    private fun togglePlanComplete(plan: StudyPlan) {
        val newStatus = if (plan.status == StudyPlan.STATUS_COMPLETED) {
            StudyPlan.STATUS_PENDING
        } else {
            StudyPlan.STATUS_COMPLETED
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) { planRepository.updatePlanStatus(plan.id, newStatus) }
            if (!isAdded) return@launch
            if (updated) loadPlans() else Snackbar.make(requireView(), R.string.plan_status_update_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(plan: StudyPlan) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(R.string.plan_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = withContext(Dispatchers.IO) { planRepository.deletePlan(plan.id) }
                    if (!isAdded) return@launch
                    if (deleted) {
                        Snackbar.make(requireView(), R.string.plan_delete_success, Snackbar.LENGTH_SHORT).show()
                        loadPlans()
                    } else {
                        Snackbar.make(requireView(), R.string.plan_delete_failed, Snackbar.LENGTH_SHORT).show()
                    }
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
        private const val MENU_ADD_MANUAL = 1
        private const val MENU_GENERATE_TODAY = 2
        private const val MENU_GENERATE_WEEK = 3
    }
}
