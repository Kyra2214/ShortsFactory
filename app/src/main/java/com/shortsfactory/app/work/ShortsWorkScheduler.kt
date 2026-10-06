package com.shortsfactory.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortsWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun enqueueAnalysis(projectId: Long, preset: String): Operation {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(
                workDataOf(
                    WorkKeys.PROJECT_ID to projectId,
                    WorkKeys.PRESET to preset
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(analysisTag(projectId))
            .build()

        return workManager.enqueueUniqueWork(
            analysisWorkName(projectId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueExport(
        projectId: Long,
        platforms: List<String>,
        quality: String,
        resolution: String,
        fps: Int,
        subtitleStyle: String
    ): Operation {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(
                workDataOf(
                    WorkKeys.PROJECT_ID to projectId,
                    WorkKeys.PLATFORMS to platforms.joinToString(","),
                    WorkKeys.QUALITY to quality,
                    WorkKeys.RESOLUTION to resolution,
                    WorkKeys.FPS to fps,
                    WorkKeys.SUBTITLE_STYLE to subtitleStyle
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .addTag(exportTag(projectId))
            .build()

        return workManager.enqueueUniqueWork(
            exportWorkName(projectId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAnalysis(projectId: Long) {
        workManager.cancelUniqueWork(analysisWorkName(projectId))
    }

    fun cancelExport(projectId: Long) {
        workManager.cancelUniqueWork(exportWorkName(projectId))
    }

    fun observeAnalysis(projectId: Long): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(analysisWorkName(projectId))

    fun observeExport(projectId: Long): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(exportWorkName(projectId))

    fun analysisWorkName(projectId: Long): String = "analysis:$projectId"
    fun exportWorkName(projectId: Long): String = "export:$projectId"
    fun analysisTag(projectId: Long): String = "analysis-tag:$projectId"
    fun exportTag(projectId: Long): String = "export-tag:$projectId"
}

object WorkKeys {
    const val PROJECT_ID = "project_id"
    const val SHORT_ID = "short_id"
    const val PRESET = "preset"
    const val EXPORT_ID = "export_id"
    const val PLATFORMS = "platforms"
    const val QUALITY = "quality"
    const val RESOLUTION = "resolution"
    const val FPS = "fps"
    const val SUBTITLE_STYLE = "subtitle_style"
    const val STAGE = "stage"
    const val PROGRESS = "progress"
    const val MESSAGE = "message"
    const val CURRENT = "current"
    const val TOTAL = "total"
    const val ERROR = "error"
    const val DONE_COUNT = "done_count"
    const val FAILED_COUNT = "failed_count"
}
