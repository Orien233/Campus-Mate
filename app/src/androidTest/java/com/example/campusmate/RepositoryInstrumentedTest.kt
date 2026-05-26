package com.example.campusmate

import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.domain.reminder.ReminderReceiver
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.NotificationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun courseRepository_crudAndSoftDelete() {
        val repository = CourseRepository(context)
        val uniqueName = "测试课程 ${DateTimeUtils.nowMillis()}"

        val courseId = repository.addCourse(
            Course(
                name = uniqueName,
                weekday = 2,
                startSection = 1,
                endSection = 2
            )
        )

        assertTrue(courseId > 0L)
        assertEquals(uniqueName, repository.getCourseById(courseId)?.name)

        val updated = repository.getCourseById(courseId)!!.copy(name = "$uniqueName 更新")
        assertTrue(repository.updateCourse(updated))
        assertEquals("$uniqueName 更新", repository.getCourseById(courseId)?.name)

        assertTrue(repository.deleteCourse(courseId))
        assertNull(repository.getCourseById(courseId))
    }

    @Test
    fun courseRepository_detectsTimeConflict() {
        val repository = CourseRepository(context)
        val baseSection = 1000 + (DateTimeUtils.nowMillis() % 1000).toInt()
        val courseId = repository.addCourse(
            Course(
                name = "冲突检测基准课 $baseSection",
                weekday = 7,
                startSection = baseSection,
                endSection = baseSection + 1,
                startWeek = 1,
                endWeek = 18,
                weekType = Course.WEEK_TYPE_EVERY
            )
        )

        assertTrue(courseId > 0L)
        assertTrue(
            repository.hasTimeConflict(
                Course(
                    name = "冲突检测重叠课",
                    weekday = 7,
                    startSection = baseSection + 1,
                    endSection = baseSection + 2,
                    startWeek = 8,
                    endWeek = 12,
                    weekType = Course.WEEK_TYPE_EVERY
                )
            )
        )
        assertTrue(repository.deleteCourse(courseId))
    }

    @Test
    fun taskRepository_crudStatusAndSoftDelete() {
        val repository = TaskRepository(context)
        val title = "测试任务 ${DateTimeUtils.nowMillis()}"

        val taskId = repository.addTask(
            StudyTask(
                title = title,
                type = StudyTask.TYPE_HOMEWORK,
                dueAt = DateTimeUtils.nowMillis() + 60_000L
            )
        )

        assertTrue(taskId > 0L)
        assertEquals(title, repository.getTaskById(taskId)?.title)
        assertTrue(repository.markDone(taskId))
        assertEquals(StudyTask.STATUS_DONE, repository.getTaskById(taskId)?.status)
        assertTrue(repository.deleteTask(taskId))
        assertNull(repository.getTaskById(taskId))
    }

    @Test
    fun reminderReceiver_ignoresCompletedTask() {
        val repository = TaskRepository(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val taskId = repository.addTask(
            StudyTask(
                title = "已完成任务提醒测试 ${DateTimeUtils.nowMillis()}",
                type = StudyTask.TYPE_REVIEW,
                remindAt = DateTimeUtils.nowMillis() + 60_000L,
                status = StudyTask.STATUS_DONE
            )
        )
        val notificationId = NotificationUtils.taskReminderNotificationId(taskId)
        notificationManager.cancel(notificationId)

        ReminderReceiver().onReceive(
            context,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_TASK_REMINDER
                putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            }
        )

        assertFalse(notificationManager.activeNotifications.any { it.id == notificationId })
        assertTrue(repository.deleteTask(taskId))
    }

    @Test
    fun studyRecordRepository_addAndQueryByDate() {
        val repository = StudyRecordRepository(context)
        val date = DateTimeUtils.todayDate()

        val recordId = repository.addStudyRecord(
            StudyRecord(
                title = "测试学习记录",
                durationSec = 300,
                recordDate = date,
                source = StudyRecord.SOURCE_MANUAL
            )
        )

        assertTrue(recordId > 0L)
        assertNotNull(repository.getRecordsByDate(date).firstOrNull { it.id == recordId })
    }
}
