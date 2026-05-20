package com.example.campusmate.ui.import_

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.ImportLogRepository
import com.example.campusmate.domain.import_.CourseDraft
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/** Preview and confirmation screen for parsed course drafts. */
class ImportPreviewActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var adapter: CourseDraftAdapter
    private lateinit var courseRepository: CourseRepository
    private lateinit var importLogRepository: ImportLogRepository
    private var sourceType: Int = ImportLog.SOURCE_PASTED_HTML
    private var draftItems: List<CourseDraftItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_preview)

        rootView = findViewById(R.id.importPreviewRoot)
        courseRepository = CourseRepository(this)
        importLogRepository = ImportLogRepository(this)
        sourceType = intent.getIntExtra(EXTRA_SOURCE_TYPE, ImportLog.SOURCE_PASTED_HTML)
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
        importLogRepository.addImportLog(
            ImportLog(
                sourceType = sourceType,
                importedCount = importedCount,
                skippedCount = skippedCount,
                conflictCount = conflictCount,
                message = getString(R.string.import_log_message, importedCount, skippedCount, conflictCount)
            )
        )

        Toast.makeText(this, getString(R.string.import_success_message, importedCount, skippedCount), Toast.LENGTH_LONG).show()
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

    companion object {
        const val EXTRA_DRAFTS = "extra_drafts"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
    }
}
