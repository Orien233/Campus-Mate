package com.example.campusmate.ui.statistics

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.statistics.HeatmapCalculator
import com.example.campusmate.domain.statistics.HeatmapDay
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.ui.focus.FocusActivity
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

/** Shows study duration summary, heatmap, and per-day study records. */
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {
    private lateinit var repository: StudyRecordRepository
    private lateinit var taskRepository: TaskRepository
    private val calculator = HeatmapCalculator()
    private lateinit var heatmapAdapter: HeatmapAdapter
    private lateinit var recordsAdapter: StudyRecordAdapter
    private lateinit var todayDurationText: TextView
    private lateinit var weekDurationText: TextView
    private lateinit var streakText: TextView
    private lateinit var heatmapSubtitleText: TextView
    private lateinit var heatmapIndicatorText: TextView
    private lateinit var recordsEmptyStateView: View
    private lateinit var recordsRecyclerView: RecyclerView
    private lateinit var taskCompletionSummaryText: TextView
    private lateinit var taskCompletionDetailText: TextView
    private lateinit var contentView: View
    private var heatmapExpanded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = StudyRecordRepository(requireContext())
        taskRepository = TaskRepository(requireContext())
        todayDurationText = view.findViewById(R.id.statisticsTodayDurationText)
        weekDurationText = view.findViewById(R.id.statisticsWeekDurationText)
        streakText = view.findViewById(R.id.statisticsStreakText)
        heatmapSubtitleText = view.findViewById(R.id.statisticsHeatmapSubtitle)
        heatmapIndicatorText = view.findViewById(R.id.statisticsHeatmapIndicator)
        recordsEmptyStateView = view.findViewById(R.id.statisticsRecordsEmptyState)
        recordsRecyclerView = view.findViewById(R.id.statisticsRecordsRecyclerView)
        taskCompletionSummaryText = view.findViewById(R.id.taskCompletionSummaryText)
        taskCompletionDetailText = view.findViewById(R.id.taskCompletionDetailText)
        contentView = view.findViewById(R.id.statisticsContent)

        heatmapAdapter = HeatmapAdapter { day -> showDayRecords(day) }
        view.findViewById<RecyclerView>(R.id.heatmapRecyclerView).apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = heatmapAdapter
            setHasFixedSize(true)
        }
        recordsAdapter = StudyRecordAdapter()
        recordsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recordsAdapter
            setHasFixedSize(false)
        }
        view.findViewById<View>(R.id.statisticsHeatmapHeader).setOnClickListener {
            heatmapExpanded = !heatmapExpanded
            loadStatistics()
        }
        view.findViewById<MaterialButton>(R.id.statisticsStartFocusButton).setOnClickListener {
            startActivity(Intent(requireContext(), FocusActivity::class.java))
        }
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.statisticsRecordsHeader,
            contentId = R.id.statisticsRecordsContent,
            indicatorId = R.id.statisticsRecordsIndicator,
            expandedByDefault = true
        )
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.statisticsTaskHeader,
            contentId = R.id.statisticsTaskContent,
            indicatorId = R.id.statisticsTaskIndicator
        )
    }

    override fun onResume() {
        super.onResume()
        loadStatistics()
    }

    private fun loadStatistics() {
        val today = DateTimeUtils.todayDate()
        val startDate = LocalDate.parse(today, HeatmapCalculator.formatter)
            .minusDays((HeatmapCalculator.DEFAULT_DAY_COUNT - 1).toLong())
            .format(HeatmapCalculator.formatter)
        val stats = repository.getDailyStats(startDate, today)
        val todayDuration = repository.getTodayDuration()
        val weeklyDuration = repository.getWeeklyDuration()
        val streak = calculator.calculateStreak(stats, today)

        todayDurationText.text = getString(R.string.duration_minutes, todayDuration / 60)
        weekDurationText.text = getString(R.string.duration_minutes, weeklyDuration / 60)
        streakText.text = getString(R.string.statistics_streak_days, streak)
        bindHeatmap(stats, today)
        bindStudyRecords(startDate, today)
        bindTaskCompletion()

        contentView.visibility = View.VISIBLE
    }

    private fun bindHeatmap(stats: List<com.example.campusmate.data.model.DailyStudyStat>, today: String) {
        val dayCount = if (heatmapExpanded) {
            HeatmapCalculator.DEFAULT_DAY_COUNT
        } else {
            COMPACT_HEATMAP_DAY_COUNT
        }
        heatmapAdapter.submitList(calculator.calculate(stats, today, dayCount))
        heatmapSubtitleText.setText(
            if (heatmapExpanded) R.string.statistics_heatmap_subtitle_expanded else R.string.statistics_heatmap_subtitle_compact
        )
        heatmapIndicatorText.setText(if (heatmapExpanded) R.string.ui_collapse else R.string.ui_expand)
    }

    private fun bindStudyRecords(startDate: String, today: String) {
        val records = repository.getRecordsBetween(startDate, today)
            .sortedByDescending { it.startAt ?: it.createdAt }
            .take(RECENT_RECORD_LIMIT)
        recordsAdapter.submitList(records)
        val isEmpty = records.isEmpty()
        recordsEmptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recordsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun bindTaskCompletion() {
        val tasks = taskRepository.getAllTasks()
        val total = tasks.size
        val done = tasks.count { it.status == StudyTask.STATUS_DONE }
        val todo = tasks.count { it.status == StudyTask.STATUS_TODO }
        val overdue = tasks.count {
            it.status == StudyTask.STATUS_TODO && it.dueAt?.let { dueAt -> dueAt < DateTimeUtils.nowMillis() } == true
        }
        val rate = if (total > 0) (done * 100) / total else 0
        taskCompletionSummaryText.text = getString(R.string.statistics_task_completion_summary, rate)
        taskCompletionDetailText.text = getString(R.string.statistics_task_completion_detail, done, todo, overdue)
    }

    private fun showDayRecords(day: HeatmapDay) {
        val records = repository.getRecordsByDate(day.date)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_day_records, null)
        dialogView.findViewById<TextView>(R.id.dayRecordsSummaryText).text =
            getString(R.string.statistics_day_summary, day.date, day.durationSec / 60, day.recordCount)
        val emptyText = dialogView.findViewById<TextView>(R.id.dayRecordsEmptyText)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.dayRecordsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = StudyRecordAdapter().apply { submitList(records) }
        val isEmpty = records.isEmpty()
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.statistics_day_detail_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    companion object {
        private const val COMPACT_HEATMAP_DAY_COUNT = 28
        private const val RECENT_RECORD_LIMIT = 5
    }
}
