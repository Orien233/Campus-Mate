package com.example.campusmate.data.repository

import android.content.Context
import com.example.campusmate.util.DateTimeUtils

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
        setWeatherCity(value, WEATHER_CITY_SOURCE_MANUAL)
    }

    fun setWeatherCityFromLocation(value: String) {
        setWeatherCity(value, WEATHER_CITY_SOURCE_LOCATION)
    }

    fun getWeatherCitySource(): String {
        return preferences.getString(KEY_WEATHER_CITY_SOURCE, WEATHER_CITY_SOURCE_MANUAL)
            ?: WEATHER_CITY_SOURCE_MANUAL
    }

    private fun setWeatherCity(value: String, source: String) {
        preferences.edit()
            .putString(KEY_WEATHER_CITY, value.trim().ifBlank { DEFAULT_WEATHER_CITY })
            .putString(KEY_WEATHER_CITY_SOURCE, source)
            .putLong(KEY_WEATHER_CITY_UPDATED_AT, DateTimeUtils.nowMillis())
            .apply()
    }

    fun getWeatherCityUpdatedAt(): Long = preferences.getLong(KEY_WEATHER_CITY_UPDATED_AT, 0L)

    fun hasSeenWeatherLocationGuide(): Boolean = preferences.getBoolean(KEY_WEATHER_LOCATION_GUIDE_SHOWN, false)

    fun setWeatherLocationGuideShown(value: Boolean) {
        preferences.edit().putBoolean(KEY_WEATHER_LOCATION_GUIDE_SHOWN, value).apply()
    }

    fun isFocusDndEnabled(): Boolean = preferences.getBoolean(KEY_FOCUS_DND_ENABLED, false)

    fun setFocusDndEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_FOCUS_DND_ENABLED, value).apply()
    }

    fun isNotificationFilterEnabled(): Boolean = preferences.getBoolean(KEY_NOTIFICATION_FILTER_ENABLED, false)

    fun setNotificationFilterEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_FILTER_ENABLED, value).apply()
    }

    fun getPlanEarliestTime(): String = getPlanTime(KEY_PLAN_EARLIEST_TIME, DEFAULT_PLAN_EARLIEST_TIME)

    fun setPlanEarliestTime(value: String) {
        preferences.edit().putString(KEY_PLAN_EARLIEST_TIME, normalizePlanTime(value, DEFAULT_PLAN_EARLIEST_TIME)).apply()
    }

    fun getPlanLatestTime(): String = getPlanTime(KEY_PLAN_LATEST_TIME, DEFAULT_PLAN_LATEST_TIME)

    fun setPlanLatestTime(value: String) {
        preferences.edit().putString(KEY_PLAN_LATEST_TIME, normalizePlanTime(value, DEFAULT_PLAN_LATEST_TIME)).apply()
    }

    private fun getPlanTime(key: String, defaultValue: String): String {
        return normalizePlanTime(preferences.getString(key, defaultValue).orEmpty(), defaultValue)
    }

    private fun normalizePlanTime(value: String, defaultValue: String): String {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(value.trim()) ?: return defaultValue
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return defaultValue
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return defaultValue
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
    }

    /**
     * Optional "section -> clock time range" mapping for timetable display.
     *
     * Stored as a JSON array string to avoid DB/schema changes.
     * Example: [{"section":1,"start":"08:00","end":"09:50"}]
     */
    fun getSectionTimeSlots(): List<SettingsSectionTimeSlot> {
        val raw = preferences.getString(KEY_SECTION_TIME_SLOTS_JSON, null)?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val section = obj.optInt("section", -1)
                    val start = obj.optString("start", "")
                    val end = obj.optString("end", "")
                    if (section > 0 && start.isNotBlank() && end.isNotBlank()) {
                        add(SettingsSectionTimeSlot(section = section, startTime = start, endTime = end))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setSectionTimeSlots(slots: List<SettingsSectionTimeSlot>) {
        val array = org.json.JSONArray()
        for (slot in slots) {
            val obj = org.json.JSONObject()
            obj.put("section", slot.section)
            obj.put("start", slot.startTime)
            obj.put("end", slot.endTime)
            array.put(obj)
        }
        preferences.edit().putString(KEY_SECTION_TIME_SLOTS_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "campusmate_settings"
        private const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_IMMERSIVE_MODE_ENABLED = "immersive_mode_enabled"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_CITY_SOURCE = "weather_city_source"
        private const val KEY_WEATHER_CITY_UPDATED_AT = "weather_city_updated_at"
        private const val KEY_WEATHER_LOCATION_GUIDE_SHOWN = "weather_location_guide_shown"
        private const val KEY_FOCUS_DND_ENABLED = "focus_dnd_enabled"
        private const val KEY_NOTIFICATION_FILTER_ENABLED = "notification_filter_enabled"
        private const val KEY_PLAN_EARLIEST_TIME = "plan_earliest_time"
        private const val KEY_PLAN_LATEST_TIME = "plan_latest_time"
        private const val KEY_SECTION_TIME_SLOTS_JSON = "section_time_slots_json"
        private const val DEFAULT_DAILY_GOAL_MINUTES = 60
        private const val DEFAULT_WEATHER_CITY = "\u5317\u4eac"
        private const val DEFAULT_PLAN_EARLIEST_TIME = "08:00"
        private const val DEFAULT_PLAN_LATEST_TIME = "22:00"
        const val WEATHER_CITY_SOURCE_MANUAL = "manual"
        const val WEATHER_CITY_SOURCE_LOCATION = "location"
    }
}

data class SettingsSectionTimeSlot(
    val section: Int,
    val startTime: String,
    val endTime: String
)
