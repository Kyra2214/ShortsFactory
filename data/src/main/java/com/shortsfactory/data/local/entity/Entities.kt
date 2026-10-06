package com.shortsfactory.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val videoUri: String,
    val videoName: String,
    val videoDurationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val videoSizeBytes: Long,
    val sourceType: String,
    val sourceUrl: String? = null,
    val analysisStatus: String = "idle", // idle | queued | running | done | failed | cancelled
    val analysisProgress: Float = 0f,
    val analysisError: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val createdAtMs: Long = System.currentTimeMillis()
)

/** Tabela 'shorts' (Room não pode usar 'short' como nome de tabela). */
@Entity(
    tableName = "shorts",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])]
)
data class ShortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val startMs: Long,
    val endMs: Long,
    val score: Float,
    val title: String,
    val hook: String,
    val topic: String,
    val reason: String,
    val description: String = "",
    val hashtags: String = "",
    val cta: String = "",
    val localPath: String? = null,
    val status: String = "pending", // pending | queued | processing | done | failed | cancelled
    val exportProgress: Float = 0f,
    val exportError: String? = null,
    /** Sobe a cada mudança de início/fim; permite saber se um export/artefato é do intervalo atual. */
    val intervalVersion: Int = 0,
    /** Trilha de foco (relativa ao clipe) calculada na análise; `null` = recalcular. Zerada ao mudar o intervalo. */
    val focusTrackJson: String? = null,
    /** Legendas (relativas ao clipe) derivadas da análise; `null` = recalcular. Zeradas ao mudar o intervalo. */
    val subtitlesJson: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])]
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val json: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_analyses",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"])]
)
data class AIAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val provider: String,
    val json: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subtitles",
    foreignKeys = [ForeignKey(entity = ShortEntity::class, parentColumns = ["id"], childColumns = ["shortId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["shortId"])]
)
data class SubtitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortId: Long,
    val json: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "exports",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ShortEntity::class, parentColumns = ["id"], childColumns = ["shortId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["shortId"])]
)
data class ExportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val shortId: Long? = null,
    val platform: String,
    val quality: String,
    val resolution: String,
    val fps: Int,
    val outputPath: String? = null,
    val status: String = "pending", // pending | queued | running | done | failed | cancelled
    val progress: Float = 0f,
    val attemptCount: Int = 0,
    val errorMessage: String? = null,
    val startedAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

/** Registro de um lote de exportação de um projeto: total, concluídos, falhos, cancelados e estado. */
@Entity(
    tableName = "export_batches",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["projectId"])]
)
data class ExportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val total: Int,
    val completed: Int,
    val failed: Int,
    val cancelled: Int,
    val state: String, // queued | running | done | partial | failed | cancelled
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)


/** Textos de publicação por corte e plataforma (Room v6, aditivo). `hashtags` é um JSON de lista de strings. */
@Entity(
    tableName = "short_platform_metadata",
    foreignKeys = [ForeignKey(entity = ShortEntity::class, parentColumns = ["id"], childColumns = ["shortId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["shortId", "platform"], unique = true)]
)
data class ShortPlatformMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortId: Long,
    val platform: String,
    val title: String,
    val description: String,
    val hashtagsJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)
