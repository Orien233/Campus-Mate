package com.example.campusmate.ui.plan

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.plan.LlmPlanGenerateService
import com.example.campusmate.domain.plan.LlmPlanValidator
import com.example.campusmate.domain.plan.PlanCourseConflictChecker
import com.example.campusmate.domain.plan.StudyPlanContextBuilder
import com.example.campusmate.domain.plan.StudyPlanGenerator
import com.example.campusmate.ui.course.CourseUiFormatter
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

class LlmWeekPlanPreviewActivity : AppCompatActivity() {

    private lateinit var llmSettingsRepository: LlmSettingsRepository
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var llmPlanGenerateService: LlmPlanGenerateService
    private lateinit var planValidator: LlmPlanValidator
    private lateinit var planContextBuilder: StudyPlanContextBuilder
    private lateinit var localPlanGenerator: StudyPlanGenerator

    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var dayEmptyState: LinearLayout
    private lateinit var weekSummaryText: TextView
    private lateinit var weekWarningText: TextView
    private lateinit var weekCourseSummaryText: TextView
    private lateinit var weekCourseConflictStatusText: TextView
    private lateinit var errorText: TextView
    private lateinit var daySelectorContainer: LinearLayout
    private lateinit var weekPlanRecyclerView: RecyclerView
    private lateinit var cancelButton: MaterialButton
    private lateinit var confirmAllButton: MaterialButton
    private lateinit var retryButton: MaterialButton
    private lateinit var fallbackLocalButton: MaterialButton
    private lateinit var localGenerationTip: MaterialCardView
    private lateinit var closeTipButton: android.widget.ImageView

    private lateinit var adapter: WeekPlanAdapter

