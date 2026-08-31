package com.example.campusmate.ui.ai.memory

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.repository.AiMemoryRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.domain.ai.memory.LearningGrowthSummary
import com.example.campusmate.domain.ai.memory.LearningGrowthSummaryBuilder
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local management only: this Activity has no model client or generation service. */
class AiMemoryActivity : AppCompatActivity() {
    private lateinit var repository: AiMemoryRepository
    private lateinit var settings: SettingsRepository
    private lateinit var studyRecords: StudyRecordRepository
    private lateinit var root: View
    private lateinit var progress: View
    private lateinit var errorContainer: View
    private lateinit var recycler: RecyclerView
    private lateinit var addButton: FloatingActionButton
    private lateinit var adapter: AiMemoryAdapter

    private var memories: List<AiMemory> = emptyList()
    private var summary: LearningGrowthSummary? = null
    private var staleCount = 0
    private var dailyGoalMinutes = 0
    private var busy = false
    private var weeksExpanded = false

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) showMessage(getString(R.string.ai_memory_saved))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_memory)
        repository = AiMemoryRepository(this)
        settings = SettingsRepository(this)
        studyRecords = StudyRecordRepository(this)
        root = findViewById(R.id.aiMemoryRoot)
        progress = findViewById(R.id.aiMemoryProgress)
        errorContainer = findViewById(R.id.aiMemoryErrorContainer)
        recycler = findViewById(R.id.aiMemoryRecyclerView)
        addButton = findViewById(R.id.aiMemoryAddFab)
        weeksExpanded = savedInstanceState?.getBoolean(STATE_WEEKS_EXPANDED) ?: false

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.aiMemoryToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        adapter = AiMemoryAdapter(
            bindHeader = ::bindHeader,
            onEdit = { openEditor(it.id) },
            onToggleEnabled = { memory ->
                mutate { repository.setEnabled(memory.id, !memory.isEnabled) }
            },
            onTogglePinned = { memory ->
                mutate { repository.setPinned(memory.id, !memory.isPinned) }
            },
            onDelete = ::confirmDelete
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.visibility = View.GONE
        addButton.setOnClickListener { openEditor() }
        findViewById<MaterialButton>(R.id.aiMemoryRetryButton).setOnClickListener { loadData() }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_WEEKS_EXPANDED, weeksExpanded)
        super.onSaveInstanceState(outState)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadData(showRefreshMessage: Boolean = false) {
        if (busy) return
        setBusy(true)
        errorContainer.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val anchor = DateTimeUtils.todayDate()
                    val start = DateTimeUtils.datePlusDays(anchor, 1 - LearningGrowthSummaryBuilder.WINDOW_DAYS)
                    val goal = settings.getDailyGoalMinutes()
                    val records = studyRecords.getRecordsBetween(start, anchor)
                    LoadedData(
                        repository.getMemoriesForManagement(),
                        repository.countStale(),
                        LearningGrowthSummaryBuilder.build(records, anchor, goal),
                        goal
                    )
                }
                memories = data.memories
                staleCount = data.staleCount
                summary = data.summary
                dailyGoalMinutes = data.goal
                adapter.submitList(memories)
                recycler.visibility = View.VISIBLE
                if (showRefreshMessage) showMessage(getString(R.string.ai_memory_growth_refreshed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorContainer.visibility = View.VISIBLE
            } finally {
                setBusy(false)
            }
        }
    }

    private fun bindHeader(view: View) {
        view.findViewById<SwitchMaterial>(R.id.aiMemoryEnabledSwitch).apply {
            setOnCheckedChangeListener(null)
            isChecked = settings.isAiMemoryEnabled()
            isEnabled = !busy
            setOnCheckedChangeListener { _, checked -> settings.setAiMemoryEnabled(checked) }
        }
        val current = summary
        view.findViewById<TextView>(R.id.aiMemoryGrowthRangeText).text = current?.let {
            getString(R.string.ai_memory_growth_range, it.rangeStart, it.rangeEnd, dailyGoalMinutes)
        }.orEmpty()
        view.findViewById<TextView>(R.id.aiMemoryGrowthSummaryText).text =
            if (current == null || current.sessionCount == 0) getString(R.string.ai_memory_growth_empty)
            else getString(
                R.string.ai_memory_growth_summary, current.totalMinutes, current.activeDays,
                current.sessionCount, current.goalHitDays, current.currentStreakDays
            )
        view.findViewById<TextView>(R.id.aiMemoryGrowthComparisonText).text = current?.let {
            getString(R.string.ai_memory_growth_comparison, it.recentMinutes, it.previousMinutes)
        }.orEmpty()
        view.findViewById<TextView>(R.id.aiMemoryGrowthWeeksText).apply {
            visibility = if (weeksExpanded) View.VISIBLE else View.GONE
            text = current?.weeks?.joinToString("\n") {
                getString(R.string.ai_memory_growth_week, it.rangeStart, it.rangeEnd, it.minutes, it.activeDays, it.sessionCount)
            }.orEmpty()
        }
        view.findViewById<MaterialButton>(R.id.aiMemoryGrowthToggleButton).apply {
            visibility = if (current == null || current.sessionCount == 0) View.GONE else View.VISIBLE
            setText(if (weeksExpanded) R.string.ai_memory_growth_hide else R.string.ai_memory_growth_details)
            setOnClickListener {
                weeksExpanded = !weeksExpanded
                adapter.refreshHeader()
            }
        }
        view.findViewById<MaterialButton>(R.id.aiMemoryRefreshGrowthButton).apply {
            isEnabled = !busy
            setOnClickListener { loadData(showRefreshMessage = true) }
        }
        view.findViewById<TextView>(R.id.aiMemoryCountText).text =
            getString(R.string.ai_memory_count, memories.size, AiMemory.MAX_MEMORY_COUNT, staleCount)
        view.findViewById<TextView>(R.id.aiMemoryEmptyText).visibility =
            if (memories.isEmpty()) View.VISIBLE else View.GONE
        view.findViewById<MaterialButton>(R.id.aiMemoryCleanupButton).apply {
            isEnabled = !busy
            setOnClickListener { confirmCleanup() }
        }
    }

    private fun openEditor(memoryId: Long = 0L) {
        if (busy) return
        if (memoryId == 0L && memories.size >= AiMemory.MAX_MEMORY_COUNT) {
            showMessage(getString(R.string.ai_memory_limit, AiMemory.MAX_MEMORY_COUNT))
            return
        }
        editLauncher.launch(Intent(this, AiMemoryEditActivity::class.java).apply {
            if (memoryId > 0L) putExtra(AiMemoryEditActivity.EXTRA_MEMORY_ID, memoryId)
        })
    }

    private fun confirmDelete(memory: AiMemory) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_delete)
            .setMessage(R.string.ai_memory_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                mutate(getString(R.string.ai_memory_delete_success)) { repository.deleteMemory(memory.id) }
            }
            .show()
    }

    private fun confirmCleanup() {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val cutoff = System.currentTimeMillis()
                val count = withContext(Dispatchers.IO) { repository.countStale(cutoff) }
                if (count == 0) {
                    showMessage(getString(R.string.ai_memory_cleanup_none))
                } else {
                    MaterialAlertDialogBuilder(this@AiMemoryActivity)
                        .setTitle(R.string.ai_memory_cleanup)
                        .setMessage(getString(R.string.ai_memory_cleanup_confirm, count))
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.ai_memory_cleanup) { _, _ -> pruneExpired(cutoff) }
                        .show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(getString(R.string.ai_memory_operation_failed))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun pruneExpired(cutoff: Long) {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) { repository.pruneExpired(cutoff) }
                showMessage(getString(R.string.ai_memory_cleanup_success, removed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(getString(R.string.ai_memory_operation_failed))
            } finally {
                setBusy(false)
            }
            loadData()
        }
    }

    private fun mutate(
        successMessage: String = getString(R.string.ai_memory_changed),
        action: () -> Boolean
    ) {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { action() }
                showMessage(if (saved) successMessage else getString(R.string.ai_memory_operation_failed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(getString(R.string.ai_memory_operation_failed))
            } finally {
                setBusy(false)
            }
            loadData()
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        addButton.isEnabled = !value
        adapter.busy = value
        adapter.refreshHeader()
    }

    private fun showMessage(message: String) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    }

    private data class LoadedData(
        val memories: List<AiMemory>,
        val staleCount: Int,
        val summary: LearningGrowthSummary,
        val goal: Int
    )

    private companion object {
        const val STATE_WEEKS_EXPANDED = "memory_weeks_expanded"
    }
}
