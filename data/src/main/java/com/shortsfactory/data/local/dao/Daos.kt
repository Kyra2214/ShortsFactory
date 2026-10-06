package com.shortsfactory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shortsfactory.data.local.entity.AIAnalysisEntity
import com.shortsfactory.data.local.entity.ExportBatchEntity
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.local.entity.ShortPlatformMetadataEntity
import com.shortsfactory.data.local.entity.SubtitleEntity
import com.shortsfactory.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(entity: ProjectEntity): Long

    @Update
    suspend fun update(entity: ProjectEntity)

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
    @Insert
    suspend fun insert(entity: ShortEntity): Long

    @Query("SELECT * FROM shorts WHERE projectId = :projectId ORDER BY score DESC")
    fun observeByProject(projectId: Long): Flow<List<ShortEntity>>

    @Query("SELECT * FROM shorts WHERE projectId = :projectId ORDER BY score DESC")
    suspend fun getByProject(projectId: Long): List<ShortEntity>

    @Query("SELECT * FROM shorts WHERE id = :id")
    suspend fun getById(id: Long): ShortEntity?

    @Update
    suspend fun update(entity: ShortEntity)

    /**
     * Grava os campos editáveis. Se o intervalo mudou, o resultado do export anterior deixa de valer
     * NA MESMA instrução: versão do intervalo +1, arquivo/estado/progresso/erro zerados e artefatos
     * (foco/legendas, relativos ao clipe) descartados. Em SQLite as expressões do SET leem a linha
     * ANTIGA, então `startMs != :startMs` compara o valor persistido com o novo em todas as colunas.
     */
    @Query(
        "UPDATE shorts SET title = :title, hook = :hook, description = :description, hashtags = :hashtags, cta = :cta, " +
            "intervalVersion = CASE WHEN startMs != :startMs OR endMs != :endMs THEN intervalVersion + 1 ELSE intervalVersion END, " +
            "localPath = CASE WHEN startMs != :startMs OR endMs != :endMs THEN NULL ELSE localPath END, " +
            "status = CASE WHEN startMs != :startMs OR endMs != :endMs THEN 'pending' ELSE status END, " +
            "exportProgress = CASE WHEN startMs != :startMs OR endMs != :endMs THEN 0 ELSE exportProgress END, " +
            "exportError = CASE WHEN startMs != :startMs OR endMs != :endMs THEN NULL ELSE exportError END, " +
            "focusTrackJson = CASE WHEN startMs != :startMs OR endMs != :endMs THEN NULL ELSE focusTrackJson END, " +
            "subtitlesJson = CASE WHEN startMs != :startMs OR endMs != :endMs THEN NULL ELSE subtitlesJson END, " +
            "startMs = :startMs, endMs = :endMs, updatedAtMs = :updatedAtMs WHERE id = :id"
    )
    suspend fun updateMetadata(
        id: Long,
        title: String,
        hook: String,
        description: String,
        hashtags: String,
        cta: String,
        startMs: Long,
        endMs: Long,
        updatedAtMs: Long
    )

    /** O erro só é trocado/limpo quando [replaceError] é verdadeiro; atualizar progresso não o apaga. */
    @Query("UPDATE shorts SET status = :status, exportProgress = :progress, exportError = CASE WHEN :replaceError = 1 THEN :error ELSE exportError END, localPath = COALESCE(:localPath, localPath), updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateExportState(
        id: Long,
        status: String,
        progress: Float,
        error: String?,
        replaceError: Boolean,
        localPath: String?,
        updatedAtMs: Long
    )

    /** Re-análise: apaga os candidatos antigos (subtitles e exports dependentes caem em cascata). */
    @Query("DELETE FROM shorts WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}

@Dao
interface TranscriptDao {
    @Insert
    suspend fun insert(entity: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE projectId = :projectId ORDER BY createdAtMs DESC, id DESC LIMIT 1")
    suspend fun getByProject(projectId: Long): TranscriptEntity?

    @Query("DELETE FROM transcripts WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}

@Dao
interface AIAnalysisDao {
    @Insert
    suspend fun insert(entity: AIAnalysisEntity): Long

    @Query("SELECT * FROM ai_analyses WHERE projectId = :projectId ORDER BY createdAtMs DESC, id DESC LIMIT 1")
    suspend fun getByProject(projectId: Long): AIAnalysisEntity?

    @Query("DELETE FROM ai_analyses WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}

@Dao
interface SubtitleDao {
    @Insert
    suspend fun insert(entity: SubtitleEntity): Long

    @Query("SELECT * FROM subtitles WHERE shortId = :shortId ORDER BY createdAtMs DESC, id DESC LIMIT 1")
    suspend fun getByShort(shortId: Long): SubtitleEntity?

    @Query("DELETE FROM subtitles WHERE shortId = :shortId")
    suspend fun deleteByShort(shortId: Long)
}

@Dao
interface ExportDao {
    @Insert
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

    @Query("UPDATE exports SET status = :status, progress = :progress, attemptCount = :attemptCount, errorMessage = CASE WHEN :replaceError = 1 THEN :errorMessage ELSE errorMessage END, startedAtMs = COALESCE(:startedAtMs, startedAtMs), completedAtMs = CASE WHEN :resetCompleted = 1 THEN NULL ELSE COALESCE(:completedAtMs, completedAtMs) END, outputPath = COALESCE(:outputPath, outputPath) WHERE id = :id")
    suspend fun updateState(
        id: Long,
        status: String,
        progress: Float,
        attemptCount: Int,
        errorMessage: String?,
        replaceError: Boolean,
        startedAtMs: Long?,
        completedAtMs: Long?,
        resetCompleted: Boolean,
        outputPath: String?
    )

    /** Mudou o intervalo do Short: os exports dele (qualquer estado) não valem mais. */
    @Query("DELETE FROM exports WHERE shortId = :shortId")
    suspend fun deleteByShort(shortId: Long)
}

@Dao
interface ExportBatchDao {
    @Insert
    suspend fun insert(entity: ExportBatchEntity): Long

    @Update
    suspend fun update(entity: ExportBatchEntity)

    @Query("SELECT * FROM export_batches WHERE projectId = :projectId ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(projectId: Long): ExportBatchEntity?

    @Query("SELECT * FROM export_batches WHERE projectId = :projectId ORDER BY id DESC LIMIT 1")
    fun observeLatest(projectId: Long): Flow<ExportBatchEntity?>
}

@Dao
interface ShortPlatformMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShortPlatformMetadataEntity): Long

    @Query("SELECT * FROM short_platform_metadata WHERE shortId = :shortId ORDER BY platform")
    suspend fun getByShort(shortId: Long): List<ShortPlatformMetadataEntity>

    @Query("SELECT * FROM short_platform_metadata WHERE shortId = :shortId ORDER BY platform")
    fun observeByShort(shortId: Long): Flow<List<ShortPlatformMetadataEntity>>

    @Query("DELETE FROM short_platform_metadata WHERE shortId = :shortId AND platform = :platform")
    suspend fun delete(shortId: Long, platform: String)
}
