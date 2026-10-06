package com.shortsfactory.export

import android.content.Context
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.domain.export.ExportFileNaming
import com.shortsfactory.domain.export.ExportOutputValidator
import com.shortsfactory.domain.pipeline.VideoEngine
import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * Prévia fiel: mesmo `processClip` e mesma montagem do [ShortsProcessingManager.assembleClipSpec] da exportação,
 * em 540x960. Cache em `cache/preview/<projectId>/` por fingerprint (intervalo, versão, estilo). Cancelar o
 * coroutine chamador mata o FFmpeg; o `.part` é sempre apagado.
 */
class ClipPreviewManager @Inject constructor(
    private val videoEngine: VideoEngine,
    private val projectRepository: ProjectRepository,
    private val shortRepository: ShortRepository,
    private val processingManager: ShortsProcessingManager,
    private val application: Context
) {

    /** @return caminho do MP4 da prévia (do cache ou recém-renderizado). */
    suspend fun render(shortId: Long, subtitleStyleKey: String, onProgress: (Float) -> Unit): String {
        val candidate = shortRepository.getById(shortId) ?: error("Short não encontrado.")
        val project = projectRepository.getById(candidate.projectId) ?: error("Projeto não encontrado.")
        require(
            candidate.startMs >= 0L && candidate.endMs > candidate.startMs &&
                (project.videoDurationMs <= 0L || candidate.endMs <= project.videoDurationMs)
        ) { "Intervalo de vídeo inválido." }

        val style = processingManager.resolveStyle(subtitleStyleKey)
        val fingerprint = ExportFileNaming.fingerprint(
            candidate.startMs, candidate.endMs, candidate.intervalVersion,
            PLATFORM, QUALITY, RESOLUTION, FPS, style.styleKey
        )
        val dir = File(application.cacheDir, "preview/${project.id}").apply { mkdirs() }
        val output = File(dir, ExportFileNaming.fileName(candidate.id, PLATFORM, QUALITY, RESOLUTION, FPS, fingerprint))
        val durationMs = candidate.endMs - candidate.startMs

        if (output.isFile && output.length() > 0L && isValid(output, durationMs)) {
            onProgress(1f)
            return output.absolutePath
        }
        output.delete()
        // Prévias antigas do mesmo Short (intervalo/estilo anteriores) deixam de ser úteis.
        val prefix = "${candidate.id}_"
        dir.listFiles()?.filter { it.name.startsWith(prefix) && it != output }?.forEach { it.delete() }

        check(dir.usableSpace >= MIN_FREE_SPACE_BYTES) { "Não há espaço livre suficiente para gerar a prévia." }
        val part = File(dir, ExportFileNaming.partName(output.name))
        part.delete()
        try {
            val spec = processingManager.assembleClipSpec(
                project, candidate, part.absolutePath, WIDTH, HEIGHT, FPS, BITRATE_BPS, style
            )
            videoEngine.processClip(spec) { onProgress(it.coerceIn(0f, 1f)) }
            check(part.isFile && part.length() > 0L) { "O FFmpeg não gerou um arquivo de prévia válido." }
            ExportOutputValidator.validate(videoEngine.probe(part.absolutePath), WIDTH, HEIGHT, durationMs)
                ?.let { error(it) }
            Files.move(part.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            part.delete()
        }
        onProgress(1f)
        return output.absolutePath
    }

    private suspend fun isValid(file: File, durationMs: Long): Boolean = try {
        ExportOutputValidator.validate(videoEngine.probe(file.absolutePath), WIDTH, HEIGHT, durationMs) == null
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        false
    }

    companion object {
        const val WIDTH = 540
        const val HEIGHT = 960
        private const val FPS = 30
        private const val BITRATE_BPS = 2_000_000L
        private const val PLATFORM = "preview"
        private const val QUALITY = "preview"
        private const val RESOLUTION = "540 × 960"
        private const val MIN_FREE_SPACE_BYTES = 50L * 1024L * 1024L
    }
}
