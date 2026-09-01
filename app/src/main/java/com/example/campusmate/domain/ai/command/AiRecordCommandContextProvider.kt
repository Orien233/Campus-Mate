package com.example.campusmate.domain.ai.command

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.util.DateTimeUtils
import java.text.Normalizer

class AiRecordCommandContextProvider(context: Context) {
    private val courseRepository = CourseRepository(context)
    private val taskRepository = TaskRepository(context)
    private val planRepository = StudyPlanRepository(context)

    fun build(input: String): AiRecordCommandContext {
        val courses = AiRecordCandidateSelector.select(
            courseRepository.getAllCourses(), input, MAX_COURSES, { it.name }, { it.updatedAt }
        ).map(Course::toAiSnapshot)
        val tasks = AiRecordCandidateSelector.select(
            taskRepository.getAllTasks(), input, MAX_TASKS, { it.title }, { it.updatedAt }
        ).map(StudyTask::toAiSnapshot)
        val plans = AiRecordCandidateSelector.select(
            planRepository.getAllPlans(), input, MAX_PLANS, { it.title }, { it.updatedAt }
        ).map(StudyPlan::toAiSnapshot)
        return AiRecordCommandContext(DateTimeUtils.nowMillis(), courses, tasks, plans)
    }

    companion object {
        const val MAX_COURSES = 40
        const val MAX_TASKS = 60
        const val MAX_PLANS = 60
    }
}

object AiRecordCandidateSelector {
    fun <T> select(
        records: List<T>,
        input: String,
        limit: Int,
        name: (T) -> String,
        updatedAt: (T) -> Long
    ): List<T> {
        val normalizedInput = normalize(input)
        val tokens = tokenize(normalizedInput)
        return records.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<T>> {
                    score(normalize(name(it.value)), normalizedInput, tokens)
                }.thenByDescending { updatedAt(it.value) }.thenBy { it.index }
            )
            .take(limit.coerceAtLeast(0))
            .map { it.value }
    }

    private fun score(name: String, input: String, tokens: Set<String>): Int {
        if (name.isBlank()) return 0
        var score = if (input.contains(name)) 1000 + name.length else 0
        tokens.forEach { token -> if (token.length >= 2 && name.contains(token)) score += token.length * 10 }
        return score
    }

    private fun tokenize(value: String): Set<String> {
        val parts = value.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 2 }
        val bigrams = parts.flatMap { part ->
            if (part.length < 3) emptyList() else (0 until part.length - 1).map { part.substring(it, it + 2) }
        }
        return (parts + bigrams).toSet()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().trim()
}

internal fun Course.toAiSnapshot() = AiCourseSnapshot(
    id, name, teacher, classroom, weekday, startSection, endSection,
    startWeek, endWeek, weekType, color, note, updatedAt
)
internal fun StudyTask.toAiSnapshot() = AiTaskSnapshot(
    id, courseId, title, description, type, priority, dueAt, remindAt, status, updatedAt
)
internal fun StudyPlan.toAiSnapshot() = AiPlanSnapshot(
    id, title, planDate, plannedMinutes, startTime, endTime, type, status,
    actualMinutes, sourceType, updatedAt
)
