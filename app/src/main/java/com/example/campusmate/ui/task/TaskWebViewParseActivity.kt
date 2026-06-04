package com.example.campusmate.ui.task

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.task.LlmTaskParseService
import com.example.campusmate.domain.task.TaskDraft
import com.example.campusmate.domain.task.TaskParseException
import com.example.campusmate.ui.main.MainActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WebView task parsing entry.
 *
 * Users manually open a learning platform page, then extract the current page content for AI
 * parsing. The parsed task is returned to TaskEditActivity for user review; it is never saved here.
 */
class TaskWebViewParseActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var urlInput: TextInputEditText
    private lateinit var webView: WebView
    private lateinit var taskParseService: LlmTaskParseService
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val taskParsePreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_web_view_parse)

        rootView = findViewById(R.id.taskWebViewParseRoot)
        urlInput = findViewById(R.id.taskWebViewParseUrlInput)
        webView = findViewById(R.id.taskWebViewParseWebView)
        taskParseService = LlmTaskParseService(LlmSettingsRepository(this))

        setupToolbar()
        setupWebView()
        setupActions()
        clearWebViewAuthState()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        clearWebViewAuthState()
                        finish()
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        clearWebViewAuthState()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        clearWebViewAuthState()
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.taskWebViewParseToolbar)
        toolbar.title = getString(R.string.task_ai_webview_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.taskWebViewParseOpenButton).setOnClickListener {
            openUrlFromInput()
        }
        findViewById<MaterialButton>(R.id.taskWebViewParseExtractButton).setOnClickListener {
            extractAndParseCurrentPage()
        }
        findViewById<MaterialButton>(R.id.taskWebViewParseSettingsButton).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.nav_settings)
            )
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter =
                actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (isEnter) {
                openUrlFromInput()
                true
            } else {
                false
            }
        }
    }

    private fun openUrlFromInput() {
        val raw = urlInput.text?.toString().orEmpty().trim()
        if (raw.isBlank()) {
            Snackbar.make(rootView, R.string.task_ai_webview_url_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val finalUrl = normalizeUrl(raw) ?: run {
            Snackbar.make(rootView, R.string.task_ai_webview_url_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }
        clearWebViewAuthState { webView.loadUrl(finalUrl) }
    }

    private fun normalizeUrl(input: String): String? {
        val withScheme = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        return try {
            val uri = Uri.parse(withScheme)
            if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) null else withScheme
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAndParseCurrentPage() {
        if (!taskParseService.isAvailable()) {
            Snackbar.make(rootView, R.string.task_ai_parse_unavailable, Snackbar.LENGTH_LONG).show()
            return
        }
        Snackbar.make(rootView, R.string.task_ai_webview_extracting, Snackbar.LENGTH_SHORT).show()
        extractPageSnapshot { snapshot ->
            if (snapshot.isNullOrBlank()) {
                Snackbar.make(rootView, R.string.task_ai_webview_extract_failed, Snackbar.LENGTH_LONG).show()
                return@extractPageSnapshot
            }
            maybeShowAiDisclosure {
                parseWithLlm(snapshot)
            }
        }
    }

    private fun extractPageSnapshot(onResult: (String?) -> Unit) {
        val script = """
            (function(){
              try {
                var title = document.title || "";
                var text = document.body ? (document.body.innerText || "") : "";
                var html = document.documentElement ? (document.documentElement.outerHTML || "") : "";
                return JSON.stringify({
                  title: title,
                  text: text.slice(0, 12000),
                  html: html.slice(0, 12000)
                });
              } catch (e) {
                return "";
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { value ->
            onResult(decodeJsResult(value))
        }
    }

    private fun decodeJsResult(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw == "null") return null
        return try {
            JSONObject("{\"v\":$raw}").optString("v").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            raw.trim('"')
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
        }
    }

    private fun maybeShowAiDisclosure(onConfirmed: () -> Unit) {
        if (taskParsePreferences.getBoolean(KEY_AI_DISCLOSURE_SHOWN, false)) {
            onConfirmed()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_ai_privacy_title)
            .setMessage(R.string.task_ai_privacy_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.task_ai_privacy_continue) { _, _ ->
                taskParsePreferences.edit().putBoolean(KEY_AI_DISCLOSURE_SHOWN, true).apply()
                onConfirmed()
            }
            .show()
    }

    private fun parseWithLlm(snapshot: String) {
        executor.execute {
            try {
                val result = taskParseService.parseWithLlm(snapshot)
                val draft = result.drafts.first()
                val warningSummary = buildWarningSummary(result.drafts.size, result.warnings)
                runOnUiThread {
                    Toast.makeText(this, R.string.task_ai_parse_prefill_ready, Toast.LENGTH_SHORT).show()
                    setResult(
                        RESULT_OK,
                        Intent()
                            .putExtra(EXTRA_TASK_DRAFT, draft)
                            .putExtra(EXTRA_WARNING_SUMMARY, warningSummary)
                    )
                    finish()
                }
            } catch (error: TaskParseException) {
                showParseError(error.message ?: getString(R.string.task_ai_parse_failed))
            } catch (error: IllegalArgumentException) {
                showParseError(error.message ?: getString(R.string.task_ai_parse_failed))
            }
        }
    }

    private fun buildWarningSummary(draftCount: Int, warnings: List<String>): String {
        val parts = mutableListOf<String>()
        if (draftCount > 1) {
            parts += getString(R.string.task_ai_parse_multiple_result, draftCount)
        }
        if (warnings.isNotEmpty()) {
            parts += warnings.take(3).joinToString("\n")
        }
        return parts.joinToString("\n\n")
    }

    private fun showParseError(message: String) {
        runOnUiThread {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun clearWebViewAuthState(onDone: (() -> Unit)? = null) {
        val mainHandler = Handler(Looper.getMainLooper())
        val doneOnce = object {
            @Volatile
            var called = false
            fun call() {
                if (called) return
                called = true
                onDone?.invoke()
            }
        }

        mainHandler.post {
            try {
                if (::webView.isInitialized) {
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.clearSslPreferences()
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                    } catch (_: Exception) {
                    }
                }

                try {
                    WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {
                }

                try {
                    WebViewDatabase.getInstance(this).apply {
                        clearHttpAuthUsernamePassword()
                        clearFormData()
                    }
                } catch (_: Exception) {
                }

                val cookieManager = CookieManager.getInstance()
                var pending = 2
                fun onCookieDone() {
                    pending -= 1
                    if (pending <= 0) {
                        try {
                            cookieManager.flush()
                        } catch (_: Exception) {
                        }
                        doneOnce.call()
                    }
                }

                cookieManager.removeAllCookies { onCookieDone() }
                cookieManager.removeSessionCookies { onCookieDone() }
                mainHandler.postDelayed({ doneOnce.call() }, 800L)
            } catch (_: Exception) {
                doneOnce.call()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_DRAFT = "extra_task_draft"
        const val EXTRA_WARNING_SUMMARY = "extra_warning_summary"
        private const val PREFS_NAME = "campusmate_task_ai_parse"
        private const val KEY_AI_DISCLOSURE_SHOWN = "ai_disclosure_shown"
    }
}
