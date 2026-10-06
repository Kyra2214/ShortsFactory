package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiHttpException
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import com.shortsfactory.domain.model.TrendCard
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fase 4: validação de entrada no pipeline, saneamento da IA, retry tipado e limpeza do temporário. */
class MediaPipelineInputAndAiTest {

    private lateinit var input: File
    private lateinit var workDir: File
    private val updates = mutableListOf<StageProgress>()

    @Before
    fun setUp() {
        input = File.createTempFile("shorts-factory-f4-", ".mp4").apply { writeText("fixture") }
        workDir = File(input.parentFile, "analysis-f4-" + System.nanoTime())
    }

    @After
    fun tearDown() {
        input.delete()
        workDir.deleteRecursively()
    }

    private var probeInfo = InputVideoInfo("", 60_000L, 1920, 1080, 30.0, true)
    private var extractCalls = 0
    private var transcript = Transcript(listOf(TranscriptSegment(0L, 60_000L, "fala")))
    private var aiResult: () -> AIAnalysisResult = {
        AIAnalysisResult("t", "s", listOf(cand(5_000, 25_000)), null, provider = "IA X", model = "m1")
    }
    private var failStage: PipelineStage? = null
    private var failWith: Exception? = null

    private fun cand(start: Long, end: Long, title: String = "titulo") =
        ShortCandidate(0.8f, start, end, title, "h", "t", "r")

    private fun maybeFail(stage: PipelineStage) {
        if (stage == failStage) throw failWith!!
    }

