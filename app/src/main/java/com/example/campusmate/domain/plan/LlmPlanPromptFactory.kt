package com.example.campusmate.domain.plan

import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmPromptContract

object LlmPlanPromptFactory {
    fun buildRequest(contextText: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = LlmPromptContract.systemPrompt(
                role = "你是 CampusMate 的学习计划生成助手。",
                objective = "根据已提供的课程、任务、天气、学习记录、已有计划和时间窗，生成能在用户确认后执行的学习计划。",
                outputSchema = OUTPUT_SCHEMA,
                rules = RULES
            ),
            userPrompt = LlmPromptContract.userPrompt(
                instruction = "请只使用以下上下文中的事实生成计划。",
                inputLabel = "STUDY_PLAN_CONTEXT",
                content = contextText,
                maxChars = MAX_INPUT_CHARS
            ),
            responseJsonOnly = true,
            promptId = PROMPT_ID,
            promptVersion = PROMPT_VERSION
        )
    }

    private val RULES = listOf(
        "所有计划必须位于上下文给出的允许生成时间内；过去日期或无可用时间窗时返回空 plans。",
        "普通作业、复习、项目和考试准备不得与课程或已有计划重叠。",
        "上课或课程学习类计划必须位于对应课程时间内，并在标题中保留课程名。",
        "plannedMinutes 必须为 5..240 的整数，优先采用 15..180 分钟；endTime 必须晚于 startTime。",
        "优先安排高优先级、已逾期或临近截止的任务，并在连续学习之间保留合理休息。",
        "天气只用于调整出行、地点和节奏建议，不得据此虚构课程、任务或截止时间。",
        "无法满足约束的候选项不要输出，并在 warnings 中简要说明原因。",
        "type 和 sourceType 由应用本地确定，不要输出这两个字段。"
    )

    private val OUTPUT_SCHEMA = """
        {
          "plans": [
            {
              "title": "计划标题",
              "plannedMinutes": 60,
              "startTime": "HH:mm",
              "endTime": "HH:mm"
            }
          ],
          "warnings": ["可选警告"]
        }
    """.trimIndent()

    private const val PROMPT_ID = "campusmate.plan.generate"
    private const val PROMPT_VERSION = 1
    private const val MAX_INPUT_CHARS = 60_000
}
