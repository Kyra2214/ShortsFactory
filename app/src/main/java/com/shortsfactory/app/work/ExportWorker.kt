package com.shortsfactory.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
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

        return try {
            processingManager.exportBatch(
                projectId = projectId,
                platforms = platforms,
                quality = quality,
                resolution = resolution,
                fps = fps,
                onProgress = { progress -> persistProgress(progress) }
            )
            Result.success(workDataOf(WorkKeys.PROJECT_ID to projectId, WorkKeys.PROGRESS to 1f))
        } catch (ce: CancellationException) {
            processingManager.cancel()
            throw ce
        } catch (error: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(WorkKeys.ERROR to (error.message ?: "Falha na exportação.")))
        }
    }


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
