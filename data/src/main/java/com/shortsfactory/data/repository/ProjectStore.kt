package com.shortsfactory.data.repository

import androidx.room.withTransaction
import com.shortsfactory.data.local.db.ShortsDatabase
import com.shortsfactory.data.local.entity.AIAnalysisEntity
import com.shortsfactory.data.local.entity.TranscriptEntity
import com.shortsfactory.domain.editor.RangeValidation
import com.shortsfactory.domain.editor.ShortRangeValidator
import com.shortsfactory.domain.editor.TimeRange
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptCodec

/** Resultado da edição de um Short. */
sealed class ShortUpdateResult {
    /** @param intervalChanged o intervalo mudou: export, foco e legendas anteriores foram invalidados. */
    data class Saved(val intervalChanged: Boolean) : ShortUpdateResult()
    data class Rejected(val message: String) : ShortUpdateResult()
    data object NotFound : ShortUpdateResult()
}

/**
 * Operações que precisam de mais de uma tabela e, por isso, rodam numa ÚNICA transação do Room:
 * ou tudo é gravado ou nada é.
 */
class ProjectStore(private val database: ShortsDatabase) {

    /**
     * Salva o resultado de uma análise: transcript, resposta da IA e candidatos, e marca o projeto
     * como `done`. Os dados antigos do projeto são substituídos (re-análise não duplica): apagar os
     * shorts antigos remove, em cascata, as legendas e os exports que dependiam deles.
     * Se qualquer passo falhar, o estado anterior permanece intacto.
     */
    suspend fun saveAnalysis(
        projectId: Long,
        transcript: Transcript,
        provider: String,
        result: AIAnalysisResult,
        selected: List<ShortCandidate>
    ) {
        val aiRepository = AIAnalysisRepository(database.aiAnalysisDao())
        database.withTransaction {
            val transcripts = database.transcriptDao()
            transcripts.deleteByProject(projectId)
            transcripts.insert(TranscriptEntity(projectId = projectId, json = TranscriptCodec.encode(transcript)))

            val analyses = database.aiAnalysisDao()
            analyses.deleteByProject(projectId)
            analyses.insert(AIAnalysisEntity(projectId = projectId, provider = provider, json = aiRepository.encode(result)))

            val shorts = database.shortDao()
            shorts.deleteByProject(projectId)
            selected.forEach { shorts.insert(ShortRepository.candidateToEntity(projectId, it)) }

            database.projectDao().updateAnalysisState(projectId, "done", 1f, null, System.currentTimeMillis())
        }
    }

    /**
     * Edita um Short aplicando as regras do editor ([ShortRangeValidator]) contra a duração do vídeo e
     * os outros Shorts do projeto. Intervalo inválido é recusado com mensagem, nunca ajustado em silêncio.
     * Se o intervalo mudou, o export anterior (linhas em `exports` e `localPath`) é invalidado.
     */
    suspend fun updateShort(
        shortId: Long,
        title: String,
        hook: String,
        description: String,
        hashtags: String,
        cta: String,
        startMs: Long,
        endMs: Long
    ): ShortUpdateResult = database.withTransaction {
        val shorts = database.shortDao()
        val current = shorts.getById(shortId) ?: return@withTransaction ShortUpdateResult.NotFound
        val videoDurationMs = database.projectDao().getById(current.projectId)?.videoDurationMs
        val others = shorts.getByProject(current.projectId)
            .filter { it.id != shortId }
            .map { TimeRange(it.startMs, it.endMs) }

        val validation = ShortRangeValidator.validate(startMs, endMs, videoDurationMs, others)
        if (validation is RangeValidation.Invalid) {
            return@withTransaction ShortUpdateResult.Rejected(validation.message)
        }

        val intervalChanged = current.startMs != startMs || current.endMs != endMs
        shorts.updateMetadata(
            id = shortId,
            title = title.trim(),
            hook = hook.trim(),
            description = description.trim(),
            hashtags = hashtags.trim(),
            cta = cta.trim(),
            startMs = startMs,
            endMs = endMs,
            updatedAtMs = System.currentTimeMillis()
        )
        if (intervalChanged) {
            database.exportDao().deleteByShort(shortId)
            database.subtitleDao().deleteByShort(shortId)
        }
        ShortUpdateResult.Saved(intervalChanged)
    }
}
