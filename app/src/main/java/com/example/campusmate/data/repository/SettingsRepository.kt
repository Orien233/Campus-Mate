package com.example.campusmate.data.repository

import android.content.Context

/** SharedPreferences-backed settings used by features before the full settings UI lands. */
class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDailyGoalMinutes(): Int = preferences.getInt(KEY_DAILY_GOAL_MINUTES, DEFAULT_DAILY_GOAL_MINUTES)

    fun setDailyGoalMinutes(value: Int) {
        preferences.edit().putInt(KEY_DAILY_GOAL_MINUTES, value.coerceAtLeast(1)).apply()
    }

    fun isReminderEnabled(): Boolean = preferences.getBoolean(KEY_REMINDER_ENABLED, true)

    fun setReminderEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()
    }

    fun isImmersiveModeEnabled(): Boolean = preferences.getBoolean(KEY_IMMERSIVE_MODE_ENABLED, false)

    fun setImmersiveModeEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_IMMERSIVE_MODE_ENABLED, value).apply()
    }

    fun getWeatherCity(): String = preferences.getString(KEY_WEATHER_CITY, DEFAULT_WEATHER_CITY) ?: DEFAULT_WEATHER_CITY

    fun setWeatherCity(value: String) {
        preferences.edit().putString(KEY_WEATHER_CITY, value.trim().ifBlank { DEFAULT_WEATHER_CITY }).apply()
    }

    fun isMockWeatherEnabled(): Boolean = preferences.getBoolean(KEY_MOCK_WEATHER_ENABLED, true)

    fun setMockWeatherEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_MOCK_WEATHER_ENABLED, value).apply()
    }

    fun isFocusDndEnabled(): Boolean = preferences.getBoolean(KEY_FOCUS_DND_ENABLED, false)

    fun setFocusDndEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_FOCUS_DND_ENABLED, value).apply()
    }

    fun isNotificationFilterEnabled(): Boolean = preferences.getBoolean(KEY_NOTIFICATION_FILTER_ENABLED, false)

    fun setNotificationFilterEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_FILTER_ENABLED, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "campusmate_settings"
        private const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_IMMERSIVE_MODE_ENABLED = "immersive_mode_enabled"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_MOCK_WEATHER_ENABLED = "mock_weather_enabled"
        private const val KEY_FOCUS_DND_ENABLED = "focus_dnd_enabled"
        private const val KEY_NOTIFICATION_FILTER_ENABLED = "notification_filter_enabled"
        private const val DEFAULT_DAILY_GOAL_MINUTES = 60
        private const val DEFAULT_WEATHER_CITY = "\u5317\u4eac"
    }
}
