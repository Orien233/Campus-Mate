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


import com.example.campusmate.data.repository.CourseRepository


import com.example.campusmate.data.repository.LlmSettingsRepository


import com.example.campusmate.data.repository.SettingsRepository


import com.example.campusmate.data.repository.StudyPlanRepository


import com.example.campusmate.data.repository.TaskRepository


import com.example.campusmate.domain.llm.LlmClientFactory


import com.example.campusmate.domain.llm.LlmGenerateRequest


import com.example.campusmate.domain.plan.LlmPlanGenerateService


import com.example.campusmate.domain.plan.LlmPlanValidator


import com.example.campusmate.util.DateTimeUtils


import com.google.android.material.button.MaterialButton


import com.google.android.material.snackbar.Snackbar


import kotlinx.coroutines.Dispatchers


import kotlinx.coroutines.launch


import kotlinx.coroutines.withContext


import java.text.SimpleDateFormat


import java.util.Calendar


import java.util.Locale





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


        errorText = findViewById(R.id.errorText)


        planPreviewRecyclerView = findViewById(R.id.planPreviewRecyclerView)


        cancelButton = findViewById(R.id.cancelButton)


        appendButton = findViewById(R.id.appendButton)


        replaceButton = findViewById(R.id.replaceButton)


        retryButton = findViewById(R.id.retryButton)


        fallbackLocalButton = findViewById(R.id.fallbackLocalButton)





        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }


        cancelButton.setOnClickListener { finish() }


        appendButton.setOnClickListener { savePlans(append = true) }


        replaceButton.setOnClickListener { savePlans(append = false) }


        retryButton.setOnClickListener { generateLlmPlan() }


        fallbackLocalButton.setOnClickListener { useLocalFallback() }


        appendButton.visibility = if (hasExistingPlans) View.VISIBLE else View.GONE


    }





    private fun initDependencies() {


        llmSettingsRepository = LlmSettingsRepository(this)


        planRepository = StudyPlanRepository(this)


        llmPlanGenerateService = LlmPlanGenerateService(llmSettingsRepository, LlmClientFactory)


        planValidator = LlmPlanValidator()


    }





    private fun checkLlmAvailability() {


        if (!llmPlanGenerateService.isAvailable()) {


            showError(if (!llmSettingsRepository.hasApiKey()) getString(R.string.llm_plan_no_key) else getString(R.string.llm_plan_disabled))


            return


        }


        generateLlmPlan()


    }





    private fun generateLlmPlan() {


        showLoading()


        lifecycleScope.launch {


            try {


                val result = withContext(Dispatchers.IO) { generatePlanWithLlm() }


                if (result.isSuccess) {


                    val plans = result.getOrDefault(emptyList())


                    if (plans.isEmpty()) showError(getString(R.string.llm_plan_parse_error)) else {


                        generatedPlans = plans


                        showPreview()


                    }


                } else {


                    showError(result.exceptionOrNull()?.message ?: getString(R.string.llm_plan_parse_error))


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





            when (llmResult) {


                is com.example.campusmate.domain.llm.LlmGenerateResult.Success -> {


                    val (plans, validationWarnings) = planValidator.parseAndValidate(llmResult.text, planDate)


                    warnings = validationWarnings


                    if (plans.isEmpty()) Result.failure(Exception(getString(R.string.llm_plan_parse_error))) else Result.success(plans)


                }


                is com.example.campusmate.domain.llm.LlmGenerateResult.Failure -> Result.failure(Exception(llmResult.message))


            }


        } catch (e: Exception) {


            Result.failure(e)


        }


    }





    private fun buildPrompt(): String {


        val courseRepository = CourseRepository(this)


        val taskRepository = TaskRepository(this)


        val settingsRepository = SettingsRepository(this)





        val weekday = getWeekday(planDate)


        val courses = courseRepository.getCoursesByWeekday(weekday)


        val tasks = taskRepository.getAllTasks().filter { !it.isDeleted && it.status == com.example.campusmate.data.model.StudyTask.STATUS_TODO }


        val dailyGoal = settingsRepository.getDailyGoalMinutes()





        val coursesText = if (courses.isEmpty()) "No courses today" else courses.joinToString("\n") { course ->


            "Course: ${course.name}, Teacher: ${course.teacher ?: "N/A"}, Room: ${course.classroom ?: "N/A"}, Sections: ${course.startSection}-${course.endSection}"


        }


        val tasksText = if (tasks.isEmpty()) "No pending tasks" else tasks.take(10).joinToString("\n") { task ->


            "Task: ${task.title}, Type: ${getTaskTypeName(task.type)}, Priority: ${getPriorityName(task.priority)}" + (task.dueAt?.let { ", Due: ${formatDate(it)}" } ?: "")


        }





        return """


Please generate a study plan for \$planDate (${'$'}{getWeekdayName(weekday)}).





## Today's Courses


\$coursesText





## Pending Tasks


\$tasksText





## Study Goal


Daily goal: \$dailyGoal minutes





## Output Format


Return JSON in this format:


{


  "plans": [


    {


      "title": "Plan title",


      "plannedMinutes": duration in minutes,


      "startTime": "HH:mm",


      "endTime": "HH:mm",


      "type": 0,


      "sourceType": 2


    }


  ]


}





Notes:


1. Plan duration should be between 15-180 minutes


2. Avoid overlapping with course times


3. Schedule rest breaks


4. Prioritize high-priority and urgent tasks


5. Return only JSON, no other text


        """.trimIndent()


    }








    private fun savePlans(append: Boolean) {


        lifecycleScope.launch {


            try {


                withContext(Dispatchers.IO) {


                    if (!append) planRepository.deletePlansByDate(planDate)


                    planRepository.addPlans(generatedPlans.map { it.copy(id = 0L) })


                }


                Snackbar.make(findViewById(android.R.id.content), if (append) getString(R.string.llm_plan_added_success) else getString(R.string.llm_plan_replaced_success), Snackbar.LENGTH_SHORT).show()


                finish()


            } catch (e: Exception) {


                Snackbar.make(findViewById(android.R.id.content), getString(R.string.llm_plan_save_failed), Snackbar.LENGTH_SHORT).show()


            }


        }


    }





    private fun useLocalFallback() {


        lifecycleScope.launch {


            try {


                val plans = withContext(Dispatchers.IO) {


                    val courseRepository = CourseRepository(this@LlmPlanPreviewActivity)


                    val taskRepository = TaskRepository(this@LlmPlanPreviewActivity)


                    val settingsRepository = SettingsRepository(this@LlmPlanPreviewActivity)





                    val weekday = getWeekday(planDate)


                    val courses = courseRepository.getCoursesByWeekday(weekday)


                    val tasks = taskRepository.getAllTasks().filter {


                        !it.isDeleted && it.status == com.example.campusmate.data.model.StudyTask.STATUS_TODO


                    }


                    val dailyGoal = settingsRepository.getDailyGoalMinutes()


                    val generated = mutableListOf<StudyPlan>()





                    var currentHour = 7


                    var currentMinute = 30





                    for (course in courses.sortedBy { it.startSection }) {


                        val startTime = formatTime(currentHour, currentMinute)


                        val courseMinutes = (course.endSection - course.startSection + 1) * 45


                        val endMinute = currentMinute + courseMinutes


                        val (endHour, endMin) = adjustTime(currentHour, endMinute)





                        generated.add(


                            StudyPlan(


                                title = "上课: ${course.name}",


                                planDate = planDate,


                                plannedMinutes = courseMinutes,


                                startTime = startTime,


                                endTime = formatTime(endHour, endMin),


                                type = StudyPlan.TYPE_WEEKLY,


                                sourceType = StudyPlan.SOURCE_LLM


                            )


                        )





                        currentHour = endHour


                        currentMinute = endMin + 10


                        if (currentMinute >= 60) {


                            currentHour += currentMinute / 60


                            currentMinute = currentMinute % 60


                        }


                    }





                    val remainingMinutes = dailyGoal - generated.sumOf { it.plannedMinutes }


                    if (remainingMinutes > 0 && tasks.isNotEmpty()) {


                        for (task in tasks.take(3)) {


                            val taskMinutes = minOf(remainingMinutes, 60)


                            val startTime = formatTime(currentHour, currentMinute)


                            val endMinute = currentMinute + taskMinutes


                            val (endHour, endMin) = adjustTime(currentHour, endMinute)





                            generated.add(


                                StudyPlan(


                                    title = task.title,


                                    planDate = planDate,


                                    plannedMinutes = taskMinutes,


                                    startTime = startTime,


                                    endTime = formatTime(endHour, endMin),


                                    type = StudyPlan.TYPE_WEEKLY,


                                    sourceType = StudyPlan.SOURCE_LLM


                                )


                            )





                            currentHour = endHour


                            currentMinute = endMin + 10


                            if (currentMinute >= 60) {


                                currentHour += currentMinute / 60


                                currentMinute = currentMinute % 60


                            }


                        }


                    }





                    generated


                }


                generatedPlans = plans


                showPreview()


                Snackbar.make(findViewById(android.R.id.content), getString(R.string.plan_generate_fallback_to_local), Snackbar.LENGTH_SHORT).show()


            } catch (e: Exception) {


                showError(mapExceptionToMessage(e))


            }


        }


    }





    private fun formatTime(hour: Int, minute: Int): String {


        return String.format(Locale.US, "%02d:%02d", hour, minute)


    }





    private fun adjustTime(hour: Int, minute: Int): Pair<Int, Int> {


        val h = hour + minute / 60


        val m = minute % 60


        return Pair(h, m)


    }





    private fun getTodayDate(): String = DateTimeUtils.todayDate()





    private fun getWeekday(date: String): Int {


        val calendar = Calendar.getInstance()


        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return DateTimeUtils.currentWeekday()


        calendar.time = parsed


        return when (calendar.get(Calendar.DAY_OF_WEEK)) {


            Calendar.SUNDAY -> 7


            else -> calendar.get(Calendar.DAY_OF_WEEK) - 1


        }


    }





    private fun getWeekdayName(weekday: Int): String {


        return arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[weekday - 1]


    }





    private fun getTaskTypeName(type: Int): String {


        return when (type) {


            0 -> getString(R.string.task_type_other)


            else -> getString(R.string.task_type_other)


        }


    }





    private fun getPriorityName(priority: Int): String {


        return getString(R.string.task_priority_normal)


    }





    private fun formatDate(timeMillis: Long): String {


        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(timeMillis))


    }





    private fun mapExceptionToMessage(e: Exception): String {


        return e.message ?: getString(R.string.llm_plan_parse_error)


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


        previewSummaryText.text = getString(R.string.llm_plan_preview_summary_format, generatedPlans.size, warnings.size)


        warningsText.visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE


        warningsText.text = warnings.joinToString("\n")


        planPreviewRecyclerView.layoutManager = LinearLayoutManager(this)


        planPreviewRecyclerView.adapter = LlmPlanPreviewAdapter().apply { submitList(generatedPlans) }


    }





    companion object {


        const val EXTRA_PLAN_DATE = "plan_date"


        const val EXTRA_HAS_EXISTING_PLANS = "has_existing_plans"


        const val EXTRA_USE_LOCAL_FALLBACK = "use_local_fallback"


        const val REQUEST_CODE = 1001


    }


}


