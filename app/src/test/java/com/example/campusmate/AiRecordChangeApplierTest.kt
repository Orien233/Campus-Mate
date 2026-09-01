package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.ai.command.AiRecordChange
import com.example.campusmate.domain.ai.command.AiRecordChangeApplier
import com.example.campusmate.domain.ai.command.AiRecordChangeGateway
import com.example.campusmate.domain.ai.command.AiRecordOperation
import com.example.campusmate.domain.ai.command.AiRecordSnapshot
import com.example.campusmate.domain.ai.command.AiRecordType
import com.example.campusmate.domain.ai.command.AiTaskSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecordChangeApplierTest {
    @Test
    fun staleTargetFailsButRemainingChangesContinue() {
        val gateway = FakeGateway()
        gateway.records[AiRecordType.TASK to 1L] = task(1, 20, "已变化")
        gateway.records[AiRecordType.TASK to 2L] = task(2, 10, "待修改")
        val stale = updateChange(1, 10, task(1, 10, "新标题"))
        val valid = updateChange(2, 10, task(2, 10, "已修改"))

        val report = AiRecordChangeApplier(gateway).apply(listOf(stale, valid))

        assertEquals(1, report.successCount)
        assertEquals(1, report.failures.size)
        assertTrue(report.failures.single().reason.contains("已变化"))
        assertEquals("已修改", gateway.records[AiRecordType.TASK to 2L]?.displayName)
    }

    @Test
    fun taskCreateUpdateAndDelete_allSynchronizeReminder() {
        val gateway = FakeGateway()
        gateway.records[AiRecordType.TASK to 2L] = task(2, 10, "旧任务")
        gateway.records[AiRecordType.TASK to 3L] = task(3, 10, "删除任务")
        val create = AiRecordChange(
            "create", AiRecordType.TASK, AiRecordOperation.CREATE, null, null, "新增任务",
            null, task(0, 0, "新增任务")
        )
        val update = updateChange(2, 10, task(2, 10, "完成任务").copy(status = StudyTask.STATUS_DONE))
        val delete = AiRecordChange(
            "delete", AiRecordType.TASK, AiRecordOperation.DELETE, "task:3", 10, "删除任务",
            task(3, 10, "删除任务"), null
        )

        val report = AiRecordChangeApplier(gateway).apply(listOf(create, update, delete))

        assertEquals(3, report.successCount)
        assertEquals(3, gateway.reminderSyncs.size)
        assertTrue(gateway.reminderSyncs.any { it.id == 3L && it.remindAt == null })
    }

    private fun updateChange(id: Long, expected: Long, after: AiTaskSnapshot) = AiRecordChange(
        "update-$id", AiRecordType.TASK, AiRecordOperation.UPDATE, "task:$id", expected,
        "修改任务", task(id, expected, "修改前"), after
    )

    private fun task(id: Long, updatedAt: Long, title: String) = AiTaskSnapshot(
        id, null, title, null, 0, 1, null, 9_999_999_999_999L,
        StudyTask.STATUS_TODO, updatedAt
    )

    private class FakeGateway : AiRecordChangeGateway {
        val records = mutableMapOf<Pair<AiRecordType, Long>, AiRecordSnapshot>()
        val reminderSyncs = mutableListOf<AiTaskSnapshot>()
        private var nextId = 100L

        override fun get(type: AiRecordType, id: Long): AiRecordSnapshot? = records[type to id]

        override fun create(snapshot: AiRecordSnapshot): Long {
            val id = nextId++
            records[AiRecordType.TASK to id] = (snapshot as AiTaskSnapshot).copy(id = id)
            return id
        }

        override fun update(snapshot: AiRecordSnapshot): Boolean {
            val type = when (snapshot) {
                is AiTaskSnapshot -> AiRecordType.TASK
                else -> return false
            }
            records[type to snapshot.id] = snapshot
            return true
        }

        override fun delete(type: AiRecordType, id: Long): Boolean =
            records.remove(type to id) != null

        override fun syncTaskReminder(task: AiTaskSnapshot) {
            reminderSyncs += task
        }
    }
}
