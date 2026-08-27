package com.shortsfactory.export

import android.content.Context
import android.util.Log
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.repository.ExportRepository
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.SubtitleRepository
import com.shortsfactory.domain.model.BatchExportProgress
import com.shortsfactory.domain.model.ExportPlatform
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.pipeline.ClipSpec
import com.shortsfactory.domain.pipeline.VideoEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Processa a exportação em lote dos Shorts de um projeto. */
class ShortsProcessingManager @Inject constructor(
    private val videoEngine: VideoEngine,
    private val projectRepository: ProjectRepository,
    private val shortRepository: ShortRepository,
    private val subtitleRepository: SubtitleRepository,
    private val exportRepository: ExportRepository,
    private val application: Context
) {

    @Volatile private var cancelled = false

    suspend fun exportBatch(
        projectId: Long,
        platforms: List<String>,
        quality: String,
        resolution: String,
        fps: Int,
        onProgress: (BatchExportProgress) -> Unit
    ) {
        cancelled = false
        val project = projectRepository.getById(projectId) ?: return
        val candidates = shortRepository.getByProject(projectId)
        if (candidates.isEmpty()) return

        val platformKeys = platforms.filter { key -> ExportPlatform.entries.any { it.key == key } }
            .ifEmpty { listOf("shorts") }
        val selectedQuality = ExportQuality.entries.firstOrNull {
            it.label == quality || it.name.equals(quality, ignoreCase = true)
        } ?: ExportQuality.Normal
        val selectedResolution = ResolutionPreset.ALL.firstOrNull { it.label == resolution }
            ?: ResolutionPreset.FULL_HD
        val safeFps = fps.coerceIn(1, 120)
        val targetWidth = selectedResolution.width.takeIf { it > 0 } ?: 1080
        val targetHeight = selectedResolution.height.takeIf { it > 0 } ?: 1920
        val outputDir = File(application.filesDir, "exports").apply { mkdirs() }
        val total = candidates.size
        var completed = 0

        onProgress(BatchExportProgress(total, 0, 0f, isRunning = true))

        for ((index, candidate) in candidates.withIndex()) {
            if (cancelled) break
            coroutineContext.ensureActive()

            val validInterval = candidate.startMs >= 0L &&
                candidate.endMs > candidate.startMs &&
                (project.videoDurationMs <= 0L || candidate.endMs <= project.videoDurationMs)
            if (!validInterval) {
                shortRepository.updateExportState(candidate.id, null, "failed")
                completed++
                onProgress(BatchExportProgress(total, completed, 0f, isRunning = true))
                continue
            }

            val safeTitle = candidate.title.take(30)
                .replace(Regex("[^A-Za-z0-9 _-]"), "")
                .trim()
                .ifEmpty { "short_${index + 1}" }
            val outputPath = File(outputDir, "${safeTitle}_${index + 1}.mp4").absolutePath
            val exportId = exportRepository.insert(
                ExportEntity(
                    projectId = projectId,
                    platform = platformKeys.joinToString(","),
                    quality = selectedQuality.name,
                    resolution = selectedResolution.label,
                    fps = safeFps,
                    status = "running"
                )
            )
            shortRepository.updateExportState(candidate.id, null, "processing")

            try {
                val subtitles = subtitleRepository.getSegments(candidate.id)
                val style = resolveSubtitleStyle("creator")
                videoEngine.processClip(
                    ClipSpec(
                        inputPath = project.videoUri,
                        outputPath = outputPath,
                        startMs = candidate.startMs,
                        endMs = candidate.endMs,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        fps = safeFps,
                        bitrateBps = selectedQuality.videoBitrateBps,
                        focusTrack = null,
                        subtitles = subtitles,
                        subtitleStyle = style
                    )
                ) { progress ->
                    onProgress(BatchExportProgress(total, completed, progress.coerceIn(0f, 1f), isRunning = true))
                }
                exportRepository.updateResult(exportId, outputPath, "done")
                shortRepository.updateExportState(candidate.id, outputPath, "done")
            } catch (ce: CancellationException) {
                exportRepository.updateResult(exportId, null, "cancelled")
                shortRepository.updateExportState(candidate.id, null, "failed")
                throw ce
            } catch (e: Exception) {
                val status = if (cancelled) "cancelled" else "failed"
                exportRepository.updateResult(exportId, null, status)
                shortRepository.updateExportState(candidate.id, null, "failed")
                Log.w(TAG, "Falha ao exportar ${candidate.title}", e)
                if (cancelled) break
            } finally {
                completed++
                onProgress(BatchExportProgress(total, completed, 0f, isRunning = true))
            }
        }

        onProgress(
            BatchExportProgress(
                total = total,
                current = completed,
                currentProgress = if (completed == total) 1f else 0f,
                isRunning = false
            )
        )
    }

    fun cancel() {
        cancelled = true
        videoEngine.cancel()
    }

    private fun resolveSubtitleStyle(key: String): com.shortsfactory.domain.model.SubtitleStyleConfig {
        val style = com.shortsfactory.domain.model.SubtitleStyle.entries.firstOrNull { it.key == key }
            ?: com.shortsfactory.domain.model.SubtitleStyle.Creator
        return com.shortsfactory.domain.model.SubtitleStyleConfig(
            styleKey = style.key,
            fontSizePx = when (style) {
                com.shortsfactory.domain.model.SubtitleStyle.Creator -> 64
                com.shortsfactory.domain.model.SubtitleStyle.Impacto -> 56
                else -> 48
            },
            positionPercent = if (style == com.shortsfactory.domain.model.SubtitleStyle.Creator) 78.0 else 82.0
        )
    }

    companion object {
        private const val TAG = "ShortsProcessingManager"
    }
}
