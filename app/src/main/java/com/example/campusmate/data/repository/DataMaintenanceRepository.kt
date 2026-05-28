package com.example.campusmate.data.repository

import android.content.Context
import com.example.campusmate.data.db.CampusMateContract

/** Maintenance operations for demo reset, scoped behind ContentResolver. */
class DataMaintenanceRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun clearAllData(): DataClearResult {
        val attachmentCount = resolver.delete(CampusMateContract.TaskAttachments.CONTENT_URI, null, null)
        val weatherCount = resolver.delete(CampusMateContract.WeatherCache.CONTENT_URI, null, null)
        val buddyCount = resolver.delete(CampusMateContract.StudyBuddies.CONTENT_URI, null, null)
        val profileCount = resolver.delete(CampusMateContract.UserProfile.CONTENT_URI, null, null)
        val studyRecordCount = resolver.delete(CampusMateContract.StudyRecords.CONTENT_URI, null, null)
        val focusSessionCount = resolver.delete(CampusMateContract.FocusSessions.CONTENT_URI, null, null)
        val importLogCount = resolver.delete(CampusMateContract.ImportLogs.CONTENT_URI, null, null)
        val taskCount = resolver.delete(CampusMateContract.StudyTasks.CONTENT_URI, null, null)
        val courseCount = resolver.delete(CampusMateContract.Courses.CONTENT_URI, null, null)
        return DataClearResult(
            courseCount = courseCount,
            taskCount = taskCount,
            focusSessionCount = focusSessionCount,
            studyRecordCount = studyRecordCount,
            importLogCount = importLogCount,
            profileCount = profileCount,
            buddyCount = buddyCount,
            weatherCount = weatherCount,
            attachmentCount = attachmentCount
        )
    }
}

data class DataClearResult(
    val courseCount: Int,
    val taskCount: Int,
    val focusSessionCount: Int,
    val studyRecordCount: Int,
    val importLogCount: Int,
    val profileCount: Int = 0,
    val buddyCount: Int = 0,
    val weatherCount: Int = 0,
    val attachmentCount: Int = 0
) {
    val totalCount: Int
        get() = courseCount + taskCount + focusSessionCount + studyRecordCount + importLogCount + profileCount + buddyCount + weatherCount + attachmentCount
}
