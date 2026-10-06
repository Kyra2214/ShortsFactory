package com.shortsfactory.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ProjectStore
import com.shortsfactory.domain.ai.isTransientFailure
import com.shortsfactory.domain.pipeline.AnalysisOutcome
import com.shortsfactory.domain.pipeline.MediaAnalysisPipeline
import com.shortsfactory.domain.pipeline.PipelineStage
import com.shortsfactory.domain.pipeline.StageProgress
import com.shortsfactory.domain.pipeline.StageState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val projectRepository: ProjectRepository,
    private val projectStore: ProjectStore,
    private val pipelineFactory: AnalysisPipelineFactory
) : CoroutineWorker(appContext, workerParams) {

    private var lastProgress = 0f

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(WorkKeys.PROJECT_ID, -1L)
        val preset = inputData.getString(WorkKeys.PRESET).orEmpty().ifBlank { "30s" }
        val project = projectRepository.getById(projectId)
            ?: return Result.failure(workDataOf(WorkKeys.ERROR to "Projeto não encontrado."))

        projectRepository.updateAnalysisState(projectId, "running", 0f)
        // Áudio e fragmentos temporários ficam em cacheDir/analysis/<projectId>/ e o pipeline apaga tudo no final.
        val pipeline: MediaAnalysisPipeline = pipelineFactory.create(
            java.io.File(applicationContext.cacheDir, "analysis/$projectId")
        )
        return try {
            val outcome = pipeline.analyze(
                videoPath = project.videoUri,
                config = com.shortsfactory.domain.pipeline.GenerationConfig(preset = preset, maxCandidates = 12),
                onStageUpdate = { progress -> persistProgress(projectId, progress) }
            )
            when (outcome) {
                is AnalysisOutcome.Success -> {
                    // Provedor/modelo que de fato responderam (não o "configurado").
                    val responder = listOfNotNull(outcome.result.provider, outcome.result.model).joinToString(" · ")
                    // Transcript + análise + candidatos + estado "done" numa única transação: ou grava tudo ou nada.
                    // Re-análise substitui os candidatos antigos em vez de duplicá-los.
                    projectStore.saveAnalysis(
                        projectId = projectId,
                        transcript = outcome.transcript,
                        provider = responder.ifBlank { "desconhecido" },
                        result = outcome.result,
                        selected = outcome.selected
                    )
                    Result.success(workDataOf(WorkKeys.PROJECT_ID to projectId, WorkKeys.PROGRESS to 1f))
                }
                is AnalysisOutcome.Cancelled -> {
                    // O estado é gravado uma única vez, no catch abaixo (em NonCancellable).
                    throw CancellationException("Análise cancelada.")
                }
                is AnalysisOutcome.Failed -> {
                    val canRetry = runAttemptCount < MAX_RETRIES && outcome.retryable
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
            // O processo ffmpeg morre com o coroutine (sem cancel() global). Gravar estado: NonCancellable.
            recordStop(projectId)
            throw ce
        } catch (error: Exception) {
            val message = error.message ?: "Falha inesperada na análise."
            if (runAttemptCount < MAX_RETRIES && error.isTransientFailure()) {
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

    /**
     * Grava o estado de uma parada. Cancelamento do usuário → "cancelled". Parada do sistema (restrições,
     * preempção) → "queued": o WorkManager reagenda, então marcar "cancelled" mentiria para a UI.
     */
    private suspend fun recordStop(projectId: Long) = withContext(NonCancellable) {
        if (cancelledByApp()) {
            projectRepository.updateAnalysisState(projectId, "cancelled", currentProgress(), "Análise cancelada.")
        } else {
            projectRepository.updateAnalysisState(
                projectId, "queued", currentProgress(), "Interrompida pelo sistema; será retomada."
            )
        }
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}

