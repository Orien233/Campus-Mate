package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmAuthHeaderType
import com.example.campusmate.data.model.llm.LlmProviderPreset
import com.example.campusmate.data.model.llm.LlmProviderType

object LlmProviderPresets {
    const val ID_DEEPSEEK = "deepseek"
    const val ID_OPENAI = "openai"
    const val ID_QWEN = "qwen"
    const val ID_MOONSHOT = "moonshot"
    const val ID_ZHIPU = "zhipu"
    const val ID_MIMO = "mimo"
    const val ID_GEMINI = "gemini"
    const val ID_CUSTOM = "custom"

    val all: List<LlmProviderPreset> = listOf(
        LlmProviderPreset(
            id = ID_DEEPSEEK,
            displayName = "DeepSeek",
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-chat",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "OpenAI-compatible API"
        ),
        LlmProviderPreset(
            id = ID_OPENAI,
            displayName = "OpenAI",
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4.1-mini",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "OpenAI API"
        ),
        LlmProviderPreset(
            id = ID_QWEN,
            displayName = "\u901a\u4e49\u5343\u95ee Qwen",
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "\u517c\u5bb9\u5730\u5740\u548c\u6a21\u578b\u540d\u8bf7\u4ee5\u7528\u6237\u63a7\u5236\u53f0\u4e3a\u51c6"
        ),
        LlmProviderPreset(
            id = ID_MOONSHOT,
            displayName = "Kimi / Moonshot",
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.moonshot.cn/v1",
            defaultModel = "kimi-latest",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "\u6a21\u578b\u540d\u8bf7\u4ee5\u7528\u6237\u63a7\u5236\u53f0\u4e3a\u51c6"
        ),
        LlmProviderPreset(
            id = ID_ZHIPU,
            displayName = "\u667a\u8c31 GLM",
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "\u517c\u5bb9\u5730\u5740\u548c\u6a21\u578b\u540d\u8bf7\u4ee5\u7528\u6237\u63a7\u5236\u53f0\u4e3a\u51c6"
        ),
        LlmProviderPreset(
            id = ID_MIMO,
            displayName = "\u5c0f\u7c73 MiMo",
            providerType = LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl = "",
            defaultModel = "mimo",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "\u8bf7\u6839\u636e\u5c0f\u7c73 MiMo \u5e73\u53f0\u6587\u6863\u586b\u5199 baseUrl \u548c model"
        ),
        LlmProviderPreset(
            id = ID_GEMINI,
            displayName = "Google Gemini",
            providerType = LlmProviderType.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            defaultModel = "gemini-3.5-flash",
            authHeaderType = LlmAuthHeaderType.X_GOOG_API_KEY,
            notes = "Gemini generateContent API"
        ),
        LlmProviderPreset(
            id = ID_CUSTOM,
            displayName = "\u81ea\u5b9a\u4e49 OpenAI-Compatible",
            providerType = LlmProviderType.CUSTOM_OPENAI_COMPATIBLE,
            baseUrl = "",
            defaultModel = "",
            authHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
            notes = "\u7528\u6237\u81ea\u884c\u586b\u5199 baseUrl \u548c model"
        )
    )

    val default: LlmProviderPreset = requireNotNull(findById(ID_DEEPSEEK))

    fun findById(id: String): LlmProviderPreset? = all.firstOrNull { it.id == id }
}
