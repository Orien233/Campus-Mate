package com.example.campusmate.ui.import_

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmate.R
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.domain.import_.JsoupScheduleParser
import com.example.campusmate.domain.import_.ScheduleParseException
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

/** Entry screen for importing a schedule from sample or pasted HTML. */
class ImportScheduleActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var pastedHtmlInput: TextInputEditText
    private val parser = JsoupScheduleParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_schedule)

        rootView = findViewById(R.id.importScheduleRoot)
        pastedHtmlInput = findViewById(R.id.pastedHtmlInput)
        setupToolbar()

        findViewById<MaterialButton>(R.id.importSampleButton).setOnClickListener {
            parseAndOpenPreview(readSampleHtml(), ImportLog.SOURCE_SAMPLE_HTML)
        }
        findViewById<MaterialButton>(R.id.importPastedButton).setOnClickListener {
            parseAndOpenPreview(pastedHtmlInput.text?.toString().orEmpty(), ImportLog.SOURCE_PASTED_HTML)
        }
        findViewById<MaterialButton>(R.id.importWebViewButton).setOnClickListener {
            startActivity(Intent(this, WebViewImportActivity::class.java))
        }
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

    private fun parseAndOpenPreview(html: String, sourceType: Int) {
        try {
            val drafts = parser.parse(html)
            startActivity(
                Intent(this, ImportPreviewActivity::class.java)
                    .putExtra(ImportPreviewActivity.EXTRA_SOURCE_TYPE, sourceType)
                    .putExtra(ImportPreviewActivity.EXTRA_DRAFTS, ArrayList(drafts))
            )
        } catch (error: ScheduleParseException) {
            Snackbar.make(rootView, error.message ?: getString(R.string.import_parse_failed), Snackbar.LENGTH_LONG).show()
        } catch (error: IllegalArgumentException) {
            Snackbar.make(rootView, error.message ?: getString(R.string.import_parse_failed), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun readSampleHtml(): String {
        return assets.open("sample_schedule.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
