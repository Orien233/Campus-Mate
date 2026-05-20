package com.example.campusmate.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.domain.statistics.HeatmapCalculator
import com.example.campusmate.domain.statistics.HeatmapDay
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

/** Shows study duration summary, heatmap, and per-day study records. */
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {
    private lateinit var repository: StudyRecordRepository
    private val calculator = HeatmapCalculator()
    private lateinit var heatmapAdapter: HeatmapAdapter
    private lateinit var todayDurationText: TextView
    private lateinit var weekDurationText: TextView
    private lateinit var streakText: TextView
    private lateinit var emptyStateView: View
    private lateinit var contentView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = StudyRecordRepository(requireContext())
        todayDurationText = view.findViewById(R.id.statisticsTodayDurationText)
        weekDurationText = view.findViewById(R.id.statisticsWeekDurationText)
        streakText = view.findViewById(R.id.statisticsStreakText)
        emptyStateView = view.findViewById(R.id.statisticsEmptyState)
        contentView = view.findViewById(R.id.statisticsContent)

        heatmapAdapter = HeatmapAdapter { day -> showDayRecords(day) }
        view.findViewById<RecyclerView>(R.id.heatmapRecyclerView).apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = heatmapAdapter
            setHasFixedSize(true)
        }
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
        val heatmapDays = calculator.calculate(stats, today)
        val todayDuration = repository.getTodayDuration()
        val weeklyDuration = repository.getWeeklyDuration()
        val streak = calculator.calculateStreak(stats, today)

        todayDurationText.text = getString(R.string.duration_minutes, todayDuration / 60)
        weekDurationText.text = getString(R.string.duration_minutes, weeklyDuration / 60)
        streakText.text = getString(R.string.statistics_streak_days, streak)
        heatmapAdapter.submitList(heatmapDays)

        val hasRecords = stats.any { it.durationSec > 0 }
        emptyStateView.visibility = if (hasRecords) View.GONE else View.VISIBLE
        contentView.visibility = View.VISIBLE
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
}
