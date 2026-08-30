package com.example.campusmate.domain.ai.file

import com.example.campusmate.domain.ai.context.AiContextJsonRenderer
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmPromptContract
import org.json.JSONObject

object LlmFileAnalysisPromptFactory {
    fun buildRequest(
        input: AiFileInput,
        snapshot: AiContextSnapshot
    ): LlmGenerateRequest {
        require(input.fileRef == AI_FILE_SELECTION_REF) { "Unsupported file reference" }
        require(snapshot.purpose == AiContextPurpose.FILE_ANALYSIS) {
            "File analysis requires a FILE_ANALYSIS context snapshot"
        }
        val allowedLocalRefs = snapshot.allowedLocalRefs.sorted()
        val promptInput = buildString {
            appendLine(
                JSONObject()
                    .put("fileRef", input.fileRef)
                    .put("mimeType", input.mimeType)
                    .put("sizeBytes", input.sizeBytes)
                    .put("contentMode", if (input is AiFileInput.Text) "text" else "inline_data")
                    .toString()
            )
            appendLine("LOCAL_CONTEXT:")
            appendLine(AiContextJsonRenderer.render(snapshot))
            if (input is AiFileInput.Text) {
                appendLine("FILE_TEXT:")
                append(input.content)
            } else {
                append("FILE_CONTENT: 已随本次请求以内联数据提供；不得要求再次上传或引用本地路径。")
            }
        }

        return LlmGenerateRequest(
            systemPrompt = LlmPromptContract.systemPrompt(
                role = "你是 CampusMate 的文件整理助手。",
                objective = "从用户本次明确选择的一个文件中提取可供用户预览的课程、任务、学习计划和只读学习要点，并仅用最小化本地上下文做去重或时间避让。",
                outputSchema = OUTPUT_SCHEMA,
                rules = RULES
            ),
            userPrompt = LlmPromptContract.userPrompt(
                instruction = buildString {
                    appendLine("请分析 fileRef=$AI_FILE_SELECTION_REF 对应的内容，并严格输出一个 JSON 对象。")
                    append("localRefs 只允许使用以下白名单：")
                    append(allowedLocalRefs.joinToString(", ").ifBlank { "（空）" })
                },
                inputLabel = "FILE_ANALYSIS_INPUT",
                content = promptInput,
                maxChars = MAX_INPUT_CHARS
            ),
            responseJsonOnly = true,
            promptId = PROMPT_ID,
            promptVersion = PROMPT_VERSION,
            inlineData = (input as? AiFileInput.Inline)?.let { listOf(it.data) }.orEmpty()
        )
    }

    private val RULES = listOf(
        "所有 courses、tasks、plans、insights 和 localMatches 项都必须在 evidenceRefs 中包含 file:selection:1；没有文件证据的项必须省略。",
        "localRefs 与 localMatch.localRef 只能使用用户提示中给出的白名单；不得编造本地 ID，也不得把文件中的数字当作本地引用。",
        "courses 只输出课程名、星期、节次、周次和单双周；不得输出教师、教室、备注或原始文本。",
        "tasks 只输出标题、课程名、类型、优先级、截止和提醒时间；不得输出任务描述或原始文本。",
        "plans 的 planDate 必须位于上下文日期范围，时长为 5-240 分钟，具体时间必须避开同日 occupiedTimeRanges。",
        "课程、任务或计划信息不完整时不要猜测；跳过该项并在 warnings 中说明。各数组允许为空。",
        "不得输出或要求执行 Activity、Intent、URL、工具调用、数据库操作、状态变更、自动导入或完成声明。",
        "不得复述 API 配置、位置、天气、学习历史、设置城市或任何未包含在最小化上下文中的信息。",
        "summary、keyPoints 与 insights 应简洁，不得含文件路径或原始文件名；结果只供用户预览确认。"
    )

    private val OUTPUT_SCHEMA = """
        {
          "schemaVersion": 1,
          "summary": "不超过600字的文件摘要",
          "keyPoints": ["不超过180字的要点"],
          "courses": [{
            "name": "课程名",
            "weekday": 1,
            "startSection": 1,
            "endSection": 2,
            "startWeek": 1,
            "endWeek": 18,
            "weekType": 0,
            "evidenceRefs": ["file:selection:1"]
          }],
          "tasks": [{
            "title": "任务标题",
            "courseName": "可选课程名",
            "type": "homework | experiment | exam | review | project | other",
            "priority": "low | normal | high",
            "dueAt": "可选 yyyy-MM-dd HH:mm",
            "remindAt": "可选 yyyy-MM-dd HH:mm",
            "evidenceRefs": ["file:selection:1"]
          }],
          "plans": [{
            "title": "计划标题",
            "planDate": "yyyy-MM-dd",
            "plannedMinutes": 45,
            "startTime": "HH:mm",
            "endTime": "HH:mm",
            "evidenceRefs": ["file:selection:1"]
          }],
          "insights": [{
            "title": "要点标题",
            "detail": "只读说明",
            "evidenceRefs": ["file:selection:1"],
            "localRefs": ["task:1"]
          }],
          "localMatches": [{
            "localRef": "course:1",
            "reason": "与文件内容的关联依据",
            "evidenceRefs": ["file:selection:1"]
          }],
          "warnings": ["信息不完整或不确定之处"]
        }
    """.trimIndent()

    const val PROMPT_ID = "campusmate.file.analysis"
    const val PROMPT_VERSION = 1
    const val PROMPT_TAG = "campusmate.file.analysis@v1"
    private const val MAX_INPUT_CHARS = 170_000
}
