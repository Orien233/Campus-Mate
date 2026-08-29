package com.example.campusmate.domain.ai.context

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.SettingsSectionTimeSlot
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.weather.WeatherResult
import java.util.TimeZone

internal interface AiContextDataSource {
    fun loadCourses(): List<Course>
    fun loadTasks(): List<StudyTask>
    fun loadPlans(startDate: String, endDate: String): List<StudyPlan>
    fun loadStudyRecords(startDate: String, endDate: String): List<StudyRecord>
    fun loadCachedWeather(city: String): WeatherResult?
    fun loadSettings(): AiContextSettingsSource
}

internal data class AiContextSettingsSource(
    val dailyGoalMinutes: Int,
    val earliestPlanTime: String,
    val latestPlanTime: String,
    val weatherCity: String,
    val sectionTimeSlots: List<SettingsSectionTimeSlot>,
    val zoneId: String
)

internal class RepositoryAiContextDataSource(context: Context) : AiContextDataSource {
    private val appContext = context.applicationContext
    private val courseRepository = CourseRepository(appContext)
    private val taskRepository = TaskRepository(appContext)
    private val planRepository = StudyPlanRepository(appContext)
    private val recordRepository = StudyRecordRepository(appContext)
    private val weatherRepository = WeatherRepository(appContext)
    private val settingsRepository = SettingsRepository(appContext)

    override fun loadCourses(): List<Course> = courseRepository.getAllCourses()

    override fun loadTasks(): List<StudyTask> = taskRepository.getAllTasks()

    override fun loadPlans(startDate: String, endDate: String): List<StudyPlan> {
        return planRepository.getWeeklyPlans(startDate, endDate)
    }

    override fun loadStudyRecords(startDate: String, endDate: String): List<StudyRecord> {
        return recordRepository.getRecordsBetween(startDate, endDate)
    }

    override fun loadCachedWeather(city: String): WeatherResult? {
        return weatherRepository.getCachedWeather(city)
    }

    override fun loadSettings(): AiContextSettingsSource {
        return AiContextSettingsSource(
            dailyGoalMinutes = settingsRepository.getDailyGoalMinutes(),
            earliestPlanTime = settingsRepository.getPlanEarliestTime(),
            latestPlanTime = settingsRepository.getPlanLatestTime(),
            weatherCity = settingsRepository.getWeatherCity(),
            sectionTimeSlots = settingsRepository.getSectionTimeSlots(),
            zoneId = TimeZone.getDefault().id
        )
    }
}
