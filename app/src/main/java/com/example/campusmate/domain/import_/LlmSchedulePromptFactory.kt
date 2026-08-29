package com.example.campusmate.domain.import_

import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmPromptContract

object LlmSchedulePromptFactory {
    fun buildRequest(html: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = LlmPromptContract.systemPrompt(
                role = "你是 CampusMate 的课表解析助手。",
                objective = "从课表 HTML 中提取全部可验证课程，并把不确定项写入 warnings。",
                outputSchema = OUTPUT_SCHEMA,
                rules = RULES
            ),
            userPrompt = LlmPromptContract.userPrompt(
                instruction = "请从以下课表 HTML 中提取课程事实。",
                inputLabel = "SCHEDULE_HTML",
                content = html,
                maxChars = MAX_INPUT_CHARS
            ),
            responseJsonOnly = true,
            promptId = PROMPT_ID,
            promptVersion = PROMPT_VERSION
        )
    }

    private val RULES = listOf(
        "weekday 必须是 1..7 的整数，周一为 1、周日为 7。",
        "startSection 和 endSection 必须是正整数，且开始节次不得晚于结束节次。",
        "startWeek 和 endWeek 必须是正整数，且开始周不得晚于结束周。",
        "weekType 只能是 0、1、2，分别表示每周、单周、双周。",
        "课程名或时间字段缺失时丢弃该课程，并在 warnings 中说明，不得猜测。",
        "地点可由 campus、building、room、location、venue、place 等字段合并为 classroom。",
        "教师可来自 teacher、teacherName、instructor、lecturer 或教师相关中文文案。",
        "忽略选中、置入等页面状态文字和方括号内的参考时间，不把原始 HTML 填入业务字段。",
        "北交大课表常以行标题表示节次，单元格可能依次包含课程号、课程名、周次、教师、校区与教室。"
    )

    private val OUTPUT_SCHEMA = """
        {
          "courses": [
            {
              "name": "课程名",
              "teacher": "教师或空字符串",
              "classroom": "地点或空字符串",
              "weekday": 1,
              "startSection": 1,
              "endSection": 2,
              "startWeek": 1,
              "endWeek": 16,
              "weekType": 0,
              "note": "备注或空字符串",
              "color": "#1B6B5F"
            }
          ],
          "warnings": ["可选警告"]
        }
    """.trimIndent()

    private const val PROMPT_ID = "campusmate.schedule.parse"
    private const val PROMPT_VERSION = 1
    private const val MAX_INPUT_CHARS = 120_000
}
