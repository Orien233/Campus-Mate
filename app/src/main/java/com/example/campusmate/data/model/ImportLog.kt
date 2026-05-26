package com.example.campusmate.data.model

/** Summary of one schedule import attempt. */
data class ImportLog(
    val id: Long = 0L,
    val sourceType: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val conflictCount: Int,
    val createdAt: Long = 0L,
    val message: String? = null
) {
    companion object {
        const val SOURCE_SAMPLE_HTML = 0
        const val SOURCE_PASTED_HTML = 1
        const val SOURCE_WEBVIEW = 2
    }
}
