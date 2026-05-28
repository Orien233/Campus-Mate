package com.example.campusmate.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

/** Owns local SQLite schema creation and versioned upgrades. */
class CampusMateDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_COURSES)
        db.execSQL(SQL_CREATE_STUDY_TASKS)
        db.execSQL(SQL_CREATE_FOCUS_SESSIONS)
        db.execSQL(SQL_CREATE_STUDY_RECORDS)
        db.execSQL(SQL_CREATE_IMPORT_LOGS)
        db.execSQL(SQL_CREATE_USER_PROFILE)
        db.execSQL(SQL_CREATE_STUDY_BUDDIES)
        db.execSQL(SQL_CREATE_WEATHER_CACHE)
        db.execSQL(SQL_CREATE_TASK_ATTACHMENTS)
        db.execSQL(SQL_INDEX_COURSES_TIME)
        db.execSQL(SQL_INDEX_TASKS_COURSE)
        db.execSQL(SQL_INDEX_TASKS_DUE)
        db.execSQL(SQL_INDEX_RECORDS_DATE)
        db.execSQL(SQL_INDEX_BUDDIES_GITHUB)
        db.execSQL(SQL_INDEX_BUDDIES_EMAIL)
        db.execSQL(SQL_INDEX_WEATHER_CITY)
        db.execSQL(SQL_INDEX_ATTACHMENTS_TASK)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(SQL_CREATE_USER_PROFILE)
            db.execSQL(SQL_CREATE_STUDY_BUDDIES)
            db.execSQL(SQL_INDEX_BUDDIES_GITHUB)
            db.execSQL(SQL_INDEX_BUDDIES_EMAIL)
        }
        if (oldVersion < 3) {
            db.execSQL(SQL_CREATE_WEATHER_CACHE)
            db.execSQL(SQL_INDEX_WEATHER_CITY)
        }
        if (oldVersion < 4) {
            db.execSQL(SQL_CREATE_TASK_ATTACHMENTS)
            db.execSQL(SQL_INDEX_ATTACHMENTS_TASK)
        }
    }

    companion object {
        const val DATABASE_NAME = "campus_mate.db"
        const val DATABASE_VERSION = 4

        private const val SQL_CREATE_COURSES = """
            CREATE TABLE courses (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                teacher TEXT,
                classroom TEXT,
                weekday INTEGER NOT NULL,
                start_section INTEGER NOT NULL,
                end_section INTEGER NOT NULL,
                start_week INTEGER DEFAULT 1,
                end_week INTEGER DEFAULT 18,
                week_type INTEGER DEFAULT 0,
                color TEXT,
                note TEXT,
                created_at INTEGER,
                updated_at INTEGER,
                is_deleted INTEGER DEFAULT 0
            )
        """

        private const val SQL_CREATE_STUDY_TASKS = """
            CREATE TABLE study_tasks (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                course_id INTEGER,
                title TEXT NOT NULL,
                description TEXT,
                type INTEGER NOT NULL,
                priority INTEGER DEFAULT 1,
                due_at INTEGER,
                remind_at INTEGER,
                status INTEGER DEFAULT 0,
                created_at INTEGER,
                updated_at INTEGER,
                is_deleted INTEGER DEFAULT 0
            )
        """

        private const val SQL_CREATE_FOCUS_SESSIONS = """
            CREATE TABLE focus_sessions (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER,
                course_id INTEGER,
                planned_duration_sec INTEGER,
                actual_duration_sec INTEGER,
                start_at INTEGER,
                end_at INTEGER,
                status INTEGER,
                pause_count INTEGER DEFAULT 0,
                interrupt_count INTEGER DEFAULT 0,
                created_at INTEGER
            )
        """

        private const val SQL_CREATE_STUDY_RECORDS = """
            CREATE TABLE study_records (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER,
                course_id INTEGER,
                focus_session_id INTEGER,
                title TEXT,
                duration_sec INTEGER NOT NULL,
                record_date TEXT NOT NULL,
                start_at INTEGER,
                end_at INTEGER,
                source INTEGER,
                note TEXT,
                created_at INTEGER
            )
        """

        private const val SQL_CREATE_IMPORT_LOGS = """
            CREATE TABLE import_logs (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_type INTEGER,
                imported_count INTEGER,
                skipped_count INTEGER,
                conflict_count INTEGER,
                created_at INTEGER,
                message TEXT
            )
        """

        private const val SQL_CREATE_USER_PROFILE = """
            CREATE TABLE IF NOT EXISTS user_profile (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                nickname TEXT,
                school TEXT,
                major TEXT,
                grade TEXT,
                bio TEXT,
                avatar_uri TEXT,
                github TEXT,
                email TEXT,
                phone TEXT,
                show_email INTEGER DEFAULT 0,
                show_phone INTEGER DEFAULT 0,
                created_at INTEGER,
                updated_at INTEGER
            )
        """

        private const val SQL_CREATE_STUDY_BUDDIES = """
            CREATE TABLE IF NOT EXISTS study_buddies (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                nickname TEXT NOT NULL,
                school TEXT,
                major TEXT,
                grade TEXT,
                bio TEXT,
                github TEXT,
                email TEXT,
                phone TEXT,
                source INTEGER,
                added_at INTEGER,
                note TEXT
            )
        """

        private const val SQL_CREATE_WEATHER_CACHE = """
            CREATE TABLE IF NOT EXISTS weather_cache (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                city TEXT,
                weather_text TEXT,
                temperature TEXT,
                humidity TEXT,
                wind TEXT,
                source TEXT,
                raw_json TEXT,
                updated_at INTEGER
            )
        """

        private const val SQL_CREATE_TASK_ATTACHMENTS = """
            CREATE TABLE IF NOT EXISTS task_attachments (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER NOT NULL,
                uri TEXT NOT NULL,
                mime_type TEXT,
                title TEXT,
                created_at INTEGER
            )
        """

        private const val SQL_INDEX_COURSES_TIME =
            "CREATE INDEX idx_courses_time ON courses(weekday, start_section, end_section, start_week, end_week, is_deleted)"
        private const val SQL_INDEX_TASKS_COURSE =
            "CREATE INDEX idx_tasks_course ON study_tasks(course_id, is_deleted)"
        private const val SQL_INDEX_TASKS_DUE =
            "CREATE INDEX idx_tasks_due ON study_tasks(due_at, status, is_deleted)"
        private const val SQL_INDEX_RECORDS_DATE =
            "CREATE INDEX idx_records_date ON study_records(record_date)"
        private const val SQL_INDEX_BUDDIES_GITHUB =
            "CREATE INDEX IF NOT EXISTS idx_buddies_github ON study_buddies(github)"
        private const val SQL_INDEX_BUDDIES_EMAIL =
            "CREATE INDEX IF NOT EXISTS idx_buddies_email ON study_buddies(email)"
        private const val SQL_INDEX_WEATHER_CITY =
            "CREATE INDEX IF NOT EXISTS idx_weather_city ON weather_cache(city, updated_at)"
        private const val SQL_INDEX_ATTACHMENTS_TASK =
            "CREATE INDEX IF NOT EXISTS idx_attachments_task ON task_attachments(task_id, created_at)"
    }
}
