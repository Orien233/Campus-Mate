package com.example.campusmate.ui.ai.file

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusmate.R
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.ai.context.AiContextBuildRequest
import com.example.campusmate.domain.ai.context.AiContextOrchestrator
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.command.AiRecordCommandContextProvider
import com.example.campusmate.domain.ai.command.AiRecordCommandResult
import com.example.campusmate.domain.ai.command.AiRecordCommandService
import com.example.campusmate.domain.ai.file.AiFileAnalysisEnvelope
import com.example.campusmate.domain.ai.file.AiFileAnalysisError
import com.example.campusmate.domain.ai.file.AiFileAnalysisResult
import com.example.campusmate.domain.ai.file.AiFileContentReader
import com.example.campusmate.domain.ai.file.AiFileInputError
import com.example.campusmate.domain.ai.file.AiFileInputPolicy
import com.example.campusmate.domain.ai.file.AiFileReadException
import com.example.campusmate.domain.ai.file.AiSelectedFile
import com.example.campusmate.domain.ai.file.LlmFileAnalysisService
import com.example.campusmate.ui.import_.ImportPreviewActivity
import com.example.campusmate.ui.plan.LlmWeekPlanPreviewActivity
import com.example.campusmate.ui.settings.SettingsFragment
import com.example.campusmate.ui.settings.SettingsSectionActivity
import com.example.campusmate.ui.task.TaskImportPreviewActivity
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiFileAnalysisActivity : AppCompatActivity() {
    private lateinit var settingsRepository: LlmSettingsRepository
    private lateinit var contentReader: AiFileContentReader
    private lateinit var contextOrchestrator: AiContextOrchestrator
    private lateinit var analysisService: LlmFileAnalysisService
    private lateinit var commandContextProvider: AiRecordCommandContextProvider
    private lateinit var commandService: AiRecordCommandService

    private lateinit var commandInput: TextInputEditText
    private lateinit var supportText: TextView
    private lateinit var selectedNameText: TextView
    private lateinit var selectedMetaText: TextView
    private lateinit var selectButton: MaterialButton
    private lateinit var analyzeButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var errorText: TextView
    private lateinit var resultContainer: View
    private lateinit var provenanceText: TextView
    private lateinit var summaryText: TextView
    private lateinit var keyPointsText: TextView
    private lateinit var coursesText: TextView
    private lateinit var coursesButton: MaterialButton
    private lateinit var tasksText: TextView
    private lateinit var tasksButton: MaterialButton
    private lateinit var plansText: TextView
    private lateinit var plansButton: MaterialButton
    private lateinit var insightsText: TextView
    private lateinit var warningsText: TextView

    private var selectedUri: Uri? = null
    private var selectedFile: AiSelectedFile? = null
    private var currentEnvelope: AiFileAnalysisEnvelope? = null
    private var coursesProcessed = false
    private var tasksProcessed = false
    private var plansProcessed = false
    private var coursePreviewInFlight = false
    private var taskPreviewInFlight = false
    private var planPreviewInFlight = false
    private var commandRequestInFlight = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::inspectSelection)
    }
    private val coursePreviewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            coursePreviewInFlight = false
            if (result.resultCode == RESULT_OK) {
                coursesProcessed = true
            }
            bindCategoryActions()
        }
    private val taskPreviewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            taskPreviewInFlight = false
            if (result.resultCode == RESULT_OK) {
                tasksProcessed = true
            }
            bindCategoryActions()
        }
    private val planPreviewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            planPreviewInFlight = false
            if (result.resultCode == RESULT_OK) {
                plansProcessed = true
            }
            bindCategoryActions()
        }
    private val commandPreviewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) commandInput.text?.clear()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_file_analysis)
        initDependencies()
        initViews()
        bindCapabilitySummary()
        bindEmptySelection()
    }

    override fun onResume() {
        super.onResume()
        if (::settingsRepository.isInitialized) {
            bindCapabilitySummary()
            revalidateSelection()
        }
    }

    private fun initDependencies() {
        settingsRepository = LlmSettingsRepository(this)
        contentReader = AiFileContentReader(contentResolver)
        contextOrchestrator = AiContextOrchestrator(applicationContext)
        analysisService = LlmFileAnalysisService(settingsRepository)
        commandContextProvider = AiRecordCommandContextProvider(applicationContext)
        commandService = AiRecordCommandService(settingsRepository)
    }

    private fun initViews() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.fileAnalysisToolbar)
            .setNavigationOnClickListener { finish() }
        commandInput = findViewById(R.id.recordCommandInput)
        supportText = findViewById(R.id.fileAnalysisSupportText)
        selectedNameText = findViewById(R.id.fileAnalysisSelectedNameText)
        selectedMetaText = findViewById(R.id.fileAnalysisSelectedMetaText)
        selectButton = findViewById(R.id.fileAnalysisSelectButton)
        analyzeButton = findViewById(R.id.fileAnalysisAnalyzeButton)
        settingsButton = findViewById(R.id.fileAnalysisSettingsButton)
        progress = findViewById(R.id.fileAnalysisProgress)
        statusText = findViewById(R.id.fileAnalysisStatusText)
        errorText = findViewById(R.id.fileAnalysisErrorText)
        resultContainer = findViewById(R.id.fileAnalysisResultContainer)
        provenanceText = findViewById(R.id.fileAnalysisProvenanceText)
        summaryText = findViewById(R.id.fileAnalysisSummaryText)
        keyPointsText = findViewById(R.id.fileAnalysisKeyPointsText)
        coursesText = findViewById(R.id.fileAnalysisCoursesText)
        coursesButton = findViewById(R.id.fileAnalysisCoursesButton)
        tasksText = findViewById(R.id.fileAnalysisTasksText)
        tasksButton = findViewById(R.id.fileAnalysisTasksButton)
        plansText = findViewById(R.id.fileAnalysisPlansText)
        plansButton = findViewById(R.id.fileAnalysisPlansButton)
        insightsText = findViewById(R.id.fileAnalysisInsightsText)
        warningsText = findViewById(R.id.fileAnalysisWarningsText)

        selectButton.setOnClickListener { chooseFile() }
        analyzeButton.setOnClickListener { confirmAnalysis() }
        settingsButton.setOnClickListener { openAiSettings() }
        coursesButton.setOnClickListener { openCoursePreview() }
        tasksButton.setOnClickListener { openTaskPreview() }
        plansButton.setOnClickListener { openPlanPreview() }
        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                runTextCommand()
                true
            } else {
                false
            }
        }
    }

    private fun runTextCommand() {
        if (commandRequestInFlight) return
        val input = commandInput.text?.toString()?.trim().orEmpty()
        if (input.isBlank()) {
            showError(getString(R.string.ai_record_input_required))
            return
        }
        commandRequestInFlight = true
        commandInput.isEnabled = false
        selectButton.isEnabled = false
        analyzeButton.isEnabled = false
        showBusy(R.string.ai_record_analyzing)
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(commandInput.windowToken, 0)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                commandService.generate(input) { commandContextProvider.build(input) }
            }
            commandRequestInFlight = false
            commandInput.isEnabled = true
            selectButton.isEnabled = true
            revalidateSelection()
            showIdle()
            when (result) {
                is AiRecordCommandResult.Success -> {
                    errorText.visibility = View.GONE
                    commandPreviewLauncher.launch(
                        AiRecordChangePreviewActivity.intentFor(this@AiFileAnalysisActivity, result.envelope)
                    )
                }
                is AiRecordCommandResult.Failure -> showError(result.message)
            }
        }
    }

    private fun chooseFile() {
        val unavailable = analysisService.unavailableReason()
        if (unavailable != null) {
            showError(messageForAnalysisError(unavailable))
            return
        }
        filePicker.launch(arrayOf("*/*"))
    }

    private fun inspectSelection(uri: Uri) {
        clearAnalysisResult()
        bindEmptySelection()
        showBusy(R.string.ai_file_checking)
        lifecycleScope.launch {
            val inspected = runCatching {
                withContext(Dispatchers.IO) { contentReader.inspect(uri) }
            }
            val file = inspected.getOrElse { error ->
                showIdle()
                showError(messageForThrowable(error))
                return@launch
            }
            val kind = AiFileInputPolicy.resolveKind(file.mimeType, file.displayName)
            val policyError = if (kind == null) {
                AiFileInputError.UNSUPPORTED_TYPE
            } else {
                AiFileInputPolicy.validate(
                    kind,
                    file.sizeBytes,
                    settingsRepository.getConfig().multimodalCapability
                )
            }
            if (policyError != null) {
                showIdle()
                showError(messageForInputError(policyError))
                bindEmptySelection()
                return@launch
            }
            selectedUri = uri
            selectedFile = file
            selectedNameText.text = file.displayName.ifBlank {
                getString(R.string.ai_file_selected_name_fallback)
            }
            selectedMetaText.text = getString(
                R.string.ai_file_selected_meta,
                file.mimeType,
                file.sizeBytes?.let(::formatSize) ?: getString(R.string.ai_file_size_unknown)
            )
            analyzeButton.isEnabled = true
            showIdle()
        }
    }

    private fun confirmAnalysis() {
        val file = selectedFile ?: return
        if (selectedUri == null) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ai_file_confirm_title)
            .setMessage(
                getString(
                    R.string.ai_file_confirm_message,
                    file.displayName.ifBlank { getString(R.string.ai_file_selected_name_fallback) },
                    settingsRepository.getConfig().displayName,
                    settingsRepository.getConfig().model
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_file_confirm_send) { _, _ -> runAnalysis() }
            .show()
    }

    private fun runAnalysis() {
        val uri = selectedUri ?: return
        val file = selectedFile ?: return
        analysisService.unavailableReason()?.let { error ->
            showError(messageForAnalysisError(error))
            return
        }
        showBusy(R.string.ai_file_analyzing)
        selectButton.isEnabled = false
        analyzeButton.isEnabled = false
        lifecycleScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val config = settingsRepository.getConfig()
                    val input = contentReader.read(uri, file, config.multimodalCapability)
                    analysisService.generate(input) {
                        val today = DateTimeUtils.todayDate()
                        contextOrchestrator.build(
                            AiContextBuildRequest(
                                purpose = AiContextPurpose.FILE_ANALYSIS,
                                startDate = today,
                                endDate = DateTimeUtils.datePlusDays(today, FILE_CONTEXT_HORIZON_DAYS - 1)
                            )
                        )
                    }
                }
            }
            selectButton.isEnabled = true
            showIdle()
            val result = outcome.getOrElse { error ->
                analyzeButton.isEnabled = true
                showError(messageForThrowable(error))
                return@launch
            }
            when (result) {
                is AiFileAnalysisResult.Success -> {
                    selectedUri = null
                    bindAnalysis(result.envelope)
                }
                is AiFileAnalysisResult.Failure -> {
                    analyzeButton.isEnabled = true
                    showError(messageForAnalysisError(result.error))
                }
            }
        }
    }

    private fun bindAnalysis(envelope: AiFileAnalysisEnvelope) {
        currentEnvelope = envelope
        coursesProcessed = false
        tasksProcessed = false
        plansProcessed = false
        val analysis = envelope.analysis
        resultContainer.visibility = View.VISIBLE
        provenanceText.text = getString(
            R.string.ai_file_provenance,
            envelope.providerName,
            envelope.model,
            envelope.promptTag
        )
        summaryText.text = analysis.summary
        bindOptionalList(keyPointsText, analysis.keyPoints)
        coursesText.text = previewLines(analysis.courses.map { it.draft.name })
        tasksText.text = previewLines(analysis.tasks.map { it.draft.title })
        plansText.text = previewLines(
            analysis.plans.map { candidate ->
                getString(
                    R.string.ai_file_plan_line,
                    candidate.draft.planDate,
                    candidate.draft.startTime,
                    candidate.draft.endTime,
                    candidate.draft.title
                )
            }
        )
        val insights = buildList {
            analysis.insights.forEach { add("${it.title}：${it.detail}") }
            analysis.localMatches.forEach { add(it.reason) }
        }
        bindOptionalList(insightsText, insights)
        bindOptionalList(warningsText, analysis.warnings)
        bindCategoryActions()
    }

    private fun bindCategoryActions() {
        val analysis = currentEnvelope?.analysis ?: return
        bindCategoryButton(
            coursesButton,
            analysis.courses.size,
            coursesProcessed,
            coursePreviewInFlight,
            R.string.ai_file_review_courses
        )
        bindCategoryButton(
            tasksButton,
            analysis.tasks.size,
            tasksProcessed,
            taskPreviewInFlight,
            R.string.ai_file_review_tasks
        )
        bindCategoryButton(
            plansButton,
            analysis.plans.size,
            plansProcessed,
            planPreviewInFlight,
            R.string.ai_file_review_plans
        )
    }

    private fun bindCategoryButton(
        button: MaterialButton,
        itemCount: Int,
        processed: Boolean,
        inFlight: Boolean,
        actionText: Int
    ) {
        button.isEnabled = itemCount > 0 && !processed && !inFlight
        button.text = when {
            processed -> getString(R.string.ai_file_category_processed)
            inFlight -> getString(R.string.ai_file_category_open)
            itemCount == 0 -> getString(R.string.ai_file_category_empty)
            else -> getString(actionText, itemCount)
        }
    }

    private fun openCoursePreview() {
        val analysis = currentEnvelope?.analysis ?: return
        if (analysis.courses.isEmpty() || coursesProcessed || coursePreviewInFlight) return
        coursePreviewInFlight = true
        bindCategoryActions()
        coursePreviewLauncher.launch(
            Intent(this, ImportPreviewActivity::class.java)
                .putExtra(
                    ImportPreviewActivity.EXTRA_DRAFTS,
                    ArrayList(analysis.courses.map { it.draft })
                )
                .putExtra(ImportPreviewActivity.EXTRA_SOURCE_TYPE, ImportLog.SOURCE_AI_FILE)
                .putExtra(
                    ImportPreviewActivity.EXTRA_PARSER_LABEL,
                    getString(R.string.ai_file_parser_label)
                )
                .putStringArrayListExtra(
                    ImportPreviewActivity.EXTRA_WARNINGS,
                    ArrayList(analysis.warnings)
                )
        )
    }

    private fun openTaskPreview() {
        val analysis = currentEnvelope?.analysis ?: return
        if (analysis.tasks.isEmpty() || tasksProcessed || taskPreviewInFlight) return
        taskPreviewInFlight = true
        bindCategoryActions()
        taskPreviewLauncher.launch(
            Intent(this, TaskImportPreviewActivity::class.java)
                .putExtra(
                    TaskImportPreviewActivity.EXTRA_TASK_DRAFTS,
                    ArrayList(analysis.tasks.map { it.draft })
                )
                .putStringArrayListExtra(
                    TaskImportPreviewActivity.EXTRA_WARNINGS,
                    ArrayList(analysis.warnings)
                )
        )
    }

    private fun openPlanPreview() {
        val analysis = currentEnvelope?.analysis ?: return
        if (analysis.plans.isEmpty() || plansProcessed || planPreviewInFlight) return
        planPreviewInFlight = true
        bindCategoryActions()
        val plans = analysis.plans.map { candidate ->
            StudyPlan(
                title = candidate.draft.title,
                planDate = candidate.draft.planDate,
                plannedMinutes = candidate.draft.plannedMinutes,
                startTime = candidate.draft.startTime,
                endTime = candidate.draft.endTime,
                type = StudyPlan.TYPE_WEEKLY,
                sourceType = StudyPlan.SOURCE_LLM
            )
        }
        planPreviewLauncher.launch(
            Intent(this, LlmWeekPlanPreviewActivity::class.java)
                .putExtra(LlmWeekPlanPreviewActivity.EXTRA_PRECOMPUTED_PLANS, ArrayList(plans))
                .putStringArrayListExtra(
                    LlmWeekPlanPreviewActivity.EXTRA_PREVIEW_WARNINGS,
                    ArrayList(analysis.warnings)
                )
        )
    }

    private fun bindCapabilitySummary() {
        val config = settingsRepository.getConfig()
        supportText.text = when (config.multimodalCapability) {
            LlmMultimodalCapability.TEXT_ONLY -> getString(R.string.ai_file_support_text_only)
            LlmMultimodalCapability.IMAGE_INPUT -> getString(R.string.ai_file_support_images)
            LlmMultimodalCapability.IMAGE_AND_FILE_INPUT ->
                getString(R.string.ai_file_support_images_pdf)
        }
    }

    private fun revalidateSelection() {
        val file = selectedFile ?: return
        val unavailable = analysisService.unavailableReason()
        if (unavailable != null) {
            analyzeButton.isEnabled = false
            showError(messageForAnalysisError(unavailable))
            return
        }
        val kind = AiFileInputPolicy.resolveKind(file.mimeType, file.displayName) ?: return
        val error = AiFileInputPolicy.validate(
            kind,
            file.sizeBytes,
            settingsRepository.getConfig().multimodalCapability
        )
        analyzeButton.isEnabled = error == null && selectedUri != null
        if (error != null) {
            showError(messageForInputError(error))
        } else if (currentEnvelope == null) {
            errorText.visibility = View.GONE
        }
    }

    private fun bindEmptySelection() {
        selectedUri = null
        selectedFile = null
        selectedNameText.text = getString(R.string.ai_file_no_selection)
        selectedMetaText.text = getString(R.string.ai_file_no_selection_help)
        analyzeButton.isEnabled = false
    }

    private fun clearAnalysisResult() {
        currentEnvelope = null
        coursesProcessed = false
        tasksProcessed = false
        plansProcessed = false
        coursePreviewInFlight = false
        taskPreviewInFlight = false
        planPreviewInFlight = false
        resultContainer.visibility = View.GONE
        errorText.visibility = View.GONE
    }

    private fun showBusy(messageRes: Int) {
        progress.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        statusText.setText(messageRes)
        errorText.visibility = View.GONE
    }

    private fun showIdle() {
        progress.visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.setText(R.string.ai_file_status_idle)
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun bindOptionalList(view: TextView, values: List<String>) {
        view.visibility = if (values.isEmpty()) View.GONE else View.VISIBLE
        view.text = values.joinToString("\n") { "• $it" }
    }

    private fun previewLines(values: List<String>): String {
        if (values.isEmpty()) return getString(R.string.ai_file_category_empty_description)
        val visible = values.take(MAX_PREVIEW_LINES).map { "• $it" }
        return if (values.size > visible.size) {
            visible.joinToString("\n") + "\n" +
                getString(R.string.ai_file_category_more, values.size - visible.size)
        } else {
            visible.joinToString("\n")
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024L -> getString(R.string.ai_file_size_bytes, bytes)
            bytes < 1024L * 1024L ->
                getString(R.string.ai_file_size_kib, bytes / 1024.0)
            else -> getString(R.string.ai_file_size_mib, bytes / (1024.0 * 1024.0))
        }
    }

    private fun messageForThrowable(error: Throwable): String {
        return if (error is AiFileReadException) {
            messageForInputError(error.error)
        } else {
            getString(R.string.ai_file_error_read_failed)
        }
    }

    private fun messageForInputError(error: AiFileInputError): String {
        return getString(
            when (error) {
                AiFileInputError.UNSUPPORTED_TYPE -> R.string.ai_file_error_unsupported
                AiFileInputError.CAPABILITY_MISMATCH -> R.string.ai_file_error_capability
                AiFileInputError.EMPTY_FILE -> R.string.ai_file_error_empty
                AiFileInputError.TEXT_TOO_LARGE -> R.string.ai_file_error_text_too_large
                AiFileInputError.BINARY_TOO_LARGE -> R.string.ai_file_error_binary_too_large
                AiFileInputError.TEXT_TOO_LONG -> R.string.ai_file_error_text_too_long
                AiFileInputError.READ_FAILED -> R.string.ai_file_error_read_failed
            }
        )
    }

    private fun messageForAnalysisError(error: AiFileAnalysisError): String {
        return getString(
            when (error) {
                AiFileAnalysisError.DISABLED -> R.string.ai_file_error_disabled
                AiFileAnalysisError.NO_API_KEY -> R.string.ai_file_error_no_key
                AiFileAnalysisError.INVALID_INPUT -> R.string.ai_file_error_capability
                AiFileAnalysisError.REQUEST_FAILED -> R.string.ai_file_error_request
                AiFileAnalysisError.INVALID_RESPONSE -> R.string.ai_file_error_response
            }
        )
    }

    private fun openAiSettings() {
        startActivity(SettingsSectionActivity.intentFor(this, SettingsFragment.SECTION_AI))
    }

    companion object {
        private const val FILE_CONTEXT_HORIZON_DAYS = 7
        private const val MAX_PREVIEW_LINES = 3
    }
}
