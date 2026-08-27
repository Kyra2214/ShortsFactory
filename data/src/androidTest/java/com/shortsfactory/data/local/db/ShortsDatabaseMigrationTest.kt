package com.shortsfactory.data.local.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortsDatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrateV1ToV3_preservesRowsAndAddsPersistentState() {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.delete()
        databaseFile.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            legacy.execSQL(
                """
                CREATE TABLE projects (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    videoUri TEXT NOT NULL,
                    videoName TEXT NOT NULL,
                    videoDurationMs INTEGER NOT NULL,
                    videoWidth INTEGER NOT NULL,
                    videoHeight INTEGER NOT NULL,
                    videoSizeBytes INTEGER NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceUrl TEXT,
                    createdAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE shorts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    startMs INTEGER NOT NULL,
                    endMs INTEGER NOT NULL,
                    score REAL NOT NULL,
                    title TEXT NOT NULL,
                    hook TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    localPath TEXT,
                    status TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE transcripts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    json TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE ai_analyses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    provider TEXT NOT NULL,
                    json TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE subtitles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    shortId INTEGER NOT NULL,
                    json TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE exports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    projectId INTEGER NOT NULL,
                    platform TEXT NOT NULL,
                    quality TEXT NOT NULL,
                    resolution TEXT NOT NULL,
                    fps INTEGER NOT NULL,
                    outputPath TEXT,
                    status TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO projects (
                    id, name, videoUri, videoName, videoDurationMs, videoWidth,
                    videoHeight, videoSizeBytes, sourceType, sourceUrl, createdAtMs
                ) VALUES (1, 'Projeto legado', 'content://video/1', 'video.mp4', 60000,
                    1920, 1080, 1024, 'gallery', NULL, 1000)
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO shorts (
                    id, projectId, startMs, endMs, score, title, hook, topic, reason,
                    localPath, status, createdAtMs
                ) VALUES (1, 1, 0, 15000, 0.8, 'Título legado', 'Gancho', 'Tema',
                    'Motivo', NULL, 'pending', 1000)
                """.trimIndent()
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, ShortsDatabase::class.java, DATABASE_NAME)
            .addMigrations(ShortsDatabase.MIGRATION_1_2, ShortsDatabase.MIGRATION_2_3)
            .build()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            assertEquals(3, sqlite.version)

            sqlite.query(
                "SELECT analysisStatus, analysisProgress, analysisError, updatedAtMs FROM projects WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("idle", cursor.getString(0))
                assertEquals(0f, cursor.getFloat(1), 0f)
                assertTrue(cursor.isNull(2))
                assertEquals(1000L, cursor.getLong(3))
            }

            sqlite.query(
                "SELECT description, hashtags, cta, exportProgress, exportError, updatedAtMs FROM shorts WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals(0f, cursor.getFloat(3), 0f)
                assertTrue(cursor.isNull(4))
                assertEquals(1000L, cursor.getLong(5))
            }

            sqlite.query("SELECT shortId, progress, attemptCount, errorMessage, startedAtMs, completedAtMs FROM exports")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.isNull(0))
                    assertEquals(0f, cursor.getFloat(1), 0f)
                    assertEquals(0, cursor.getInt(2))
                    assertTrue(cursor.isNull(3))
                    assertTrue(cursor.isNull(4))
                    assertTrue(cursor.isNull(5))
                    assertFalse(cursor.moveToNext())
                }
        } finally {
            migrated.close()
            databaseFile.delete()
        }
    }

    companion object {
        private const val DATABASE_NAME = "migration-test.db"
    }
}
