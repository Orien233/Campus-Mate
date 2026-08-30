package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig

/** Read-only settings contract shared by LLM-backed domain services. */
interface LlmSettingsSource {
    fun getConfig(): LlmProviderConfig
    fun hasApiKey(): Boolean
    fun getApiKey(): String?
}
