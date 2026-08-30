package com.example.campusmate.domain.ai.advice

import com.example.campusmate.domain.ai.context.AiContextJsonRenderer
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmPromptContract

object LlmDashboardAdvicePromptFactory {
    fun buildRequest(snapshot: AiContextSnapshot): LlmGenerateRequest {
        val allowedRefs = DashboardAdviceContextPolicy.allowedEvidenceRefs(snapshot)
            .sorted()
            .joinToString(", ")
        return LlmGenerateRequest(
            systemPrompt = LlmPromptContract.systemPrompt(
                role = "你是 CampusMate 首页的学习建议助手。",
                objective = "根据本地提供的课程、任务、计划占用、天气与学习进度，给出当天可执行且有事实依据的简短建议。",
                outputSchema = OUTPUT_SCHEMA,
                rules = RULES
            ),
            userPrompt = LlmPromptContract.userPrompt(
                instruction = buildString {
                    appendLine("请只使用以下上下文生成今日建议。")
                    append("evidenceRefs 只允许使用这个本地白名单：")
                    append(allowedRefs)
                },
                inputLabel = "DASHBOARD_AI_CONTEXT",
                content = AiContextJsonRenderer.render(snapshot),
                maxChars = MAX_INPUT_CHARS
            ),
            responseJsonOnly = true,
            promptId = PROMPT_ID,
            promptVersion = PROMPT_VERSION
        )
    }

    private val RULES = listOf(
        "items 最多三条，按紧迫性从高到低排列；每条必须至少提供一个白名单中的 evidenceRef。",
        "优先考虑已逾期或临近截止的任务、当天课程准备、已有计划空档和每日学习目标。",
        "suggestedDate 只能使用上下文 range 内的 yyyy-MM-dd；startTime 与 endTime 要么同时省略，要么与 suggestedDate 一起提供并使用 HH:mm，具体时间不得与当天 occupiedTimeRanges 冲突。",
        "只有 usableForRealtimeAdvice=true 时才能使用 weather:current 并判断当前天气或出行条件。",
        "课程教学周未解析或上下文有 omissions/warnings 时必须采用保守表述，不得把不确定安排说成已确认事实。",
        "不得虚构课程、截止时间、天气、学习成果或本地记录，也不得输出链接、Activity、Intent、数据库操作或工具调用。",
        "建议只供用户阅读；不得宣称已创建、修改、完成或删除任何本地数据。",
        "信息不足时可依据 settings:daily-goal 给出保守建议，但仍不得猜测缺失事实。"
    )

    private val OUTPUT_SCHEMA = """
        {
          "headline": "不超过60字的今日判断",
          "summary": "不超过240字的依据摘要",
          "items": [
            {
              "title": "不超过50字的建议标题",
              "detail": "不超过180字的执行说明",
              "priority": "high | normal | low",
              "suggestedDate": "可选 yyyy-MM-dd",
              "startTime": "可选 HH:mm",
              "endTime": "可选 HH:mm",
              "evidenceRefs": ["task:12", "course:3"]
            }
          ],
          "warnings": ["可选警告"]
        }
    """.trimIndent()

    const val PROMPT_ID = "campusmate.dashboard.advice"
    const val PROMPT_VERSION = 1
    const val PROMPT_TAG = "campusmate.dashboard.advice@v1"
    private const val MAX_INPUT_CHARS = 48_000
}
