package com.example.campusmate.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.ui.focus.FocusActivity
import com.example.campusmate.ui.import_.ImportScheduleActivity
import com.example.campusmate.ui.main.MainActivity
import com.example.campusmate.ui.task.TaskEditActivity
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private lateinit var courseRepository: CourseRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var studyRecordRepository: StudyRecordRepository
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var weatherRepository: WeatherRepository
    private var weatherLoadToken: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        courseRepository = CourseRepository(requireContext())
        taskRepository = TaskRepository(requireContext())
        studyRecordRepository = StudyRecordRepository(requireContext())
        planRepository = StudyPlanRepository(requireContext())
        settingsRepository = SettingsRepository(requireContext())
        weatherRepository = WeatherRepository(requireContext())

        view.findViewById<MaterialButton>(R.id.startFocusButton).setOnClickListener {
            startActivity(Intent(requireContext(), FocusActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.addTaskButton).setOnClickListener {
            startActivity(Intent(requireContext(), TaskEditActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.importScheduleButton).setOnClickListener {
            startActivity(Intent(requireContext(), ImportScheduleActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.refreshWeatherButton).setOnClickListener {
            loadWeather(forceRefresh = true)
        }
        view.findViewById<MaterialButton>(R.id.generatePlanButton).setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_plan)
        }
        view.findViewById<MaterialButton>(R.id.viewStatisticsButton).setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_statistics)
        }

        CollapsibleSection.bind(
            root = view,
            headerId = R.id.weatherHeader,
            contentId = R.id.weatherExpandedContent,
            indicatorId = R.id.weatherExpandIndicator
        )
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.quickActionsHeader,
            contentId = R.id.quickActionsExpandedContent,
            indicatorId = R.id.quickActionsExpandIndicator
        )
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val currentView = view ?: return

        val todayCourses = courseRepository.getTodayCourses()
        val pendingTasks = taskRepository.getAllTasks().count { it.status == StudyTask.STATUS_TODO }
        val todayDurationMinutes = getCompletedTaskStudyMinutesForDate(DateTimeUtils.todayDate())
        val currentWeekDurationMinutes = getCurrentWeekCompletedMinutes()
        val yesterdayDurationMinutes = getCompletedTaskStudyMinutesForDate(DateTimeUtils.offsetDate(DateTimeUtils.todayDate(), -1))
        val previousWeekDurationMinutes = getPreviousWeekCompletedMinutes()

        val todayTrendDelta = todayDurationMinutes - yesterdayDurationMinutes
        val weekTrendDelta = currentWeekDurationMinutes - previousWeekDurationMinutes

        val todayPlans = planRepository.getPlansByDate(DateTimeUtils.todayDate())
        val completedPlans = todayPlans.count { it.status == StudyPlan.STATUS_COMPLETED }
        val totalPlans = todayPlans.size
        val planCompletionText = if (totalPlans > 0) {
            "$completedPlans/$totalPlans 计划 · ${completedPlans * 100 / totalPlans}% 完成"
        } else {
            "暂无今日计划"
        }

        currentView.findViewById<TextView>(R.id.tvTodayCourseCount).text = todayCourses.size.toString()
        currentView.findViewById<TextView>(R.id.tvPendingTaskCount).text = pendingTasks.toString()
        currentView.findViewById<TextView>(R.id.tvTodayFocusMinutes).text =
            getString(R.string.duration_minutes, todayDurationMinutes)
        currentView.findViewById<TextView>(R.id.tvTodayPlanCompletion).text = planCompletionText
        currentView.findViewById<TextView>(R.id.tvWeekFocusMinutes).text =
            getString(R.string.duration_minutes, currentWeekDurationMinutes)
        currentView.findViewById<TextView>(R.id.tvTodayTrend).text = formatTrend(todayTrendDelta)
        currentView.findViewById<TextView>(R.id.tvWeekTrend).text = formatWeekTrend(weekTrendDelta)
        currentView.findViewById<TextView>(R.id.tvTodayTrend).setTextColor(colorForDelta(todayTrendDelta))
        currentView.findViewById<TextView>(R.id.tvWeekTrend).setTextColor(colorForDelta(weekTrendDelta))
        currentView.findViewById<TextView>(R.id.tvNextCourseValue).text =
            todayCourses.firstOrNull()?.let {
                getString(R.string.dashboard_next_course_format, it.name, it.startSection, it.endSection)
            } ?: getString(R.string.dashboard_no_next_course)

        loadWeather(forceRefresh = false)
    }

    private fun loadWeather(forceRefresh: Boolean) {
        val currentView = view ?: return
        val city = settingsRepository.getWeatherCity()
        val useMock = settingsRepository.isMockWeatherEnabled()
        val token = DateTimeUtils.nowMillis()
        weatherLoadToken = token

        currentView.findViewById<TextView>(R.id.weatherCityText).text = city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = getString(R.string.dashboard_weather_empty)
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = ""
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""

        Thread {
            val weather = weatherRepository.getWeather(city, useMock, forceRefresh)
            val targetView = view ?: return@Thread
            targetView.post {
                if (weatherLoadToken == token && view != null) {
                    bindWeather(weather)
                }
            }
        }.start()
    }

    private fun colorForDelta(delta: Int): Int {
        return resources.getColor(
            if (delta >= 0) R.color.campus_success else R.color.campus_accent,
            null
        )
    }

    private fun formatTrend(delta: Int): String {
        val sign = if (delta >= 0) "+" else ""
        return "较昨日 $sign$delta 分钟"
    }

    private fun formatWeekTrend(delta: Int): String {
        val sign = if (delta >= 0) "+" else ""
        return "较上周 $sign$delta 分钟"
    }

    private fun getCompletedTaskStudyMinutesForDate(date: String): Int {
        return planRepository.getPlansByDate(date)
            .filter { it.status == StudyPlan.STATUS_COMPLETED }
            .sumOf { it.actualMinutes.takeIf { minutes -> minutes > 0 } ?: it.plannedMinutes }
    }

    private fun getCurrentWeekCompletedMinutes(): Int {
        val weekStart = DateTimeUtils.startOfWeekDate()
        return (0..6).sumOf { offset ->
            val date = DateTimeUtils.offsetDate(weekStart, offset)
            getCompletedTaskStudyMinutesForDate(date)
        }
    }

    private fun getPreviousWeekCompletedMinutes(): Int {
        val previousWeekStart = DateTimeUtils.startOfWeekDate(offsetWeeks = 1)
        return (0..6).sumOf { offset ->
            val date = DateTimeUtils.offsetDate(previousWeekStart, offset)
            getCompletedTaskStudyMinutesForDate(date)
        }
    }

    private fun bindWeather(weather: WeatherResult?) {
        val currentView = view ?: return
        if (weather == null) {
            currentView.findViewById<TextView>(R.id.weatherTemperatureText).text =
                getString(R.string.dashboard_weather_empty)
            return
        }
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = weather.temperature
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = weather.weatherText
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text =
            getString(R.string.dashboard_weather_humidity, weather.humidity)
        currentView.findViewById<TextView>(R.id.weatherWindText).text =
            getString(R.string.dashboard_weather_wind, weather.wind)
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text =
            getString(R.string.dashboard_weather_update, weather.updatedAt, weather.source)
    }
}
