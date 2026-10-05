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
    AIAnalysis("Análise de IA"),
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
    private val transcription: TranscriptionService,
    private val videoEngine: VideoEngine? = null
) {

    suspend fun analyze(
        videoPath: String,
        config: GenerationConfig,
        onStageUpdate: suspend (StageProgress) -> Unit
    ): AnalysisOutcome {
        PipelineStage.values().forEach { stage ->
            onStageUpdate(StageProgress(stage, StageState.PENDING))
        }

        var currentStage: PipelineStage? = null
        suspend fun update(stage: PipelineStage, state: StageState, progress: Float = 0f, message: String? = null) {
            onStageUpdate(StageProgress(stage, state, progress, message))
        }

        try {
            currentStage = PipelineStage.VideoInput
            update(currentStage, StageState.PROCESSING)
            val input = validateInput(videoPath)
            update(currentStage, StageState.COMPLETED, 1f, input.width.toString() + "x" + input.height + ", " + input.durationMs + "ms")

            val audioPath = videoPath.replaceLast("video", "audio") + ".mp3"
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
            val withSubtitles = selected.map { candidate ->
                candidate.copy(subtitles = buildSubtitles(candidate, transcript))
            }
            update(currentStage, StageState.COMPLETED, 1f, withSubtitles.sumOf { it.subtitles.size }.toString() + " segmentos")

            currentStage = PipelineStage.FocusTracking
            update(currentStage, StageState.PROCESSING)
            val withFocus = withSubtitles.map { candidate ->
                candidate.copy(
                    focusTrack = videoEngine?.detectFocusTrack(videoPath, candidate.startMs, candidate.endMs)
                        ?: staticCenterTrack(candidate)
                )
            }
            update(currentStage, StageState.COMPLETED, 1f, withFocus.size.toString() + " trilhas")

            return AnalysisOutcome.Success(transcript, result.copy(candidates = withFocus), withFocus)
        } catch (ce: CancellationException) {
            currentStage?.let { update(it, StageState.CANCELLED, message = "Análise cancelada.") }
            return AnalysisOutcome.Cancelled
        } catch (e: Exception) {
            val message = e.message ?: "Erro desconhecido"
            currentStage?.let { update(it, StageState.FAILED, message = message) }
            return AnalysisOutcome.Failed("Falha em ${currentStage?.label ?: "etapa desconhecida"}: $message")
        }
    }

    private fun validateInput(videoPath: String): InputVideoInfo {
        require(videoPath.isNotBlank()) { "O caminho do vídeo está vazio." }
        val file = java.io.File(videoPath)
        require(file.exists() && file.isFile) { "Vídeo de entrada não encontrado: " + videoPath }
        val engine = videoEngine
        return engine?.probe(videoPath) ?: InputVideoInfo(videoPath, 0L, 0, 0, 0.0, true)
    }

    private fun buildSubtitles(candidate: ShortCandidate, transcript: Transcript): List<SubtitleSegment> {
        return transcript.segments.asSequence()
            .filter { it.endMs > candidate.startMs && it.startMs < candidate.endMs }
            .map { SubtitleSegment(maxOf(it.startMs, candidate.startMs), minOf(it.endMs, candidate.endMs), it.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)) }
            .filter { it.endMs > it.startMs && it.words.isNotEmpty() }
            .toList()
    }

    private fun staticCenterTrack(candidate: ShortCandidate): FocusTrack {
        val duration = (candidate.endMs - candidate.startMs).coerceAtLeast(1L)
        val points = listOf(candidate.startMs, candidate.startMs + duration / 2, candidate.endMs).distinct()
            .map { time -> FocusPoint(time, 0.5f, 0.5f, 1f, 1f) }
        return FocusTrack(points, TrackingMethod.STATIC_CENTER)
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
    suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long): List<String>
    suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit = {})
    suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long): FocusTrack
    suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String)
    fun isAlreadyTargetFormat(path: String, target: com.shortsfactory.domain.model.ResolutionPreset, fps: Int): Boolean
}
