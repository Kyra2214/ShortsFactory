package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaPipelineExecutionTest {

    @Test
    fun `all declared stages execute real work and enrich candidates`() = runTest {
        val input = File.createTempFile("shorts-factory-", ".mp4").apply { writeText("fixture") }
        try {
            val engine = FakeVideoEngine()
            val candidate = ShortCandidate(
                score = 0.9f,
                startMs = 500L,
                endMs = 4_500L,
                title = "hook",
                hook = "hook",
                topic = "topic",
                reason = "reason"
            )
            val pipeline = MediaAnalysisPipeline(
                aiProvider = FakeAiProvider(candidate),
                candidateSelector = CandidateSelector(),
                audioExtractor = FakeAudioExtractor(),
                transcription = FakeTranscription(),
                videoEngine = engine
            )
            val updates = mutableListOf<StageProgress>()

            val outcome = pipeline.analyze(
                input.absolutePath,
                GenerationConfig("30s", 3)
            ) { updates += it }

            assertTrue(outcome is AnalysisOutcome.Success)
            val success = outcome as AnalysisOutcome.Success
            assertEquals(1, success.selected.size)
            assertTrue(success.selected.single().subtitles.isNotEmpty())
            assertEquals(TrackingMethod.FACE_TRACKING, success.selected.single().focusTrack?.method)
            assertEquals(1, engine.detectCalls)
            assertTrue(updates.any { it.stage == PipelineStage.VideoInput && it.state == StageState.COMPLETED })
            assertTrue(updates.any { it.stage == PipelineStage.SubtitleGeneration && it.state == StageState.COMPLETED })
            assertTrue(updates.any { it.stage == PipelineStage.FocusTracking && it.state == StageState.COMPLETED })
        } finally {
            input.delete()
        }
    }

    @Test
    fun `missing input fails at video input instead of reporting completed work`() = runTest {
        val updates = mutableListOf<StageProgress>()
        val outcome = MediaAnalysisPipeline(
            FakeAiProvider(ShortCandidate(0.9f, 0L, 1_000L, "t", "h", "topic", "reason")),
            CandidateSelector(),
            FakeAudioExtractor(),
            FakeTranscription()
        ).analyze("/definitely/missing/video.mp4", GenerationConfig("30s")) { updates += it }

        assertTrue(outcome is AnalysisOutcome.Failed)
        assertTrue(updates.any { it.stage == PipelineStage.VideoInput && it.state == StageState.FAILED })
        assertTrue(updates.none { it.stage == PipelineStage.AudioExtraction && it.state == StageState.PROCESSING })
    }

    private class FakeAudioExtractor : AudioExtractorService {
        override suspend fun extract(videoPath: String, outputPath: String) {
            File(outputPath).writeText("audio")
        }
    }

    private class FakeTranscription : TranscriptionService {
        override suspend fun transcribe(audioPath: String) =
            Transcript(
                listOf(
                    TranscriptSegment(0L, 1_200L, "primeiro trecho"),
                    TranscriptSegment(1_200L, 2_700L, "segundo trecho"),
                    TranscriptSegment(2_700L, 6_000L, "terceiro trecho")
                )
            )
    }

    private class FakeAiProvider(
        private val candidate: ShortCandidate
    ) : AIProvider {
        override val providerName = "fake"
        override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint) =
            AIAnalysisResult("fixture", "fixture", listOf(candidate), 2_000L)
        override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> = emptyList()
        override suspend fun analyzeTrends(query: String, region: String): String = ""
        override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String = ""
    }

    private class FakeVideoEngine : VideoEngine {
        var detectCalls = 0
        override suspend fun probe(path: String) = InputVideoInfo(path, 10_000L, 1920, 1080, 30.0, true)
        override suspend fun extractAudio(videoPath: String, outputPath: String) = Unit
        override suspend fun splitAudio(audioPath: String, outputDir: String, chunkDurationMs: Long) = emptyList<String>()
        override suspend fun processClip(spec: ClipSpec, onProgress: (Float) -> Unit) = Unit
        override suspend fun detectFocusTrack(videoPath: String, startMs: Long, endMs: Long): FocusTrack {
            detectCalls++
            return FocusTrack(
                listOf(FocusPoint(startMs, 0.4f, 0.4f, 0.3f, 0.3f)),
                TrackingMethod.FACE_TRACKING
            )
        }
        override suspend fun extractFrame(videoPath: String, timeMs: Long, outputPath: String) = Unit
        override suspend fun isAlreadyTargetFormat(path: String, target: com.shortsfactory.domain.model.ResolutionPreset, fps: Int) = false
    }
}
