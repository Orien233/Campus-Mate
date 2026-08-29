package com.example.campusmate.ui.dashboard

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.weather.WeatherLocationResolver
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.ui.focus.FocusActivity
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.PermissionUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dashboard entry point for daily course, task, and focus summaries. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private lateinit var courseRepository: CourseRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var planRepository: StudyPlanRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var weatherRepository: WeatherRepository
    private var weatherLoadToken: Long = 0L
    private var weatherGuideDialogShowing = false

    private val weatherLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            settingsRepository.setWeatherLocationGuideShown(true)
            if (granted) {
                loadWeatherFromLocation(forceRefresh = true, userTriggered = true)
            } else {
                view?.let {
                    Snackbar.make(it, R.string.dashboard_weather_permission_denied, Snackbar.LENGTH_LONG).show()
                }
                loadWeather(forceRefresh = false)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        courseRepository = CourseRepository(requireContext())
        taskRepository = TaskRepository(requireContext())
        planRepository = StudyPlanRepository(requireContext())
        settingsRepository = SettingsRepository(requireContext())
        weatherRepository = WeatherRepository(requireContext())

        view.findViewById<MaterialButton>(R.id.startFocusButton).setOnClickListener {
            startActivity(Intent(requireContext(), FocusActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.refreshWeatherButton).setOnClickListener {
            requestLocationAndLoadWeather(forceRefresh = true, userTriggered = true)
        }
        view.findViewById<MaterialButton>(R.id.refreshCurrentWeatherButton).setOnClickListener {
            loadWeather(forceRefresh = true)
        }
        CollapsibleSection.bind(
            root = view,
            headerId = R.id.weatherHeader,
            contentId = R.id.weatherExpandedContent,
            indicatorId = R.id.weatherExpandIndicator
        )
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
        if (!maybeShowWeatherLocationGuide()) {
            loadWeather(forceRefresh = false)
        }
    }

    private fun loadDashboard() {
        val todayDate = DateTimeUtils.todayDate()
        val weekStartDate = DateTimeUtils.startOfWeekDate()
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val todayCourses = courseRepository.getTodayCourses()
                val pendingTasks = taskRepository.getAllTasks().count { it.status == StudyTask.STATUS_TODO }
                val todayPlans = planRepository.getPlansByDate(todayDate)
                val todayDurationMinutes = getCompletedPlanMinutesForDate(todayDate)
                val weeklyDurationMinutes = getWeekCompletedMinutes(weekStartDate)
                DashboardSnapshot(
                    todayCourses = todayCourses,
                    pendingTasks = pendingTasks,
                    totalPlans = todayPlans.size,
                    completedPlans = todayPlans.count { it.status == StudyPlan.STATUS_COMPLETED },
                    todayDurationMinutes = todayDurationMinutes,
                    weeklyDurationMinutes = weeklyDurationMinutes,
                    todayTrendDelta = todayDurationMinutes - getCompletedPlanMinutesForDate(
                        DateTimeUtils.datePlusDays(todayDate, -1)
                    ),
                    weekTrendDelta = weeklyDurationMinutes - getWeekCompletedMinutes(
                        DateTimeUtils.datePlusDays(weekStartDate, -7)
                    )
                )
            }
            if (!isAdded || view == null) return@launch
            bindDashboard(snapshot)
        }
    }

    private fun bindDashboard(snapshot: DashboardSnapshot) {
        val currentView = view ?: return
        val todayCourses = snapshot.todayCourses
        currentView.findViewById<TextView>(R.id.tvTodayCourseCount).text = todayCourses.size.toString()
        currentView.findViewById<TextView>(R.id.tvPendingTaskCount).text = snapshot.pendingTasks.toString()
        currentView.findViewById<TextView>(R.id.tvTodayFocusMinutes).text = getString(R.string.duration_minutes, snapshot.todayDurationMinutes)
        currentView.findViewById<TextView>(R.id.tvWeekFocusMinutes).text = getString(R.string.duration_minutes, snapshot.weeklyDurationMinutes)
        currentView.findViewById<TextView>(R.id.tvTodayPlanCompletion).text =
            if (snapshot.totalPlans > 0) {
                val completionRate = snapshot.completedPlans * 100 / snapshot.totalPlans
                getString(R.string.dashboard_plan_completion_format, snapshot.completedPlans, snapshot.totalPlans, completionRate)
            } else {
                getString(R.string.dashboard_plan_completion_empty)
            }
        currentView.findViewById<TextView>(R.id.tvTodayTrend).apply {
            text = formatTrend(R.string.dashboard_today_trend_format, snapshot.todayTrendDelta)
            setTextColor(requireContext().getColor(colorForDelta(snapshot.todayTrendDelta)))
        }
        currentView.findViewById<TextView>(R.id.tvWeekTrend).apply {
            text = formatTrend(R.string.dashboard_week_trend_format, snapshot.weekTrendDelta)
            setTextColor(requireContext().getColor(colorForDelta(snapshot.weekTrendDelta)))
        }
        currentView.findViewById<TextView>(R.id.tvNextCourseValue).text =
            todayCourses.firstOrNull()?.let { course ->
                course.classroom?.takeIf { it.isNotBlank() }?.let { classroom ->
                    getString(R.string.dashboard_next_course_with_room_format, course.name, course.startSection, course.endSection, classroom)
                } ?: getString(R.string.dashboard_next_course_format, course.name, course.startSection, course.endSection)
            }
                ?: getString(R.string.dashboard_no_next_course)
    }

    private data class DashboardSnapshot(
        val todayCourses: List<Course>,
        val pendingTasks: Int,
        val totalPlans: Int,
        val completedPlans: Int,
        val todayDurationMinutes: Int,
        val weeklyDurationMinutes: Int,
        val todayTrendDelta: Int,
        val weekTrendDelta: Int
    )

    private fun getCompletedPlanMinutesForDate(date: String): Int {
        return planRepository.getPlansByDate(date)
            .filter { it.status == StudyPlan.STATUS_COMPLETED }
            .sumOf { it.actualMinutes.takeIf { minutes -> minutes > 0 } ?: it.plannedMinutes }
    }

    private fun getWeekCompletedMinutes(weekStartDate: String): Int {
        val weekEndDate = DateTimeUtils.datePlusDays(weekStartDate, 6)
        return planRepository.getWeeklyPlans(weekStartDate, weekEndDate)
            .filter { it.status == StudyPlan.STATUS_COMPLETED }
            .sumOf { it.actualMinutes.takeIf { minutes -> minutes > 0 } ?: it.plannedMinutes }
    }

    private fun formatTrend(formatRes: Int, delta: Int): String {
        val sign = if (delta >= 0) "+" else "-"
        return getString(formatRes, sign, kotlin.math.abs(delta))
    }

    private fun colorForDelta(delta: Int): Int {
        return if (delta >= 0) R.color.success else R.color.campus_accent
    }

    private fun loadWeather(forceRefresh: Boolean) {
        val currentView = view ?: return
        val city = settingsRepository.getWeatherCity()
        val token = DateTimeUtils.nowMillis()
        weatherLoadToken = token
        currentView.findViewById<TextView>(R.id.weatherCityText).text = city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = getString(R.string.dashboard_weather_empty)
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = ""
        currentView.findViewById<TextView>(R.id.weatherCitySourceText).text = ""
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""

        Thread {
            val weather = weatherRepository.getWeather(city, forceRefresh)
            val targetView = view ?: return@Thread
            targetView.post {
                if (weatherLoadToken == token && view != null) {
                    if (weather != null) {
                        val sourceLabel = if (weather.city != city) {
                            getString(R.string.dashboard_weather_city_source_cache)
                        } else {
                            weatherCitySourceLabel()
                        }
                        bindWeather(weather, sourceLabel)
                    } else {
                        bindWeatherUnavailable(city)
                    }
                }
            }
        }.start()
    }

    private fun maybeShowWeatherLocationGuide(): Boolean {
        if (settingsRepository.hasSeenWeatherLocationGuide() || weatherGuideDialogShowing) return false
        if (PermissionUtils.hasCoarseLocationPermission(requireContext())) {
            settingsRepository.setWeatherLocationGuideShown(true)
            loadWeatherFromLocation(forceRefresh = false, userTriggered = false)
            return true
        }

        weatherGuideDialogShowing = true
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dashboard_weather_location_intro_title)
            .setMessage(R.string.dashboard_weather_location_intro_message)
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                settingsRepository.setWeatherLocationGuideShown(true)
                loadWeather(forceRefresh = false)
            }
            .setPositiveButton(R.string.dashboard_weather_use_location) { _, _ ->
                settingsRepository.setWeatherLocationGuideShown(true)
                weatherLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            .setOnDismissListener {
                weatherGuideDialogShowing = false
            }
            .show()
        return true
    }

    private fun requestLocationAndLoadWeather(forceRefresh: Boolean, userTriggered: Boolean) {
        if (!PermissionUtils.hasCoarseLocationPermission(requireContext())) {
            settingsRepository.setWeatherLocationGuideShown(true)
            weatherLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        loadWeatherFromLocation(forceRefresh = forceRefresh, userTriggered = userTriggered)
    }

    private fun loadWeatherFromLocation(forceRefresh: Boolean, userTriggered: Boolean) {
        val currentView = view ?: return
        val token = DateTimeUtils.nowMillis()
        weatherLoadToken = token
        currentView.findViewById<TextView>(R.id.weatherCityText).text = getString(R.string.dashboard_weather_locating)
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = getString(R.string.dashboard_weather_empty)
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = ""
        currentView.findViewById<TextView>(R.id.weatherCitySourceText).text = ""
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""

        val appContext = requireContext().applicationContext
        Thread {
            val resolvedCity = WeatherLocationResolver(appContext).resolveCity()
            val targetCity = resolvedCity ?: settingsRepository.getWeatherCity()
            if (!resolvedCity.isNullOrBlank()) {
                settingsRepository.setWeatherCityFromLocation(resolvedCity)
            }
            val weather = weatherRepository.getWeather(targetCity, forceRefresh)
            val targetView = view ?: return@Thread
            targetView.post {
                if (weatherLoadToken != token || view == null) return@post
                if (resolvedCity.isNullOrBlank() && userTriggered) {
                    Snackbar.make(targetView, R.string.dashboard_weather_location_failed, Snackbar.LENGTH_LONG).show()
                }
                if (weather != null) {
                    val sourceLabel = when {
                        weather.city != targetCity -> getString(R.string.dashboard_weather_city_source_cache)
                        !resolvedCity.isNullOrBlank() -> getString(R.string.dashboard_weather_city_source_location)
                        else -> weatherCitySourceLabel()
                    }
                    bindWeather(weather, sourceLabel)
                } else {
                    bindWeatherUnavailable(targetCity)
                }
            }
        }.start()
    }

    private fun bindWeather(weather: WeatherResult, citySourceLabel: String) {
        val currentView = view ?: return
        currentView.findViewById<TextView>(R.id.weatherCityText).text = weather.city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = weather.temperature
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = weather.weatherText
        currentView.findViewById<TextView>(R.id.weatherCitySourceText).text =
            getString(R.string.dashboard_weather_city_source_format, citySourceLabel)
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text =
            getString(R.string.dashboard_weather_humidity, weather.humidity)
        currentView.findViewById<TextView>(R.id.weatherWindText).text =
            getString(R.string.dashboard_weather_wind, weather.wind)
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = getString(
            R.string.dashboard_weather_update,
            DateTimeUtils.formatDateTime(weather.updatedAt),
            weather.source
        )
    }

    private fun bindWeatherUnavailable(city: String) {
        val currentView = view ?: return
        currentView.findViewById<TextView>(R.id.weatherCityText).text = city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = getString(R.string.dashboard_weather_unavailable)
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = getString(R.string.dashboard_weather_unavailable_body)
        currentView.findViewById<TextView>(R.id.weatherCitySourceText).text =
            getString(R.string.dashboard_weather_city_source_format, weatherCitySourceLabel())
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""
    }

    private fun weatherCitySourceLabel(): String {
        return when (settingsRepository.getWeatherCitySource()) {
            SettingsRepository.WEATHER_CITY_SOURCE_LOCATION -> getString(R.string.dashboard_weather_city_source_location)
            else -> getString(R.string.dashboard_weather_city_source_manual)
        }
    }
}
