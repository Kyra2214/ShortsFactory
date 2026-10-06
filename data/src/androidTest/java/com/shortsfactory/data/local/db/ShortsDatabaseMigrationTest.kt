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
    fun migrateV1ToV5_preservesRowsAndAddsPersistentState() {
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
            legacy.execSQL(
                """
                INSERT INTO exports (
                    id, projectId, platform, quality, resolution, fps, outputPath, status, createdAtMs
                ) VALUES (1, 1, 'youtube', 'high', '1080x1920', 30, NULL, 'queued', 1000)
                """.trimIndent()
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, ShortsDatabase::class.java, DATABASE_NAME)
            .addMigrations(ShortsDatabase.MIGRATION_1_2, ShortsDatabase.MIGRATION_2_3, ShortsDatabase.MIGRATION_3_4, ShortsDatabase.MIGRATION_4_5, ShortsDatabase.MIGRATION_5_6)
            .build()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            assertEquals(6, sqlite.version)

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

            sqlite.query("SELECT intervalVersion, focusTrackJson, subtitlesJson FROM shorts WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
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


    /**
     * 3 → 4 com um banco v3 REAL (sem FK) contendo órfãos. Abrir com Room valida o schema final contra as
     * entidades (colunas, FKs e índices); depois se confere que órfãos sumiram e que a cascata funciona.
     */
    @Test
    fun migrateV3ToV4_dropsOrphansEnforcesForeignKeysAndCascades() {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.delete()
        databaseFile.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            createV3Schema(legacy)
            legacy.execSQL("INSERT INTO projects (id, name, videoUri, videoName, videoDurationMs, videoWidth, videoHeight, videoSizeBytes, sourceType, analysisStatus, analysisProgress, updatedAtMs, createdAtMs) VALUES (1, 'P1', 'u', 'v.mp4', 60000, 1920, 1080, 1, 'gallery', 'done', 1.0, 1000, 1000)")
            legacy.execSQL("INSERT INTO shorts (id, projectId, startMs, endMs, score, title, hook, topic, reason, description, hashtags, cta, localPath, status, exportProgress, exportError, updatedAtMs, createdAtMs) VALUES (1, 1, 0, 15000, 0.9, 'ok', 'h', 't', 'r', '', '', '', '/e/1.mp4', 'done', 1.0, NULL, 1000, 1000)")
            legacy.execSQL("INSERT INTO shorts (id, projectId, startMs, endMs, score, title, hook, topic, reason, description, hashtags, cta, localPath, status, exportProgress, exportError, updatedAtMs, createdAtMs) VALUES (2, 99, 0, 15000, 0.9, 'orfao', 'h', 't', 'r', '', '', '', NULL, 'pending', 0.0, NULL, 1000, 1000)")
            legacy.execSQL("INSERT INTO transcripts (projectId, json, createdAtMs) VALUES (1, '{}', 1), (99, '{}', 1)")
            legacy.execSQL("INSERT INTO ai_analyses (projectId, provider, json, createdAtMs) VALUES (1, 'x', '{}', 1), (99, 'x', '{}', 1)")
            legacy.execSQL("INSERT INTO subtitles (shortId, json, createdAtMs) VALUES (1, '[]', 1), (2, '[]', 1)")
            legacy.execSQL("INSERT INTO exports (projectId, shortId, platform, quality, resolution, fps, status, progress, attemptCount, createdAtMs) VALUES (1, 1, 'yt', 'Normal', 'r', 30, 'done', 1.0, 1, 1), (1, 77, 'yt', 'Normal', 'r', 30, 'done', 1.0, 1, 1), (99, NULL, 'yt', 'Normal', 'r', 30, 'done', 1.0, 1, 1)")
            legacy.version = 3
        } finally {
            legacy.close()
        }

        val db = Room.databaseBuilder(context, ShortsDatabase::class.java, DATABASE_NAME)
            .addMigrations(ShortsDatabase.MIGRATION_3_4, ShortsDatabase.MIGRATION_4_5, ShortsDatabase.MIGRATION_5_6)
            .build()
        try {
            val sqlite = db.openHelper.writableDatabase
            assertEquals(1, count(sqlite, "shorts"))
            assertEquals(1, count(sqlite, "transcripts"))
            assertEquals(1, count(sqlite, "ai_analyses"))
            assertEquals(1, count(sqlite, "subtitles"))
            assertEquals(1, count(sqlite, "exports")) // some o export com short inexistente e o de projeto órfão
            sqlite.query("SELECT localPath, status, intervalVersion FROM shorts WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("/e/1.mp4", c.getString(0))
                assertEquals("done", c.getString(1))
                assertEquals(0, c.getInt(2))
            }

            // FK imposta: filho sem pai é recusado.
            val rejected = runCatching {
                sqlite.execSQL("INSERT INTO transcripts (projectId, json, createdAtMs) VALUES (12345, '{}', 1)")
            }.isFailure
            assertTrue("FK deveria recusar projectId inexistente", rejected)

            // Cascata: apagar o projeto limpa todas as tabelas dependentes.
            kotlinx.coroutines.runBlocking { db.projectDao().delete(1L) }
            listOf("shorts", "transcripts", "ai_analyses", "subtitles", "exports").forEach {
                assertEquals(it, 0, count(sqlite, it))
            }
        } finally {
            db.close()
            databaseFile.delete()
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun createV3Schema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, videoUri TEXT NOT NULL, videoName TEXT NOT NULL, videoDurationMs INTEGER NOT NULL, videoWidth INTEGER NOT NULL, videoHeight INTEGER NOT NULL, videoSizeBytes INTEGER NOT NULL, sourceType TEXT NOT NULL, sourceUrl TEXT, analysisStatus TEXT NOT NULL DEFAULT 'idle', analysisProgress REAL NOT NULL DEFAULT 0.0, analysisError TEXT, updatedAtMs INTEGER NOT NULL DEFAULT 0, createdAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE shorts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, score REAL NOT NULL, title TEXT NOT NULL, hook TEXT NOT NULL, topic TEXT NOT NULL, reason TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', hashtags TEXT NOT NULL DEFAULT '', cta TEXT NOT NULL DEFAULT '', localPath TEXT, status TEXT NOT NULL, exportProgress REAL NOT NULL DEFAULT 0.0, exportError TEXT, updatedAtMs INTEGER NOT NULL DEFAULT 0, createdAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE transcripts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, json TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE ai_analyses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, provider TEXT NOT NULL, json TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE subtitles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shortId INTEGER NOT NULL, json TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE exports (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, shortId INTEGER, platform TEXT NOT NULL, quality TEXT NOT NULL, resolution TEXT NOT NULL, fps INTEGER NOT NULL, outputPath TEXT, status TEXT NOT NULL, progress REAL NOT NULL DEFAULT 0.0, attemptCount INTEGER NOT NULL DEFAULT 0, errorMessage TEXT, startedAtMs INTEGER, completedAtMs INTEGER, createdAtMs INTEGER NOT NULL)")
    }

    companion object {
        private const val DATABASE_NAME = "migration-test.db"
    }
}
