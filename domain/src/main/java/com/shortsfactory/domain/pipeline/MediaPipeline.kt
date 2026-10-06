package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.isTransientFailure
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.SubtitleStyleConfig
import com.shortsfactory.domain.model.Transcript
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
        val selected: List<ShortCandidate>,
        val discarded: List<DiscardedCandidate> = emptyList()
    ) : AnalysisOutcome()

    /** @param retryable falha transitória (rede, 408/429/5xx): repetir pode dar certo. */
    data class Failed(val message: String, val retryable: Boolean = false) : AnalysisOutcome()
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
    private val videoEngine: VideoEngine? = null,
    /** Diretório temporário desta análise (áudio e fragmentos); apagado inteiro ao terminar. */
    private val audioWorkDir: File? = null,
    private val candidateScorer: CandidateScorer = CandidateScorer()
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
        // Inicia uma etapa: respeita cancelamento entre etapas (a etapa que ia começar fica CANCELLED,
        // e a anterior mantém COMPLETED) e registra o início.
        suspend fun begin(stage: PipelineStage) {
            currentStage = stage
            currentCoroutineContext().ensureActive()
            update(stage, StageState.PROCESSING)
        }

        val workDir = audioWorkDir ?: File(File(videoPath).absoluteFile.parentFile, ".analysis_" + File(videoPath).name)
        val audioPath = File(workDir, "audio.m4a").absolutePath
        var videoDurationMs = 0L
        try {
            begin(PipelineStage.VideoInput)
            // Preset desconhecido falha aqui, antes de qualquer etapa cara (sem default silencioso).
            val preset = DurationPreset.fromKey(config.preset)
            val input = validateInput(videoPath)
            videoDurationMs = input.durationMs
            update(PipelineStage.VideoInput, StageState.COMPLETED, 1f, input.width.toString() + "x" + input.height + ", " + input.durationMs + "ms")

            begin(PipelineStage.AudioExtraction)
            workDir.mkdirs()
            audioExtractor.extract(videoPath, audioPath)
            update(PipelineStage.AudioExtraction, StageState.COMPLETED, 1f)

            begin(PipelineStage.Transcription)
            val transcript = transcription.transcribe(audioPath)
            check(transcript.segments.isNotEmpty()) { "A transcrição não contém fala." }
            update(PipelineStage.Transcription, StageState.COMPLETED, 1f)

            begin(PipelineStage.AIAnalysis)
            val result = aiProvider.analyzeVideo(transcript, GenerationSummaryHint(config.preset))
            update(PipelineStage.AIAnalysis, StageState.COMPLETED, 1f)

            begin(PipelineStage.CandidateSelection)
            // A saída da IA é entrada não confiável: descarta o que não existe no vídeo/transcrição.
            val sanitized = AiResponseSanitizer.sanitize(result.candidates, videoDurationMs, transcript)
            check(sanitized.kept.isNotEmpty()) {
                "A IA não devolveu nenhum candidato válido (${sanitized.summary()})."
            }
            val rescored = sanitized.kept.map { candidate ->
                candidateScorer.score(candidate, transcript, preset.maxMs)
            }
            val selection = candidateSelector.selectWithReport(
                rescored,
                config,
                videoDurationMs = videoDurationMs,
                suggestedDurationMs = result.suggestedDurationMs
            )
            val selected = selection.selected
            check(selected.isNotEmpty()) {
                "Nenhum candidato atende às regras de seleção (" + selection.summary() + ")."
            }
            update(
                PipelineStage.CandidateSelection,
                StageState.COMPLETED,
                1f,
                sanitized.summary() + "; " + selection.summary()
            )

            begin(PipelineStage.SubtitleGeneration)
            val withSubtitles = selected.map { candidate ->
                candidate.copy(subtitles = buildSubtitles(candidate, transcript))
            }
            update(PipelineStage.SubtitleGeneration, StageState.COMPLETED, 1f, withSubtitles.sumOf { it.subtitles.size }.toString() + " segmentos")

            begin(PipelineStage.FocusTracking)
            val withFocus = withSubtitles.map { candidate ->
                candidate.copy(
                    focusTrack = videoEngine?.detectFocusTrack(videoPath, candidate.startMs, candidate.endMs)
                        ?: staticCenterTrack(candidate)
                )
            }
            update(PipelineStage.FocusTracking, StageState.COMPLETED, 1f, withFocus.size.toString() + " trilhas")

            return AnalysisOutcome.Success(transcript, result.copy(candidates = withFocus), withFocus, sanitized.discarded)
        } catch (ce: CancellationException) {
            // Cancelamento real do coroutine: o estado final é gravado em NonCancellable, porque
            // `update` chama funções suspend (Room/WorkManager) que falhariam num coroutine já cancelado.
            // Exceção: TimeoutCancellationException com o coroutine ainda ativo é um tempo limite interno
            // (falha da etapa), não cancelamento do usuário.
            if (ce is TimeoutCancellationException && currentCoroutineContext().isActive) {
                return failed(currentStage, ce, ::update)
            }
            withContext(NonCancellable) {
                currentStage?.let { interrupted ->
                    update(interrupted, StageState.CANCELLED, message = "Análise cancelada.")
                    cancelPendingStages(interrupted, ::update)
                }
            }
            return AnalysisOutcome.Cancelled
        } catch (e: Exception) {
            return failed(currentStage, e, ::update)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun failed(
        stage: PipelineStage?,
        error: Exception,
        update: suspend (PipelineStage, StageState, Float, String?) -> Unit
    ): AnalysisOutcome.Failed {
        val retryable = error.isTransientFailure()
        val message = error.message ?: "Erro desconhecido"
        withContext(NonCancellable) {
            stage?.let { failedStage ->
                update(failedStage, StageState.FAILED, 0f, message)
                cancelPendingStages(failedStage, update, "Ignorada porque uma etapa anterior falhou.")
            }
        }
        return AnalysisOutcome.Failed("Falha em ${stage?.label ?: "etapa desconhecida"}: $message", retryable)
    }

    private suspend fun cancelPendingStages(
        failedStage: PipelineStage,
        update: suspend (PipelineStage, StageState, Float, String?) -> Unit,
        message: String = "Ignorada porque a análise foi cancelada."
    ) {
        val index = PipelineStage.values().indexOf(failedStage)
        PipelineStage.values().drop(index + 1).forEach { stage ->
            update(stage, StageState.CANCELLED, 0f, message)
        }
    }

    /**
     * Valida a entrada ANTES de qualquer estágio caro: arquivo, tamanho, duração, resolução e áudio
     * ([MediaValidator]). Sem motor de vídeo só é possível checar o arquivo.
     */
    private suspend fun validateInput(videoPath: String): InputVideoInfo {
        require(videoPath.isNotBlank()) { "O caminho do vídeo está vazio." }
        val file = File(videoPath)
        require(file.exists() && file.isFile) { "Vídeo de entrada não encontrado: " + videoPath }
        require(file.length() > 0L) { "O arquivo de vídeo está vazio ou indisponível." }
        val engine = videoEngine ?: return InputVideoInfo(videoPath, 0L, 0, 0, 0.0, true)
        val info = engine.probe(videoPath)
        val validation = MediaValidator.validate(
            MediaMetadata(videoPath, file.length(), info.durationMs, info.width, info.height, info.hasAudio)
        )
        require(validation.valid) { validation.message }
        return info
    }

    private fun buildSubtitles(candidate: ShortCandidate, transcript: Transcript): List<SubtitleSegment> =
        SubtitleTiming.fromTranscript(transcript.segments, candidate.startMs, candidate.endMs)

    private fun staticCenterTrack(candidate: ShortCandidate): FocusTrack {
        val duration = (candidate.endMs - candidate.startMs).coerceAtLeast(1L)
        val points = listOf(candidate.startMs, candidate.startMs + duration / 2, candidate.endMs).distinct()
            .map { time -> FocusPoint(time, 0.5f, 0.5f, 1f, 1f) }
        return FocusTrack(points, TrackingMethod.STATIC_CENTER)
    }
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
    /**
     * Todas as operações respeitam o cancelamento do coroutine que as chamou: ao cancelar, o processo
     * externo do PRÓPRIO chamador é encerrado e [CancellationException] é lançada. Não existe `cancel()`
     * global; cancele o `Job` do dono. Falhas do processo viram [FfmpegFailedException].
     */
    suspend fun probe(path: String): InputVideoInfo
    suspend fun extractAudio(videoPath: String, outputPath: String)
    suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long): List<String>
    suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit = {})
    suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long): FocusTrack
    suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String)
    suspend fun isAlreadyTargetFormat(path: String, target: com.shortsfactory.domain.model.ResolutionPreset, fps: Int): Boolean
}
