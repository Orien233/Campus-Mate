package com.example.campusmate.data.repository

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.model.UserProfile
import com.example.campusmate.util.DateTimeUtils
import java.util.Calendar

/** Creates a presentation-ready local data set for classroom demonstration. */
class DemoDataRepository(context: Context) {
    private val courseRepository = CourseRepository(context)
    private val taskRepository = TaskRepository(context)
    private val studyPlanRepository = StudyPlanRepository(context)
    private val studyRecordRepository = StudyRecordRepository(context)
    private val importLogRepository = ImportLogRepository(context)
    private val userProfileRepository = UserProfileRepository(context)
    private val studyBuddyRepository = StudyBuddyRepository(context)

    fun seedStageTwoDemoData(): DemoSeedResult = seedPresentationDemoData()

    fun seedPresentationDemoData(): DemoSeedResult {
        val todayWeekday = DateTimeUtils.currentWeekday()
        val courseIds = createCourses(todayWeekday)
        val taskIds = createTasks(courseIds)
        createPlans()
        createProfileAndBuddies()
        val recordCount = createStudyRecords(courseIds, taskIds)
        importLogRepository.addImportLog(
            ImportLog(
                sourceType = ImportLog.SOURCE_SAMPLE_HTML,
                importedCount = courseIds.size,
                skippedCount = 0,
                conflictCount = 1,
                message = "演示数据：课程、任务、计划、专注记录、热力图和学习伙伴样例"
            )
        )
        return DemoSeedResult(
            courseCount = courseIds.size,
            taskCount = taskIds.size,
            recordCount = recordCount
        )
    }

    private fun createCourses(todayWeekday: Int): List<Long> {
        val courses = listOf(
            Course(
                name = "高等数学",
                teacher = "陈老师",
                classroom = "教学楼 A201",
                weekday = todayWeekday,
                startSection = 1,
                endSection = 2,
                color = "#1B6B5F",
                note = "答辩演示课程"
            ),
            Course(
                name = "大学英语",
                teacher = "李老师",
                classroom = "教学楼 B305",
                weekday = 3,
                startSection = 3,
                endSection = 4,
                color = "#2F5D9E",
                note = "口语展示和听力训练"
            ),
            Course(
                name = "移动应用开发",
                teacher = "周老师",
                classroom = "实验楼 C402",
                weekday = 5,
                startSection = 5,
                endSection = 6,
                color = "#E8A23A",
                note = "Android 课程设计主线"
            ),
            Course(
                name = "数据结构",
                teacher = "王老师",
                classroom = "教学楼 A108",
                weekday = 2,
                startSection = 7,
                endSection = 8,
                color = "#8D5A97",
                note = "复习栈、队列和图"
            ),
            Course(
                name = "操作系统",
                teacher = "赵老师",
                classroom = "教学楼 D210",
                weekday = 4,
                startSection = 1,
                endSection = 2,
                color = "#C2513A",
                note = "线程同步实验"
            )
        )
        return courses.map { courseRepository.addCourse(it) }.filter { it > 0L }
    }

    private fun createTasks(courseIds: List<Long>): List<Long> {
        val mathId = courseIds.getOrNull(0)
        val englishId = courseIds.getOrNull(1)
        val androidId = courseIds.getOrNull(2)
        val structureId = courseIds.getOrNull(3)
        val osId = courseIds.getOrNull(4)
        val tasks = listOf(
            StudyTask(
                courseId = mathId,
                title = "完成高等数学课程学习",
                description = "用于演示课程学习类任务应安排在课程时间内。",
                type = StudyTask.TYPE_REVIEW,
                priority = StudyTask.PRIORITY_HIGH,
                dueAt = millisFromToday(dayOffset = 0, hour = 12, minute = 0)
            ),
            StudyTask(
                courseId = mathId,
                title = "完成高数习题第 3 章",
                description = "用于演示任务新增、10 秒提醒和专注入口。",
                type = StudyTask.TYPE_HOMEWORK,
                priority = StudyTask.PRIORITY_HIGH,
                dueAt = millisFromToday(dayOffset = 1, hour = 20, minute = 0),
                remindAt = DateTimeUtils.nowMillis() + DEMO_REMINDER_DELAY_MILLIS
            ),
            StudyTask(
                courseId = englishId,
                title = "准备英语口语展示",
                type = StudyTask.TYPE_REVIEW,
                priority = StudyTask.PRIORITY_NORMAL,
                dueAt = millisFromToday(dayOffset = 2, hour = 18, minute = 30)
            ),
            StudyTask(
                courseId = androidId,
                title = "整理 CampusMate 答辩稿",
                description = "串联课表、任务、提醒、专注和统计闭环。",
                type = StudyTask.TYPE_PROJECT,
                priority = StudyTask.PRIORITY_HIGH,
                dueAt = millisFromToday(dayOffset = 3, hour = 22, minute = 0)
            ),
            StudyTask(
                courseId = structureId,
                title = "复习图的最短路径",
                type = StudyTask.TYPE_REVIEW,
                priority = StudyTask.PRIORITY_NORMAL,
                dueAt = millisFromToday(dayOffset = -1, hour = 23, minute = 0)
            ),
            StudyTask(
                courseId = osId,
                title = "完成线程同步实验报告",
                type = StudyTask.TYPE_EXPERIMENT,
                priority = StudyTask.PRIORITY_LOW,
                dueAt = millisFromToday(dayOffset = -2, hour = 17, minute = 0),
                status = StudyTask.STATUS_DONE
            )
        )
        return tasks.map { taskRepository.addTask(it) }.filter { it > 0L }
    }

