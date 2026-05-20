package com.example.campusmate.ui.task

import android.content.Context
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.util.DateTimeUtils

/** Presentation helpers for task type, priority, status, and dates. */
object TaskUiFormatter {
    fun typeLabel(context: Context, type: Int): String {
        val labels = context.resources.getStringArray(R.array.task_type_labels)
        return labels.getOrElse(type) { context.getString(R.string.task_type_other) }
    }

    fun priorityLabel(context: Context, priority: Int): String {
        val labels = context.resources.getStringArray(R.array.task_priority_labels)
        return labels.getOrElse(priority) { context.getString(R.string.task_priority_normal) }
    }

    fun statusLabel(context: Context, status: Int): String {
        return when (status) {
            StudyTask.STATUS_DONE -> context.getString(R.string.task_status_done)
            StudyTask.STATUS_ARCHIVED -> context.getString(R.string.task_status_archived)
            else -> context.getString(R.string.task_status_todo)
        }
    }

    fun dueLabel(context: Context, dueAt: Long?): String {
        return dueAt?.let { DateTimeUtils.formatDateTime(it) } ?: context.getString(R.string.task_no_due_time)
    }

    fun remindLabel(context: Context, remindAt: Long?): String {
        return remindAt?.let { DateTimeUtils.formatDateTime(it) } ?: context.getString(R.string.task_no_reminder)
    }
}
