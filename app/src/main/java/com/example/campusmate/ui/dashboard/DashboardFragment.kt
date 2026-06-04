package com.example.campusmate.ui.dashboard

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.weather.WeatherLocationResolver
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.ui.common.CollapsibleSection
import com.example.campusmate.ui.focus.FocusActivity
import com.example.campusmate.ui.import_.ImportScheduleActivity
import com.example.campusmate.ui.main.MainActivity
import com.example.campusmate.ui.task.TaskEditActivity
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.PermissionUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Dashboard entry point for daily course, task, and focus summaries. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private lateinit var courseRepository: CourseRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var studyRecordRepository: StudyRecordRepository
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
        studyRecordRepository = StudyRecordRepository(requireContext())
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
            requestLocationAndLoadWeather(forceRefresh = true, userTriggered = true)
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
        val currentView = view ?: return
        val todayCourses = courseRepository.getTodayCourses()
        val pendingTasks = taskRepository.getAllTasks().count { it.status == StudyTask.STATUS_TODO }
        val todayDurationMinutes = studyRecordRepository.getTodayDuration() / 60
        val weeklyDurationMinutes = studyRecordRepository.getWeeklyDuration() / 60

        currentView.findViewById<TextView>(R.id.tvTodayCourseCount).text = todayCourses.size.toString()
        currentView.findViewById<TextView>(R.id.tvPendingTaskCount).text = pendingTasks.toString()
        currentView.findViewById<TextView>(R.id.tvTodayFocusMinutes).text = getString(R.string.duration_minutes, todayDurationMinutes)
        currentView.findViewById<TextView>(R.id.tvWeekFocusMinutes).text = getString(R.string.duration_minutes, weeklyDurationMinutes)
        currentView.findViewById<TextView>(R.id.tvNextCourseValue).text =
            todayCourses.firstOrNull()?.let { getString(R.string.dashboard_next_course_format, it.name, it.startSection, it.endSection) }
                ?: getString(R.string.dashboard_no_next_course)
        if (!maybeShowWeatherLocationGuide()) {
            loadWeather(forceRefresh = false)
        }
    }

    private fun loadWeather(forceRefresh: Boolean) {
        val currentView = view ?: return
        val city = settingsRepository.getWeatherCity()
        val token = DateTimeUtils.nowMillis()
        weatherLoadToken = token
        currentView.findViewById<TextView>(R.id.weatherCityText).text = city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = getString(R.string.dashboard_weather_empty)
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = ""
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""

        Thread {
            val weather = weatherRepository.getWeather(city, forceRefresh)
            val targetView = view ?: return@Thread
            targetView.post {
                if (weatherLoadToken == token && view != null) {
                    if (weather != null) {
                        bindWeather(weather)
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
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""

        val appContext = requireContext().applicationContext
        Thread {
            val resolvedCity = WeatherLocationResolver(appContext).resolveCity()
            val targetCity = resolvedCity ?: settingsRepository.getWeatherCity()
            if (!resolvedCity.isNullOrBlank()) {
                settingsRepository.setWeatherCity(resolvedCity)
            }
            val weather = weatherRepository.getWeather(targetCity, forceRefresh)
            val targetView = view ?: return@Thread
            targetView.post {
                if (weatherLoadToken != token || view == null) return@post
                if (resolvedCity.isNullOrBlank() && userTriggered) {
                    Snackbar.make(targetView, R.string.dashboard_weather_location_failed, Snackbar.LENGTH_LONG).show()
                }
                if (weather != null) {
                    bindWeather(weather)
                } else {
                    bindWeatherUnavailable(targetCity)
                }
            }
        }.start()
    }

    private fun bindWeather(weather: WeatherResult) {
        val currentView = view ?: return
        currentView.findViewById<TextView>(R.id.weatherCityText).text = weather.city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = weather.temperature
        currentView.findViewById<TextView>(R.id.weatherConditionText).text = weather.weatherText
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
        currentView.findViewById<TextView>(R.id.weatherHumidityText).text = ""
        currentView.findViewById<TextView>(R.id.weatherWindText).text = ""
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = ""
    }
}
