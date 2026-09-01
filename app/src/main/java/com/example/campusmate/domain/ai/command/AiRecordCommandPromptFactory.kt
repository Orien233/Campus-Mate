package com.example.campusmate.domain.ai.command

import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmPromptContract
import org.json.JSONArray
import org.json.JSONObject

object AiRecordCommandPromptFactory {
    fun buildRequest(input: String, context: AiRecordCommandContext): LlmGenerateRequest {
        require(input.isNotBlank() && input.length <= MAX_INPUT_CHARS)
        val contextJson = JSONObject()
            .put("generatedAt", context.generatedAt)
            .put("courses", JSONArray(context.courses.map(::courseJson)))
            .put("tasks", JSONArray(context.tasks.map(::taskJson)))
            .put("plans", JSONArray(context.plans.map(::planJson)))
        return LlmGenerateRequest(
            systemPrompt = LlmPromptContract.systemPrompt(
                role = "你是 CampusMate 的自然语言记录整理助手。",
                objective = "把用户文字解析成课程、任务、计划的新增、明确字段修改或删除候选；结果只供本地校验和用户批量预览。",
                outputSchema = OUTPUT_SCHEMA,
                rules = RULES
            ),
            userPrompt = LlmPromptContract.userPrompt(
                instruction = "从 USER_TEXT 中提取最多 30 项变更。UPDATE/DELETE 必须引用 RECORD_CONTEXT 中存在的精确 ref。",
                inputLabel = "RECORD_COMMAND_INPUT",
                content = "RECORD_CONTEXT:\n$contextJson\nUSER_TEXT:\n$input",
                maxChars = MAX_PROMPT_CHARS
            ),
            responseJsonOnly = true,
            promptId = PROMPT_ID,
            promptVersion = PROMPT_VERSION
        )
    }

    private fun courseJson(value: AiCourseSnapshot) = JSONObject()
        .put("ref", "course:${value.id}").put("updatedAt", value.updatedAt)
        .put("name", value.name).put("teacher", value.teacher).put("classroom", value.classroom)
        .put("weekday", value.weekday).put("startSection", value.startSection)
        .put("endSection", value.endSection).put("startWeek", value.startWeek)
        .put("endWeek", value.endWeek).put("weekType", value.weekType)
        .put("color", value.color).put("note", value.note)

    private fun taskJson(value: AiTaskSnapshot) = JSONObject()
        .put("ref", "task:${value.id}").put("updatedAt", value.updatedAt)
        .put("courseRef", value.courseId?.let { "course:$it" }).put("title", value.title)
        .put("description", value.description).put("type", value.type)
        .put("priority", value.priority).put("dueAt", value.dueAt)
        .put("remindAt", value.remindAt).put("status", value.status)

    private fun planJson(value: AiPlanSnapshot) = JSONObject()
        .put("ref", "plan:${value.id}").put("updatedAt", value.updatedAt)
        .put("title", value.title).put("planDate", value.planDate)
        .put("plannedMinutes", value.plannedMinutes).put("startTime", value.startTime)
        .put("endTime", value.endTime).put("type", value.type).put("status", value.status)

    private val RULES = listOf(
        "每项必须包含 recordType、operation、evidenceQuote 和 fields；evidenceQuote 必须是 USER_TEXT 中能逐字找到的短片段。",
        "UPDATE/DELETE 必须包含 targetRef，且只能使用上下文白名单中的 course:ID、task:ID、plan:ID；不得编造 ID。",
        "UPDATE 的 fields 只放用户明确要求改变的字段；缺失字段表示保留，JSON null 只用于清空可空字段。",
        "DELETE 仅在用户文字明确表达删除意图时输出；不要把完成、归档、取消提醒解释为删除。",
        "不得修改 ID、来源、创建/更新时间、实际学习时长或删除标记。",
        "日期时间：任务 dueAt/remindAt 使用 yyyy-MM-dd HH:mm；计划 planDate 使用 yyyy-MM-dd，startTime/endTime 使用 HH:mm。",
        "课程 weekType 使用 0每周/1单周/2双周；任务 type 0..5、priority 0..2、status 0待办/1完成/2归档；计划 type 0每日/1每周、status 0待完成/1完成/2过期。",
        "信息含糊、目标不唯一、字段不足或不合法时跳过该项并写 warnings；不得要求应用自动执行。"
    )

    private val OUTPUT_SCHEMA = """
        {
          "schemaVersion": 1,
          "changes": [{
            "recordType": "course | task | plan",
            "operation": "create | update | delete",
            "targetRef": "update/delete 时必填",
            "evidenceQuote": "USER_TEXT 原文片段",
            "fields": {}
          }],
          "warnings": ["含糊或跳过原因"]
        }
    """.trimIndent()

    const val PROMPT_ID = "campusmate.record.command"
    const val PROMPT_VERSION = 1
    const val PROMPT_TAG = "campusmate.record.command@v1"
    const val MAX_INPUT_CHARS = 4000
    private const val MAX_PROMPT_CHARS = 80_000
}
