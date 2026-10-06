package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cancelamento no meio de cada estágio. Os fakes SUSPENDEM e o callback `onStageUpdate` também suspende
 * (`delay(1)`), como o Room: sem `NonCancellable` o estado final nunca seria gravado.
 */
class MediaPipelineCancellationTest {

    private lateinit var input: File
    private lateinit var workDir: File

    @Before
    fun setUp() {
        input = File.createTempFile("shorts-factory-cancel-", ".mp4").apply { writeText("fixture") }
        workDir = File(input.parentFile, "analysis-" + System.nanoTime())
    }

    @After
    fun tearDown() {
        input.delete()
        workDir.deleteRecursively()
    }

    private class Harness(val blockAt: PipelineStage?) {
        val reachedBlock = CompletableDeferred<Unit>()
        val updates = mutableListOf<StageProgress>()
        var outcome: AnalysisOutcome? = null

        suspend fun maybeBlock(stage: PipelineStage) {
            if (stage == blockAt) {
                reachedBlock.complete(Unit)
                awaitCancellation()
            }
        }
    }

    private fun pipeline(h: Harness, aiFails: Throwable? = null): MediaAnalysisPipeline {
        val candidate = ShortCandidate(0.9f, 500L, 4_500L, "t", "h", "topic", "reason")
        return MediaAnalysisPipeline(
            aiProvider = object : AIProvider {
                override val providerName = "fake"
                override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult {
                    h.maybeBlock(PipelineStage.AIAnalysis)
                    aiFails?.let { throw it }
                    return AIAnalysisResult("x", "x", listOf(candidate), 2_000L)
                }
                override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> = emptyList()
                override suspend fun analyzeTrends(query: String, region: String): String = ""
                override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String = ""
            },
            candidateSelector = CandidateSelector(),
            audioExtractor = object : AudioExtractorService {
                override suspend fun extract(videoPath: String, outputPath: String) {
                    File(outputPath).writeText("audio")
                    h.maybeBlock(PipelineStage.AudioExtraction)
                }
            },
            transcription = object : TranscriptionService {
                override suspend fun transcribe(audioPath: String): Transcript {
                    h.maybeBlock(PipelineStage.Transcription)
                    return Transcript(listOf(TranscriptSegment(0L, 6_000L, "texto")))
                }
            },
            audioWorkDir = workDir,
            videoEngine = object : VideoEngine {
                override suspend fun probe(path: String): InputVideoInfo {
                    h.maybeBlock(PipelineStage.VideoInput)
                    return InputVideoInfo(path, 10_000L, 1920, 1080, 30.0, true)
                }
                override suspend fun extractAudio(videoPath: String, outputPath: String) = Unit
                override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long) = emptyList<String>()
                override suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit) = Unit
                override suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long): FocusTrack {
                    h.maybeBlock(PipelineStage.FocusTracking)
                    return FocusTrack(listOf(FocusPoint(startMs, 0.5f, 0.5f, 1f, 1f)), TrackingMethod.STATIC_CENTER)
                }
                override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) = Unit
                override suspend fun isAlreadyTargetFormat(path: String, target: ResolutionPreset, fps: Int) = false
            }
        )
    }

    private fun lastState(h: Harness, stage: PipelineStage): StageState? =
        h.updates.lastOrNull { it.stage == stage }?.state

    private fun assertCancelledAt(h: Harness, stage: PipelineStage) {
        assertEquals("o resultado deve ser Cancelled", AnalysisOutcome.Cancelled, h.outcome)
        assertTrue("nenhum estágio pode terminar FAILED", h.updates.none { it.state == StageState.FAILED })
        assertEquals("estágio interrompido", StageState.CANCELLED, lastState(h, stage))
        PipelineStage.values().forEach { other ->
            when {
                other.ordinal < stage.ordinal -> assertEquals("anterior $other", StageState.COMPLETED, lastState(h, other))
                other.ordinal > stage.ordinal -> assertEquals("posterior $other", StageState.CANCELLED, lastState(h, other))
            }
        }
        assertFalse("diretório temporário deve ser apagado", workDir.exists())
    }

    private fun cancelInTheMiddleOf(stage: PipelineStage) = runTest {
        val h = Harness(stage)
        val job = launch {
            h.outcome = pipeline(h).analyze(input.absolutePath, GenerationConfig("30s", 3)) { progress ->
                delay(1) // callback suspende, como uma gravação no Room
                h.updates += progress
            }
        }
        withTimeout(10_000) { h.reachedBlock.await() }
        job.cancel()
        job.join()
        assertCancelledAt(h, stage)
    }

    @Test fun `cancelar durante VideoInput grava CANCELLED`() = cancelInTheMiddleOf(PipelineStage.VideoInput)
    @Test fun `cancelar durante AudioExtraction grava CANCELLED`() = cancelInTheMiddleOf(PipelineStage.AudioExtraction)
    @Test fun `cancelar durante Transcription grava CANCELLED`() = cancelInTheMiddleOf(PipelineStage.Transcription)
    @Test fun `cancelar durante AIAnalysis grava CANCELLED`() = cancelInTheMiddleOf(PipelineStage.AIAnalysis)
    @Test fun `cancelar durante FocusTracking grava CANCELLED`() = cancelInTheMiddleOf(PipelineStage.FocusTracking)

    /** Estágios sem ponto de suspensão próprio: o cancelamento é percebido na fronteira entre estágios. */
    private fun cancelWhenStageStarts(stage: PipelineStage) = runTest {
        val h = Harness(blockAt = null)
        var cancelled = false
        lateinit var job: Job
        job = launch {
            h.outcome = pipeline(h).analyze(input.absolutePath, GenerationConfig("30s", 3)) { progress ->
                if (!cancelled && progress.stage == stage && progress.state == StageState.PROCESSING) {
                    cancelled = true
                    job.cancel()
                }
                delay(1)
                h.updates += progress
            }
        }
        job.join()
        assertCancelledAt(h, stage)
    }

    @Test fun `cancelar ao iniciar CandidateSelection grava CANCELLED`() = cancelWhenStageStarts(PipelineStage.CandidateSelection)
    @Test fun `cancelar ao iniciar SubtitleGeneration grava CANCELLED`() = cancelWhenStageStarts(PipelineStage.SubtitleGeneration)

    @Test
    fun `cancelamento entre estagios marca a proxima etapa e preserva a anterior concluida`() = runTest {
        val h = Harness(blockAt = null)
        var cancelled = false
        lateinit var job: Job
        job = launch {
            h.outcome = pipeline(h).analyze(input.absolutePath, GenerationConfig("30s", 3)) { progress ->
                if (!cancelled && progress.stage == PipelineStage.AIAnalysis && progress.state == StageState.COMPLETED) {
                    cancelled = true
                    job.cancel()
                }
                h.updates += progress
            }
        }
        job.join()
        assertEquals(AnalysisOutcome.Cancelled, h.outcome)
        assertEquals(StageState.COMPLETED, lastState(h, PipelineStage.AIAnalysis))
        assertEquals(StageState.CANCELLED, lastState(h, PipelineStage.CandidateSelection))
        assertTrue(h.updates.none { it.state == StageState.FAILED })
    }

    @Test
    fun `erro comum continua sendo Failed com a etapa marcada FAILED e as seguintes ignoradas`() = runTest {
        val h = Harness(blockAt = null)
        val outcome = pipeline(h, aiFails = IllegalStateException("provedor fora do ar"))
            .analyze(input.absolutePath, GenerationConfig("30s", 3)) { h.updates += it }

        assertTrue(outcome is AnalysisOutcome.Failed)
        assertTrue((outcome as AnalysisOutcome.Failed).message.contains("provedor fora do ar"))
        assertEquals(StageState.FAILED, lastState(h, PipelineStage.AIAnalysis))
        assertEquals(StageState.CANCELLED, lastState(h, PipelineStage.FocusTracking))
        assertEquals(StageState.COMPLETED, lastState(h, PipelineStage.Transcription))
    }

    @Test
    fun `CancellationException de timeout interno com o coroutine ativo e falha e nao cancelamento`() = runTest {
        val h = Harness(blockAt = null)
        val timeout = try {
            withTimeout(1) { delay(1_000) }
            error("deveria estourar")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            e
        }
        val outcome = pipeline(h, aiFails = timeout)
            .analyze(input.absolutePath, GenerationConfig("30s", 3)) { h.updates += it }

        assertTrue("era $outcome", outcome is AnalysisOutcome.Failed)
        assertEquals(StageState.FAILED, lastState(h, PipelineStage.AIAnalysis))
    }
}