    private fun createPlans() {
        val today = DateTimeUtils.todayDate()
        val plans = listOf(
            StudyPlan(
                title = "上课: 高等数学",
                planDate = today,
                plannedMinutes = 95,
                startTime = "08:00",
                endTime = "09:35",
                sourceType = StudyPlan.SOURCE_AUTO
            ),
            StudyPlan(
                title = "完成高等数学课程学习",
                planDate = today,
                plannedMinutes = 60,
                startTime = "08:20",
                endTime = "09:20",
                sourceType = StudyPlan.SOURCE_LLM
            ),
            StudyPlan(
                title = "整理 CampusMate 答辩稿",
                planDate = today,
                plannedMinutes = 90,
                startTime = "19:30",
                endTime = "21:00",
                sourceType = StudyPlan.SOURCE_AUTO
            ),
            StudyPlan(
                title = "英语口语展示练习",
                planDate = DateTimeUtils.datePlusDays(today, 1),
                plannedMinutes = 45,
                startTime = "20:00",
                endTime = "20:45",
                sourceType = StudyPlan.SOURCE_AUTO
            )
        )
        studyPlanRepository.addPlans(plans)
    }

    private fun createProfileAndBuddies() {
        userProfileRepository.saveProfile(
            UserProfile(
                nickname = "CampusMate 演示用户",
                school = "北京交通大学",
                major = "软件工程",
                grade = "大三",
                bio = "用于课程项目答辩的本地学习名片。",
                github = "campusmate-demo",
                email = "demo@example.edu",
                showEmail = true
            )
        )
        studyBuddyRepository.addBuddy(
            StudyBuddy(
                nickname = "结对同学 A",
                school = "北京交通大学",
                major = "计算机科学与技术",
                grade = "大三",
                bio = "通过二维码确认添加的学习伙伴。",
                github = "campusmate-buddy-a",
                email = "buddy-a@example.edu",
                source = StudyBuddy.SOURCE_QR,
                note = "答辩演示伙伴"
            )
        )
    }

    private fun createStudyRecords(courseIds: List<Long>, taskIds: List<Long>): Int {
        val samples = listOf(
            RecordSample(0, 35, "高数习题复盘", 0, 0),
            RecordSample(1, 25, "英语听力训练", 1, 1),
            RecordSample(2, 60, "Android 页面联调", 2, 2),
            RecordSample(3, 15, "数据结构错题", 3, 3),
            RecordSample(4, 90, "课程设计实现", 2, 2),
            RecordSample(6, 45, "操作系统实验", 4, 4),
            RecordSample(7, 20, "英语单词复习", 1, 1),
            RecordSample(8, 120, "专注冲刺", 2, 2),
            RecordSample(10, 30, "高数公式整理", 0, 0),
            RecordSample(13, 55, "数据结构练习", 3, 3),
            RecordSample(18, 75, "答辩流程演练", 2, 2),
            RecordSample(25, 40, "阶段性复习", 0, 0)
        )
        samples.forEach { sample ->
            val startAt = millisFromToday(dayOffset = -sample.daysAgo, hour = 19, minute = 0)
            val durationSec = sample.durationMinutes * 60
            studyRecordRepository.addStudyRecord(
                StudyRecord(
                    taskId = taskIds.getOrNull(sample.taskIndex),
                    courseId = courseIds.getOrNull(sample.courseIndex),
                    title = sample.title,
                    durationSec = durationSec,
                    recordDate = DateTimeUtils.formatDate(startAt),
                    startAt = startAt,
                    endAt = startAt + durationSec * 1000L,
                    source = StudyRecord.SOURCE_MANUAL,
                    note = "答辩演示数据"
                )
            )
        }
        return samples.size
    }

    private fun millisFromToday(dayOffset: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        private const val DEMO_REMINDER_DELAY_MILLIS = 10_000L
    }
}

private data class RecordSample(
    val daysAgo: Int,
    val durationMinutes: Int,
    val title: String,
    val courseIndex: Int,
    val taskIndex: Int
)

data class DemoSeedResult(
    val courseCount: Int,
    val taskCount: Int,
    val recordCount: Int
)
