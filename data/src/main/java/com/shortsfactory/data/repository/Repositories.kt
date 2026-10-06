package com.shortsfactory.data.repository

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
import com.shortsfactory.domain.export.ExportBatchState
import com.shortsfactory.domain.export.PlatformMetadata
import com.shortsfactory.domain.model.ExportPlatform
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

    /** Insere os candidatos de UMA análise. Re-análise passa por [ProjectStore.saveAnalysis], que apaga os antigos. */
    suspend fun insertCandidates(projectId: Long, candidates: List<ShortCandidate>): List<Long> =
        candidates.map { dao.insert(candidateToEntity(projectId, it)) }

    /**
     * Grava os campos editáveis. Mudar o intervalo invalida o export anterior (ver `ShortDao.updateMetadata`).
     * Esta camada só garante `fim > início`; os limites completos (vídeo, mínimo, teto, sobreposição)
     * são aplicados por [ProjectStore.updateShort].
     */
    suspend fun updateMetadata(
        id: Long,
        title: String,
        hook: String,
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
            hook = hook.trim(),
            description = description.trim(),
            hashtags = hashtags.trim(),
            cta = cta.trim(),
            startMs = startMs,
            endMs = endMs,
            updatedAtMs = now()
        )
    }

    /** Estado final do export. O erro anterior só é limpo ao concluir (`done`); falha/cancelamento o preservam. */
    suspend fun updateExportState(id: Long, localPath: String?, status: String) {
        dao.updateExportState(
            id = id,
            status = status,
            progress = if (status == "done") 1f else 0f,
            error = null,
            replaceError = status == "done",
            localPath = localPath,
            updatedAtMs = now()
        )
    }

    /**
     * Atualiza estado/progresso. O erro é preservado, exceto numa transição explícita: nova mensagem
     * (`error != null`), início de execução (`processing`) ou conclusão (`done`).
     */
    suspend fun updateExportProgress(id: Long, status: String, progress: Float, error: String? = null) {
        dao.updateExportState(
            id = id,
            status = status,
            progress = progress,
            error = error,
            replaceError = error != null || status == "processing" || status == "done",
            localPath = null,
            updatedAtMs = now()
        )
    }

    companion object {
        /** Candidato da análise → linha de `shorts`, com legendas e foco persistidos. */
        fun candidateToEntity(projectId: Long, candidate: ShortCandidate): ShortEntity = ShortEntity(
            projectId = projectId,
            startMs = candidate.startMs,
            endMs = candidate.endMs,
            score = candidate.score,
            title = candidate.title,
            hook = candidate.hook,
            topic = candidate.topic,
            reason = candidate.reason,
            status = "pending",
            subtitlesJson = candidate.subtitles.takeIf { it.isNotEmpty() }?.let(CandidateArtifactsCodec::encodeSubtitles),
            focusTrackJson = candidate.focusTrack?.let(CandidateArtifactsCodec::encodeFocusTrack),
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
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun save(projectId: Long, provider: String, result: AIAnalysisResult) {
        dao.insert(AIAnalysisEntity(projectId = projectId, provider = provider, json = encode(result)))
    }

    /** JSON persistido da análise (usado também por [ProjectStore], dentro de uma transação). */
    fun encode(result: AIAnalysisResult): String =
        json.encodeToString(PersistedAnalysis.serializer(), result.toPersisted())

    suspend fun get(projectId: Long): AIAnalysisResult? {
        val entity = dao.getByProject(projectId) ?: return null
        return runCatching {
            json.decodeFromString(PersistedAnalysis.serializer(), entity.json).toDomain()
        }.getOrNull()
    }

    @Serializable
    private data class PersistedAnalysis(
        val title: String,
        val summary: String,
        val suggestedDurationMs: Long? = null,
        val candidates: List<PersistedCandidate> = emptyList()
    ) {
        fun toDomain() = AIAnalysisResult(
            title = title,
            summary = summary,
            candidates = candidates.map { it.toDomain() },
            suggestedDurationMs = suggestedDurationMs
        )
    }

    @Serializable
    private data class PersistedCandidate(
        val score: Float,
        val startMs: Long,
        val endMs: Long,
        val title: String,
        val hook: String,
        val topic: String,
        val reason: String,
        val subtitles: List<PersistedSubtitle>,
        val focusTrack: PersistedFocusTrack?
    ) {
        fun toDomain() = ShortCandidate(
            score, startMs, endMs, title, hook, topic, reason,
            focusTrack?.toDomain(),
            subtitles.map { SubtitleSegment(it.startMs, it.endMs, it.words) }
        )
    }

    @Serializable
    private data class PersistedSubtitle(val startMs: Long, val endMs: Long, val words: List<String>)

    @Serializable
    private data class PersistedFocusTrack(
        val method: String,
        val points: List<PersistedFocusPoint>
    ) {
        fun toDomain() = com.shortsfactory.domain.pipeline.FocusTrack(
            points.map { com.shortsfactory.domain.pipeline.FocusPoint(it.timeMs, it.centerX, it.centerY, it.width, it.height) },
            runCatching { com.shortsfactory.domain.pipeline.TrackingMethod.valueOf(method) }
                .getOrDefault(com.shortsfactory.domain.pipeline.TrackingMethod.STATIC_CENTER)
        )
    }

    @Serializable
    private data class PersistedFocusPoint(
        val timeMs: Long,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float
    )

    private fun AIAnalysisResult.toPersisted() = PersistedAnalysis(
        title = title,
        summary = summary,
        suggestedDurationMs = suggestedDurationMs,
        candidates = candidates.map { candidate ->
            PersistedCandidate(
                candidate.score,
                candidate.startMs,
                candidate.endMs,
                candidate.title,
                candidate.hook,
                candidate.topic,
                candidate.reason,
                candidate.subtitles.map { PersistedSubtitle(it.startMs, it.endMs, it.words) },
                candidate.focusTrack?.let { track ->
                    PersistedFocusTrack(
                        track.method.name,
                        track.points.map { PersistedFocusPoint(it.timeMs, it.centerX, it.centerY, it.width, it.height) }
                    )
                }
            )
        }
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

class ExportBatchRepository(private val dao: ExportBatchDao) {
    fun observeLatest(projectId: Long): Flow<ExportBatchEntity?> = dao.observeLatest(projectId)
    suspend fun getLatest(projectId: Long): ExportBatchEntity? = dao.getLatest(projectId)

    /** Inicia (ou retoma, se o último lote ainda está aberto) o lote do projeto; contadores recomeçam do zero. */
    suspend fun start(projectId: Long, total: Int): Long {
        val now = System.currentTimeMillis()
        val open = dao.getLatest(projectId)?.takeIf { ExportBatchState.isOpen(it.state) }
        if (open != null) {
            dao.update(open.copy(total = total, completed = 0, failed = 0, cancelled = 0, state = ExportBatchState.RUNNING, updatedAtMs = now))
            return open.id
        }
        return dao.insert(
            ExportBatchEntity(projectId = projectId, total = total, completed = 0, failed = 0, cancelled = 0, state = ExportBatchState.RUNNING)
        )
    }

    /** Cancelamento pelo usuário: fecha o lote aberto (o worker pode nem ter começado). Idempotente. */
    suspend fun cancelOpen(projectId: Long) {
        val open = dao.getLatest(projectId)?.takeIf { ExportBatchState.isOpen(it.state) } ?: return
        dao.update(
            open.copy(
                cancelled = (open.total - open.completed - open.failed).coerceAtLeast(0),
                state = ExportBatchState.CANCELLED,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    /** O id do lote é o token: gravações de uma execução antiga (outro `id`) nunca alteram o lote atual. */
    suspend fun record(id: Long, projectId: Long, completed: Int, failed: Int, cancelled: Int, state: String) {
        val current = dao.getLatest(projectId)?.takeIf { it.id == id } ?: return
        dao.update(current.copy(completed = completed, failed = failed, cancelled = cancelled, state = state, updatedAtMs = System.currentTimeMillis()))
    }
}

class PlatformMetadataRepository(private val dao: ShortPlatformMetadataDao) {
    suspend fun save(shortId: Long, items: List<PlatformMetadata>) {
        items.forEach { m ->
            dao.upsert(
                ShortPlatformMetadataEntity(
                    shortId = shortId, platform = m.platform.key, title = m.title, description = m.description,
                    hashtagsJson = kotlinx.serialization.json.JsonArray(m.hashtags.map { kotlinx.serialization.json.JsonPrimitive(it) }).toString()
                )
            )
        }
    }

    suspend fun get(shortId: Long): List<PlatformMetadata> = dao.getByShort(shortId).mapNotNull { it.toDomain() }

    suspend fun delete(shortId: Long, platform: ExportPlatform) = dao.delete(shortId, platform.key)

    private fun ShortPlatformMetadataEntity.toDomain(): PlatformMetadata? {
        val p = ExportPlatform.entries.firstOrNull { it.key == platform } ?: return null
        val tags = runCatching { Json.parseToJsonElement(hashtagsJson).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
        return PlatformMetadata(p, title, description, tags)
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

    suspend fun markQueued(id: Long) =
        update(id, status = "queued", progress = 0f, errorMessage = null, replaceError = true, resetCompleted = true)

    suspend fun markRunning(id: Long): ExportEntity? {
        val current = dao.getById(id) ?: return null
        update(
            id = id,
            status = "running",
            progress = current.progress,
            attemptCount = current.attemptCount + 1,
            startedAtMs = current.startedAtMs ?: now(),
            errorMessage = null,
            replaceError = true,
            resetCompleted = true
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
        resetCompleted: Boolean = false,
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
            resetCompleted = resetCompleted,
            outputPath = outputPath
        )
    }
}

private fun now(): Long = System.currentTimeMillis()
