package com.example.campusmate

import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.domain.import_.CourseDraft
import com.example.campusmate.domain.task.TaskDraft
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFilePreviewPayloadTest {
    @Test
    fun validatedDraftPayloads_areSerializableWithoutSourceText() {
        val course = CourseDraft(
            name = "算法设计",
            weekday = 3,
            startSection = 3,
            endSection = 4,
            startWeek = 1,
            endWeek = 16,
            sourceText = null
        )
        val task = TaskDraft(title = "提交算法作业", sourceText = null)
        val plan = StudyPlan(
            title = "整理算法笔记",
            planDate = "2026-06-08",
            plannedMinutes = 60,
            startTime = "10:00",
            endTime = "11:00",
            sourceType = StudyPlan.SOURCE_LLM
        )

        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { stream ->
                stream.writeObject(arrayListOf(course))
                stream.writeObject(arrayListOf(task))
                stream.writeObject(arrayListOf(plan))
            }
            output.toByteArray()
        }

        assertTrue(bytes.isNotEmpty())
        assertTrue(course.sourceText == null)
        assertTrue(task.sourceText == null)
    }
}
