package com.example.campusmate

import com.example.campusmate.data.model.Course
import com.example.campusmate.domain.import_.JsoupScheduleParser
import com.example.campusmate.domain.import_.LlmCourseDraftValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    @Test
    fun jsoupScheduleParser_parsesSampleScheduleHtml() {
        val sampleHtml = readSampleScheduleHtml()
        val drafts = JsoupScheduleParser().parse(sampleHtml)

        assertEquals(6, drafts.size)
        assertTrue(drafts.any { it.name == "高等数学" && it.weekday == 1 && it.startSection == 1 && it.endSection == 2 })
        assertTrue(drafts.any { it.name.contains("冲突示例") && it.startWeek == 8 && it.endWeek == 12 })
    }

    @Test
    fun llmCourseValidator_mergesLocationAliases() {
        val result = LlmCourseDraftValidator().parse(
            """
            {
              "courses": [
                {
                  "name": "软件工程",
                  "teacherName": "王老师",
                  "campus": "海淀校区",
                  "building": "主楼",
                  "room": "A101",
                  "location": "海淀校区 主楼 A101",
                  "weekday": 2,
                  "startSection": 3,
                  "endSection": 4,
                  "startWeek": 1,
                  "endWeek": 16,
                  "oddEven": "双周"
                }
              ]
            }
            """.trimIndent()
        )

        val draft = result.drafts.single()
        assertEquals("海淀校区 主楼 A101", draft.classroom)
        assertEquals("王老师", draft.teacher)
        assertEquals(Course.WEEK_TYPE_EVEN, draft.weekType)
    }

    @Test
    fun jsoupScheduleParser_genericFallbackExtractsTeacherRoomAndWeeks() {
        val html = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <th>第3-4节</th>
                <td>
                  数据结构
                  教师：李老师
                  地点：海淀校区 思源楼 SY105
                  周次：1-16周 单周
                </td>
              </tr>
            </table>
        """.trimIndent()

        val draft = JsoupScheduleParser().parse(html).single()

        assertEquals("数据结构", draft.name)
        assertEquals("李老师", draft.teacher)
        assertEquals("海淀校区 思源楼 SY105", draft.classroom)
        assertEquals(1, draft.weekday)
        assertEquals(3, draft.startSection)
        assertEquals(4, draft.endSection)
        assertEquals(1, draft.startWeek)
        assertEquals(16, draft.endWeek)
        assertEquals(Course.WEEK_TYPE_ODD, draft.weekType)
    }

    private fun readSampleScheduleHtml(): String {
        val candidates = listOf(
            File("src/main/assets/sample_schedule.html"),
            File("app/src/main/assets/sample_schedule.html")
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }
}
