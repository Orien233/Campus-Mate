package com.example.campusmate.domain.task

import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmPromptContract

object LlmTaskPromptFactory {
    fun buildRequest(pageContent: String, nowText: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = LlmPromptContract.systemPrompt(
                role = "你是 CampusMate 的任务网页解析助手。",
                objective = "从网页文本或 HTML 中提取学习任务、作业、实验、考试、复习和项目安排。当前时间：${nowText.trim()}。相对日期必须换算为明确时间。",
                outputSchema = OUTPUT_SCHEMA,
                rules = RULES
            ),
            userPrompt = LlmPromptContract.userPrompt(
                instruction = "请从以下网页资料中提取任务事实。",
                inputLabel = "TASK_PAGE_CONTENT",
                content = pageContent,
                maxChars = MAX_INPUT_CHARS
            ),
            responseJsonOnly = true,
            promptId = PROMPT_ID,
            promptVersion = PROMPT_VERSION
        )
    }

    private val RULES = listOf(
        "title 必须简洁，不能把整段网页内容作为标题。",
        "type 只能是 homework、experiment、exam、review、project、other 之一。",
        "priority 只能是 low、normal、high 之一；明确紧急、重要或临近截止时才使用 high。",
        "dueAt 和 remindAt 使用 yyyy-MM-dd HH:mm；不确定时返回空字符串，不得猜测。",
        "同一网页含多个任务时全部返回；应用只会在用户检查后预填或保存。",
        "sourceText 只能保留支持该任务的简短原文片段，不得复制整页内容。"
    )

    private val OUTPUT_SCHEMA = """
        {
          "tasks": [
            {
              "title": "任务标题",
              "description": "任务说明或空字符串",
              "courseName": "关联课程名或空字符串",
              "type": "homework",
              "priority": "normal",
              "dueAt": "yyyy-MM-dd HH:mm 或空字符串",
              "remindAt": "yyyy-MM-dd HH:mm 或空字符串",
              "sourceText": "支持任务判断的简短原文或空字符串"
            }
          ],
          "warnings": ["可选警告"]
        }
    """.trimIndent()

    private const val PROMPT_ID = "campusmate.task.parse"
    private const val PROMPT_VERSION = 1
    private const val MAX_INPUT_CHARS = 80_000
}
