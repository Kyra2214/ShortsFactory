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
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.SubtitleSegment
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
                    status = "pending"
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
        dao.updateMetadata(
            id = id,
            title = title,
            description = description,
            hashtags = hashtags,
            cta = cta,
            startMs = startMs,
            endMs = endMs
        )
    }

    suspend fun updateExportState(id: Long, localPath: String?, status: String) {
        dao.updateExportState(id, localPath, status)
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
    suspend fun insert(entity: ExportEntity): Long = dao.insert(entity)
    suspend fun updateResult(id: Long, outputPath: String?, status: String) =
        dao.updateResult(id, outputPath, status)
}
