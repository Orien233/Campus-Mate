package com.example.campusmate.ui.plan

import android.content.Intent
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
import com.example.campusmate.domain.plan.LlmPlanGenerateService
import com.example.campusmate.domain.plan.LlmPlanValidator
import com.example.campusmate.domain.plan.PlanCourseConflictChecker
import com.example.campusmate.domain.plan.StudyPlanContextBuilder
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LlmPlanPreviewActivity : AppCompatActivity() {

    private lateinit var llmSettingsRepository: LlmSettingsRepository
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var llmPlanGenerateService: LlmPlanGenerateService
    private lateinit var planValidator: LlmPlanValidator

    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var actionContainer: LinearLayout
    private lateinit var previewSummaryText: TextView
    private lateinit var warningsText: TextView
    private lateinit var courseConflictStatusText: TextView
    private lateinit var errorText: TextView
    private lateinit var planPreviewRecyclerView: RecyclerView
    private lateinit var cancelButton: MaterialButton
    private lateinit var appendButton: MaterialButton
    private lateinit var replaceButton: MaterialButton
    private lateinit var retryButton: MaterialButton
    private lateinit var fallbackLocalButton: MaterialButton

    private var generatedPlans: List<StudyPlan> = emptyList()
    private var warnings: List<String> = emptyList()
    private var planDate: String = ""
    private var hasExistingPlans: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_plan_preview)

        initViews()
        initDependencies()
        planDate = intent.getStringExtra(EXTRA_PLAN_DATE) ?: getTodayDate()
        hasExistingPlans = intent.getBooleanExtra(EXTRA_HAS_EXISTING_PLANS, false)

        checkLlmAvailability()
    }

    private fun initViews() {
        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        actionContainer = findViewById(R.id.actionContainer)
        previewSummaryText = findViewById(R.id.previewSummaryText)
        warningsText = findViewById(R.id.warningsText)
        courseConflictStatusText = findViewById(R.id.courseConflictStatusText)
        errorText = findViewById(R.id.errorText)
        planPreviewRecyclerView = findViewById(R.id.planPreviewRecyclerView)
        cancelButton = findViewById(R.id.cancelButton)
        appendButton = findViewById(R.id.appendButton)
        replaceButton = findViewById(R.id.replaceButton)
        retryButton = findViewById(R.id.retryButton)
        fallbackLocalButton = findViewById(R.id.fallbackLocalButton)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        cancelButton.setOnClickListener { finish() }

        appendButton.setOnClickListener {
            savePlans(append = true)
        }

        replaceButton.setOnClickListener {
            savePlans(append = false)
        }

        retryButton.setOnClickListener {
            generateLlmPlan()
        }

        fallbackLocalButton.setOnClickListener {
            useLocalFallback()
        }

        if (hasExistingPlans) {
            appendButton.visibility = View.VISIBLE
        } else {
            appendButton.visibility = View.GONE
        }
    }

    private fun initDependencies() {
        llmSettingsRepository = LlmSettingsRepository(this)
        planRepository = StudyPlanRepository(this)
        llmPlanGenerateService = LlmPlanGenerateService(llmSettingsRepository, LlmClientFactory)
        planValidator = LlmPlanValidator()
    }

    private fun checkLlmAvailability() {
        if (!llmPlanGenerateService.isAvailable()) {
            if (!llmSettingsRepository.hasApiKey()) {
                showError(getString(R.string.llm_plan_no_key))
            } else {
                showError(getString(R.string.llm_plan_disabled))
            }
            return
        }
        generateLlmPlan()
    }

    private fun generateLlmPlan() {
        showLoading()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    generatePlanWithLlm()
                }
                if (result.isSuccess) {
                    val plans = result.getOrDefault(emptyList())
                    if (plans.isEmpty()) {
                        showError(getString(R.string.llm_plan_parse_error))
                    } else {
                        generatedPlans = plans
                        showPreview()
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: getString(R.string.llm_plan_parse_error)
                    showError(error)
                }
            } catch (e: Exception) {
                showError(mapExceptionToMessage(e))
            }
        }
    }

    private suspend fun generatePlanWithLlm(): Result<List<StudyPlan>> {
        return try {
            val prompt = buildPrompt()
            val request = llmPlanGenerateService.buildPrompt(prompt)
            val config = llmSettingsRepository.getConfig()
            val apiKey = llmSettingsRepository.getApiKey() ?: return Result.failure(Exception("No API Key"))

            val client = LlmClientFactory.create(config)
            val llmResult = client.generate(request, config, apiKey)

            return when (llmResult) {
                is com.example.campusmate.domain.llm.LlmGenerateResult.Success -> {
                    val jsonContent = llmResult.text
                    val planContext = StudyPlanContextBuilder(this).buildForDate(planDate)
                    val (plans, validationWarnings) = planValidator.parseAndValidate(jsonContent, planContext)
                    warnings = validationWarnings
                    if (plans.isEmpty()) {
                        Result.failure(Exception(getString(R.string.llm_plan_parse_error)))
                    } else {
                        Result.success(plans)
                    }
                }
                is com.example.campusmate.domain.llm.LlmGenerateResult.Failure -> {
                    Result.failure(Exception(llmResult.message))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPrompt(): String {
        val contextText = StudyPlanContextBuilder(this)
            .buildForDate(planDate)
            .toPromptText(maxTasks = 12)

        return """
$contextText

## 输出要求
请以 JSON 格式返回学习计划，格式如下：
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

注意事项：
1. 计划时长建议在 15-180 分钟之间
2. 普通作业、复习、项目和考试准备计划必须避开课程时间
3. “上课”“完成课程学习”“课程学习”等课程本身相关计划应放在对应课程时间内，并在标题中保留课程名
4. 合理安排休息时间
5. 优先安排高优先级和即将截止的任务
6. 只返回 JSON，不要其他内容
        """.trimIndent()
    }

    private fun getTodayDate(): String {
        return DateTimeUtils.todayDate()
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
        actionContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        actionContainer.visibility = View.GONE
        errorText.text = message
    }

    private fun showPreview() {
        loadingContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        actionContainer.visibility = View.VISIBLE

        val totalMinutes = generatedPlans.sumOf { it.plannedMinutes }
        previewSummaryText.text = getString(R.string.llm_plan_generated_count, generatedPlans.size, totalMinutes)

        planPreviewRecyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = LlmPlanPreviewAdapter()
        planPreviewRecyclerView.adapter = adapter
        adapter.submitList(generatedPlans)

        if (warnings.isNotEmpty()) {
            warningsText.visibility = View.VISIBLE
            warningsText.text = warnings.joinToString("\n")
        } else {
            warningsText.visibility = View.GONE
        }
        bindCourseConflictStatus()
    }

    private fun bindCourseConflictStatus() {
        val context = StudyPlanContextBuilder(this).buildForDate(planDate)
        val conflicts = PlanCourseConflictChecker.findConflicts(generatedPlans, context)
        if (conflicts.isEmpty()) {
            courseConflictStatusText.text = getString(R.string.llm_plan_course_check_passed)
            courseConflictStatusText.setTextColor(getColor(R.color.success))
            return
        }
        courseConflictStatusText.text = buildString {
            append(getString(R.string.llm_plan_course_check_warning))
            append("\n")
            append(
                conflicts.take(3).joinToString("\n") { conflict ->
                    getString(
                        R.string.llm_plan_course_conflict_format,
                        conflict.planTitle,
                        conflict.courseName,
                        conflict.courseTimeRange
                    )
                }
            )
        }
        courseConflictStatusText.setTextColor(getColor(R.color.warning))
    }

    private fun savePlans(append: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!append) {
                        planRepository.deletePlansByDate(planDate)
                    } else {
                        planRepository.deletePlansOverlapping(generatedPlans)
                    }
                    planRepository.addPlans(generatedPlans.map { it.copy(id = 0L) })
                }
                val message = if (append) {
                    getString(R.string.llm_plan_added_success)
                } else {
                    getString(R.string.llm_plan_replaced_success)
                }
                Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content),
                    getString(R.string.task_save_failed), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun useLocalFallback() {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_USE_LOCAL_FALLBACK, true)
            putExtra(EXTRA_PLAN_DATE, planDate)
        })
        finish()
    }

    companion object {
        const val EXTRA_PLAN_DATE = "plan_date"
        const val EXTRA_HAS_EXISTING_PLANS = "has_existing_plans"
        const val EXTRA_USE_LOCAL_FALLBACK = "use_local_fallback"
        const val REQUEST_CODE = 1001
    }
}
