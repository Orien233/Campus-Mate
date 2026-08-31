package com.example.campusmate.ui.ai.memory

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.campusmate.R
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.AiMemoryDraft
import com.example.campusmate.data.repository.AiMemoryRepository
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** An explicit local form; only Save writes the user's draft. */
class AiMemoryEditActivity : AppCompatActivity() {
    private lateinit var repository: AiMemoryRepository
    private lateinit var saveViewModel: AiMemorySaveViewModel
    private lateinit var root: View
    private lateinit var progress: View
    private lateinit var categoryInput: MaterialAutoCompleteTextView
    private lateinit var contentLayout: TextInputLayout
    private lateinit var contentInput: TextInputEditText
    private lateinit var retentionInput: MaterialAutoCompleteTextView
    private lateinit var enabledSwitch: SwitchMaterial
    private lateinit var pinnedSwitch: SwitchMaterial
    private lateinit var saveButton: MaterialButton

    private var memoryId = 0L
    private var category = AiMemory.CATEGORY_GOAL
    private var retention = RETENTION_FOREVER
    private var originalExpiresAt: Long? = null
    private var ready = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_memory_edit)
        repository = AiMemoryRepository(this)
        saveViewModel = ViewModelProvider(this)[AiMemorySaveViewModel::class.java]
        saveViewModel.restorePendingSave(savedInstanceState?.getBoolean(STATE_SAVE_PENDING) == true)
        memoryId = intent.getLongExtra(EXTRA_MEMORY_ID, 0L)
        root = findViewById(R.id.aiMemoryEditRoot)
        progress = findViewById(R.id.aiMemoryEditProgress)
        categoryInput = findViewById(R.id.aiMemoryCategoryInput)
        contentLayout = findViewById(R.id.aiMemoryContentInputLayout)
        contentInput = findViewById(R.id.aiMemoryContentInput)
        retentionInput = findViewById(R.id.aiMemoryRetentionInput)
        enabledSwitch = findViewById(R.id.aiMemoryItemEnabledSwitch)
        pinnedSwitch = findViewById(R.id.aiMemoryPinnedSwitch)
        saveButton = findViewById(R.id.saveAiMemoryButton)
        contentLayout.counterMaxLength = AiMemory.MAX_CONTENT_LENGTH

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.aiMemoryEditToolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(if (memoryId > 0L) R.string.ai_memory_edit else R.string.ai_memory_add)
        }
        categoryInput.setOnItemClickListener { _, _, position, _ ->
            category = AiMemoryUiFormatter.categories[position]
        }
        retentionInput.setOnItemClickListener { _, _, position, _ -> retention = position }
        saveButton.setOnClickListener { saveMemory() }

        when {
            savedInstanceState?.getBoolean(STATE_READY) == true -> {
                category = savedInstanceState.getInt(STATE_CATEGORY, AiMemory.CATEGORY_GOAL)
                retention = savedInstanceState.getInt(STATE_RETENTION, RETENTION_FOREVER)
                originalExpiresAt = savedInstanceState.getLong(STATE_EXPIRY, 0L).takeIf { it > 0L }
                contentInput.setText(savedInstanceState.getString(STATE_CONTENT).orEmpty())
                enabledSwitch.isChecked = savedInstanceState.getBoolean(STATE_ENABLED, true)
                pinnedSwitch.isChecked = savedInstanceState.getBoolean(STATE_PINNED, false)
                ready = true
                bindDropdowns()
                setBusy(false)
            }
            memoryId > 0L -> loadMemory()
            else -> {
                enabledSwitch.isChecked = true
                ready = true
                bindDropdowns()
            }
        }
        if (saveViewModel.state.value == AiMemorySaveViewModel.State.SAVING) setBusy(true)
        saveViewModel.state.observe(this, ::renderSaveState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_READY, ready)
        outState.putInt(STATE_CATEGORY, category)
        outState.putInt(STATE_RETENTION, retention)
        outState.putLong(STATE_EXPIRY, originalExpiresAt ?: 0L)
        outState.putString(STATE_CONTENT, contentInput.text?.toString().orEmpty())
        outState.putBoolean(STATE_ENABLED, enabledSwitch.isChecked)
        outState.putBoolean(STATE_PINNED, pinnedSwitch.isChecked)
        outState.putBoolean(STATE_SAVE_PENDING, saveViewModel.hasUnconfirmedSave)
        super.onSaveInstanceState(outState)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadMemory() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val memory = withContext(Dispatchers.IO) { repository.getMemoryById(memoryId) }
                if (memory == null) {
                    showMessage(R.string.ai_memory_not_found)
                    return@launch
                }
                category = memory.category
                contentInput.setText(memory.content)
                enabledSwitch.isChecked = memory.isEnabled
                pinnedSwitch.isChecked = memory.isPinned
                originalExpiresAt = memory.expiresAt
                retention = if (originalExpiresAt == null) RETENTION_FOREVER else RETENTION_KEEP
                ready = true
                bindDropdowns()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(R.string.ai_memory_load_failed)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun bindDropdowns() {
        val categoryLabels = AiMemoryUiFormatter.categories.map { AiMemoryUiFormatter.category(this, it) }
        categoryInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryLabels))
        categoryInput.setText(AiMemoryUiFormatter.category(this, category), false)
        val retentionLabels = mutableListOf(
            getString(R.string.ai_memory_retention_forever),
            getString(R.string.ai_memory_retention_30),
            getString(R.string.ai_memory_retention_90),
            getString(R.string.ai_memory_retention_180)
        )
        originalExpiresAt?.let {
            retentionLabels.add(getString(R.string.ai_memory_retention_keep, DateTimeUtils.formatDate(it)))
        }
        retentionInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, retentionLabels))
        retentionInput.setText(retentionLabels[retention], false)
    }

    private fun saveMemory() {
        if (!ready || busy) return
        val content = contentInput.text?.toString().orEmpty().trim()
        contentLayout.error = when {
            content.isBlank() -> getString(R.string.ai_memory_required)
            content.length > AiMemory.MAX_CONTENT_LENGTH -> getString(R.string.ai_memory_too_long, AiMemory.MAX_CONTENT_LENGTH)
            else -> null
        }
        if (contentLayout.error != null) return
        val draft = AiMemoryDraft(
            category = category,
            content = content,
            isEnabled = enabledSwitch.isChecked,
            isPinned = pinnedSwitch.isChecked,
            expiresAt = selectedExpiry()
        )
        saveViewModel.save(memoryId, draft)
    }

    private fun renderSaveState(state: AiMemorySaveViewModel.State) {
        when (state) {
            AiMemorySaveViewModel.State.IDLE -> Unit
            AiMemorySaveViewModel.State.SAVING -> setBusy(true)
            AiMemorySaveViewModel.State.SAVED -> {
                ready = false
                setBusy(false)
                setResult(RESULT_OK)
                finish()
            }
            AiMemorySaveViewModel.State.FAILED -> {
                setBusy(false)
                showMessage(R.string.ai_memory_operation_failed)
            }
            AiMemorySaveViewModel.State.LIMIT -> {
                setBusy(false)
                Snackbar.make(
                    root, getString(R.string.ai_memory_limit, AiMemory.MAX_MEMORY_COUNT), Snackbar.LENGTH_LONG
                ).show()
            }
            AiMemorySaveViewModel.State.UNCERTAIN -> {
                ready = false
                setBusy(false)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ai_memory_title)
                    .setMessage(R.string.ai_memory_save_uncertain)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ai_memory_return_to_list) { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun selectedExpiry(): Long? {
        val days = when (retention) {
            1 -> 30
            2 -> 90
            3 -> 180
            RETENTION_KEEP -> return originalExpiresAt
            else -> return null
        }
        return Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
    }

    private fun setBusy(value: Boolean) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        val editable = ready && !value && saveViewModel.state.value in setOf(
            AiMemorySaveViewModel.State.IDLE,
            AiMemorySaveViewModel.State.FAILED,
            AiMemorySaveViewModel.State.LIMIT
        )
        saveButton.isEnabled = editable
        categoryInput.isEnabled = editable
        contentInput.isEnabled = editable
        retentionInput.isEnabled = editable
        enabledSwitch.isEnabled = editable
        pinnedSwitch.isEnabled = editable
    }

    private fun showMessage(messageRes: Int) {
        Snackbar.make(root, messageRes, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_MEMORY_ID = "extra_memory_id"
        private const val RETENTION_FOREVER = 0
        private const val RETENTION_KEEP = 4
        private const val STATE_READY = "memory_edit_ready"
        private const val STATE_CATEGORY = "memory_edit_category"
        private const val STATE_CONTENT = "memory_edit_content"
        private const val STATE_RETENTION = "memory_edit_retention"
        private const val STATE_EXPIRY = "memory_edit_expiry"
        private const val STATE_ENABLED = "memory_edit_enabled"
        private const val STATE_PINNED = "memory_edit_pinned"
        private const val STATE_SAVE_PENDING = "memory_edit_save_pending"
    }
}
