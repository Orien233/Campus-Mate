package com.example.campusmate.ui.plan

import android.os.Bundle
import android.view.View
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LlmWeekPlanPreviewActivity : AppCompatActivity() {

    private lateinit var llmSettingsRepository: LlmSettingsRepository
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var llmPlanGenerateService: LlmPlanGenerateService
    private lateinit var planValidator: LlmPlanValidator
    private lateinit var planContextBuilder: StudyPlanContextBuilder

    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var dayEmptyState: LinearLayout
    private lateinit var weekSummaryText: TextView
    private lateinit var weekWarningText: TextView
    private lateinit var weekCourseSummaryText: TextView
    private lateinit var weekCourseConflictStatusText: TextView
    private lateinit var errorText: TextView
    private lateinit var dayTabLayout: TabLayout
    private lateinit var weekPlanRecyclerView: RecyclerView
    private lateinit var cancelButton: MaterialButton
    private lateinit var confirmAllButton: MaterialButton
    private lateinit var retryButton: MaterialButton
    private lateinit var fallbackLocalButton: MaterialButton
    private lateinit var localGenerationTip: com.google.android.material.card.MaterialCardView
    private lateinit var closeTipButton: android.widget.ImageView

    private lateinit var adapter: WeekPlanAdapter

    private var allWeekPlans: Map<String, List<StudyPlan>> = emptyMap()
    private var selectedDayDate: String = ""
    private var warnings: List<String> = emptyList()
    private var usedLocalGeneration: Boolean = false

    private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_week_plan_preview)

        initViews()
        initDependencies()
        setupTabs()
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
        dayTabLayout = findViewById(R.id.dayTabLayout)
        weekPlanRecyclerView = findViewById(R.id.weekPlanRecyclerView)
        cancelButton = findViewById(R.id.cancelButton)
        confirmAllButton = findViewById(R.id.confirmAllButton)
        retryButton = findViewById(R.id.retryButton)
        fallbackLocalButton = findViewById(R.id.fallbackLocalButton)
        localGenerationTip = findViewById(R.id.localGenerationTip)
        closeTipButton = findViewById(R.id.closeTipButton)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        cancelButton.setOnClickListener { finish() }

        confirmAllButton.setOnClickListener {
            saveAllPlans()
        }

        retryButton.setOnClickListener {
            generateWeekPlan()
        }

        fallbackLocalButton.setOnClickListener {
            useLocalFallback()
        }

        closeTipButton.setOnClickListener {
            localGenerationTip.visibility = View.GONE
        }
    }

    private fun initDependencies() {
        llmSettingsRepository = LlmSettingsRepository(this)
        planRepository = StudyPlanRepository(this)
        llmPlanGenerateService = LlmPlanGenerateService(llmSettingsRepository, LlmClientFactory)
        planValidator = LlmPlanValidator()
        planContextBuilder = StudyPlanContextBuilder(this)
    }

    private fun setupTabs() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val weekday = getCurrentWeekday()
        calendar.add(Calendar.DAY_OF_MONTH, -(weekday - 1))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNumberFormat = SimpleDateFormat("d", Locale.US)

        for (i in 0..6) {
            val dayDate = dateFormat.format(calendar.time)
            val dayOfWeek = weekdayNames[i]
            val dayNumber = dayNumberFormat.format(calendar.time)
            val monthDayFormat = SimpleDateFormat("M月d日", Locale.CHINA)
            val monthDay = monthDayFormat.format(calendar.time)

            val tab = dayTabLayout.newTab()
            tab.text = "$dayOfWeek\n$monthDay"
            tab.tag = dayDate
            dayTabLayout.addTab(tab)

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        dayTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { selectedTab ->
                    // Update visual state for all tabs
                    for (i in 0 until dayTabLayout.tabCount) {
                        dayTabLayout.getTabAt(i)?.view?.isSelected = (i == selectedTab.position)
                    }
                    selectedTab.tag?.let { date ->
                        selectedDayDate = date as String
                        showPlansForDay(date)
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.view?.isSelected = false
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.view?.isSelected = true
            }
        })

        // Select today by default
        val todayDate = dateFormat.format(Date())
        for (i in 0 until dayTabLayout.tabCount) {
            val tab = dayTabLayout.getTabAt(i)
            if (tab?.tag == todayDate) {
                tab.select()
                selectedDayDate = todayDate
                // Set visual state for selected tab
                tab.view?.isSelected = true
                break
            }
        }
        if (selectedDayDate.isEmpty() && dayTabLayout.tabCount > 0) {
            val firstTab = dayTabLayout.getTabAt(0)
            firstTab?.select()
            selectedDayDate = firstTab?.tag as? String ?: ""
            firstTab?.view?.isSelected = true
        }
    }

    private fun getCurrentWeekday(): Int {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            Calendar.SUNDAY -> 7
            else -> dayOfWeek - 1
        }
    }

    private fun generateWeekPlan() {
        showLoading()
        lifecycleScope.launch {
            try {
                val aiAvailable = llmPlanGenerateService.isAvailable() && llmSettingsRepository.hasApiKey()
                val plans = withContext(Dispatchers.IO) {
                    generateWeekPlansWithLlm(aiAvailable)
                }
                if (plans.isEmpty()) {
                    showError(getString(R.string.llm_plan_parse_error))
                } else {
                    allWeekPlans = plans
                    usedLocalGeneration = !aiAvailable
                    showContent()
                }
            } catch (e: Exception) {
                showError(mapExceptionToMessage(e))
            }
        }
    }

    private suspend fun generateWeekPlansWithLlm(aiAvailable: Boolean): Map<String, List<StudyPlan>> {
        val weekPlans = mutableMapOf<String, List<StudyPlan>>()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val weekday = getCurrentWeekday()
        calendar.add(Calendar.DAY_OF_MONTH, -(weekday - 1))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val warningsList = mutableListOf<String>()

        for (day in 0..6) {
            val dayDate = dateFormat.format(calendar.time)

            if (aiAvailable) {
                val prompt = buildDayPrompt(dayDate)
                val request = llmPlanGenerateService.buildPrompt(prompt)
                val config = llmSettingsRepository.getConfig()
                val apiKey = llmSettingsRepository.getApiKey() ?: return emptyMap()

                val client = LlmClientFactory.create(config)
                val llmResult = client.generate(request, config, apiKey)

                when (llmResult) {
                    is LlmGenerateResult.Success -> {
                        val jsonContent = llmResult.text
                        val (plans, dayWarnings) = planValidator.parseAndValidate(jsonContent, dayDate)
                        warningsList.addAll(dayWarnings.map { "${weekdayNames[day]}: $it" })
                        weekPlans[dayDate] = plans
                    }
                    is LlmGenerateResult.Failure -> {
                        val localPlans = generateLocalDayPlans(dayDate)
                        weekPlans[dayDate] = localPlans
                    }
                }
            } else {
                val localPlans = generateLocalDayPlans(dayDate)
                weekPlans[dayDate] = localPlans
            }

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        warnings = warningsList
        return weekPlans
    }

    private fun generateLocalDayPlans(date: String): List<StudyPlan> {
        val planContext = planContextBuilder.buildForDate(date)
        val courses = planContext.courses
        val tasks = planContext.tasks
        val plans = mutableListOf<StudyPlan>()

        var currentHour = 7
        var currentMinute = 30

        for (course in courses.sortedBy { it.startSection }) {
            val startTime = formatTime(currentHour, currentMinute)
            val courseMinutes = calculateCourseDuration(course)
            val endMinute = currentMinute + courseMinutes
            val (endHour, endMin) = adjustTime(currentHour, endMinute)

            plans.add(
                StudyPlan(
                    title = "上课: ${course.name}",
                    planDate = date,
                    plannedMinutes = courseMinutes,
                    startTime = startTime,
                    endTime = formatTime(endHour, endMin),
                    type = StudyPlan.TYPE_WEEKLY,
                    sourceType = StudyPlan.SOURCE_AUTO
                )
            )

            currentHour = endHour
            currentMinute = endMin + 10
            if (currentMinute >= 60) {
                currentHour += currentMinute / 60
                currentMinute = currentMinute % 60
            }
        }

        if (tasks.isNotEmpty()) {
            val taskMinutes = tasks.take(5).sumOf { 60 }
            val endMinute = currentMinute + taskMinutes
            val (endHour, endMin) = adjustTime(currentHour, endMinute)

            plans.add(
                StudyPlan(
                    title = "自主学习: ${tasks.size}项待办",
                    planDate = date,
                    plannedMinutes = taskMinutes,
                    startTime = formatTime(currentHour, currentMinute),
                    endTime = formatTime(endHour, endMin),
                    type = StudyPlan.TYPE_WEEKLY,
                    sourceType = StudyPlan.SOURCE_AUTO
                )
            )
        }

        return plans
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

    private fun calculateCourseDuration(course: com.example.campusmate.data.model.Course): Int {
        val sections = course.endSection - course.startSection + 1
        val classDuration = 45
        val breakAfterClass = if (sections > 1) (sections - 1) * 5 else 0
        return sections * classDuration + breakAfterClass
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun adjustTime(hour: Int, totalMinutes: Int): Pair<Int, Int> {
        val adjustedHour = hour + totalMinutes / 60
        val adjustedMinute = totalMinutes % 60
        return Pair(adjustedHour.coerceAtMost(23), adjustedMinute)
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

        // Show tip banner if local generation was used
        if (usedLocalGeneration) {
            localGenerationTip.visibility = View.VISIBLE
        } else {
            localGenerationTip.visibility = View.GONE
        }

        val totalPlans = allWeekPlans.values.sumOf { it.size }
        val totalMinutes = allWeekPlans.values.flatten().sumOf { it.plannedMinutes }
        weekSummaryText.text = getString(R.string.plan_week_summary, totalPlans, totalMinutes)

        if (warnings.isNotEmpty()) {
            weekWarningText.visibility = View.VISIBLE
            weekWarningText.text = warnings.joinToString("; ")
        } else {
            weekWarningText.visibility = View.GONE
        }

        bindWeekCourseSummary()
        bindWeekCourseConflictStatus()

        showPlansForDay(selectedDayDate)
    }

    private fun bindWeekCourseSummary() {
        val summaries = allWeekPlans.keys.sorted().flatMap { date ->
            PlanCourseConflictChecker.courseBusySummary(planContextBuilder.buildForDate(date))
        }
        weekCourseSummaryText.text = if (summaries.isEmpty()) {
            getString(R.string.llm_plan_course_summary_empty)
        } else {
            getString(R.string.llm_plan_course_summary_title) + "\n" + summaries.take(6).joinToString("\n")
        }
    }

    private fun bindWeekCourseConflictStatus() {
        val conflicts = allWeekPlans.flatMap { (date, plans) ->
            PlanCourseConflictChecker.findConflicts(plans, planContextBuilder.buildForDate(date))
        }
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
        val plans = allWeekPlans[date] ?: emptyList()

        if (plans.isEmpty()) {
            weekPlanRecyclerView.visibility = View.GONE
            dayEmptyState.visibility = View.VISIBLE
        } else {
            weekPlanRecyclerView.visibility = View.VISIBLE
            dayEmptyState.visibility = View.GONE

            if (!::adapter.isInitialized) {
                adapter = WeekPlanAdapter { plan, isChecked ->
                    // Handle individual plan toggle if needed
                }
                weekPlanRecyclerView.layoutManager = LinearLayoutManager(this)
                weekPlanRecyclerView.adapter = adapter
            }

            adapter.submitList(plans.toMutableList().map { WeekPlanItem(it, true) })
        }
    }

    private fun saveAllPlans() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val allPlans = allWeekPlans.values.flatten()
                    for (plan in allPlans) {
                        planRepository.addPlan(plan.copy(id = 0L))
                    }
                }
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.plan_week_save_success, allWeekPlans.values.sumOf { it.size }),
                    Snackbar.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.task_save_failed),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun useLocalFallback() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    val weekday = getCurrentWeekday()
                    calendar.add(Calendar.DAY_OF_MONTH, -(weekday - 1))

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val localPlans = mutableMapOf<String, List<StudyPlan>>()

                    for (day in 0..6) {
                        val dayDate = dateFormat.format(calendar.time)
                        localPlans[dayDate] = generateLocalDayPlans(dayDate)
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }

                    allWeekPlans = localPlans
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
    private val onItemCheckedChange: (StudyPlan, Boolean) -> Unit
) : androidx.recyclerview.widget.ListAdapter<WeekPlanItem, WeekPlanAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<WeekPlanItem>() {
        override fun areItemsTheSame(oldItem: WeekPlanItem, newItem: WeekPlanItem): Boolean {
            return oldItem.plan.title == newItem.plan.title && oldItem.plan.planDate == newItem.plan.planDate
        }

        override fun areContentsTheSame(oldItem: WeekPlanItem, newItem: WeekPlanItem): Boolean {
            return oldItem == newItem
        }
    }
) {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_week_plan, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val planCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.planCard)
        private val planCheckbox: android.widget.CheckBox = itemView.findViewById(R.id.planCheckbox)
        private val planTitle: TextView = itemView.findViewById(R.id.planTitle)
        private val planSourceChip: Chip = itemView.findViewById(R.id.planSourceChip)
        private val planTime: TextView = itemView.findViewById(R.id.planTime)
        private val planDuration: TextView = itemView.findViewById(R.id.planDuration)

        fun bind(item: WeekPlanItem) {
            val plan = item.plan

            planTitle.text = plan.title

            val timeStr = if (plan.startTime != null && plan.endTime != null) {
                "${plan.startTime} - ${plan.endTime}"
            } else {
                itemView.context.getString(R.string.plan_no_specific_time)
            }
            planTime.text = timeStr

            planDuration.text = itemView.context.getString(R.string.plan_item_duration_format, plan.plannedMinutes)

            val sourceName = when (plan.sourceType) {
                StudyPlan.SOURCE_LLM -> itemView.context.getString(R.string.plan_source_llm)
                else -> itemView.context.getString(R.string.plan_source_auto)
            }
            planSourceChip.text = sourceName

            planCheckbox.isChecked = item.isSelected
            planCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onItemCheckedChange(plan, isChecked)
            }

            // Highlight selected state
            if (item.isSelected) {
                planCard.strokeColor = itemView.context.getColor(R.color.campus_primary)
                planCard.strokeWidth = 2
                planCard.cardElevation = 4f
            } else {
                planCard.strokeColor = itemView.context.getColor(R.color.campus_divider)
                planCard.strokeWidth = 0
                planCard.cardElevation = 2f
            }

            itemView.setOnClickListener {
                planCheckbox.isChecked = !planCheckbox.isChecked
            }
        }
    }
}
