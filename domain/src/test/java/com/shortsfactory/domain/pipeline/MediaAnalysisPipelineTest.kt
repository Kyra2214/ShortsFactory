package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaAnalysisPipelineTest {

    // A pipeline valida a existência do arquivo de entrada; os testes precisam de um arquivo real.
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun videoPath(): String =
        temporaryFolder.newFile("source.mp4").apply { writeText("fixture") }.absolutePath

    @Test
    fun `success emits all stages and selects candidates`() = runTest {
        val updates = mutableListOf<StageProgress>()
        val candidate = candidate()
        val pipeline = MediaAnalysisPipeline(
            aiProvider = FakeAiProvider { _, _ -> AIAnalysisResult("Título", "Resumo", listOf(candidate)) },
            candidateSelector = CandidateSelector(),
            audioExtractor = object : AudioExtractorService {
                override suspend fun extract(videoPath: String, outputPath: String) = Unit
            },
            transcription = object : TranscriptionService {
                override suspend fun transcribe(audioPath: String) = Transcript(listOf(TranscriptSegment(0L, 30_000L, "fala")))
            }
        )

        val outcome = pipeline.analyze(videoPath(), GenerationConfig("30s")) { updates += it }

        assertTrue(outcome is AnalysisOutcome.Success)
        assertEquals(PipelineStage.values().toSet(), updates.map { it.stage }.toSet())
        assertTrue(updates.filter { it.state == StageState.COMPLETED }.isNotEmpty())
        assertEquals(StageState.COMPLETED, updates.last { it.stage == PipelineStage.FocusTracking }.state)
    }

    @Test
    fun `failure marks the active stage`() = runTest {
        val updates = mutableListOf<StageProgress>()
        val pipeline = MediaAnalysisPipeline(
            aiProvider = FakeAiProvider { _, _ -> error("provider unavailable") },
            candidateSelector = CandidateSelector(),
            audioExtractor = object : AudioExtractorService {
                override suspend fun extract(videoPath: String, outputPath: String) = Unit
            },
            transcription = object : TranscriptionService {
                override suspend fun transcribe(audioPath: String) = Transcript(listOf(TranscriptSegment(0L, 30_000L, "fala")))
            }
        )

        val outcome = pipeline.analyze(videoPath(), GenerationConfig("30s")) { updates += it }

        assertTrue(outcome is AnalysisOutcome.Failed)
        val failed = updates.last { it.stage == PipelineStage.AIAnalysis }
        assertEquals(StageState.FAILED, failed.state)
        assertEquals("provider unavailable", failed.message)
    }

    @Test
    fun `cancellation reports the active stage`() = runTest {
        val updates = mutableListOf<StageProgress>()
        val pipeline = MediaAnalysisPipeline(
            aiProvider = FakeAiProvider { _, _ -> throw CancellationException("cancelled") },
            candidateSelector = CandidateSelector(),
            audioExtractor = object : AudioExtractorService {
                override suspend fun extract(videoPath: String, outputPath: String) = Unit
            },
            transcription = object : TranscriptionService {
                override suspend fun transcribe(audioPath: String) = Transcript(listOf(TranscriptSegment(0L, 30_000L, "fala")))
            }
        )

        val outcome = pipeline.analyze(videoPath(), GenerationConfig("30s")) { updates += it }

        assertEquals(AnalysisOutcome.Cancelled, outcome)
        assertEquals(StageState.CANCELLED, updates.last { it.stage == PipelineStage.AIAnalysis }.state)
    }

    private fun candidate() = ShortCandidate(
        score = 0.9f,
        startMs = 0L,
        endMs = 30_000L,
        title = "Candidato",
        hook = "Hook",
        topic = "Tema",
        reason = "Motivo"
    )

    private class FakeAiProvider(
        private val analyzeBlock: suspend (Transcript, GenerationSummaryHint) -> AIAnalysisResult
    ) : AIProvider {
        override val providerName: String = "fake"
        override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint) =
            analyzeBlock(transcript, hint)
        override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> = emptyList()
        override suspend fun analyzeTrends(query: String, region: String): String = ""
        override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String = ""
    }
}
