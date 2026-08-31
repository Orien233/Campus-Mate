package com.example.campusmate

import com.example.campusmate.data.repository.DataClearResult
import org.junit.Assert.assertEquals
import org.junit.Test

class DataClearResultTest {
    @Test
    fun totalIncludesUserMemories() {
        val result = DataClearResult(courseCount = 1, taskCount = 2, focusSessionCount = 3,
            studyRecordCount = 4, importLogCount = 5, memoryCount = 7)
        assertEquals(22, result.totalCount)
        assertEquals(7, result.memoryCount)
    }
}