    private var allWeekPlans: LinkedHashMap<String, MutableList<WeekPlanItem>> = linkedMapOf()
    private var selectedDayDate: String = DateTimeUtils.todayDate()
    private var warnings: List<String> = emptyList()
    private var usedLocalGeneration: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_week_plan_preview)

        initViews()
        initDependencies()
        setupDaySelector()
        generateWeekPlan()
    }

    private fun initViews() {
        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        contentContainer = findViewById(R.id.contentContainer)
        dayEmptyState = findViewById(R.id.dayEmptyState)
        weekSummaryText = findViewById(R.id.weekSummaryText)
        weekWarningText = findViewById(R.id.weekWarningText)
        weekCourseSummaryText = findViewById(R.id.weekCourseSummaryText)
        weekCourseConflictStatusText = findViewById(R.id.weekCourseConflictStatusText)
        errorText = findViewById(R.id.errorText)
        daySelectorContainer = findViewById(R.id.daySelectorContainer)
        weekPlanRecyclerView = findViewById(R.id.weekPlanRecyclerView)
        cancelButton = findViewById(R.id.cancelButton)
        confirmAllButton = findViewById(R.id.confirmAllButton)
        retryButton = findViewById(R.id.retryButton)
        fallbackLocalButton = findViewById(R.id.fallbackLocalButton)
        localGenerationTip = findViewById(R.id.localGenerationTip)
        closeTipButton = findViewById(R.id.closeTipButton)

        adapter = WeekPlanAdapter { updateConfirmButtonState() }
        weekPlanRecyclerView.layoutManager = LinearLayoutManager(this)
        weekPlanRecyclerView.adapter = adapter

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        cancelButton.setOnClickListener { finish() }
        confirmAllButton.setOnClickListener { saveAllPlans() }
        retryButton.setOnClickListener { generateWeekPlan() }
        fallbackLocalButton.setOnClickListener { useLocalFallback() }
        closeTipButton.setOnClickListener { localGenerationTip.visibility = View.GONE }
    }

    private fun initDependencies() {
        llmSettingsRepository = LlmSettingsRepository(this)
        planRepository = StudyPlanRepository(this)
        llmPlanGenerateService = LlmPlanGenerateService(llmSettingsRepository, LlmClientFactory)
        planValidator = LlmPlanValidator()
        planContextBuilder = StudyPlanContextBuilder(this)
        localPlanGenerator = StudyPlanGenerator(this)
    }

    private fun setupDaySelector() {
        val dates = selectableDates()
        if (selectedDayDate !in dates) {
            selectedDayDate = dates.first()
        }
        daySelectorContainer.removeAllViews()
        dates.forEach { date ->
            val pill = layoutInflater.inflate(R.layout.item_selection_pill, daySelectorContainer, false) as TextView
            pill.text = formatDayLabel(date)
            pill.tag = date
            pill.isSelected = date == selectedDayDate
            pill.setOnClickListener {
                if (selectedDayDate == date) return@setOnClickListener
                selectedDayDate = date
                updateDaySelection()
                showPlansForDay(date)
            }
            daySelectorContainer.addView(pill)
        }
    }

    private fun updateDaySelection() {
        for (index in 0 until daySelectorContainer.childCount) {
            val child = daySelectorContainer.getChildAt(index)
            child.isSelected = child.tag == selectedDayDate
        }
    }

    private fun selectableDates(): List<String> {
        val today = DateTimeUtils.todayDate()
        return buildList {
            add(today)
            for (offset in 1..7) {
                add(DateTimeUtils.datePlusDays(today, offset))
            }
        }
    }

    private fun formatDayLabel(date: String): String {
        if (date == DateTimeUtils.todayDate()) {
            return getString(R.string.plan_date_today)
        }
        val millis = DateTimeUtils.parseDateMillis(date) ?: return date
        val monthDay = SimpleDateFormat("M/d", Locale.CHINA).format(Date(millis))
        val weekday = CourseUiFormatter.weekdayLabel(this, DateTimeUtils.weekdayForDate(date))
        return getString(R.string.plan_date_label_format, monthDay, weekday)
    }

    private fun generateWeekPlan() {
        showLoading()
        lifecycleScope.launch {
            try {
                val aiAvailable = llmPlanGenerateService.isAvailable() && llmSettingsRepository.hasApiKey()
                val plans = withContext(Dispatchers.IO) {
                    generatePlans(aiAvailable)
                }
                if (plans.isEmpty()) {
                    showError(getString(R.string.llm_plan_parse_error))
                } else {
                    allWeekPlans = plans.mapValuesTo(linkedMapOf()) { (_, dayPlans) ->
                        dayPlans.map { WeekPlanItem(it, true) }.toMutableList()
                    }
                    showContent()
                }
            } catch (e: Exception) {
                showError(mapExceptionToMessage(e))
            }
        }
    }

    private suspend fun generatePlans(aiAvailable: Boolean): LinkedHashMap<String, List<StudyPlan>> {
        val generated = linkedMapOf<String, List<StudyPlan>>()
        val warningsList = mutableListOf<String>()
        var usedAnyLocalGeneration = !aiAvailable

        selectableDates().forEach { dayDate ->
            if (aiAvailable) {
                val prompt = buildDayPrompt(dayDate)
                val request = llmPlanGenerateService.buildPrompt(prompt)
                val config = llmSettingsRepository.getConfig()
                val apiKey = llmSettingsRepository.getApiKey() ?: return linkedMapOf()
                val client = LlmClientFactory.create(config)
                val llmResult = client.generate(request, config, apiKey)

                when (llmResult) {
                    is LlmGenerateResult.Success -> {
                        val dayContext = planContextBuilder.buildForDate(dayDate)
                        val (plans, dayWarnings) = planValidator.parseAndValidate(llmResult.text, dayContext)
                        warningsList.addAll(dayWarnings.map { "${formatDayLabel(dayDate)}: $it" })
                        generated[dayDate] = plans
                    }
                    is LlmGenerateResult.Failure -> {
                        usedAnyLocalGeneration = true
                        generated[dayDate] = generateLocalDayPlans(dayDate)
                    }
                }
            } else {
                generated[dayDate] = generateLocalDayPlans(dayDate)
            }
        }

        warnings = warningsList
        usedLocalGeneration = usedAnyLocalGeneration
        return generated
    }

    private fun generateLocalDayPlans(date: String): List<StudyPlan> {
        return localPlanGenerator.generatePreviewPlans(date, StudyPlan.TYPE_WEEKLY)
    }

    private fun buildDayPrompt(date: String): String {
        val contextText = planContextBuilder.buildForDate(date).toPromptText(maxTasks = 8)
        return """
$contextText

## 输出要求
请以 JSON 格式返回学习计划：
{
  "plans": [
    {
      "title": "计划标题",
      "plannedMinutes": 计划时长（分钟）,
      "startTime": "开始时间 HH:mm",
      "endTime": "结束时间 HH:mm",
      "type": 0,
      "sourceType": 2
    }
  ]
}
        """.trimIndent()
    }

    private fun mapExceptionToMessage(e: Exception): String {
        val message = e.message ?: ""
        return when {
            message.contains("401") -> getString(R.string.llm_plan_auth_error)
            message.contains("403") -> getString(R.string.llm_plan_auth_error)
            message.contains("429") -> getString(R.string.llm_plan_rate_limit)
            message.contains("timeout", ignoreCase = true) -> getString(R.string.llm_plan_timeout)
            message.contains("network", ignoreCase = true) ||
                message.contains("connection", ignoreCase = true) ||
                message.contains("failed to connect") -> getString(R.string.llm_plan_network_error)
            message.contains("5") && message.contains("HTTP") -> getString(R.string.llm_plan_server_error)
            else -> getString(R.string.llm_plan_parse_error) + ": " + message
        }
    }

    private fun showLoading() {
        loadingContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        contentContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
        errorText.text = message
    }

    private fun showContent() {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE
        localGenerationTip.visibility = if (usedLocalGeneration) View.VISIBLE else View.GONE

        val allPlans = allWeekPlans.values.flatten().map { it.plan }
        val totalPlans = allPlans.size
        val totalMinutes = allPlans.sumOf { it.plannedMinutes }
        weekSummaryText.text = getString(R.string.plan_week_summary, totalPlans, totalMinutes)

        if (warnings.isNotEmpty()) {
            weekWarningText.visibility = View.VISIBLE
            weekWarningText.text = warnings.joinToString("\n")
        } else {
            weekWarningText.visibility = View.GONE
        }

        lifecycleScope.launch {
            val courseChecks = withContext(Dispatchers.IO) {
                val summaries = allPlans.groupBy { it.planDate }.flatMap { (date, _) ->
                    PlanCourseConflictChecker.courseBusySummary(planContextBuilder.buildForDate(date))
                }
                val conflicts = allPlans.flatMap { plan ->
                    PlanCourseConflictChecker.findConflicts(
                        listOf(plan),
                        planContextBuilder.buildForDate(plan.planDate)
                    )
                }
                summaries to conflicts
            }
            if (isFinishing || isDestroyed) return@launch
            bindWeekCourseSummary(courseChecks.first)
            bindWeekCourseConflictStatus(courseChecks.second)
            setupDaySelector()
            showPlansForDay(selectedDayDate)
            updateConfirmButtonState()
        }
    }

    private fun bindWeekCourseSummary(summaries: List<String>) {
        weekCourseSummaryText.text = if (summaries.isEmpty()) {
            getString(R.string.llm_plan_course_summary_empty)
        } else {
            getString(R.string.llm_plan_course_summary_title) + "\n" + summaries.take(6).joinToString("\n")
        }
    }

    private fun bindWeekCourseConflictStatus(conflicts: List<com.example.campusmate.domain.plan.PlanCourseConflict>) {
        if (conflicts.isEmpty()) {
            weekCourseConflictStatusText.text = getString(R.string.llm_plan_course_check_passed)
            weekCourseConflictStatusText.setTextColor(getColor(R.color.success))
            return
        }
        weekCourseConflictStatusText.text = buildString {
            append(getString(R.string.llm_plan_course_check_warning))
            append("\n")
            append(
                conflicts.take(4).joinToString("\n") { conflict ->
                    getString(
                        R.string.llm_plan_course_conflict_format,
                        conflict.planTitle,
                        conflict.courseName,
                        conflict.courseTimeRange
                    )
                }
            )
        }
        weekCourseConflictStatusText.setTextColor(getColor(R.color.warning))
    }

    private fun showPlansForDay(date: String) {
        val plans = allWeekPlans[date].orEmpty()
        if (plans.isEmpty()) {
            weekPlanRecyclerView.visibility = View.GONE
            dayEmptyState.visibility = View.VISIBLE
            return
        }
        weekPlanRecyclerView.visibility = View.VISIBLE
        dayEmptyState.visibility = View.GONE
        adapter.submitList(plans)
    }

    private fun updateConfirmButtonState() {
        confirmAllButton.isEnabled = allWeekPlans.values.flatten().any { it.isSelected }
    }

    private fun saveAllPlans() {
        val selectedPlans = allWeekPlans.values
            .flatten()
            .filter { it.isSelected }
            .map { it.plan }
        if (selectedPlans.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), R.string.plan_none_selected, Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    planRepository.deletePlansOverlapping(selectedPlans)
                    selectedPlans.forEach { plan ->
                        planRepository.addPlan(plan.copy(id = 0L))
                    }
                }
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.plan_week_save_success, selectedPlans.size),
                    Snackbar.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), R.string.plan_save_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun useLocalFallback() {
        lifecycleScope.launch {
            try {
                val localPlans = withContext(Dispatchers.IO) {
                    selectableDates().associateWith { generateLocalDayPlans(it) }
                }
                warnings = emptyList()
                usedLocalGeneration = true
                allWeekPlans = localPlans.mapValuesTo(linkedMapOf()) { (_, dayPlans) ->
                    dayPlans.map { WeekPlanItem(it, true) }.toMutableList()
                }
                showContent()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.plan_generate_fallback_to_local),
                    Snackbar.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                showError(mapExceptionToMessage(e))
            }
        }
    }

    companion object {
        const val EXTRA_USE_LOCAL_FALLBACK = "use_local_fallback"
        const val REQUEST_CODE = 1002
    }
}

