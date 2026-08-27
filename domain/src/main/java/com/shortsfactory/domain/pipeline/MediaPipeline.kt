package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.SubtitleStyleConfig
import com.shortsfactory.domain.model.Transcript
import kotlinx.coroutines.CancellationException

/** Etapas da pipeline de análise. */
enum class PipelineStage(val label: String) {
    VideoInput("Importação do vídeo"),
    AudioExtraction("Extração de áudio"),
    Transcription("Transcrição"),
    AIAnalysis("Análise de IA (Grok)"),
    CandidateSelection("Seleção de candidatos"),
    SubtitleGeneration("Geração de legendas"),
    FocusTracking("Rastreamento de foco")
}

/** Estado de uma etapa. */
enum class StageState { PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED }

/** Progresso de uma etapa individual. */
data class StageProgress(
    val stage: PipelineStage,
    val state: StageState,
    val progress: Float = 0f,
    val message: String? = null
)

/** Progresso consolidado do pipeline. */
data class PipelineProgress(
    val projectId: Long,
    val stages: List<StageProgress>,
    val isFinished: Boolean = stages.isNotEmpty() && stages.all {
        it.state == StageState.COMPLETED ||
            it.state == StageState.FAILED ||
            it.state == StageState.CANCELLED
    }
)

/** Configuração de geração. */
data class GenerationConfig(
    val preset: String,
    val maxCandidates: Int = 12
)

/** Saída da pipeline de análise. */
sealed class AnalysisOutcome {
    data class Success(
        val transcript: Transcript,
        val result: AIAnalysisResult,
        val selected: List<ShortCandidate>
    ) : AnalysisOutcome()

    data class Failed(val message: String) : AnalysisOutcome()
    data object Cancelled : AnalysisOutcome()
}

/** Serviço de extração de áudio (implementado pelo motor de vídeo). */
interface AudioExtractorService {
    suspend fun extract(videoPath: String, outputPath: String)
}

/** Serviço de transcrição. */
interface TranscriptionService {
    suspend fun transcribe(audioPath: String): Transcript
}

/** Pipeline de análise: importa, transcreve, analisa com IA e seleciona candidatos. */
class MediaAnalysisPipeline(
    private val aiProvider: AIProvider,
    private val candidateSelector: CandidateSelector,
    private val audioExtractor: AudioExtractorService,
    private val transcription: TranscriptionService
) {

    suspend fun analyze(
        videoPath: String,
        config: GenerationConfig,
        onStageUpdate: (StageProgress) -> Unit
    ): AnalysisOutcome {
        PipelineStage.values().forEach { stage ->
            onStageUpdate(StageProgress(stage, StageState.PENDING))
        }

        var currentStage: PipelineStage? = null
        fun update(stage: PipelineStage, state: StageState, progress: Float = 0f, message: String? = null) {
            onStageUpdate(StageProgress(stage, state, progress, message))
        }

        val audioPath = videoPath.replaceLast("video", "audio") + ".mp3"
        try {
            currentStage = PipelineStage.AudioExtraction
            update(currentStage, StageState.PROCESSING)
            audioExtractor.extract(videoPath, audioPath)
            update(currentStage, StageState.COMPLETED, 1f)

            currentStage = PipelineStage.Transcription
            update(currentStage, StageState.PROCESSING)
            val transcript = transcription.transcribe(audioPath)
            update(currentStage, StageState.COMPLETED, 1f)

            currentStage = PipelineStage.AIAnalysis
            update(currentStage, StageState.PROCESSING)
            val result = aiProvider.analyzeVideo(transcript, GenerationSummaryHint(config.preset))
            update(currentStage, StageState.COMPLETED, 1f)

            currentStage = PipelineStage.CandidateSelection
            update(currentStage, StageState.PROCESSING)
            val selected = candidateSelector.select(result.candidates, config)
            update(currentStage, StageState.COMPLETED, 1f)

            currentStage = PipelineStage.SubtitleGeneration
            update(currentStage, StageState.PROCESSING)
            update(currentStage, StageState.COMPLETED, 1f)

            currentStage = PipelineStage.FocusTracking
            update(currentStage, StageState.PROCESSING)
            update(currentStage, StageState.COMPLETED, 1f)

            return AnalysisOutcome.Success(transcript, result, selected)
        } catch (ce: CancellationException) {
            currentStage?.let { update(it, StageState.CANCELLED, message = "Análise cancelada.") }
            return AnalysisOutcome.Cancelled
        } catch (e: Exception) {
            val message = e.message ?: "Erro desconhecido"
            currentStage?.let { update(it, StageState.FAILED, message = message) }
            return AnalysisOutcome.Failed("Falha em ${currentStage?.label ?: "etapa desconhecida"}: $message")
        }
    }
}

