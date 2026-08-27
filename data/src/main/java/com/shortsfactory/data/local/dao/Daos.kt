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

    @Query("SELECT * FROM projects ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

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

    @Query("UPDATE shorts SET title = :title, description = :description, hashtags = :hashtags, cta = :cta, startMs = :startMs, endMs = :endMs WHERE id = :id")
    suspend fun updateMetadata(
        id: Long,
        title: String,
        description: String,
        hashtags: String,
        cta: String,
        startMs: Long,
        endMs: Long
    )

    @Query("UPDATE shorts SET localPath = :localPath, status = :status WHERE id = :id")
    suspend fun updateExportState(id: Long, localPath: String?, status: String)
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE projectId = :projectId")
    suspend fun getByProject(projectId: Long): TranscriptEntity?
}

@Dao
interface AIAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AIAnalysisEntity): Long

    @Query("SELECT * FROM ai_analyses WHERE projectId = :projectId")
    suspend fun getByProject(projectId: Long): AIAnalysisEntity?
}

@Dao
interface SubtitleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SubtitleEntity): Long

    @Query("SELECT * FROM subtitles WHERE shortId = :shortId")
    suspend fun getByShort(shortId: Long): SubtitleEntity?
}

@Dao
interface ExportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExportEntity): Long

    @Query("SELECT * FROM exports WHERE projectId = :projectId ORDER BY createdAtMs DESC")
    fun observeByProject(projectId: Long): Flow<List<ExportEntity>>

    @Query("UPDATE exports SET outputPath = :outputPath, status = :status WHERE id = :id")
    suspend fun updateResult(id: Long, outputPath: String?, status: String)
}
