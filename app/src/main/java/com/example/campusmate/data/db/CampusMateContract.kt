package com.example.campusmate.data.db

import android.net.Uri
import android.provider.BaseColumns
import com.example.campusmate.app.AppConfig

/** Contract constants for every table exposed by CampusMateProvider. */
object CampusMateContract {
    const val AUTHORITY = AppConfig.PROVIDER_AUTHORITY
    val BASE_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

    const val PATH_COURSES = "courses"
    const val PATH_TASKS = "tasks"
    const val PATH_FOCUS_SESSIONS = "focus_sessions"
    const val PATH_STUDY_RECORDS = "study_records"
    const val PATH_IMPORT_LOGS = "import_logs"
    const val PATH_USER_PROFILE = "user_profile"
    const val PATH_STUDY_BUDDIES = "study_buddies"
    const val PATH_WEATHER_CACHE = "weather_cache"

    object Courses : BaseColumns {
        const val TABLE_NAME = "courses"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_COURSES).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_COURSES"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_COURSES"

        const val COLUMN_NAME = "name"
        const val COLUMN_TEACHER = "teacher"
        const val COLUMN_CLASSROOM = "classroom"
        const val COLUMN_WEEKDAY = "weekday"
        const val COLUMN_START_SECTION = "start_section"
        const val COLUMN_END_SECTION = "end_section"
        const val COLUMN_START_WEEK = "start_week"
        const val COLUMN_END_WEEK = "end_week"
        const val COLUMN_WEEK_TYPE = "week_type"
        const val COLUMN_COLOR = "color"
        const val COLUMN_NOTE = "note"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_IS_DELETED = "is_deleted"

        const val WEEK_TYPE_EVERY = 0
        const val WEEK_TYPE_ODD = 1
        const val WEEK_TYPE_EVEN = 2

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object StudyTasks : BaseColumns {
        const val TABLE_NAME = "study_tasks"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_TASKS).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_TASKS"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_TASKS"

        const val COLUMN_COURSE_ID = "course_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_TYPE = "type"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_DUE_AT = "due_at"
        const val COLUMN_REMIND_AT = "remind_at"
        const val COLUMN_STATUS = "status"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_IS_DELETED = "is_deleted"

        const val TYPE_HOMEWORK = 0
        const val TYPE_EXPERIMENT = 1
        const val TYPE_EXAM = 2
        const val TYPE_REVIEW = 3
        const val TYPE_PROJECT = 4
        const val TYPE_OTHER = 5

        const val STATUS_TODO = 0
        const val STATUS_DONE = 1
        const val STATUS_ARCHIVED = 2

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object FocusSessions : BaseColumns {
        const val TABLE_NAME = "focus_sessions"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_FOCUS_SESSIONS).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_FOCUS_SESSIONS"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_FOCUS_SESSIONS"

        const val COLUMN_TASK_ID = "task_id"
        const val COLUMN_COURSE_ID = "course_id"
        const val COLUMN_PLANNED_DURATION_SEC = "planned_duration_sec"
        const val COLUMN_ACTUAL_DURATION_SEC = "actual_duration_sec"
        const val COLUMN_START_AT = "start_at"
        const val COLUMN_END_AT = "end_at"
        const val COLUMN_STATUS = "status"
        const val COLUMN_PAUSE_COUNT = "pause_count"
        const val COLUMN_INTERRUPT_COUNT = "interrupt_count"
        const val COLUMN_CREATED_AT = "created_at"

        const val STATUS_READY = 0
        const val STATUS_RUNNING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_FINISHED = 3
        const val STATUS_CANCELLED = 4

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object StudyRecords : BaseColumns {
        const val TABLE_NAME = "study_records"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_STUDY_RECORDS).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_STUDY_RECORDS"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_STUDY_RECORDS"

        const val COLUMN_TASK_ID = "task_id"
        const val COLUMN_COURSE_ID = "course_id"
        const val COLUMN_FOCUS_SESSION_ID = "focus_session_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DURATION_SEC = "duration_sec"
        const val COLUMN_RECORD_DATE = "record_date"
        const val COLUMN_START_AT = "start_at"
        const val COLUMN_END_AT = "end_at"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_NOTE = "note"
        const val COLUMN_CREATED_AT = "created_at"

        const val SOURCE_FOCUS_AUTO = 0
        const val SOURCE_MANUAL = 1

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object ImportLogs : BaseColumns {
        const val TABLE_NAME = "import_logs"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_IMPORT_LOGS).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_IMPORT_LOGS"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_IMPORT_LOGS"

        const val COLUMN_SOURCE_TYPE = "source_type"
        const val COLUMN_IMPORTED_COUNT = "imported_count"
        const val COLUMN_SKIPPED_COUNT = "skipped_count"
        const val COLUMN_CONFLICT_COUNT = "conflict_count"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_MESSAGE = "message"

        const val SOURCE_SAMPLE_HTML = 0
        const val SOURCE_PASTED_HTML = 1
        const val SOURCE_WEBVIEW = 2

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object UserProfile : BaseColumns {
        const val TABLE_NAME = "user_profile"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_USER_PROFILE).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_USER_PROFILE"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_USER_PROFILE"

        const val COLUMN_NICKNAME = "nickname"
        const val COLUMN_SCHOOL = "school"
        const val COLUMN_MAJOR = "major"
        const val COLUMN_GRADE = "grade"
        const val COLUMN_BIO = "bio"
        const val COLUMN_AVATAR_URI = "avatar_uri"
        const val COLUMN_GITHUB = "github"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_SHOW_EMAIL = "show_email"
        const val COLUMN_SHOW_PHONE = "show_phone"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object StudyBuddies : BaseColumns {
        const val TABLE_NAME = "study_buddies"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_STUDY_BUDDIES).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_STUDY_BUDDIES"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_STUDY_BUDDIES"

        const val COLUMN_NICKNAME = "nickname"
        const val COLUMN_SCHOOL = "school"
        const val COLUMN_MAJOR = "major"
        const val COLUMN_GRADE = "grade"
        const val COLUMN_BIO = "bio"
        const val COLUMN_GITHUB = "github"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_ADDED_AT = "added_at"
        const val COLUMN_NOTE = "note"

        const val SOURCE_QR = 0
        const val SOURCE_NFC = 1
        const val SOURCE_MANUAL = 2

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }

    object WeatherCache : BaseColumns {
        const val TABLE_NAME = "weather_cache"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_WEATHER_CACHE).build()
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_WEATHER_CACHE"
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_WEATHER_CACHE"

        const val COLUMN_CITY = "city"
        const val COLUMN_WEATHER_TEXT = "weather_text"
        const val COLUMN_TEMPERATURE = "temperature"
        const val COLUMN_HUMIDITY = "humidity"
        const val COLUMN_WIND = "wind"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_RAW_JSON = "raw_json"
        const val COLUMN_UPDATED_AT = "updated_at"

        fun buildItemUri(id: Long): Uri = CONTENT_URI.buildUpon().appendPath(id.toString()).build()
    }
}
