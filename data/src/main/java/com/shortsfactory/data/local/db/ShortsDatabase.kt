package com.shortsfactory.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shortsfactory.data.local.dao.AIAnalysisDao
import com.shortsfactory.data.local.dao.ExportBatchDao
import com.shortsfactory.data.local.dao.ExportDao
import com.shortsfactory.data.local.dao.ProjectDao
import com.shortsfactory.data.local.dao.ShortDao
import com.shortsfactory.data.local.dao.ShortPlatformMetadataDao
import com.shortsfactory.data.local.dao.SubtitleDao
import com.shortsfactory.data.local.dao.TranscriptDao
import com.shortsfactory.data.local.entity.AIAnalysisEntity
import com.shortsfactory.data.local.entity.ExportBatchEntity
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.ShortPlatformMetadataEntity
import com.shortsfactory.data.local.entity.SubtitleEntity
import com.shortsfactory.data.local.entity.TranscriptEntity

@Database(
    entities = [
        ProjectEntity::class,
        ShortEntity::class,
        TranscriptEntity::class,
        AIAnalysisEntity::class,
        SubtitleEntity::class,
        ExportEntity::class,
        ExportBatchEntity::class,
        ShortPlatformMetadataEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class ShortsDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun shortDao(): ShortDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun aiAnalysisDao(): AIAnalysisDao
    abstract fun subtitleDao(): SubtitleDao
    abstract fun exportDao(): ExportDao
    abstract fun exportBatchDao(): ExportBatchDao
    abstract fun shortPlatformMetadataDao(): ShortPlatformMetadataDao

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

        /**
         * 3 → 4: chaves estrangeiras com CASCADE, índices e as colunas `intervalVersion`,
         * `focusTrackJson` e `subtitlesJson` em `shorts`.
         *
         * SQLite não adiciona FK com ALTER TABLE, então cada tabela é recriada (criar nova → copiar →
         * apagar a antiga → renomear). Pais primeiro (`shorts`), filhos depois, para que as FKs sempre
         * apontem para o nome final. Linhas órfãs (sem projeto/short pai) são descartadas na cópia, senão
         * a FK não poderia existir. As tabelas novas NÃO têm `DEFAULT`, para casar com o que o Room espera
         * das entidades. Se mudar uma entidade, regenere o schema JSON e ajuste aqui.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach { database.execSQL(it) }
            }
        }

        /** 4 → 5: tabela `export_batches` (lote de exportação por projeto). Sem `DEFAULT`, como as entidades. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach { database.execSQL(it) }
            }
        }

        /** 5 → 6: tabela `short_platform_metadata` (aditiva; nenhuma tabela existente é alterada). */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach { database.execSQL(it) }
            }
        }

        internal val MIGRATION_5_6_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS short_platform_metadata (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shortId INTEGER NOT NULL, platform TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, hashtagsJson TEXT NOT NULL, updatedAtMs INTEGER NOT NULL, FOREIGN KEY(shortId) REFERENCES shorts(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_short_platform_metadata_shortId_platform ON short_platform_metadata (shortId, platform)"
        )

        internal val MIGRATION_4_5_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS export_batches (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, total INTEGER NOT NULL, completed INTEGER NOT NULL, failed INTEGER NOT NULL, cancelled INTEGER NOT NULL, state TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS index_export_batches_projectId ON export_batches (projectId)"
        )

        /** Uma instrução por item (também executada em SQLite real pelo script de validação). */
        internal val MIGRATION_3_4_SQL: List<String> = listOf(
            // shorts
            "CREATE TABLE shorts_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, score REAL NOT NULL, title TEXT NOT NULL, hook TEXT NOT NULL, topic TEXT NOT NULL, reason TEXT NOT NULL, description TEXT NOT NULL, hashtags TEXT NOT NULL, cta TEXT NOT NULL, localPath TEXT, status TEXT NOT NULL, exportProgress REAL NOT NULL, exportError TEXT, intervalVersion INTEGER NOT NULL, focusTrackJson TEXT, subtitlesJson TEXT, updatedAtMs INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "INSERT INTO shorts_new (id, projectId, startMs, endMs, score, title, hook, topic, reason, description, hashtags, cta, localPath, status, exportProgress, exportError, intervalVersion, focusTrackJson, subtitlesJson, updatedAtMs, createdAtMs) SELECT id, projectId, startMs, endMs, score, title, hook, topic, reason, description, hashtags, cta, localPath, status, exportProgress, exportError, 0, NULL, NULL, updatedAtMs, createdAtMs FROM shorts WHERE projectId IN (SELECT id FROM projects)",
            "DROP TABLE shorts",
            "ALTER TABLE shorts_new RENAME TO shorts",
            "CREATE INDEX index_shorts_projectId ON shorts (projectId)",
            // transcripts
            "CREATE TABLE transcripts_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, json TEXT NOT NULL, createdAtMs INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "INSERT INTO transcripts_new (id, projectId, json, createdAtMs) SELECT id, projectId, json, createdAtMs FROM transcripts WHERE projectId IN (SELECT id FROM projects)",
            "DROP TABLE transcripts",
            "ALTER TABLE transcripts_new RENAME TO transcripts",
            "CREATE INDEX index_transcripts_projectId ON transcripts (projectId)",
            // ai_analyses
            "CREATE TABLE ai_analyses_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, provider TEXT NOT NULL, json TEXT NOT NULL, createdAtMs INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "INSERT INTO ai_analyses_new (id, projectId, provider, json, createdAtMs) SELECT id, projectId, provider, json, createdAtMs FROM ai_analyses WHERE projectId IN (SELECT id FROM projects)",
            "DROP TABLE ai_analyses",
            "ALTER TABLE ai_analyses_new RENAME TO ai_analyses",
            "CREATE INDEX index_ai_analyses_projectId ON ai_analyses (projectId)",
            // subtitles
            "CREATE TABLE subtitles_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shortId INTEGER NOT NULL, json TEXT NOT NULL, createdAtMs INTEGER NOT NULL, FOREIGN KEY(shortId) REFERENCES shorts(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "INSERT INTO subtitles_new (id, shortId, json, createdAtMs) SELECT id, shortId, json, createdAtMs FROM subtitles WHERE shortId IN (SELECT id FROM shorts)",
            "DROP TABLE subtitles",
            "ALTER TABLE subtitles_new RENAME TO subtitles",
            "CREATE INDEX index_subtitles_shortId ON subtitles (shortId)",
            // exports (shortId é opcional: NULL = export sem short associado)
            "CREATE TABLE exports_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, shortId INTEGER, platform TEXT NOT NULL, quality TEXT NOT NULL, resolution TEXT NOT NULL, fps INTEGER NOT NULL, outputPath TEXT, status TEXT NOT NULL, progress REAL NOT NULL, attemptCount INTEGER NOT NULL, errorMessage TEXT, startedAtMs INTEGER, completedAtMs INTEGER, createdAtMs INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(shortId) REFERENCES shorts(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "INSERT INTO exports_new (id, projectId, shortId, platform, quality, resolution, fps, outputPath, status, progress, attemptCount, errorMessage, startedAtMs, completedAtMs, createdAtMs) SELECT id, projectId, shortId, platform, quality, resolution, fps, outputPath, status, progress, attemptCount, errorMessage, startedAtMs, completedAtMs, createdAtMs FROM exports WHERE projectId IN (SELECT id FROM projects) AND (shortId IS NULL OR shortId IN (SELECT id FROM shorts))",
            "DROP TABLE exports",
            "ALTER TABLE exports_new RENAME TO exports",
            "CREATE INDEX index_exports_projectId ON exports (projectId)",
            "CREATE INDEX index_exports_shortId ON exports (shortId)"
        )
    }
}
