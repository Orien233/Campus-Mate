package com.example.campusmate.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.campusmate.data.model.llm.LlmAuthHeaderType
import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmProviderType
import com.example.campusmate.data.model.llm.LlmScheduleParseMode
import com.example.campusmate.domain.import_.LlmScheduleParseSettingsSource

/** SharedPreferences-backed LLM settings with encrypted local API key storage. */
class LlmSettingsRepository(context: Context) : LlmScheduleParseSettingsSource {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val encryptedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getConfig(): LlmProviderConfig {
        val default = LlmProviderConfig()
        return LlmProviderConfig(
            enabled = preferences.getBoolean(KEY_ENABLED, default.enabled),
            scheduleParseEnabled = preferences.getBoolean(KEY_SCHEDULE_PARSE_ENABLED, default.scheduleParseEnabled),
            taskParseEnabled = preferences.getBoolean(KEY_TASK_PARSE_ENABLED, default.taskParseEnabled),
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
            .putBoolean(KEY_TASK_PARSE_ENABLED, config.taskParseEnabled)
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

    fun saveApiKey(apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isBlank()) return
        encryptedPreferences.edit()
            .putString(KEY_API_KEY, normalized)
            .apply()
    }

    override fun getApiKey(): String? {
        return encryptedPreferences.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun getMaskedApiKey(): String = maskApiKey(getApiKey())

    fun clearApiKey() {
        encryptedPreferences.edit().remove(KEY_API_KEY).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
        clearApiKey()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.getEnum(key: String, default: T): T {
        val value = getString(key, default.name) ?: default.name
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }

    companion object {
        private const val PREFS_NAME = "campusmate_llm_settings"
        private const val ENCRYPTED_PREFS_NAME = "campusmate_llm_secure_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULE_PARSE_ENABLED = "schedule_parse_enabled"
        private const val KEY_TASK_PARSE_ENABLED = "task_parse_enabled"
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
        private const val KEY_API_KEY = "api_key"

        fun hasMaskedShape(value: String): Boolean = value.contains(MASK_MARKER)

        fun maskApiKey(apiKey: String?): String {
            val normalized = apiKey?.trim().orEmpty()
            if (normalized.isBlank()) return ""
            if (normalized.length <= 8) return MASK_MARKER
            return normalized.take(4) + MASK_MARKER + normalized.takeLast(4)
        }

        private const val MASK_MARKER = "\u2022\u2022\u2022\u2022"
    }

    override fun hasApiKey(): Boolean = getApiKey() != null
}