private fun String.replaceLast(old: String, new: String): String {
    val idx = lastIndexOf(old)
    return if (idx >= 0) substring(0, idx) + new + substring(idx + old.length) else this
}

/** Dica de resumo passada à IA. */
data class GenerationSummaryHint(val preset: String)

/** Pontos de rastreamento de foco (placeholder para integração ML Kit). */
data class FocusPoint(
    val timeMs: Long,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

/** Método de tracking aplicado. */
enum class TrackingMethod { STATIC_CENTER, FACE_TRACKING }

/** Trilha de foco ao longo do trecho. */
data class FocusTrack(val points: List<FocusPoint>, val method: TrackingMethod)

/** Seleção de candidatos com remoção de sobreposição. */
class CandidateSelector {

    fun select(candidates: List<ShortCandidate>, config: GenerationConfig): List<ShortCandidate> {
        val limit = config.maxCandidates.coerceAtLeast(0)
        if (limit == 0) return emptyList()

        val sorted = candidates
            .asSequence()
            .filter { it.startMs >= 0L && it.endMs > it.startMs }
            .sortedByDescending { it.score }
            .toList()
        val maxMs = maxDurationFor(config.preset)
        if (maxMs == null) return sorted.take(limit)

        val picked = mutableListOf<ShortCandidate>()
        for (candidate in sorted) {
            if (picked.size >= limit) break
            val duration = candidate.endMs - candidate.startMs
            if (duration > maxMs) continue
            val overlaps = picked.any { it.endMs > candidate.startMs && it.startMs < candidate.endMs }
            if (!overlaps) picked += candidate
        }
        return picked
    }

    private fun maxDurationFor(preset: String): Long? = when (preset) {
        "15s" -> DurationPreset.FifteenSeconds.maxMs
        "30s" -> DurationPreset.ThirtySeconds.maxMs
        "45s" -> DurationPreset.FortyFiveSeconds.maxMs
        "60s" -> DurationPreset.SixtySeconds.maxMs
        "90s" -> DurationPreset.NinetySeconds.maxMs
        "ai" -> null
        else -> DurationPreset.ThirtySeconds.maxMs
    }
}

/** Especificação de clipe para processamento. */
data class ClipSpec(
    val inputPath: String,
    val outputPath: String,
    val startMs: Long,
    val endMs: Long,
    val targetWidth: Int = 1080,
    val targetHeight: Int = 1920,
    val fps: Int = 30,
    val bitrateBps: Long = 8_000_000L,
    val focusTrack: FocusTrack?,
    val subtitles: List<SubtitleSegment>,
    val subtitleStyle: SubtitleStyleConfig
)

/** Informações do vídeo de entrada. */
data class InputVideoInfo(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean
)

/** Interface do motor de vídeo. */
interface VideoEngine {
    fun cancel()
    fun probe(path: String): InputVideoInfo
    suspend fun extractAudio(videoPath: String, outputPath: String)
    suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit = {})
    suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long): FocusTrack
    suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String)
    fun isAlreadyTargetFormat(path: String, target: com.shortsfactory.domain.model.ResolutionPreset, fps: Int): Boolean
}
