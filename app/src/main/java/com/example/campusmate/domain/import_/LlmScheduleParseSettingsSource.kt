package com.example.campusmate.domain.import_

import com.example.campusmate.data.model.llm.LlmProviderConfig

interface LlmScheduleParseSettingsSource {
    fun getConfig(): LlmProviderConfig
    fun hasApiKey(): Boolean
    fun getApiKey(): String?
}

