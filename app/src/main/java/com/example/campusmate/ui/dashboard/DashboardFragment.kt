package com.example.campusmate.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.ui.focus.FocusActivity
import com.example.campusmate.ui.import_.ImportScheduleActivity
import com.example.campusmate.ui.task.TaskEditActivity
import com.example.campusmate.util.DateTimeUtils
import com.google.android.material.button.MaterialButton

/** Dashboard entry point for daily course, task, and focus summaries. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private lateinit var courseRepository: CourseRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var studyRecordRepository: StudyRecordRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var weatherRepository: WeatherRepository
    private var weatherLoadToken: Long = 0L

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
            loadWeather(forceRefresh = true)
        }
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
        currentView.findViewById<TextView>(R.id.weatherDetailText).text = ""
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

    private fun bindWeather(weather: WeatherResult) {
        val currentView = view ?: return
        currentView.findViewById<TextView>(R.id.weatherCityText).text = weather.city
        currentView.findViewById<TextView>(R.id.weatherTemperatureText).text = weather.temperature
        currentView.findViewById<TextView>(R.id.weatherDetailText).text = getString(
            R.string.dashboard_weather_detail,
            weather.weatherText,
            weather.humidity,
            weather.wind
        )
        currentView.findViewById<TextView>(R.id.weatherUpdatedText).text = getString(
            R.string.dashboard_weather_update,
            DateTimeUtils.formatDateTime(weather.updatedAt),
            weather.source
        )
    }
}
