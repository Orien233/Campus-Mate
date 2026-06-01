package com.example.campusmate.ui.import_

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
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.import_.LlmScheduleParseService
import com.example.campusmate.domain.import_.ScheduleParseException
import com.example.campusmate.domain.import_.ScheduleParseResult
import com.example.campusmate.domain.import_.WebViewScheduleExtractor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WebView schedule import entry.
 *
 * User manually logs into the academic system in WebView, navigates to the schedule page,
 * then extracts current page HTML and feeds it into the LLM-first import pipeline.
 * If LLM is unavailable or fails, the flow falls back to local Jsoup parsing.
 *
 * No account/password/cookie is stored. No captcha bypass.
 */
class WebViewImportActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var urlInput: TextInputEditText
    private lateinit var webView: WebView
    private lateinit var llmScheduleParseService: LlmScheduleParseService
    private val extractor = WebViewScheduleExtractor()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val importPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val preferLlm: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_PREFER_LLM, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view_import)

        rootView = findViewById(R.id.webViewImportRoot)
        urlInput = findViewById(R.id.webViewImportUrlInput)
        webView = findViewById(R.id.webViewImportWebView)
        llmScheduleParseService = LlmScheduleParseService(LlmSettingsRepository(this))

        setupToolbar()
        setupWebView()
        setupActions()
        maybePrefillBjtuScheduleUrl()
        // Privacy: start every entry with a clean auth state so WebView never "auto logs in"
        // because of persisted cookies/storage from a previous session.
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
        // Strict privacy: never persist login state (cookies/storage) across sessions.
        // This ensures the next entry requires re-login, matching project constraints.
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
        val toolbar = findViewById<MaterialToolbar>(R.id.webViewImportToolbar)
        toolbar.title = getString(R.string.import_webview_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupWebView() {
        extractor.prepare(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.webViewImportOpenButton).setOnClickListener {
            openUrlFromInput()
        }
        findViewById<MaterialButton>(R.id.webViewImportExtractButton).setOnClickListener {
            extractAndParseCurrentPage()
        }
        findViewById<MaterialButton>(R.id.webViewImportFallbackButton).setOnClickListener {
            Snackbar.make(rootView, R.string.import_webview_fallback_hint, Snackbar.LENGTH_LONG).show()
            startActivity(Intent(this, ImportScheduleActivity::class.java))
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
            Snackbar.make(rootView, R.string.import_webview_url_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val finalUrl = normalizeUrl(raw) ?: run {
            Snackbar.make(rootView, R.string.import_webview_url_invalid, Snackbar.LENGTH_SHORT).show()
            return
        }
        // Ensure "Open" always starts from a logged-out state (require re-login).
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
        Snackbar.make(rootView, R.string.import_webview_extracting, Snackbar.LENGTH_SHORT).show()
        extractor.extractHtmlWithWait(webView) { html ->
            if (html.isNullOrBlank()) {
                runOnUiThread {
                    Snackbar.make(rootView, R.string.import_webview_extract_failed, Snackbar.LENGTH_LONG).show()
                }
                return@extractHtmlWithWait
            }
            if (preferLlm && llmScheduleParseService.isAvailable()) {
                runOnUiThread { maybeShowAiDisclosure { parseWithLlm(html) } }
            } else {
                if (preferLlm) {
                    runOnUiThread {
                        Snackbar.make(rootView, R.string.import_llm_unavailable_fallback, Snackbar.LENGTH_LONG).show()
                    }
                }
                parseWithLocal(html, null)
            }
        }
    }

    private fun maybePrefillBjtuScheduleUrl() {
        val current = urlInput.text?.toString().orEmpty().trim()
        if (current.isNotBlank()) return
        // Keep this as a convenience default; users can still change it freely.
        urlInput.setText(BJTU_PORTAL_URL)
    }

    private fun parseWithLlm(html: String) {
        executor.execute {
            try {
                val result = llmScheduleParseService.parseWithLlm(html)
                openPreview(result)
            } catch (error: ScheduleParseException) {
                runOnUiThread {
                    promptLocalFallback(
                        html = html,
                        errorMessage = error.message ?: getString(R.string.import_llm_parse_failed)
                    )
                }
            }
        }
    }

    private fun parseWithLocal(html: String, fallbackReason: String?) {
        executor.execute {
            try {
                val result = llmScheduleParseService.parseLocal(html, fallbackReason)
                openPreview(result)
            } catch (error: ScheduleParseException) {
                runOnUiThread {
                    Snackbar.make(rootView, error.message ?: getString(R.string.import_parse_failed), Snackbar.LENGTH_LONG)
                        .show()
                }
            } catch (error: IllegalArgumentException) {
                runOnUiThread {
                    Snackbar.make(rootView, error.message ?: getString(R.string.import_parse_failed), Snackbar.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun promptLocalFallback(html: String, errorMessage: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_llm_fallback_title)
            .setMessage(getString(R.string.import_llm_fallback_message, errorMessage))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.import_llm_fallback_use_local) { _, _ ->
                parseWithLocal(html, errorMessage)
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

    private fun openPreview(result: ScheduleParseResult) {
        runOnUiThread {
            startActivity(
                Intent(this, ImportPreviewActivity::class.java)
                    .putExtra(ImportPreviewActivity.EXTRA_SOURCE_TYPE, ImportLog.SOURCE_WEBVIEW)
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

    private fun clearWebViewAuthState(onDone: (() -> Unit)? = null) {
        // This method is intentionally "best-effort" and must never block UI (avoid ANR/freeze).
        // Some WebView implementations may delay cookie callbacks; use a small timeout fallback.
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
                        // Ignore; best-effort cleanup only.
                    }
                }

                try {
                    WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {
                    // Ignore; best-effort cleanup only.
                }

                try {
                    WebViewDatabase.getInstance(this).apply {
                        clearHttpAuthUsernamePassword()
                        clearFormData()
                    }
                } catch (_: Exception) {
                    // Ignore; best-effort cleanup only.
                }

                val cookieManager = CookieManager.getInstance()
                var pending = 2
                fun onCookieDone() {
                    pending -= 1
                    if (pending <= 0) {
                        try {
                            cookieManager.flush()
                        } catch (_: Exception) {
                            // Ignore; best-effort cleanup only.
                        }
                        doneOnce.call()
                    }
                }

                // Clear both persistent + session cookies to prevent "auto login" on next entry.
                cookieManager.removeAllCookies { onCookieDone() }
                cookieManager.removeSessionCookies { onCookieDone() }

                // Safety net: never wait indefinitely for cookie callbacks.
                mainHandler.postDelayed({ doneOnce.call() }, 800L)
            } catch (_: Exception) {
                doneOnce.call()
            }
        }
    }

    companion object {
        const val EXTRA_PREFER_LLM = "extra_prefer_llm"
        private const val PREFS_NAME = "campusmate_import_schedule"
        private const val KEY_AI_DISCLOSURE_SHOWN = "ai_disclosure_shown"
        // Some BJTU sub-pages will fail when opened directly (SSO/session required).
        // Prefill the MIS portal and let users navigate to the schedule page manually.
        private const val BJTU_PORTAL_URL = "https://mis.bjtu.edu.cn/"
    }
}
