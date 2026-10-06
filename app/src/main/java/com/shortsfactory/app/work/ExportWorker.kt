package com.shortsfactory.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shortsfactory.domain.model.BatchExportProgress
import com.shortsfactory.export.ShortsProcessingManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processingManager: ShortsProcessingManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(WorkKeys.PROJECT_ID, -1L)
        if (projectId <= 0L) return Result.failure(workDataOf(WorkKeys.ERROR to "Projeto inválido."))

        val platforms = inputData.getString(WorkKeys.PLATFORMS)
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val quality = inputData.getString(WorkKeys.QUALITY).orEmpty().ifBlank { "Normal" }
        val resolution = inputData.getString(WorkKeys.RESOLUTION).orEmpty().ifBlank { "1080 × 1920" }
        val fps = inputData.getInt(WorkKeys.FPS, 30)
        val subtitleStyle = inputData.getString(WorkKeys.SUBTITLE_STYLE).orEmpty().ifBlank { "creator" }

        // Serviço em primeiro plano; se o sistema não permitir iniciá-lo agora, segue sem ele.
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException e similares: a exportação continua como trabalho normal.
        }

        return try {
            val result = processingManager.exportBatch(
                projectId = projectId,
                platforms = platforms,
                quality = quality,
                resolution = resolution,
                fps = fps,
                subtitleStyle = subtitleStyle,
                cancelledByUser = { cancelledByApp() },
                onProgress = { progress -> persistProgress(progress) }
            )
            when {
                result.error != null -> Result.failure(workDataOf(WorkKeys.ERROR to result.error))
                result.allFailed -> Result.failure(
                    workDataOf(
                        WorkKeys.PROJECT_ID to projectId,
                        WorkKeys.TOTAL to result.total,
                        WorkKeys.DONE_COUNT to result.done,
                        WorkKeys.FAILED_COUNT to result.failed,
                        WorkKeys.ERROR to "Todas as ${result.total} exportações falharam."
                    )
                )
                // Parcial ou total: FAILED_COUNT > 0 informa quantas falharam.
                else -> Result.success(
                    workDataOf(
                        WorkKeys.PROJECT_ID to projectId,
                        WorkKeys.PROGRESS to 1f,
                        WorkKeys.TOTAL to result.total,
                        WorkKeys.DONE_COUNT to result.done,
                        WorkKeys.FAILED_COUNT to result.failed
                    )
                )
            }
        } catch (ce: CancellationException) {
            // O estado dos itens já foi gravado pelo manager (NonCancellable); o processo ffmpeg morre com o
            // coroutine. Nunca converter cancelamento em retry/failure.
            throw ce
        } catch (error: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(WorkKeys.ERROR to (error.message ?: "Falha na exportação.")))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = ExportNotification.foregroundInfo(applicationContext)

    private fun persistProgress(progress: BatchExportProgress) {
        val totalProgress = if (progress.total <= 0) {
            0f
        } else {
            ((progress.current + progress.currentProgress) / progress.total).coerceIn(0f, 1f)
        }
        setProgressAsync(
            Data.Builder()
                .putLong(WorkKeys.PROJECT_ID, inputData.getLong(WorkKeys.PROJECT_ID, -1L))
                .putFloat(WorkKeys.PROGRESS, totalProgress)
                .putInt(WorkKeys.CURRENT, progress.current)
                .putInt(WorkKeys.TOTAL, progress.total)
                .build()
        )
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}
