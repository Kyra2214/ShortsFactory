package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
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

/** Fase 5: a seleção determinística dentro do pipeline (todos os presets, vídeo real, preset inválido). */
class MediaPipelineSelectionTest {

    private lateinit var input: File
    private lateinit var workDir: File
    private val updates = mutableListOf<StageProgress>()
    private var videoMs = 120_000L
    private var suggested: Long? = null
    private var candidates: List<ShortCandidate> = emptyList()
    private var extractCalls = 0

    @Before
    fun setUp() {
        input = File.createTempFile("shorts-factory-f5-", ".mp4").apply { writeText("fixture") }
        workDir = File(input.parentFile, "analysis-f5-" + System.nanoTime())
    }

    @After
    fun tearDown() {
        input.delete()
        workDir.deleteRecursively()
    }

    private fun cand(start: Long, end: Long, score: Float, title: String) =
        ShortCandidate(score, start, end, title, "hook?", "t", "r")

    private fun pipeline() = MediaAnalysisPipeline(
        aiProvider = object : AIProvider {
            override val providerName = "fake"
            override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint) =
                AIAnalysisResult("t", "s", candidates, suggested)
            override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> = emptyList()
            override suspend fun analyzeTrends(query: String, region: String): String = ""
            override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String = ""
        },
        candidateSelector = CandidateSelector(),
        audioExtractor = object : AudioExtractorService {
            override suspend fun extract(videoPath: String, outputPath: String) {
                extractCalls++
                File(outputPath).writeText("audio")
            }
        },
        transcription = object : TranscriptionService {
            override suspend fun transcribe(audioPath: String) =
                Transcript(listOf(TranscriptSegment(0L, videoMs, "fala")))
        },
        videoEngine = object : VideoEngine {
            override suspend fun probe(path: String) = InputVideoInfo(path, videoMs, 1920, 1080, 30.0, true)
            override suspend fun extractAudio(videoPath: String, outputPath: String) = Unit
            override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long) = emptyList<String>()
            override suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit) = Unit
            override suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long) =
                FocusTrack(listOf(FocusPoint(startMs, 0.5f, 0.5f, 1f, 1f)), TrackingMethod.STATIC_CENTER)
            override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) = Unit
            override suspend fun isAlreadyTargetFormat(path: String, target: com.shortsfactory.domain.model.ResolutionPreset, fps: Int) = false
        },
        audioWorkDir = workDir
    )

    private suspend fun run(preset: String, max: Int = 5) =
        pipeline().analyze(input.absolutePath, GenerationConfig(preset, max)) { updates += it }

    @Test
    fun `ai preset no longer returns overlapping candidates`() = runTest {
        candidates = listOf(
            cand(0, 40_000, 0.9f, "A"),
            cand(20_000, 60_000, 0.8f, "B"),
            cand(70_000, 100_000, 0.7f, "C")
        )
        val outcome = run("ai") as AnalysisOutcome.Success
        val picked = outcome.selected.sortedBy { it.startMs }
        assertEquals(2, picked.size)
        picked.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
    }

    @Test
    fun `candidate shorter than the minimum never reaches the output`() = runTest {
        candidates = listOf(cand(0, 1_500, 0.9f, "curto"), cand(10_000, 30_000, 0.5f, "ok"))
        val outcome = run("30s") as AnalysisOutcome.Success
        assertEquals(listOf("ok"), outcome.selected.map { it.title })
    }

    @Test
    fun `ai suggested duration is applied as ceiling`() = runTest {
        suggested = 30_000L
        candidates = listOf(cand(0, 50_000, 0.9f, "longo"), cand(60_000, 80_000, 0.5f, "curto"))
        val outcome = run("ai") as AnalysisOutcome.Success
        assertEquals(listOf("curto"), outcome.selected.map { it.title })
    }

    @Test
    fun `selection summary is reported in the stage message`() = runTest {
        candidates = listOf(cand(0, 20_000, 0.9f, "A"), cand(10_000, 30_000, 0.8f, "B"))
        run("30s")
        val message = updates.last { it.stage == PipelineStage.CandidateSelection && it.state == StageState.COMPLETED }.message!!
        assertTrue(message, message.contains("1 selecionados"))
        assertTrue(message, message.contains("sobreposto"))
    }

    @Test
    fun `nothing selectable fails the selection stage without retry`() = runTest {
        candidates = listOf(cand(0, 50_000, 0.9f, "longo demais para 15s"))
        val outcome = run("15s") as AnalysisOutcome.Failed
        assertFalse(outcome.retryable)
        assertTrue(outcome.message, outcome.message.contains("Nenhum candidato atende"))
        assertEquals(StageState.FAILED, updates.last { it.stage == PipelineStage.CandidateSelection }.state)
        assertEquals(StageState.CANCELLED, updates.last { it.stage == PipelineStage.SubtitleGeneration }.state)
        assertFalse(workDir.exists())
    }

    @Test
    fun `unknown preset fails at VideoInput before audio extraction`() = runTest {
        candidates = listOf(cand(0, 20_000, 0.9f, "A"))
        val outcome = run("120s") as AnalysisOutcome.Failed
        assertTrue(outcome.message, outcome.message.contains("120s"))
        assertFalse(outcome.retryable)
        assertEquals(StageState.FAILED, updates.last { it.stage == PipelineStage.VideoInput }.state)
        assertEquals(0, extractCalls)
    }
}
