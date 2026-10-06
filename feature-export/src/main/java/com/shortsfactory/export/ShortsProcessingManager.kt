package com.shortsfactory.export

import android.content.Context
import android.util.Log
import com.shortsfactory.data.local.entity.ExportEntity
import com.shortsfactory.data.repository.CandidateArtifactsCodec
import com.shortsfactory.data.repository.ExportBatchRepository
import com.shortsfactory.data.repository.ExportRepository
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.TranscriptRepository
import com.shortsfactory.domain.export.ExportBatchResult
import com.shortsfactory.domain.export.ExportBatchState
import com.shortsfactory.domain.export.ExportFileNaming
import com.shortsfactory.domain.export.ExportOutputValidator
import com.shortsfactory.domain.model.BatchExportProgress
import com.shortsfactory.domain.model.ExportPlatform
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.model.SubtitleStyle
import com.shortsfactory.domain.pipeline.ClipSpec
import com.shortsfactory.domain.pipeline.SubtitleTiming
import com.shortsfactory.domain.pipeline.VideoEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/** Processa a exportação em lote dos Shorts de um projeto. */
class ShortsProcessingManager @Inject constructor(
    private val videoEngine: VideoEngine,
    private val projectRepository: ProjectRepository,
    private val shortRepository: ShortRepository,
    private val transcriptRepository: TranscriptRepository,
    private val exportRepository: ExportRepository,
    private val exportBatchRepository: ExportBatchRepository,
    private val application: Context
) {

    /**
     * Exporta em lote. Cancelar o coroutine que chama este método (WorkManager, `Job`) é a ÚNICA forma de
     * cancelar: o processo ffmpeg do item em andamento morre junto e o item fica `cancelled` (nunca `failed`).
     *
     * @param cancelledByUser distingue cancelamento do usuário de parada do sistema (restrições, preempção):
     *   só o primeiro grava `cancelled`; a parada do sistema deixa o item `queued`, pois o WorkManager reagenda.
     */
    suspend fun exportBatch(
        projectId: Long,
        platforms: List<String>,
        quality: String,
        resolution: String,
        fps: Int,
        subtitleStyle: String = SubtitleStyle.Creator.key,
        cancelledByUser: () -> Boolean = { true },
        onProgress: (BatchExportProgress) -> Unit
    ): ExportBatchResult {
        val project = projectRepository.getById(projectId)
            ?: return ExportBatchResult(0, 0, 0, "Projeto não encontrado.")
        val candidates = shortRepository.getByProject(projectId)
        if (candidates.isEmpty()) return ExportBatchResult(0, 0, 0, "O projeto não tem Shorts para exportar.")

        val platformKeys = platforms.filter { key -> ExportPlatform.entries.any { it.key == key } }
            .ifEmpty { listOf(ExportPlatform.YOUTUBE.key) }
        val platformKey = platformKeys.joinToString(",")
        val selectedQuality = ExportQuality.entries.firstOrNull {
            it.label == quality || it.name.equals(quality, ignoreCase = true)
        } ?: ExportQuality.Normal
        val selectedResolution = ResolutionPreset.ALL.firstOrNull { it.label == resolution }
            ?: ResolutionPreset.FULL_HD
        val safeFps = fps.coerceIn(1, 120)
        val targetWidth = selectedResolution.width
        val targetHeight = selectedResolution.height
        val outputDir = File(application.filesDir, ExportFileNaming.directory(projectId)).apply { mkdirs() }
        check(outputDir.usableSpace >= MIN_FREE_SPACE_BYTES) {
            "Não há espaço livre suficiente para exportar os Shorts."
        }

        val style = resolveSubtitleStyle(subtitleStyle)
        val total = candidates.size
        var completed = 0
        var doneCount = 0
        var failedCount = 0
        val batchId = exportBatchRepository.start(projectId, total)
        onProgress(BatchExportProgress(total, 0, 0f, isRunning = true))

        try {
            for (candidate in candidates) {
                currentCoroutineContext().ensureActive()

                val validInterval = candidate.startMs >= 0L &&
                    candidate.endMs > candidate.startMs &&
                    (project.videoDurationMs <= 0L || candidate.endMs <= project.videoDurationMs)
                if (!validInterval) {
                    shortRepository.updateExportProgress(candidate.id, "failed", 0f, "Intervalo de vídeo inválido.")
                    completed++
                    failedCount++
                    exportBatchRepository.record(batchId, projectId, doneCount, failedCount, 0, ExportBatchState.RUNNING)
                    onProgress(BatchExportProgress(total, completed, 0f, isRunning = true))
                    continue
                }

                val fingerprint = ExportFileNaming.fingerprint(
                    candidate.startMs, candidate.endMs, candidate.intervalVersion,
                    platformKey, selectedQuality.name, selectedResolution.label, safeFps, style.styleKey
                )
                val outputFile = File(
                    outputDir,
                    ExportFileNaming.fileName(
                        candidate.id, platformKey, selectedQuality.name, selectedResolution.label, safeFps, fingerprint
                    )
                )
                val partFile = File(outputDir, ExportFileNaming.partName(outputFile.name))
                val outputPath = outputFile.absolutePath
                val existing = exportRepository.getLatestForShort(
                    projectId = projectId,
                    shortId = candidate.id,
                    platform = platformKey,
                    quality = selectedQuality.name,
                    resolution = selectedResolution.label,
                    fps = safeFps
                )
                if (existing?.status == "done" && existing.outputPath == outputPath && outputFile.isFile && outputFile.length() > 0L) {
                    // O fingerprint está no nome do arquivo: nome igual = intervalo e parâmetros iguais.
                    val reusable = try {
                        ExportOutputValidator.validate(
                            videoEngine.probe(outputPath), targetWidth, targetHeight, candidate.endMs - candidate.startMs
                        ) == null
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.w(TAG, "Export existente de ${candidate.title} não pôde ser validado; será regerado.", e)
                        false
                    }
                    if (reusable) {
                        shortRepository.updateExportState(candidate.id, outputPath, "done")
                        completed++
                        doneCount++
                        exportBatchRepository.record(batchId, projectId, doneCount, failedCount, 0, ExportBatchState.RUNNING)
                        onProgress(BatchExportProgress(total, completed, 1f, isRunning = true))
                        continue
                    }
                    outputFile.delete()
                }

                val exportId = existing?.id ?: exportRepository.insert(
                    ExportEntity(
                        projectId = projectId,
                        shortId = candidate.id,
                        platform = platformKey,
                        quality = selectedQuality.name,
                        resolution = selectedResolution.label,
                        fps = safeFps,
                        status = "queued"
                    )
                )

                try {
                    partFile.delete()
                    exportRepository.markQueued(exportId)
                    exportRepository.markRunning(exportId)
                    shortRepository.updateExportProgress(candidate.id, "processing", 0f)
                    // Legendas persistidas (Fase 6; `[]` válido = sem fala); transcript só se ausentes/corrompidas.
                    val subtitles = CandidateArtifactsCodec.decodeSubtitles(candidate.subtitlesJson)
                        ?: transcriptRepository.get(projectId)?.segments
                            ?.let { SubtitleTiming.fromTranscript(it, candidate.startMs, candidate.endMs) }
                            .orEmpty()
                    // Trilha persistida (Fase 6) tem prioridade; recalcula só se ausente/corrompida.
                    // Falha na trilha de foco não derruba o export (foco central); cancelamento NUNCA é engolido.
                    val focusTrack = CandidateArtifactsCodec.decodeFocusTrack(candidate.focusTrackJson) ?: try {
                        videoEngine.detectFocusTrack(project.videoUri, candidate.startMs, candidate.endMs)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.w(TAG, "Trilha de foco indisponível para ${candidate.title}; usando foco central.", e)
                        null
                    }
                    coroutineScope {
                        val progressUpdates = Channel<Float>(Channel.CONFLATED)
                        launch {
                            var lastWriteMs = 0L
                            for (value in progressUpdates) {
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastWriteMs >= PROGRESS_WRITE_INTERVAL_MS) {
                                    lastWriteMs = nowMs
                                    exportRepository.updateProgress(exportId, value)
                                }
                            }
                        }
                        try {
                            videoEngine.processClip(
                                ClipSpec(
                                    inputPath = project.videoUri,
                                    outputPath = partFile.absolutePath,
                                    startMs = candidate.startMs,
                                    endMs = candidate.endMs,
                                    targetWidth = targetWidth,
                                    targetHeight = targetHeight,
                                    fps = safeFps,
                                    bitrateBps = selectedQuality.videoBitrateBps,
                                    focusTrack = focusTrack,
                                    subtitles = subtitles,
                                    subtitleStyle = style
                                )
                            ) { progress ->
                                val clamped = progress.coerceIn(0f, 1f)
                                progressUpdates.trySend(clamped)
                                onProgress(BatchExportProgress(total, completed, clamped, isRunning = true))
                            }
                        } finally {
                            progressUpdates.close()
                        }
                    }
                    check(partFile.isFile && partFile.length() > 0L) {
                        "O FFmpeg não gerou um arquivo de saída válido."
                    }
                    val outputInfo = videoEngine.probe(partFile.absolutePath)
                    ExportOutputValidator.validate(outputInfo, targetWidth, targetHeight, candidate.endMs - candidate.startMs)
                        ?.let { error(it) }
                    Files.move(partFile.toPath(), outputFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    exportRepository.markDone(exportId, outputPath)
                    shortRepository.updateExportState(candidate.id, outputPath, "done")
                    completed++
                    doneCount++
                    exportBatchRepository.record(batchId, projectId, doneCount, failedCount, 0, ExportBatchState.RUNNING)
                    onProgress(BatchExportProgress(total, completed, 0f, isRunning = true))
                } catch (e: Exception) {
                    // "Parado" = o coroutine dono foi cancelado, ou um CancellationException (ex.: FfmpegCancelledException).
                    // Vale também para exceções comuns lançadas por causa do cancelamento (ex.: IOException do processo
                    // morto). Exceção: TimeoutCancellationException com o coroutine ativo é falha do item.
                    val stopped = !currentCoroutineContext().isActive ||
                        (e is CancellationException && e !is TimeoutCancellationException)
                    if (stopped) {
                        // O coroutine já está cancelado: gravar o estado exige NonCancellable, senão as chamadas
                        // suspend (Room) lançam de novo e o item ficaria "processing" para sempre.
                        withContext(NonCancellable) {
                            if (cancelledByUser()) {
                                exportRepository.markCancelled(exportId)
                                shortRepository.updateExportProgress(candidate.id, "cancelled", 0f, "Exportação cancelada.")
                            } else {
                                exportRepository.markQueued(exportId)
                                shortRepository.updateExportProgress(candidate.id, "queued", 0f)
                            }
                            partFile.delete()
                            onProgress(BatchExportProgress(total, completed, 0f, isRunning = false))
                        }
                        throw if (e is CancellationException) e else CancellationException("Exportação cancelada.", e)
                    }
                    exportRepository.markFailed(exportId, e.message ?: "Falha ao gerar o arquivo.")
                    shortRepository.updateExportProgress(candidate.id, "failed", 0f, e.message)
                    partFile.delete()
                    Log.w(TAG, "Falha ao exportar ${candidate.title}", e)
                    completed++
                    failedCount++
                    exportBatchRepository.record(batchId, projectId, doneCount, failedCount, 0, ExportBatchState.RUNNING)
                    onProgress(BatchExportProgress(total, completed, 0f, isRunning = true))
                }
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable) {
                exportBatchRepository.record(
                    batchId, projectId, doneCount, failedCount, total - doneCount - failedCount,
                    if (cancelledByUser()) ExportBatchState.CANCELLED else ExportBatchState.QUEUED
                )
            }
            throw ce
        } catch (e: Exception) {
            withContext(NonCancellable) {
                exportBatchRepository.record(batchId, projectId, doneCount, failedCount, 0, ExportBatchState.FAILED)
            }
            throw e
        }
        exportBatchRepository.record(
            batchId, projectId, doneCount, failedCount, 0, ExportBatchState.resolve(total, doneCount, failedCount)
        )

        onProgress(
            BatchExportProgress(
                total = total,
                current = completed,
                currentProgress = if (completed == total) 1f else 0f,
                isRunning = false
            )
        )
        return ExportBatchResult(total, doneCount, failedCount)
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
        private const val PROGRESS_WRITE_INTERVAL_MS = 1_000L
        private const val MIN_FREE_SPACE_BYTES = 100L * 1024L * 1024L
    }
}
