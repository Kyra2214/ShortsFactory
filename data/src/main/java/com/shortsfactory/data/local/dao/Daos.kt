package com.shortsfactory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shortsfactory.data.local.entity.AIAnalysisEntity
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.SubtitleEntity
import com.shortsfactory.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProjectEntity): Long

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC, createdAtMs DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("UPDATE projects SET analysisStatus = :status, analysisProgress = :progress, analysisError = :error, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateAnalysisState(id: Long, status: String, progress: Float, error: String?, updatedAtMs: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShortDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ShortEntity): Long

    @Query("SELECT * FROM shorts WHERE projectId = :projectId ORDER BY score DESC")
    fun observeByProject(projectId: Long): Flow<List<ShortEntity>>

    @Query("SELECT * FROM shorts WHERE projectId = :projectId ORDER BY score DESC")
    suspend fun getByProject(projectId: Long): List<ShortEntity>

    @Query("SELECT * FROM shorts WHERE id = :id")
    suspend fun getById(id: Long): ShortEntity?

    @Update
    suspend fun update(entity: ShortEntity)

    @Query("UPDATE shorts SET title = :title, description = :description, hashtags = :hashtags, cta = :cta, startMs = :startMs, endMs = :endMs, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateMetadata(
        id: Long,
        title: String,
        description: String,
        hashtags: String,
        cta: String,
        startMs: Long,
        endMs: Long,
        updatedAtMs: Long
    )

    @Query("UPDATE shorts SET status = :status, exportProgress = :progress, exportError = :error, localPath = COALESCE(:localPath, localPath), updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateExportState(
        id: Long,
        status: String,
        progress: Float,
        error: String?,
        localPath: String?,
        updatedAtMs: Long
    )
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE projectId = :projectId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getByProject(projectId: Long): TranscriptEntity?
}

@Dao
interface AIAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AIAnalysisEntity): Long

    @Query("SELECT * FROM ai_analyses WHERE projectId = :projectId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getByProject(projectId: Long): AIAnalysisEntity?
}

@Dao
interface SubtitleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SubtitleEntity): Long

    @Query("SELECT * FROM subtitles WHERE shortId = :shortId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getByShort(shortId: Long): SubtitleEntity?
}

@Dao
interface ExportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExportEntity): Long

    @Query("SELECT * FROM exports WHERE projectId = :projectId ORDER BY createdAtMs DESC")
    fun observeByProject(projectId: Long): Flow<List<ExportEntity>>

    @Query("SELECT * FROM exports WHERE id = :id")
    suspend fun getById(id: Long): ExportEntity?

    @Query("SELECT * FROM exports WHERE projectId = :projectId AND shortId = :shortId AND platform = :platform AND quality = :quality AND resolution = :resolution AND fps = :fps ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getLatestForShort(
        projectId: Long,
        shortId: Long,
        platform: String,
        quality: String,
        resolution: String,
        fps: Int
    ): ExportEntity?

    @Query("SELECT * FROM exports WHERE projectId = :projectId AND status IN ('pending', 'queued', 'running', 'failed') ORDER BY createdAtMs ASC")
    suspend fun getResumableByProject(projectId: Long): List<ExportEntity>

    @Query("UPDATE exports SET status = :status, progress = :progress, attemptCount = :attemptCount, errorMessage = CASE WHEN :replaceError = 1 THEN :errorMessage ELSE errorMessage END, startedAtMs = COALESCE(:startedAtMs, startedAtMs), completedAtMs = COALESCE(:completedAtMs, completedAtMs), outputPath = COALESCE(:outputPath, outputPath) WHERE id = :id")
    suspend fun updateState(
        id: Long,
        status: String,
        progress: Float,
        attemptCount: Int,
        errorMessage: String?,
        replaceError: Boolean,
        startedAtMs: Long?,
        completedAtMs: Long?,
        outputPath: String?
    )
}
