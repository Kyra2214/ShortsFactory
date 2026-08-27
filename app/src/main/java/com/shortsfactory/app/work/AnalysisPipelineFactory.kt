package com.shortsfactory.app.work

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.pipeline.AudioExtractorService
import com.shortsfactory.domain.pipeline.CandidateSelector
import com.shortsfactory.domain.pipeline.MediaAnalysisPipeline
import com.shortsfactory.domain.pipeline.TranscriptionService
import com.shortsfactory.domain.pipeline.VideoEngine
import javax.inject.Inject

class AnalysisPipelineFactory @Inject constructor(
    private val aiProvider: AIProvider,
    private val videoEngine: VideoEngine,
    private val transcriptionService: TranscriptionService
) {
    fun create(): MediaAnalysisPipeline = MediaAnalysisPipeline(
        aiProvider = aiProvider,
        candidateSelector = CandidateSelector(),
        audioExtractor = object : AudioExtractorService {
            override suspend fun extract(videoPath: String, outputPath: String) {
                videoEngine.extractAudio(videoPath, outputPath)
            }
        },
        transcription = transcriptionService
    )
}
