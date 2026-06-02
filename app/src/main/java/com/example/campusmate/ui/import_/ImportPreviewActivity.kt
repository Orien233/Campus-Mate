package com.example.campusmate.ui.import_

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.ImportLogRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.SettingsSectionTimeSlot
import com.example.campusmate.domain.import_.CourseDraft
import com.example.campusmate.domain.import_.SectionTimeSlot
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/** Preview and confirmation screen for parsed course drafts. */
class ImportPreviewActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var adapter: CourseDraftAdapter
    private lateinit var courseRepository: CourseRepository
    private lateinit var importLogRepository: ImportLogRepository
    private lateinit var previewInfoText: TextView
    private var sourceType: Int = ImportLog.SOURCE_PASTED_HTML
    private var parserLabel: String = ""
    private var fallbackReason: String? = null
    private var warnings: List<String> = emptyList()
    private var draftItems: List<CourseDraftItem> = emptyList()
    private var sectionTimeSlots: List<SectionTimeSlot> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_preview)

        rootView = findViewById(R.id.importPreviewRoot)
        courseRepository = CourseRepository(this)
        importLogRepository = ImportLogRepository(this)
        sourceType = intent.getIntExtra(EXTRA_SOURCE_TYPE, ImportLog.SOURCE_PASTED_HTML)
        parserLabel = intent.getStringExtra(EXTRA_PARSER_LABEL).orEmpty()
        fallbackReason = intent.getStringExtra(EXTRA_FALLBACK_REASON)
        warnings = intent.getStringArrayListExtra(EXTRA_WARNINGS).orEmpty()
        setupToolbar()
        setupList()
        setupActions()
        loadDrafts()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.importPreviewToolbar)
        toolbar.title = getString(R.string.import_preview_title)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupList() {
        adapter = CourseDraftAdapter()
        previewInfoText = findViewById(R.id.importPreviewInfoText)
        findViewById<RecyclerView>(R.id.courseDraftRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ImportPreviewActivity)
            adapter = this@ImportPreviewActivity.adapter
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.importSkipConflictsButton).setOnClickListener {
            importSelected(skipConflicts = true)
        }
        findViewById<MaterialButton>(R.id.importAllSelectedButton).setOnClickListener {
            importSelected(skipConflicts = false)
        }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun loadDrafts() {
        val drafts = intent.getSerializableExtra(EXTRA_DRAFTS) as? ArrayList<CourseDraft> ?: arrayListOf()
        sectionTimeSlots =
            (intent.getSerializableExtra(EXTRA_SECTION_TIME_SLOTS) as? ArrayList<SectionTimeSlot>)?.toList()
                ?: emptyList()
        draftItems = drafts.mapIndexed { index, draft ->
            val hasExistingConflict = courseRepository.hasTimeConflict(
                weekday = draft.weekday,
                startSection = draft.startSection,
                endSection = draft.endSection,
                startWeek = draft.startWeek,
                endWeek = draft.endWeek,
                weekType = draft.weekType
            )
            val hasImportConflict = drafts.withIndex().any { (otherIndex, otherDraft) ->
                otherIndex != index && draft.conflictsWith(otherDraft)
            }
            val hasConflict = hasExistingConflict || hasImportConflict
            CourseDraftItem(
                draft = draft,
                hasConflict = hasConflict,
                conflictMessage = if (hasConflict) getString(R.string.import_conflict_message) else null,
                selected = !hasConflict
            )
        }
        adapter.submitList(draftItems)
        findViewById<TextView>(R.id.importPreviewSummaryText).text = getString(
            R.string.import_preview_summary,
            draftItems.size,
            draftItems.count { it.hasConflict },
            draftItems.count { it.selected }
        )
        previewInfoText.text = buildPreviewInfoText()
        previewInfoText.visibility = if (previewInfoText.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun buildPreviewInfoText(): CharSequence? {
        val parts = mutableListOf<String>()
        if (parserLabel.isNotBlank()) {
            parts.add(getString(R.string.import_preview_parser_info, displayParserLabel(parserLabel)))
        }
        if (sectionTimeSlots.isNotEmpty()) {
            parts.add(getString(R.string.import_preview_section_time_info, sectionTimeSlots.size))
        }
        fallbackReason?.takeIf { it.isNotBlank() }?.let {
            parts.add(getString(R.string.import_preview_fallback_info, it))
        }
        if (warnings.isNotEmpty()) {
            val previewWarnings = warnings.take(3)
            val tail = if (warnings.size > previewWarnings.size) {
                "\n" + getString(R.string.import_preview_warning_more, warnings.size - previewWarnings.size)
            } else {
                ""
            }
            parts.add(
                getString(R.string.import_preview_warning_info, warnings.size) +
                    "\n" +
                    previewWarnings.joinToString(separator = "\n") +
                    tail
            )
        }
        return parts.joinToString(separator = "\n\n").takeIf { it.isNotBlank() }
    }

    private fun importSelected(skipConflicts: Boolean) {
        val selected = adapter.selectedItems()
        val candidates = if (skipConflicts) selected.filterNot { it.hasConflict } else selected
        if (candidates.isEmpty()) {
            Snackbar.make(rootView, R.string.import_no_selected_courses, Snackbar.LENGTH_SHORT).show()
            return
        }

        val importedCount = courseRepository.addCourses(candidates.map { it.draft.toCourse() })
        val skippedCount = draftItems.size - importedCount
        val conflictCount = draftItems.count { it.hasConflict }
        val resolvedParserLabel = displayParserLabel(parserLabel).ifBlank {
            getString(R.string.import_preview_parser_unknown)
        }
        importLogRepository.addImportLog(
            ImportLog(
                sourceType = sourceType,
                importedCount = importedCount,
                skippedCount = skippedCount,
                conflictCount = conflictCount,
                message = getString(
                    R.string.import_log_message_with_parser,
                    importedCount,
                    skippedCount,
                    conflictCount,
                    resolvedParserLabel
                )
            )
        )

        // Apply extracted section clock times only after the user confirms import.
        val appliedSectionTimes = sectionTimeSlots.isNotEmpty()
        if (appliedSectionTimes) {
            SettingsRepository(this).setSectionTimeSlots(
                sectionTimeSlots.map { SettingsSectionTimeSlot(it.section, it.startTime, it.endTime) }
            )
        }

        val baseMessage = getString(R.string.import_success_message, importedCount, skippedCount)
        val finalMessage = if (appliedSectionTimes) {
            baseMessage + "\n" + getString(R.string.import_section_time_applied)
        } else {
            baseMessage
        }
        Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun CourseDraft.conflictsWith(other: CourseDraft): Boolean {
        return weekday == other.weekday &&
            startSection <= other.endSection &&
            endSection >= other.startSection &&
            startWeek <= other.endWeek &&
            endWeek >= other.startWeek &&
            weekTypesCompatible(weekType, other.weekType)
    }

    private fun weekTypesCompatible(typeA: Int, typeB: Int): Boolean {
        return typeA == Course.WEEK_TYPE_EVERY ||
            typeB == Course.WEEK_TYPE_EVERY ||
            typeA == typeB
    }

    private fun CourseDraft.toCourse(): Course {
        return Course(
            name = name,
            teacher = teacher,
            classroom = classroom,
            weekday = weekday,
            startSection = startSection,
            endSection = endSection,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekType,
            color = color,
            note = note
        )
    }

    private fun displayParserLabel(rawLabel: String): String {
        return when {
            rawLabel.contains("AI", ignoreCase = true) -> getString(R.string.import_ai_mode)
            rawLabel.contains("Local", ignoreCase = true) -> getString(R.string.import_local_mode)
            else -> rawLabel
        }
    }

    companion object {
        const val EXTRA_DRAFTS = "extra_drafts"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
        const val EXTRA_PARSER_LABEL = "extra_parser_label"
        const val EXTRA_FALLBACK_REASON = "extra_fallback_reason"
        const val EXTRA_WARNINGS = "extra_warnings"
        const val EXTRA_SECTION_TIME_SLOTS = "extra_section_time_slots"
    }
}
