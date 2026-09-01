package com.example.campusmate.domain.ai.command

import java.io.Serializable

enum class AiRecordType : Serializable { COURSE, TASK, PLAN }
enum class AiRecordOperation : Serializable { CREATE, UPDATE, DELETE }

sealed interface AiRecordSnapshot : Serializable {
    val id: Long
    val updatedAt: Long
    val displayName: String
}

data class AiCourseSnapshot(
    override val id: Long,
    val name: String,
    val teacher: String?,
    val classroom: String?,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,
    val color: String?,
    val note: String?,
    override val updatedAt: Long
) : AiRecordSnapshot { override val displayName: String get() = name }

data class AiTaskSnapshot(
    override val id: Long,
    val courseId: Long?,
    val title: String,
    val description: String?,
    val type: Int,
    val priority: Int,
    val dueAt: Long?,
    val remindAt: Long?,
    val status: Int,
    override val updatedAt: Long
) : AiRecordSnapshot { override val displayName: String get() = title }

data class AiPlanSnapshot(
    override val id: Long,
    val title: String,
    val planDate: String,
    val plannedMinutes: Int,
    val startTime: String?,
    val endTime: String?,
    val type: Int,
    val status: Int,
    val actualMinutes: Int,
    val sourceType: Int,
    override val updatedAt: Long
) : AiRecordSnapshot { override val displayName: String get() = title }

data class AiRecordChange(
    val changeId: String,
    val recordType: AiRecordType,
    val operation: AiRecordOperation,
    val targetRef: String?,
    val expectedUpdatedAt: Long?,
    val evidenceQuote: String,
    val before: AiRecordSnapshot?,
    val after: AiRecordSnapshot?,
    val hasTimeConflict: Boolean = false,
    val warnings: List<String> = emptyList()
) : Serializable {
    val defaultSelected: Boolean get() = operation != AiRecordOperation.DELETE && !hasTimeConflict
    val displayName: String get() = after?.displayName ?: before?.displayName.orEmpty()
}

data class AiRecordCommandContext(
    val generatedAt: Long,
    val courses: List<AiCourseSnapshot>,
    val tasks: List<AiTaskSnapshot>,
    val plans: List<AiPlanSnapshot>,
    val warnings: List<String> = emptyList()
) {
    val allowedRefs: Set<String>
        get() = buildSet {
            courses.forEach { add("course:${it.id}") }
            tasks.forEach { add("task:${it.id}") }
            plans.forEach { add("plan:${it.id}") }
        }
}

data class AiRecordCommandEnvelope(
    val changes: List<AiRecordChange>,
    val warnings: List<String>,
    val providerName: String,
    val model: String,
    val promptTag: String
) : Serializable

enum class AiRecordCommandError { DISABLED, NO_API_KEY, INVALID_INPUT, REQUEST_FAILED, INVALID_RESPONSE }

sealed class AiRecordCommandResult {
    data class Success(val envelope: AiRecordCommandEnvelope) : AiRecordCommandResult()
    data class Failure(val error: AiRecordCommandError, val message: String) : AiRecordCommandResult()
}

data class AiRecordApplyFailure(val changeId: String, val displayName: String, val reason: String) : Serializable
data class AiRecordApplyReport(val successCount: Int, val failures: List<AiRecordApplyFailure>) : Serializable
