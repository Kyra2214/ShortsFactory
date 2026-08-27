package com.shortsfactory.data.repository

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
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ProjectRepository(private val dao: ProjectDao) {
    suspend fun insert(entity: ProjectEntity): Long = dao.insert(entity)
    suspend fun getById(id: Long): ProjectEntity? = dao.getById(id)
    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun updateAnalysisState(
        id: Long,
        status: String,
        progress: Float,
        error: String? = null
    ) {
        dao.updateAnalysisState(id, status, progress.coerceIn(0f, 1f), error, now())
    }
}

class ShortRepository(private val dao: ShortDao) {
    fun observeByProject(projectId: Long): Flow<List<ShortEntity>> = dao.observeByProject(projectId)
    suspend fun getByProject(projectId: Long): List<ShortEntity> = dao.getByProject(projectId)
    suspend fun getById(id: Long): ShortEntity? = dao.getById(id)

    suspend fun insertCandidates(projectId: Long, candidates: List<ShortCandidate>): List<Long> {
        return candidates.map { candidate ->
            dao.insert(
                ShortEntity(
                    projectId = projectId,
                    startMs = candidate.startMs,
                    endMs = candidate.endMs,
                    score = candidate.score,
                    title = candidate.title,
                    hook = candidate.hook,
                    topic = candidate.topic,
                    reason = candidate.reason,
                    description = "",
                    hashtags = "",
                    cta = "",
                    status = "pending",
                    updatedAtMs = now()
                )
            )
        }
    }

    suspend fun updateMetadata(
        id: Long,
        title: String,
        description: String,
        hashtags: String,
        cta: String,
        startMs: Long,
        endMs: Long
    ) {
        require(endMs > startMs) { "O fim do Short precisa ser maior que o início." }
        dao.updateMetadata(
            id = id,
            title = title.trim(),
            description = description.trim(),
            hashtags = hashtags.trim(),
            cta = cta.trim(),
            startMs = startMs,
            endMs = endMs,
            updatedAtMs = now()
        )
    }

    suspend fun updateExportState(id: Long, localPath: String?, status: String) {
        dao.updateExportState(
            id = id,
            status = status,
            progress = if (status == "done") 1f else 0f,
            error = null,
            localPath = localPath,
            updatedAtMs = now()
        )
    }

    suspend fun updateExportProgress(id: Long, status: String, progress: Float, error: String? = null) {
        dao.updateExportState(
            id = id,
            status = status,
            progress = progress,
            error = error,
            localPath = null,
            updatedAtMs = now()
        )
    }
}

class TranscriptRepository(private val dao: TranscriptDao) {
    suspend fun save(projectId: Long, transcript: Transcript) {
        dao.insert(TranscriptEntity(projectId = projectId, json = TranscriptCodec.encode(transcript)))
    }

    suspend fun get(projectId: Long): Transcript? {
        val entity = dao.getByProject(projectId) ?: return null
        return TranscriptCodec.decode(entity.json)
    }
}

class AIAnalysisRepository(private val dao: AIAnalysisDao) {
    private val json = Json { encodeDefaults = true }

    suspend fun save(projectId: Long, provider: String, result: AIAnalysisResult) {
        dao.insert(
            AIAnalysisEntity(
                projectId = projectId,
                provider = provider,
                json = json.encodeToString(
                    PersistedAnalysis.serializer(),
                    PersistedAnalysis(
                        title = result.title,
                        summary = result.summary,
                        candidateCount = result.candidates.size,
                        suggestedDurationMs = result.suggestedDurationMs
                    )
                )
            )
        )
    }

    @Serializable
    private data class PersistedAnalysis(
        val title: String,
        val summary: String,
        val candidateCount: Int,
        val suggestedDurationMs: Long?
    )
}

class SubtitleRepository(private val dao: SubtitleDao) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun save(shortId: Long, json: String) {
        dao.insert(SubtitleEntity(shortId = shortId, json = json))
    }

    suspend fun get(shortId: Long): String? = dao.getByShort(shortId)?.json

    suspend fun getSegments(shortId: Long): List<SubtitleSegment> {
        val raw = get(shortId) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val objectValue = element.jsonObject
                val startMs = objectValue["startMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val endMs = objectValue["endMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val words = objectValue["words"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()
                if (endMs <= startMs || words.isEmpty()) null else SubtitleSegment(startMs, endMs, words)
            }
        }.getOrDefault(emptyList())
    }
}

class ExportRepository(private val dao: ExportDao) {
    fun observeByProject(projectId: Long): Flow<List<ExportEntity>> = dao.observeByProject(projectId)
    suspend fun getById(id: Long): ExportEntity? = dao.getById(id)
    suspend fun getLatestForShort(
        projectId: Long,
        shortId: Long,
        platform: String,
        quality: String,
        resolution: String,
        fps: Int
    ): ExportEntity? = dao.getLatestForShort(projectId, shortId, platform, quality, resolution, fps)
    suspend fun getResumableByProject(projectId: Long): List<ExportEntity> = dao.getResumableByProject(projectId)
    suspend fun insert(entity: ExportEntity): Long = dao.insert(entity)

    suspend fun markQueued(id: Long) = update(id, status = "queued", progress = 0f, errorMessage = null, replaceError = true)

    suspend fun markRunning(id: Long): ExportEntity? {
        val current = dao.getById(id) ?: return null
        update(
            id = id,
            status = "running",
            progress = current.progress,
            attemptCount = current.attemptCount + 1,
            startedAtMs = current.startedAtMs ?: now(),
            errorMessage = null,
            replaceError = true
        )
        return dao.getById(id)
    }

    suspend fun updateProgress(id: Long, progress: Float) =
        update(id, status = "running", progress = progress)

    suspend fun markDone(id: Long, outputPath: String) =
        update(id, status = "done", progress = 1f, outputPath = outputPath, errorMessage = null, replaceError = true, completedAtMs = now())

    suspend fun markFailed(id: Long, message: String) =
        update(id, status = "failed", errorMessage = message, replaceError = true, completedAtMs = now())

    suspend fun markCancelled(id: Long, message: String = "Exportação cancelada.") =
        update(id, status = "cancelled", errorMessage = message, replaceError = true, completedAtMs = now())

    /** Compatibilidade para chamadas legadas do manager de exportação. */
    suspend fun updateResult(id: Long, outputPath: String?, status: String) {
        when (status) {
            "done" -> if (outputPath != null) markDone(id, outputPath) else update(id, status = status, progress = 1f)
            "cancelled" -> markCancelled(id)
            "failed" -> markFailed(id, "A exportação falhou.")
            else -> update(id, status = status, outputPath = outputPath)
        }
    }

    private suspend fun update(
        id: Long,
        status: String,
        progress: Float? = null,
        attemptCount: Int? = null,
        errorMessage: String? = null,
        replaceError: Boolean = false,
        startedAtMs: Long? = null,
        completedAtMs: Long? = null,
        outputPath: String? = null
    ) {
        val current = dao.getById(id) ?: return
        dao.updateState(
            id = id,
            status = status,
            progress = (progress ?: current.progress).coerceIn(0f, 1f),
            attemptCount = attemptCount ?: current.attemptCount,
            errorMessage = errorMessage,
            replaceError = replaceError,
            startedAtMs = startedAtMs,
            completedAtMs = completedAtMs,
            outputPath = outputPath
        )
    }
}

private fun now(): Long = System.currentTimeMillis()