data class WeekPlanItem(
    val plan: StudyPlan,
    var isSelected: Boolean = true
)

class WeekPlanAdapter(
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<WeekPlanAdapter.ViewHolder>() {
    private val items = mutableListOf<WeekPlanItem>()

    fun submitList(newItems: List<WeekPlanItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_week_plan, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val planCard: MaterialCardView = itemView.findViewById(R.id.planCard)
        private val planCheckbox: CheckBox = itemView.findViewById(R.id.planCheckbox)
        private val planTitle: TextView = itemView.findViewById(R.id.planTitle)
        private val planSourceText: TextView = itemView.findViewById(R.id.planSourceText)
        private val planTime: TextView = itemView.findViewById(R.id.planTime)
        private val planDuration: TextView = itemView.findViewById(R.id.planDuration)

        fun bind(item: WeekPlanItem) {
            val plan = item.plan
            planCheckbox.setOnCheckedChangeListener(null)
            planCheckbox.isChecked = item.isSelected

            planTitle.text = plan.title
            planTime.text = if (plan.startTime != null && plan.endTime != null) {
                "${plan.startTime} - ${plan.endTime}"
            } else {
                itemView.context.getString(R.string.plan_no_specific_time)
            }
            planDuration.text = itemView.context.getString(R.string.plan_item_duration_format, plan.plannedMinutes)
            planSourceText.text = when (plan.sourceType) {
                StudyPlan.SOURCE_LLM -> itemView.context.getString(R.string.plan_source_llm)
                StudyPlan.SOURCE_MANUAL -> itemView.context.getString(R.string.plan_source_manual)
                else -> itemView.context.getString(R.string.plan_source_auto)
            }

            applySelectedState(item.isSelected)
            planCheckbox.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                items[position].isSelected = isChecked
                applySelectedState(isChecked)
                onSelectionChanged()
            }
            itemView.setOnClickListener {
                planCheckbox.isChecked = !planCheckbox.isChecked
            }
        }

        private fun applySelectedState(isSelected: Boolean) {
            planCard.setCardBackgroundColor(
                itemView.context.getColor(
                    if (isSelected) R.color.campus_primary_light else R.color.campus_surface
                )
            )
            planTitle.alpha = if (isSelected) 1.0f else 0.72f
            planTime.alpha = if (isSelected) 1.0f else 0.72f
            planDuration.alpha = if (isSelected) 1.0f else 0.72f
            planSourceText.alpha = if (isSelected) 1.0f else 0.72f
        }
    }
}
