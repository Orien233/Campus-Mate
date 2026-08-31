package com.example.campusmate

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.campusmate.data.db.CampusMateContract.AiMemories
import com.example.campusmate.data.db.CampusMateDbHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises migration SQL on an in-memory database, never the installed app database. */
@RunWith(AndroidJUnit4::class)
class AiMemoryMigrationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun upgradeFromV5AddsMemoryWithoutChangingExistingTablesOrRows() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE courses (_id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE study_records (_id INTEGER PRIMARY KEY, actual_minutes INTEGER)")
            db.execSQL("INSERT INTO courses VALUES (42, 'existing course')")
            db.execSQL("INSERT INTO study_records VALUES (73, 35)")
            val coursesBefore = schema(db, "courses")
            val recordsBefore = schema(db, "study_records")

            CampusMateDbHelper(context).use { it.onUpgrade(db, 5, 6) }

            assertEquals(coursesBefore, schema(db, "courses"))
            assertEquals(recordsBefore, schema(db, "study_records"))
            db.rawQuery("SELECT name FROM courses WHERE _id=42", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("existing course", it.getString(0))
            }
            db.rawQuery("SELECT actual_minutes FROM study_records WHERE _id=73", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(35, it.getInt(0))
            }
            assertMemorySchemaAndDefaults(db)
        }
    }

    @Test
    fun freshDatabaseUsesTheSameV6MemorySchema() {
        SQLiteDatabase.create(null).use { db ->
            CampusMateDbHelper(context).use { it.onCreate(db) }
            assertEquals(6, CampusMateDbHelper.DATABASE_VERSION)
            assertMemorySchemaAndDefaults(db)
        }
    }

    private fun assertMemorySchemaAndDefaults(db: SQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(ai_memories)", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        assertEquals(setOf("_id", "category", "content", "is_enabled", "is_pinned",
            "expires_at", "created_at", "updated_at"), columns)
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ai_memories_active'", null).use {
            assertTrue(it.moveToFirst())
        }
        val id = db.insertOrThrow(AiMemories.TABLE_NAME, null, ContentValues().apply {
            put(AiMemories.COLUMN_CONTENT, "user entered goal")
            put(AiMemories.COLUMN_CREATED_AT, 100L)
            put(AiMemories.COLUMN_UPDATED_AT, 100L)
        })
        db.rawQuery("SELECT category, is_enabled, is_pinned, expires_at FROM ai_memories WHERE _id=?",
            arrayOf(id.toString())).use {
            assertTrue(it.moveToFirst())
            assertEquals(AiMemories.CATEGORY_OTHER, it.getInt(0))
            assertEquals(1, it.getInt(1))
            assertEquals(0, it.getInt(2))
            assertTrue(it.isNull(3))
        }
    }

    private fun schema(db: SQLiteDatabase, table: String): String =
        db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            check(it.moveToFirst())
            it.getString(0)
        }
}
