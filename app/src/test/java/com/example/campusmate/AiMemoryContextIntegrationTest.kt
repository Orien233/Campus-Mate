package com.example.campusmate

import com.example.campusmate.data.model.*
import com.example.campusmate.domain.ai.advice.DashboardAdviceContextPolicy
import com.example.campusmate.domain.ai.advice.LlmDashboardAdviceValidator
import com.example.campusmate.domain.ai.context.*
import com.example.campusmate.domain.ai.memory.*
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.util.DateTimeUtils
import org.junit.Assert.*
import org.junit.Test

class AiMemoryContextIntegrationTest {
    private val now = DateTimeUtils.parseDateMillis("2026-06-08")!! + 10 * 3_600_000L

    @Test fun disabledSettingKeepsExistingReadScopeAndNeverLoadsMemory() {
        val source = Source(enabled = false)
        val snapshot = build(source)
        assertEquals(0, source.memoryReads)
        assertEquals("2026-05-26" to "2026-06-08", source.recordRange)
        assertTrue(snapshot.memoryContext.memories.isEmpty())
        assertNull(snapshot.memoryContext.growth)
        assertFalse(AiContextJsonRenderer.render(snapshot).contains("memoryContext"))
    }

    @Test fun optInRetrievesRelevantMemoryAndDerivesGrowthWithoutExpandingRecentStatistics() {
        val source = Source(enabled = true)
        val snapshot = build(source)
        assertEquals(1, source.memoryReads)
        assertEquals("2026-04-14" to "2026-06-08", source.recordRange)
        assertEquals(setOf("memory:1"), snapshot.allowedMemoryRefs)
        assertFalse(snapshot.allowedLocalRefs.contains("memory:1"))
        assertEquals(60, snapshot.learningProgress.totalMinutes)
        assertEquals(120, snapshot.memoryContext.growth?.totalMinutes)
        assertTrue(DashboardAdviceContextPolicy.allowedEvidenceRefs(snapshot).contains("learning:growth"))
        val rendered = AiContextJsonRenderer.render(snapshot)
        assertFalse(rendered.contains("无关秘密记忆"))
    }

    @Test fun filePurposeNeverReadsMemoryOrGrowthEvenWhenEnabled() {
        val source = Source(enabled = true)
        val snapshot = build(source, AiContextPurpose.FILE_ANALYSIS)
        assertEquals(0, source.memoryReads)
        assertNull(source.recordRange)
        assertTrue(snapshot.allowedMemoryRefs.isEmpty())
        val injected = snapshot.copy(memoryContext = AiMemoryContext(listOf(
            AiMemoryFact("memory:99", "goal", "秘密长期目标", now, null)
        )))
        val rendered = AiContextJsonRenderer.render(injected)
        assertFalse(rendered.contains("memory:"))
        assertFalse(rendered.contains("秘密长期目标"))
        assertFalse(rendered.contains("memoryContext"))
        assertTrue(injected.allowedMemoryRefs.isEmpty())
    }

    @Test fun fingerprintChangesOnSelectedMemoryEditsExpiryAndGrowthButNotCaptureTime() {
        val first = build(Source(true))
        val hash = DashboardAdviceContextPolicy.fingerprint(first)
        assertEquals(hash, DashboardAdviceContextPolicy.fingerprint(first.copy(generatedAt = now + 1_000)))
        val edited = first.copy(memoryContext = first.memoryContext.copy(
            memories = first.memoryContext.memories.map { it.copy(content = "新的数学学习目标") }
        ))
        assertNotEquals(hash, DashboardAdviceContextPolicy.fingerprint(edited))
        val expired = build(Source(true, expiry = now), time = now)
        assertTrue(expired.allowedMemoryRefs.isEmpty())
        assertNotEquals(hash, DashboardAdviceContextPolicy.fingerprint(expired))
        val newRecords = first.copy(memoryContext = first.memoryContext.copy(
            growth = first.memoryContext.growth!!.copy(totalMinutes = 180)
        ))
        assertNotEquals(hash, DashboardAdviceContextPolicy.fingerprint(newRecords))
    }

    @Test fun dashboardValidatorOnlyAcceptsSelectedMemoryEvidence() {
        val snapshot = build(Source(true))
        val raw = """{
          "headline":"学习建议","summary":"根据学习目标安排。",
          "items":[
            {"title":"复习数学","detail":"整理错题","evidenceRefs":["memory:1"]},
            {"title":"不存在的依据","detail":"不应显示","evidenceRefs":["memory:99"]}
          ]
        }"""
        val result = LlmDashboardAdviceValidator().parseAndValidate(raw, snapshot)
        assertEquals(listOf("复习数学"), result.advice!!.items.map { it.title })
        assertEquals(listOf("memory:1"), result.advice!!.items.single().evidenceRefs)
    }

    private fun build(
        source: Source,
        purpose: AiContextPurpose = AiContextPurpose.DASHBOARD_ADVICE,
        time: Long = now
    ) = AiContextOrchestrator(source) { time }.build(AiContextBuildRequest(purpose, "2026-06-08"))

    private inner class Source(private val enabled: Boolean, private val expiry: Long? = null) : AiContextDataSource {
        var memoryReads = 0
        var recordRange: Pair<String, String>? = null
        override fun loadCourses() = emptyList<Course>()
        override fun loadTasks() = listOf(StudyTask(id = 1, title = "数学作业"))
        override fun loadPlans(startDate: String, endDate: String) = emptyList<StudyPlan>()
        override fun loadStudyRecords(startDate: String, endDate: String): List<StudyRecord> {
            recordRange = startDate to endDate
            return listOf(
                StudyRecord(recordDate = "2026-06-08", durationSec = 3_600),
                StudyRecord(recordDate = "2026-04-14", durationSec = 3_600)
            )
        }
        override fun loadCachedWeather(city: String): WeatherResult? = null
        override fun loadSettings() = AiContextSettingsSource(
            60, "08:00", "22:00", "北京", emptyList(), "Asia/Shanghai", enabled
        )
        override fun loadMemories(nowMillis: Long): List<AiMemory> {
            memoryReads++
            return listOf(
                AiMemory(id = 1, category = AiMemory.CATEGORY_GOAL, content = "数学先做错题", expiresAt = expiry),
                AiMemory(id = 2, content = "无关秘密记忆"),
                AiMemory(id = 3, content = "数学停用记忆", isEnabled = false)
            )
        }
    }
}
