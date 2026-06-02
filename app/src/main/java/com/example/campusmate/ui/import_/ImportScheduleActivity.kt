package com.example.campusmate.ui.import_

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.import_.LlmScheduleParseService
import com.example.campusmate.domain.import_.ScheduleParseException
import com.example.campusmate.domain.import_.ScheduleParseResult
import com.example.campusmate.ui.main.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Entry screen for importing a schedule from sample or pasted HTML. */
class ImportScheduleActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var pastedHtmlInput: TextInputEditText
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup
    private lateinit var llmScheduleParseService: LlmScheduleParseService
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val importPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_schedule)

        rootView = findViewById(R.id.importScheduleRoot)
        pastedHtmlInput = findViewById(R.id.pastedHtmlInput)
        modeToggleGroup = findViewById(R.id.importModeToggleGroup)
        llmScheduleParseService = LlmScheduleParseService(LlmSettingsRepository(this))

        setupToolbar()
        setupModeToggle()
        setupActions()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.importScheduleToolbar)
        toolbar.title = getString(R.string.import_schedule_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupModeToggle() {
        modeToggleGroup.check(R.id.importAiModeButton)
        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (checkedId == R.id.importLocalModeButton) {
                showMessage(getString(R.string.import_local_mode_selected))
            } else if (checkedId == R.id.importAiModeButton) {
                showMessage(getString(R.string.import_ai_mode_selected))
            }
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.importSampleButton).setOnClickListener {
            parseAndOpenPreview(readSampleHtml(), ImportLog.SOURCE_SAMPLE_HTML)
        }
        findViewById<MaterialButton>(R.id.importPastedButton).setOnClickListener {
            parseAndOpenPreview(pastedHtmlInput.text?.toString().orEmpty(), ImportLog.SOURCE_PASTED_HTML)
        }
        findViewById<MaterialButton>(R.id.importWebViewButton).setOnClickListener {
            startActivity(
                Intent(this, WebViewImportActivity::class.java)
                    .putExtra(WebViewImportActivity.EXTRA_PREFER_LLM, isLlmPreferred())
            )
        }
        findViewById<MaterialButton>(R.id.importSettingsButton).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.nav_settings)
            )
        }

        pastedHtmlInput.setOnEditorActionListener { _, actionId, event ->
            val isParseAction =
                actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (isParseAction) {
                parseAndOpenPreview(pastedHtmlInput.text?.toString().orEmpty(), ImportLog.SOURCE_PASTED_HTML)
                true
            } else {
                false
            }
        }
    }

    private fun parseAndOpenPreview(html: String, sourceType: Int) {
        val normalizedHtml = html.trim()
        if (normalizedHtml.isBlank()) {
            showMessage(getString(R.string.import_html_empty))
            return
        }

        if (isLlmPreferred() && llmScheduleParseService.isAvailable()) {
            maybeShowAiDisclosure {
                parseWithLlm(normalizedHtml, sourceType)
            }
            return
        }

        if (isLlmPreferred()) {
            showMessage(getString(R.string.import_llm_unavailable_fallback))
        }
        parseWithLocal(normalizedHtml, sourceType, null)
    }

    private fun parseWithLlm(html: String, sourceType: Int) {
        executor.execute {
            try {
                val result = llmScheduleParseService.parseWithLlm(html)
                openPreview(result, sourceType)
            } catch (error: ScheduleParseException) {
                runOnUiThread {
                    promptLocalFallback(
                        html = html,
                        sourceType = sourceType,
                        errorMessage = error.message ?: getString(R.string.import_llm_parse_failed)
                    )
                }
            }
        }
    }

    private fun parseWithLocal(html: String, sourceType: Int, fallbackReason: String?) {
        executor.execute {
            try {
                val result = llmScheduleParseService.parseLocal(html, fallbackReason)
                openPreview(result, sourceType)
            } catch (error: ScheduleParseException) {
                showParseError(error.message ?: getString(R.string.import_parse_failed))
            } catch (error: IllegalArgumentException) {
                showParseError(error.message ?: getString(R.string.import_parse_failed))
            }
        }
    }

    private fun promptLocalFallback(html: String, sourceType: Int, errorMessage: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_llm_fallback_title)
            .setMessage(getString(R.string.import_llm_fallback_message, errorMessage))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.import_llm_fallback_use_local) { _, _ ->
                parseWithLocal(html, sourceType, errorMessage)
            }
            .show()
    }

    private fun maybeShowAiDisclosure(onConfirmed: () -> Unit) {
        if (importPreferences.getBoolean(KEY_AI_DISCLOSURE_SHOWN, false)) {
            onConfirmed()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_llm_privacy_title)
            .setMessage(R.string.import_llm_privacy_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.import_llm_privacy_continue) { _, _ ->
                importPreferences.edit().putBoolean(KEY_AI_DISCLOSURE_SHOWN, true).apply()
                onConfirmed()
            }
            .show()
    }

    private fun openPreview(result: ScheduleParseResult, sourceType: Int) {
        runOnUiThread {
            startActivity(
                Intent(this, ImportPreviewActivity::class.java)
                    .putExtra(ImportPreviewActivity.EXTRA_SOURCE_TYPE, sourceType)
                    .putExtra(ImportPreviewActivity.EXTRA_PARSER_LABEL, result.parserLabel)
                    .putExtra(ImportPreviewActivity.EXTRA_FALLBACK_REASON, result.fallbackReason)
                    .putStringArrayListExtra(
                        ImportPreviewActivity.EXTRA_WARNINGS,
                        ArrayList(result.warnings)
                    )
                    .putExtra(
                        ImportPreviewActivity.EXTRA_SECTION_TIME_SLOTS,
                        ArrayList(result.sectionTimeSlots)
                    )
                    .putExtra(ImportPreviewActivity.EXTRA_DRAFTS, ArrayList(result.drafts))
            )
        }
    }

    private fun showParseError(message: String) {
        runOnUiThread {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun isLlmPreferred(): Boolean {
        return modeToggleGroup.checkedButtonId != R.id.importLocalModeButton
    }

    private fun readSampleHtml(): String {
        return assets.open("sample_schedule.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    companion object {
        private const val PREFS_NAME = "campusmate_import_schedule"
        private const val KEY_AI_DISCLOSURE_SHOWN = "ai_disclosure_shown"
    }
}
