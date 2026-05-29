package com.example.campusmate.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.campusmate.data.model.llm.LlmAuthHeaderType
import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmProviderType
import com.example.campusmate.data.model.llm.LlmScheduleParseMode

/** SharedPreferences-backed LLM settings. API key storage is added separately. */
class LlmSettingsRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): LlmProviderConfig {
        val default = LlmProviderConfig()
        return LlmProviderConfig(
            enabled = preferences.getBoolean(KEY_ENABLED, default.enabled),
            scheduleParseEnabled = preferences.getBoolean(KEY_SCHEDULE_PARSE_ENABLED, default.scheduleParseEnabled),
            planGenerateEnabled = preferences.getBoolean(KEY_PLAN_GENERATE_ENABLED, default.planGenerateEnabled),
            scheduleParseMode = preferences.getEnum(KEY_SCHEDULE_PARSE_MODE, default.scheduleParseMode),
            providerPresetId = preferences.getString(KEY_PROVIDER_PRESET_ID, default.providerPresetId)
                ?: default.providerPresetId,
            providerType = preferences.getEnum(KEY_PROVIDER_TYPE, default.providerType),
            displayName = preferences.getString(KEY_DISPLAY_NAME, default.displayName) ?: default.displayName,
            baseUrl = preferences.getString(KEY_BASE_URL, default.baseUrl) ?: default.baseUrl,
            model = preferences.getString(KEY_MODEL, default.model) ?: default.model,
            authHeaderType = preferences.getEnum(KEY_AUTH_HEADER_TYPE, default.authHeaderType),
            temperature = preferences.getFloat(KEY_TEMPERATURE, default.temperature),
            timeoutMillis = preferences.getInt(KEY_TIMEOUT_MILLIS, default.timeoutMillis),
            maxOutputTokens = preferences.getInt(KEY_MAX_OUTPUT_TOKENS, default.maxOutputTokens)
        )
    }

    fun saveConfig(config: LlmProviderConfig) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putBoolean(KEY_SCHEDULE_PARSE_ENABLED, config.scheduleParseEnabled)
            .putBoolean(KEY_PLAN_GENERATE_ENABLED, config.planGenerateEnabled)
            .putString(KEY_SCHEDULE_PARSE_MODE, config.scheduleParseMode.name)
            .putString(KEY_PROVIDER_PRESET_ID, config.providerPresetId)
            .putString(KEY_PROVIDER_TYPE, config.providerType.name)
            .putString(KEY_DISPLAY_NAME, config.displayName)
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_MODEL, config.model.trim())
            .putString(KEY_AUTH_HEADER_TYPE, config.authHeaderType.name)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putInt(KEY_TIMEOUT_MILLIS, config.timeoutMillis)
            .putInt(KEY_MAX_OUTPUT_TOKENS, config.maxOutputTokens)
            .apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.getEnum(key: String, default: T): T {
        val value = getString(key, default.name) ?: default.name
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }

    companion object {
        private const val PREFS_NAME = "campusmate_llm_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULE_PARSE_ENABLED = "schedule_parse_enabled"
        private const val KEY_PLAN_GENERATE_ENABLED = "plan_generate_enabled"
        private const val KEY_SCHEDULE_PARSE_MODE = "schedule_parse_mode"
        private const val KEY_PROVIDER_PRESET_ID = "provider_preset_id"
        private const val KEY_PROVIDER_TYPE = "provider_type"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_AUTH_HEADER_TYPE = "auth_header_type"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TIMEOUT_MILLIS = "timeout_millis"
        private const val KEY_MAX_OUTPUT_TOKENS = "max_output_tokens"
    }
}
