package com.example.campusmate.domain.import_

import com.example.campusmate.domain.llm.LlmGenerateRequest

object LlmSchedulePromptFactory {
    fun buildRequest(html: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = buildSystemPrompt(),
            userPrompt = buildUserPrompt(html),
            responseJsonOnly = true
        )
    }

    private fun buildSystemPrompt(): String {
        return """
            你是 CampusMate 的课表解析助手。
            请从用户提供的课表 HTML 中提取课程信息，并且只返回严格 JSON（不要输出 markdown、解释、代码块或任何前后缀文本）。
            注意：输出必须是一个 JSON 对象，且只能包含 JSON。

            返回 JSON 的结构必须符合以下形状：
            {
              "courses": [
                {
                  "name": "课程名",
                  "teacher": "教师（可选）",
                  "classroom": "教室（可选）",
                  "weekday": 1,
                  "startSection": 1,
                  "endSection": 2,
                  "startWeek": 1,
                  "endWeek": 16,
                  "weekType": 0,
                  "note": "备注（可选）",
                  "color": "#1B6B5F"
                }
              ],
              "warnings": [
                "可选警告 1",
                "可选警告 2"
              ]
            }

            规则：
            - weekday 必须是 1..7 的整数（周一=1，周日=7）。
            - startSection/endSection 必须是正整数，且 startSection <= endSection。
            - startWeek/endWeek 必须是正整数，且 startWeek <= endWeek。
            - weekType 只能是 0/1/2：0=每周，1=单周，2=双周。
            - 如果字段缺失或不确定，不要编造数据；请丢弃该课程，并在 warnings 里说明原因。
            - 不要把原始 HTML 直接塞进课程字段。
            - 如果存在多门课程，请全部输出。
            - 地点信息尽量写入 classroom；如果页面拆成 campus/building/room/location/venue/place，也要合并成可读地点。
            - 教师可来自 teacher/teacherName/instructor/lecturer 等字段或页面中的“教师/老师/授课教师”文案。

            常见中文模式提示：
            - “星期一/星期二/…/星期日” 对应 weekday 1..7。
            - “第1节、第2节…” 对应 startSection/endSection。
            - “第01-16周/第1-16周” 对应 startWeek/endWeek。
            - “单周/双周/每周” 对应 weekType。

            北交大选课课表页（示例）提示：
            - 行标题可能是“第1节 [08:00-09:50]”“第2节 [10:10-12:00]”等：
              其中“第1节/第2节”用于节次（startSection/endSection）；方括号里的时间段仅作参考，不要写入任何字段。
            - 单元格内常见信息顺序（可能有变体）：
              课程号[班号]
              课程名
              第01-16周  教师名
              校区/楼宇  教室号
              [选中]/[置入] 等状态文本
              请提取：课程名、教师、教室、weekday、start/endSection、start/endWeek、weekType；忽略“[选中]”“[置入]”等状态词。
            - 如果地点拆成“校区、教学楼、房间号”，请组合成类似“海淀校区 主楼 A101”的 classroom。
        """.trimIndent()
    }

    private fun buildUserPrompt(html: String): String {
        return """
            请从下面的课表 HTML 中提取课程，并且只返回 JSON：

            $html
        """.trimIndent()
    }
}
