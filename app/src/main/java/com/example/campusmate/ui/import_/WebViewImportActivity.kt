package com.example.campusmate.ui.import_

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.example.campusmate.R
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.domain.import_.JsoupScheduleParser
import com.example.campusmate.domain.import_.ScheduleParseException
import com.example.campusmate.domain.import_.WebViewScheduleExtractor
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

/**
 * Stage-12 WebView schedule import entry.
 *
 * User manually logs into the academic system in WebView, navigates to the schedule page,
 * then extracts current page HTML and feeds it into JsoupScheduleParser.
 *
 * No account/password/cookie is stored. No captcha bypass.
 */
class WebViewImportActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var urlInput: TextInputEditText
    private lateinit var webView: WebView
    private val extractor = WebViewScheduleExtractor()
    private val parser = JsoupScheduleParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view_import)

        rootView = findViewById(R.id.webViewImportRoot)
        urlInput = findViewById(R.id.webViewImportUrlInput)
        webView = findViewById(R.id.webViewImportWebView)

        setupToolbar()
        setupWebView()
        setupActions()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
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
                // Stay inside WebView; user can still open external browser if needed.
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
        webView.loadUrl(finalUrl)
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
        extractor.extractHtml(webView) { html ->
            if (html.isNullOrBlank()) {
                runOnUiThread {
                    Snackbar.make(rootView, R.string.import_webview_extract_failed, Snackbar.LENGTH_LONG).show()
                }
                return@extractHtml
            }
            try {
                val drafts = parser.parse(html)
                runOnUiThread {
                    startActivity(
                        Intent(this, ImportPreviewActivity::class.java)
                            .putExtra(ImportPreviewActivity.EXTRA_SOURCE_TYPE, ImportLog.SOURCE_WEBVIEW)
                            .putExtra(ImportPreviewActivity.EXTRA_DRAFTS, ArrayList(drafts))
                    )
                }
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
}
