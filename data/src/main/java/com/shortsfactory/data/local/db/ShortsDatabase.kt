package com.shortsfactory.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shortsfactory.data.local.dao.AIAnalysisDao
import com.shortsfactory.data.local.dao.ExportDao
import com.shortsfactory.data.local.dao.ProjectDao
import com.shortsfactory.data.local.dao.ShortDao
import com.shortsfactory.data.local.dao.SubtitleDao
import com.shortsfactory.data.local.dao.TranscriptDao
import com.shortsfactory.data.local.entity.AIAnalysisEntity
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.SubtitleEntity
import com.shortsfactory.data.local.entity.TranscriptEntity

@Database(
    entities = [
        ProjectEntity::class,
        ShortEntity::class,
        TranscriptEntity::class,
        AIAnalysisEntity::class,
        SubtitleEntity::class,
        ExportEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ShortsDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun shortDao(): ShortDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun aiAnalysisDao(): AIAnalysisDao
    abstract fun subtitleDao(): SubtitleDao
    abstract fun exportDao(): ExportDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shorts ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE shorts ADD COLUMN hashtags TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE shorts ADD COLUMN cta TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE projects ADD COLUMN analysisStatus TEXT NOT NULL DEFAULT 'idle'")
                database.execSQL("ALTER TABLE projects ADD COLUMN analysisProgress REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE projects ADD COLUMN analysisError TEXT")
                database.execSQL("ALTER TABLE projects ADD COLUMN updatedAtMs INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE projects SET updatedAtMs = createdAtMs WHERE updatedAtMs = 0")

                database.execSQL("ALTER TABLE shorts ADD COLUMN exportProgress REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE shorts ADD COLUMN exportError TEXT")
                database.execSQL("ALTER TABLE shorts ADD COLUMN updatedAtMs INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE shorts SET updatedAtMs = createdAtMs WHERE updatedAtMs = 0")

                database.execSQL("ALTER TABLE exports ADD COLUMN shortId INTEGER")
                database.execSQL("ALTER TABLE exports ADD COLUMN progress REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE exports ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exports ADD COLUMN errorMessage TEXT")
                database.execSQL("ALTER TABLE exports ADD COLUMN startedAtMs INTEGER")
                database.execSQL("ALTER TABLE exports ADD COLUMN completedAtMs INTEGER")
            }
        }
    }
}
