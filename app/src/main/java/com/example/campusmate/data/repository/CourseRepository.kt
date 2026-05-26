package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.Course
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getBooleanFlag
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getRequiredInt
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString

/** Repository for course CRUD and time-conflict checks via ContentResolver. */
class CourseRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addCourse(course: Course): Long {
        validateCourse(course)
        val now = DateTimeUtils.nowMillis()
        val uri = resolver.insert(
            CampusMateContract.Courses.CONTENT_URI,
            course.toContentValues(createdAt = now, updatedAt = now)
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun addCourses(courses: List<Course>): Int {
        if (courses.isEmpty()) return 0
        courses.forEach(::validateCourse)
        val now = DateTimeUtils.nowMillis()
        val values = courses.map { course ->
            course.toContentValues(createdAt = now, updatedAt = now)
        }.toTypedArray()
        return resolver.bulkInsert(CampusMateContract.Courses.CONTENT_URI, values)
    }

    fun updateCourse(course: Course): Boolean {
        require(course.id > 0L) { "Course id is required for update." }
        validateCourse(course)
        val rows = resolver.update(
            CampusMateContract.Courses.buildItemUri(course.id),
            course.toContentValues(updatedAt = DateTimeUtils.nowMillis()),
            null,
            null
        )
        return rows > 0
    }

    fun deleteCourse(courseId: Long): Boolean {
        require(courseId > 0L) { "Course id is required for delete." }
        val rows = resolver.update(
            CampusMateContract.Courses.buildItemUri(courseId),
            ContentValues().apply {
                put(CampusMateContract.Courses.COLUMN_IS_DELETED, 1)
                put(CampusMateContract.Courses.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
            },
            null,
            null
        )
        return rows > 0
    }

    fun getCourseById(courseId: Long): Course? {
        return queryCourses(
            uri = CampusMateContract.Courses.buildItemUri(courseId),
            selection = "${CampusMateContract.Courses.COLUMN_IS_DELETED}=?",
            selectionArgs = arrayOf("0")
        ).firstOrNull()
    }

    fun getAllCourses(): List<Course> {
        return queryCourses(
            selection = "${CampusMateContract.Courses.COLUMN_IS_DELETED}=?",
            selectionArgs = arrayOf("0"),
            sortOrder = "${CampusMateContract.Courses.COLUMN_WEEKDAY} ASC, ${CampusMateContract.Courses.COLUMN_START_SECTION} ASC"
        )
    }

    fun getCoursesByWeekday(weekday: Int): List<Course> {
        return queryCourses(
            selection = "${CampusMateContract.Courses.COLUMN_IS_DELETED}=? AND ${CampusMateContract.Courses.COLUMN_WEEKDAY}=?",
            selectionArgs = arrayOf("0", weekday.toString()),
            sortOrder = "${CampusMateContract.Courses.COLUMN_START_SECTION} ASC"
        )
    }

    fun getTodayCourses(): List<Course> = getCoursesByWeekday(DateTimeUtils.currentWeekday())

    fun hasTimeConflict(course: Course): Boolean {
        return hasTimeConflict(
            weekday = course.weekday,
            startSection = course.startSection,
            endSection = course.endSection,
            startWeek = course.startWeek,
            endWeek = course.endWeek,
            weekType = course.weekType,
            ignoreCourseId = course.id
        )
    }

    fun hasTimeConflict(
        weekday: Int,
        startSection: Int,
        endSection: Int,
        startWeek: Int,
        endWeek: Int,
        weekType: Int,
        ignoreCourseId: Long = 0L
    ): Boolean {
        return getCoursesByWeekday(weekday).any { existing ->
            existing.id != ignoreCourseId &&
                sectionsOverlap(startSection, endSection, existing.startSection, existing.endSection) &&
                weeksOverlap(startWeek, endWeek, existing.startWeek, existing.endWeek) &&
                weekTypesCompatible(weekType, existing.weekType)
        }
    }

    private fun queryCourses(
        uri: android.net.Uri = CampusMateContract.Courses.CONTENT_URI,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<Course> {
        val courses = mutableListOf<Course>()
        resolver.query(uri, null, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                courses.add(
                    Course(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        name = cursor.getRequiredString(CampusMateContract.Courses.COLUMN_NAME),
                        teacher = cursor.getNullableString(CampusMateContract.Courses.COLUMN_TEACHER),
                        classroom = cursor.getNullableString(CampusMateContract.Courses.COLUMN_CLASSROOM),
                        weekday = cursor.getRequiredInt(CampusMateContract.Courses.COLUMN_WEEKDAY),
                        startSection = cursor.getRequiredInt(CampusMateContract.Courses.COLUMN_START_SECTION),
                        endSection = cursor.getRequiredInt(CampusMateContract.Courses.COLUMN_END_SECTION),
                        startWeek = cursor.getRequiredInt(CampusMateContract.Courses.COLUMN_START_WEEK),
                        endWeek = cursor.getRequiredInt(CampusMateContract.Courses.COLUMN_END_WEEK),
                        weekType = cursor.getRequiredInt(CampusMateContract.Courses.COLUMN_WEEK_TYPE),
                        color = cursor.getNullableString(CampusMateContract.Courses.COLUMN_COLOR),
                        note = cursor.getNullableString(CampusMateContract.Courses.COLUMN_NOTE),
                        createdAt = cursor.getRequiredLong(CampusMateContract.Courses.COLUMN_CREATED_AT),
                        updatedAt = cursor.getRequiredLong(CampusMateContract.Courses.COLUMN_UPDATED_AT),
                        isDeleted = cursor.getBooleanFlag(CampusMateContract.Courses.COLUMN_IS_DELETED)
                    )
                )
            }
        }
        return courses
    }

    private fun Course.toContentValues(createdAt: Long? = null, updatedAt: Long? = null): ContentValues {
        return ContentValues().apply {
            put(CampusMateContract.Courses.COLUMN_NAME, name.trim())
            put(CampusMateContract.Courses.COLUMN_TEACHER, teacher)
            put(CampusMateContract.Courses.COLUMN_CLASSROOM, classroom)
            put(CampusMateContract.Courses.COLUMN_WEEKDAY, weekday)
            put(CampusMateContract.Courses.COLUMN_START_SECTION, startSection)
            put(CampusMateContract.Courses.COLUMN_END_SECTION, endSection)
            put(CampusMateContract.Courses.COLUMN_START_WEEK, startWeek)
            put(CampusMateContract.Courses.COLUMN_END_WEEK, endWeek)
            put(CampusMateContract.Courses.COLUMN_WEEK_TYPE, weekType)
            put(CampusMateContract.Courses.COLUMN_COLOR, color)
            put(CampusMateContract.Courses.COLUMN_NOTE, note)
            put(CampusMateContract.Courses.COLUMN_IS_DELETED, if (isDeleted) 1 else 0)
            createdAt?.let { put(CampusMateContract.Courses.COLUMN_CREATED_AT, it) }
            updatedAt?.let { put(CampusMateContract.Courses.COLUMN_UPDATED_AT, it) }
        }
    }

    private fun validateCourse(course: Course) {
        require(course.name.isNotBlank()) { "Course name cannot be blank." }
        require(course.weekday in 1..7) { "Course weekday must be 1..7." }
        require(course.startSection > 0) { "Course start section must be positive." }
        require(course.endSection >= course.startSection) { "Course end section cannot be earlier than start section." }
        require(course.startWeek > 0) { "Course start week must be positive." }
        require(course.endWeek >= course.startWeek) { "Course end week cannot be earlier than start week." }
        require(course.weekType in 0..2) { "Course week type must be 0, 1, or 2." }
    }

    private fun sectionsOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        return startA <= endB && endA >= startB
    }

    private fun weeksOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        return startA <= endB && endA >= startB
    }

    private fun weekTypesCompatible(typeA: Int, typeB: Int): Boolean {
        return typeA == Course.WEEK_TYPE_EVERY ||
            typeB == Course.WEEK_TYPE_EVERY ||
            typeA == typeB
    }
}
