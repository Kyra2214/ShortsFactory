package com.shortsfactory.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shortsfactory.data.repository.AIAnalysisRepository
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.TranscriptRepository
import com.shortsfactory.domain.pipeline.AnalysisOutcome
import com.shortsfactory.domain.pipeline.MediaAnalysisPipeline
import com.shortsfactory.domain.pipeline.MediaMetadata
import com.shortsfactory.domain.pipeline.MediaValidator
import com.shortsfactory.domain.pipeline.PipelineStage
import com.shortsfactory.domain.pipeline.StageProgress
import com.shortsfactory.domain.pipeline.StageState
import com.shortsfactory.domain.pipeline.VideoEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val projectRepository: ProjectRepository,
    private val shortRepository: ShortRepository,
    private val transcriptRepository: TranscriptRepository,
    private val aiAnalysisRepository: AIAnalysisRepository,
    private val videoEngine: VideoEngine,
    private val pipelineFactory: AnalysisPipelineFactory
) : CoroutineWorker(appContext, workerParams) {

    private var lastProgress = 0f

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(WorkKeys.PROJECT_ID, -1L)
        val preset = inputData.getString(WorkKeys.PRESET).orEmpty().ifBlank { "30s" }
        val project = projectRepository.getById(projectId)
            ?: return Result.failure(workDataOf(WorkKeys.ERROR to "Projeto não encontrado."))

        val info = runCatching { videoEngine.probe(project.videoUri) }.getOrElse { error ->
            projectRepository.updateAnalysisState(projectId, "failed", 0f, error.message)
            return Result.failure(workDataOf(WorkKeys.ERROR to (error.message ?: "Falha ao ler a mídia.")))
        }
        val validation = MediaValidator.validate(
            MediaMetadata(
                path = project.videoUri,
                sizeBytes = project.videoSizeBytes,
                durationMs = info.durationMs,
                width = info.width,
                height = info.height,
                hasAudio = info.hasAudio
            )
        )
        if (!validation.valid) {
            projectRepository.updateAnalysisState(projectId, "failed", 0f, validation.message)
            return Result.failure(workDataOf(WorkKeys.ERROR to validation.message))
        }

        projectRepository.updateAnalysisState(projectId, "running", 0f)
        val pipeline: MediaAnalysisPipeline = pipelineFactory.create()
        return try {
            val outcome = pipeline.analyze(
                videoPath = project.videoUri,
                config = com.shortsfactory.domain.pipeline.GenerationConfig(preset = preset, maxCandidates = 12),
                onStageUpdate = { progress -> persistProgress(projectId, progress) }
            )
            when (outcome) {
                is AnalysisOutcome.Success -> {
                    transcriptRepository.save(projectId, outcome.transcript)
                    aiAnalysisRepository.save(projectId, "configured", outcome.result)
                    shortRepository.insertCandidates(projectId, outcome.selected)
                    projectRepository.updateAnalysisState(projectId, "done", 1f)
                    Result.success(workDataOf(WorkKeys.PROJECT_ID to projectId, WorkKeys.PROGRESS to 1f))
                }
                is AnalysisOutcome.Cancelled -> {
                    projectRepository.updateAnalysisState(projectId, "cancelled", currentProgress())
                    throw CancellationException("Análise cancelada.")
                }
                is AnalysisOutcome.Failed -> {
                    val canRetry = runAttemptCount < MAX_RETRIES && isTransient(outcome.message)
                    if (canRetry) {
                        projectRepository.updateAnalysisState(projectId, "queued", currentProgress(), outcome.message)
                        Result.retry()
                    } else {
                        projectRepository.updateAnalysisState(projectId, "failed", currentProgress(), outcome.message)
                        Result.failure(workDataOf(WorkKeys.ERROR to outcome.message))
                    }
                }
            }
        } catch (ce: CancellationException) {
            videoEngine.cancel()
            projectRepository.updateAnalysisState(projectId, "cancelled", currentProgress(), "Análise cancelada.")
            throw ce
        } catch (error: Exception) {
            val message = error.message ?: "Falha inesperada na análise."
            if (runAttemptCount < MAX_RETRIES && isTransient(message)) {
                projectRepository.updateAnalysisState(projectId, "queued", currentProgress(), message)
                Result.retry()
            } else {
                projectRepository.updateAnalysisState(projectId, "failed", currentProgress(), message)
                Result.failure(workDataOf(WorkKeys.ERROR to message))
            }
        }
    }


    private suspend fun persistProgress(projectId: Long, stage: StageProgress) {
        val stageCount = PipelineStage.values().size.toFloat()
        val stageProgress = when (stage.state) {
            StageState.COMPLETED -> 1f
            StageState.FAILED, StageState.CANCELLED -> stage.progress
            else -> stage.progress
        }
        val total = ((stage.stage.ordinal + stageProgress) / stageCount).coerceIn(0f, 1f)
        lastProgress = total
        projectRepository.updateAnalysisState(
            id = projectId,
            status = when (stage.state) {
                StageState.FAILED -> "failed"
                StageState.CANCELLED -> "cancelled"
                else -> "running"
            },
            progress = total,
            error = stage.message
        )
        setProgress(
            Data.Builder()
                .putLong(WorkKeys.PROJECT_ID, projectId)
                .putString(WorkKeys.STAGE, stage.stage.name)
                .putFloat(WorkKeys.PROGRESS, total)
                .putString(WorkKeys.MESSAGE, stage.message)
                .build()
        )
    }

    private fun currentProgress(): Float = lastProgress

    private fun isTransient(message: String): Boolean {
        val normalized = message.lowercase()
        return listOf("timeout", "indisponível", "http 408", "http 429", "http 500", "http 502", "http 503", "http 504")
            .any(normalized::contains)
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}

