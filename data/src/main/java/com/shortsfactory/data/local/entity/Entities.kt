package com.shortsfactory.data.local.entity

import androidx.room.Entity
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
    val createdAtMs: Long = System.currentTimeMillis()
)

/** Tabela 'shorts' (Room não pode usar 'short' como nome de tabela). */
@Entity(tableName = "shorts")
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
    val status: String = "pending", // pending | processing | done | failed
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val json: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_analyses")
data class AIAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val provider: String,
    val json: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "subtitles")
data class SubtitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortId: Long,
    val json: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "exports")
data class ExportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val platform: String,
    val quality: String,
    val resolution: String,
    val fps: Int,
    val outputPath: String? = null,
    val status: String = "pending", // pending | running | done | failed
    val createdAtMs: Long = System.currentTimeMillis()
)
