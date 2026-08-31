package com.example.campusmate

import com.example.campusmate.domain.ai.memory.AiMemoryContext
import com.example.campusmate.domain.ai.memory.AiMemoryFact
import com.example.campusmate.domain.plan.StudyPlanContext

internal fun planRagContext(memoryContext: AiMemoryContext = AiMemoryContext()): StudyPlanContext {
    return StudyPlanContext(
        date = "2026-06-05",
        weekday = 5,
        weekdayName = "周五",
        dailyGoalMinutes = 120,
        courses = emptyList(),
        tasks = emptyList(),
        weather = null,
        recentStudyRecords = emptyList(),
        existingPlans = emptyList(),
        coursesById = emptyMap(),
        courseTimeRanges = emptyMap(),
        planEarliestTime = "08:00",
        planLatestTime = "22:00",
        generationStartTime = "08:00",
        memoryContext = memoryContext
    )
}

internal fun planRagMemoryContext(content: String = "希望用短时段练习线性代数"): AiMemoryContext {
    return AiMemoryContext(
        memories = listOf(
            AiMemoryFact(
                localRef = "memory:7",
                category = "preference",
                content = content,
                updatedAt = 100L,
                expiresAt = null
            )
        )
    )
}
