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

    companion object {
        private const val PREFS_NAME = "campusmate_settings"
        private const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_IMMERSIVE_MODE_ENABLED = "immersive_mode_enabled"
        private const val DEFAULT_DAILY_GOAL_MINUTES = 60
    }
}
