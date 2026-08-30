package com.example.campusmate.domain.ai.file

import com.example.campusmate.domain.import_.CourseDraft
import com.example.campusmate.domain.task.TaskDraft

data class AiFileAnalysis(
    val summary: String,
    val keyPoints: List<String>,
    val courses: List<AiFileCourseCandidate>,
    val tasks: List<AiFileTaskCandidate>,
    val plans: List<AiFilePlanCandidate>,
    val insights: List<AiFileInsight>,
    val localMatches: List<AiFileLocalMatch>,
    val warnings: List<String>
)

data class AiFileCourseCandidate(
    val draft: CourseDraft,
    val evidenceRefs: List<String>
) {
    init {
        require(draft.sourceText == null) { "Validated course candidates cannot retain source text" }
        require(AI_FILE_SELECTION_REF in evidenceRefs) { "Course candidate requires file evidence" }
    }
}

data class AiFileTaskCandidate(
    val draft: TaskDraft,
    val evidenceRefs: List<String>
) {
    init {
        require(draft.sourceText == null) { "Validated task candidates cannot retain source text" }
        require(AI_FILE_SELECTION_REF in evidenceRefs) { "Task candidate requires file evidence" }
    }
}

data class AiFilePlanCandidate(
    val draft: AiFilePlanDraft,
    val evidenceRefs: List<String>
) {
    init {
        require(AI_FILE_SELECTION_REF in evidenceRefs) { "Plan candidate requires file evidence" }
    }
}

data class AiFilePlanDraft(
    val title: String,
    val planDate: String,
    val plannedMinutes: Int,
    val startTime: String,
    val endTime: String
)

data class AiFileInsight(
    val title: String,
    val detail: String,
    val evidenceRefs: List<String>,
    val localRefs: List<String>
) {
    init {
        require(AI_FILE_SELECTION_REF in evidenceRefs) { "Insight requires file evidence" }
    }
}

data class AiFileLocalMatch(
    val localRef: String,
    val reason: String,
    val evidenceRefs: List<String>
) {
    init {
        require(localRef.isNotBlank()) { "Local match requires a local reference" }
        require(AI_FILE_SELECTION_REF in evidenceRefs) { "Local match requires file evidence" }
    }
}

data class AiFileAnalysisEnvelope(
    val analysis: AiFileAnalysis,
    val providerName: String,
    val model: String,
    val promptTag: String
)

enum class AiFileAnalysisError {
    DISABLED,
    NO_API_KEY,
    INVALID_INPUT,
    REQUEST_FAILED,
    INVALID_RESPONSE
}

sealed class AiFileAnalysisResult {
    data class Success(val envelope: AiFileAnalysisEnvelope) : AiFileAnalysisResult()

    data class Failure(
        val error: AiFileAnalysisError,
        val message: String
    ) : AiFileAnalysisResult()
}
