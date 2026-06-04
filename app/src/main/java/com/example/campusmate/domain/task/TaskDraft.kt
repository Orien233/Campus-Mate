package com.example.campusmate.domain.task

import com.example.campusmate.data.model.StudyTask
import java.io.Serializable

/** Parsed task candidate before the user reviews and saves it in TaskEditActivity. */
data class TaskDraft(
    val title: String,
    val description: String? = null,
    val courseName: String? = null,
    val type: Int = StudyTask.TYPE_HOMEWORK,
    val priority: Int = StudyTask.PRIORITY_NORMAL,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val sourceText: String? = null,
    val warnings: List<String> = emptyList()
) : Serializable
