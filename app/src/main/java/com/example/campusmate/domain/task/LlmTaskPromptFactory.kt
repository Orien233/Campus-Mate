package com.example.campusmate.domain.task

import com.example.campusmate.domain.llm.LlmGenerateRequest

object LlmTaskPromptFactory {
    fun buildRequest(pageContent: String, nowText: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = buildSystemPrompt(nowText),
            userPrompt = buildUserPrompt(pageContent),
            responseJsonOnly = true
        )
    }

    private fun buildSystemPrompt(nowText: String): String {
        return """
            你是 CampusMate 的任务网页解析助手。
            请从用户提供的网页文本或 HTML 中提取学习任务、作业、实验、考试、复习或项目安排，并且只返回严格 JSON。
            当前时间是：$nowText。遇到“今天、明天、后天、本周五、下周一”等相对日期时，请换算为明确日期时间。

            返回 JSON 必须符合以下结构：
            {
              "tasks": [
                {
                  "title": "任务标题",
                  "description": "任务说明（可选）",
                  "courseName": "关联课程名（可选）",
                  "type": "homework|experiment|exam|review|project|other",
                  "priority": "low|normal|high",
                  "dueAt": "yyyy-MM-dd HH:mm（可选）",
                  "remindAt": "yyyy-MM-dd HH:mm（可选）",
                  "sourceText": "网页中的原始片段（可选）"
                }
              ],
              "warnings": ["可选警告"]
            }

            规则：
            - title 必须简洁，不能把整段网页内容当标题。
            - type 只能表示为 homework、experiment、exam、review、project、other 之一。
            - priority 只能表示为 low、normal、high 之一；紧急、重要、快截止视为 high。
            - dueAt/remindAt 不确定时留空，不要编造。
            - 如果网页里有多个任务，请全部返回；CampusMate 会先用第一条预填表单，让用户确认。
            - 不要输出 markdown、解释文字或代码块。
        """.trimIndent()
    }

    private fun buildUserPrompt(pageContent: String): String {
        return """
            请从下面网页内容中提取任务信息，并只返回 JSON：

            $pageContent
        """.trimIndent()
    }
}
