package com.example.campusmate.ui.ai.file

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.domain.ai.command.AiRecordChangeApplier
import com.example.campusmate.domain.ai.command.AiRecordCommandEnvelope
import com.example.campusmate.domain.ai.command.AiRecordOperation
import com.example.campusmate.domain.ai.command.RepositoryAiRecordChangeGateway
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiRecordChangePreviewActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var adapter: AiRecordChangeAdapter
    private lateinit var applyButton: MaterialButton
    private var envelope: AiRecordCommandEnvelope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_record_change_preview)
        root = findViewById(R.id.recordChangePreviewRoot)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.recordChangePreviewToolbar)
            .setNavigationOnClickListener { finish() }
        adapter = AiRecordChangeAdapter(::refreshSummary)
        findViewById<RecyclerView>(R.id.recordChangeRecycler).apply {
            layoutManager = LinearLayoutManager(this@AiRecordChangePreviewActivity)
            adapter = this@AiRecordChangePreviewActivity.adapter
        }
        applyButton = findViewById(R.id.recordChangeApplyButton)
        applyButton.setOnClickListener { confirmSelection() }
        loadEnvelope()
    }

    @Suppress("DEPRECATION")
    private fun loadEnvelope() {
        envelope = intent.getSerializableExtra(EXTRA_ENVELOPE) as? AiRecordCommandEnvelope
        val value = envelope
        if (value == null || value.changes.isEmpty()) {
            Toast.makeText(this, R.string.ai_record_preview_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        adapter.submit(value.changes)
        findViewById<TextView>(R.id.recordChangeProvenance).text = getString(
            R.string.ai_file_provenance, value.providerName, value.model, value.promptTag
        )
        findViewById<TextView>(R.id.recordChangeWarnings).apply {
            visibility = if (value.warnings.isEmpty()) View.GONE else View.VISIBLE
            text = value.warnings.joinToString("\n") { "• $it" }
        }
        refreshSummary()
    }

    private fun refreshSummary() {
        val total = envelope?.changes?.size ?: 0
        val selected = adapter.selectedChanges().size
        findViewById<TextView>(R.id.recordChangeSummary).text =
            getString(R.string.ai_record_preview_summary, total, selected)
        applyButton.isEnabled = selected > 0
    }

    private fun confirmSelection() {
        val selected = adapter.selectedChanges()
        if (selected.isEmpty()) {
            Snackbar.make(root, R.string.ai_record_none_selected, Snackbar.LENGTH_SHORT).show()
            return
        }
        val deletes = selected.filter { it.operation == AiRecordOperation.DELETE }
        if (deletes.isEmpty()) {
            applySelected(selected)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ai_record_delete_confirm_title)
            .setMessage(
                deletes.joinToString("\n") {
                    getString(R.string.ai_record_delete_confirm_line, it.displayName)
                }
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_record_delete_confirm_action) { _, _ -> applySelected(selected) }
            .show()
    }

    private fun applySelected(selected: List<com.example.campusmate.domain.ai.command.AiRecordChange>) {
        applyButton.isEnabled = false
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                AiRecordChangeApplier(RepositoryAiRecordChangeGateway(applicationContext)).apply(selected)
            }
            val message = buildString {
                append(getString(R.string.ai_record_apply_success, report.successCount))
                if (report.failures.isNotEmpty()) {
                    append("\n")
                    append(getString(R.string.ai_record_apply_failed, report.failures.size))
                    report.failures.take(8).forEach { append("\n• ${it.displayName}：${it.reason}") }
                }
            }
            MaterialAlertDialogBuilder(this@AiRecordChangePreviewActivity)
                .setTitle(R.string.ai_record_apply_report_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    setResult(RESULT_OK)
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    companion object {
        private const val EXTRA_ENVELOPE = "extra_ai_record_command_envelope"

        fun intentFor(context: Context, envelope: AiRecordCommandEnvelope): Intent =
            Intent(context, AiRecordChangePreviewActivity::class.java)
                .putExtra(EXTRA_ENVELOPE, envelope)
    }
}