    private fun pipeline() = MediaAnalysisPipeline(
        aiProvider = object : AIProvider {
            override val providerName = "fake"
            override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult {
                maybeFail(PipelineStage.AIAnalysis)
                return aiResult()
            }
            override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> = emptyList()
            override suspend fun analyzeTrends(query: String, region: String): String = ""
            override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String = ""
        },
        candidateSelector = CandidateSelector(),
        audioExtractor = object : AudioExtractorService {
            override suspend fun extract(videoPath: String, outputPath: String) {
                extractCalls++
                maybeFail(PipelineStage.AudioExtraction)
                File(outputPath).writeText("audio")
            }
        },
        transcription = object : TranscriptionService {
            override suspend fun transcribe(audioPath: String): Transcript {
                maybeFail(PipelineStage.Transcription)
                return transcript
            }
        },
        videoEngine = object : VideoEngine {
            override suspend fun probe(path: String): InputVideoInfo {
                maybeFail(PipelineStage.VideoInput)
                return probeInfo.copy(path = path)
            }
            override suspend fun extractAudio(videoPath: String, outputPath: String) = Unit
            override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long) = emptyList<String>()
            override suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit) = Unit
            override suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long): FocusTrack {
                maybeFail(PipelineStage.FocusTracking)
                return FocusTrack(listOf(FocusPoint(startMs, 0.5f, 0.5f, 1f, 1f)), TrackingMethod.STATIC_CENTER)
            }
            override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) = Unit
            override suspend fun isAlreadyTargetFormat(path: String, target: ResolutionPreset, fps: Int) = false
        },
        audioWorkDir = workDir
    )

    private suspend fun run() = pipeline().analyze(input.absolutePath, GenerationConfig("30s", 3)) { updates += it }

    private fun lastState(stage: PipelineStage) = updates.lastOrNull { it.stage == stage }?.state

    @Test
    fun `duracao zero falha em VideoInput sem extrair audio`() = runTest {
        probeInfo = probeInfo.copy(durationMs = 0L)
        val outcome = run()
        assertTrue(outcome is AnalysisOutcome.Failed)
        assertTrue((outcome as AnalysisOutcome.Failed).message.contains("duração"))
        assertFalse(outcome.retryable)
        assertEquals(StageState.FAILED, lastState(PipelineStage.VideoInput))
        assertEquals(0, extractCalls)
    }

    @Test
    fun `video sem audio falha em VideoInput`() = runTest {
        probeInfo = probeInfo.copy(hasAudio = false)
        val outcome = run() as AnalysisOutcome.Failed
        assertTrue(outcome.message.contains("áudio"))
        assertEquals(0, extractCalls)
    }

    @Test
    fun `resolucao desconhecida e arquivo vazio falham em VideoInput`() = runTest {
        probeInfo = probeInfo.copy(width = 0)
        assertTrue(run() is AnalysisOutcome.Failed)
        updates.clear()
        probeInfo = probeInfo.copy(width = 1920)
        input.writeText("")
        val outcome = run() as AnalysisOutcome.Failed
        assertTrue(outcome.message.contains("vazio"))
        assertEquals(0, extractCalls)
    }

    @Test
    fun `cada estagio falhando marca FAILED nele e CANCELLED nos seguintes`() = runTest {
        listOf(
            PipelineStage.VideoInput, PipelineStage.AudioExtraction, PipelineStage.Transcription,
            PipelineStage.AIAnalysis, PipelineStage.FocusTracking
        ).forEach { stage ->
            updates.clear()
            failStage = stage
            failWith = IllegalStateException("boom em ${stage.name}")
            val outcome = run()
            assertTrue("$stage → $outcome", outcome is AnalysisOutcome.Failed)
            assertEquals(StageState.FAILED, lastState(stage))
            PipelineStage.values().filter { it.ordinal > stage.ordinal }.forEach {
                assertEquals("$it após falha em $stage", StageState.CANCELLED, lastState(it))
            }
            assertFalse("temporário apagado após falha em $stage", workDir.exists())
        }
    }

    @Test
    fun `transcricao sem fala falha em Transcription`() = runTest {
        transcript = Transcript(emptyList())
        val outcome = run() as AnalysisOutcome.Failed
        assertTrue(outcome.message.contains("não contém fala"))
        assertEquals(StageState.FAILED, lastState(PipelineStage.Transcription))
    }

    @Test
    fun `candidatos fora do video ou da transcricao sao descartados e contados`() = runTest {
        aiResult = {
            AIAnalysisResult(
                "t", "s",
                listOf(
                    cand(5_000, 25_000),
                    cand(5_000, 90_000, "fora do video"),
                    cand(10_000, 20_000, ""),
                    cand(ShortCandidate.MISSING_MS, 20_000, "sem inicio")
                ),
                null, provider = "IA X", model = "m1"
            )
        }
        val outcome = run() as AnalysisOutcome.Success
        assertEquals(1, outcome.selected.size)
        assertEquals(3, outcome.discarded.size)
        val msg = updates.last { it.stage == PipelineStage.CandidateSelection && it.state == StageState.COMPLETED }.message!!
        assertTrue(msg, msg.contains("1 válidos, 3 descartados"))
        assertEquals("IA X", outcome.result.provider)
        assertEquals("m1", outcome.result.model)
    }

    @Test
    fun `nenhum candidato valido falha em CandidateSelection sem retry`() = runTest {
        aiResult = { AIAnalysisResult("t", "s", listOf(cand(5_000, 900_000)), null) }
        val outcome = run() as AnalysisOutcome.Failed
        assertFalse(outcome.retryable)
        assertTrue(outcome.message.contains("nenhum candidato válido"))
        assertEquals(StageState.FAILED, lastState(PipelineStage.CandidateSelection))
    }

    @Test
    fun `retry decidido por tipo - 503 e rede sao retryable, 401 e parse nao`() = runTest {
        failStage = PipelineStage.AIAnalysis
        failWith = AiHttpException(503, "indisponível")
        assertTrue((run() as AnalysisOutcome.Failed).retryable)
        failWith = java.net.SocketTimeoutException("timeout")
        assertTrue((run() as AnalysisOutcome.Failed).retryable)
        failWith = AiHttpException(401, "chave inválida")
        assertFalse((run() as AnalysisOutcome.Failed).retryable)
        // mensagem com "timeout"/"http 503" NÃO pode virar retry: antes a decisão era por substring
        failWith = IllegalStateException("timeout http 503 indisponível")
        assertFalse((run() as AnalysisOutcome.Failed).retryable)
    }

    @Test
    fun `sucesso apaga o diretorio temporario`() = runTest {
        assertTrue(run() is AnalysisOutcome.Success)
        assertFalse(workDir.exists())
    }
}
