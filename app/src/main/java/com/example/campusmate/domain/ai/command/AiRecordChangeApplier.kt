package com.example.campusmate.domain.ai.command

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.AlarmReminderScheduler

interface AiRecordChangeGateway {
    fun get(type: AiRecordType, id: Long): AiRecordSnapshot?
    fun create(snapshot: AiRecordSnapshot): Long
    fun update(snapshot: AiRecordSnapshot): Boolean
    fun delete(type: AiRecordType, id: Long): Boolean
    fun syncTaskReminder(task: AiTaskSnapshot)
}

class AiRecordChangeApplier(private val gateway: AiRecordChangeGateway) {
    fun apply(changes: List<AiRecordChange>): AiRecordApplyReport {
        val failures = mutableListOf<AiRecordApplyFailure>()
        var success = 0
        changes.forEach { change ->
            val error = runCatching { applyOne(change) }.exceptionOrNull()?.message
            if (error == null) {
                success += 1
            } else {
                failures += AiRecordApplyFailure(change.changeId, change.displayName, error)
            }
        }
        return AiRecordApplyReport(success, failures)
    }

    private fun applyOne(change: AiRecordChange) {
        if (change.operation == AiRecordOperation.CREATE) {
            val after = requireNotNull(change.after) { "新增内容缺失" }
            val id = gateway.create(after)
            require(id > 0L) { "新增失败" }
            if (after is AiTaskSnapshot) gateway.syncTaskReminder(after.copy(id = id))
            return
        }
        val id = change.targetRef?.substringAfter(':')?.toLongOrNull()
            ?: error("目标引用无效")
        val current = gateway.get(change.recordType, id) ?: error("目标已不存在")
        if (current.updatedAt != change.expectedUpdatedAt) error("目标在预览后已变化")
        when (change.operation) {
            AiRecordOperation.UPDATE -> {
                val after = requireNotNull(change.after) { "修改内容缺失" }
                require(gateway.update(after)) { "修改失败" }
                if (after is AiTaskSnapshot) gateway.syncTaskReminder(after)
            }
            AiRecordOperation.DELETE -> {
                require(gateway.delete(change.recordType, id)) { "删除失败" }
                if (change.recordType == AiRecordType.TASK) {
                    gateway.syncTaskReminder((current as AiTaskSnapshot).copyForDeletedReminder())
                }
            }
            AiRecordOperation.CREATE -> Unit
        }
    }

    private fun AiTaskSnapshot.copyForDeletedReminder() =
        copy(remindAt = null, status = StudyTask.STATUS_ARCHIVED)
}

class RepositoryAiRecordChangeGateway(context: Context) : AiRecordChangeGateway {
    private val courseRepository = CourseRepository(context)
    private val taskRepository = TaskRepository(context)
    private val planRepository = StudyPlanRepository(context)
    private val reminderScheduler = AlarmReminderScheduler(context)

    override fun get(type: AiRecordType, id: Long): AiRecordSnapshot? = when (type) {
        AiRecordType.COURSE -> courseRepository.getCourseById(id)?.toAiSnapshot()
        AiRecordType.TASK -> taskRepository.getTaskById(id)?.toAiSnapshot()
        AiRecordType.PLAN -> planRepository.getPlanById(id)?.toAiSnapshot()
    }

    override fun create(snapshot: AiRecordSnapshot): Long = when (snapshot) {
        is AiCourseSnapshot -> courseRepository.addCourse(snapshot.toCourse())
        is AiTaskSnapshot -> taskRepository.addTask(snapshot.toTask())
        is AiPlanSnapshot -> planRepository.addPlan(snapshot.toPlan())
    }

    override fun update(snapshot: AiRecordSnapshot): Boolean = when (snapshot) {
        is AiCourseSnapshot -> courseRepository.updateCourse(snapshot.toCourse())
        is AiTaskSnapshot -> taskRepository.updateTask(snapshot.toTask())
        is AiPlanSnapshot -> planRepository.updatePlan(snapshot.toPlan())
    }

    override fun delete(type: AiRecordType, id: Long): Boolean = when (type) {
        AiRecordType.COURSE -> courseRepository.deleteCourse(id)
        AiRecordType.TASK -> taskRepository.deleteTask(id)
        AiRecordType.PLAN -> planRepository.deletePlan(id)
    }

    override fun syncTaskReminder(task: AiTaskSnapshot) {
        reminderScheduler.cancelTaskReminder(task.id)
        if (
            task.status == StudyTask.STATUS_TODO &&
            task.remindAt != null &&
            task.remindAt > System.currentTimeMillis()
        ) {
            reminderScheduler.scheduleTaskReminder(task.toTask())
        }
    }

    private fun AiCourseSnapshot.toCourse() = Course(
        id, name, teacher, classroom, weekday, startSection, endSection,
        startWeek, endWeek, weekType, color, note, updatedAt = updatedAt
    )

    private fun AiTaskSnapshot.toTask() = StudyTask(
        id, courseId, title, description, type, priority,
        dueAt, remindAt, status, updatedAt = updatedAt
    )

    private fun AiPlanSnapshot.toPlan() = StudyPlan(
        id = id,
        title = title,
        planDate = planDate,
        plannedMinutes = plannedMinutes,
        actualMinutes = actualMinutes,
        startTime = startTime,
        endTime = endTime,
        type = type,
        status = status,
        sourceType = sourceType,
        updatedAt = updatedAt
    )
}
