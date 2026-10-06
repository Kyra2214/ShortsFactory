package com.shortsfactory.domain.export

import com.shortsfactory.domain.model.ExportPlatform

/** Entrada do planejamento: origem, trecho do corte e plataformas escolhidas. */
data class ExportPlanRequest(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sourceFps: Int? = null,
    val clipDurationSec: Double,
    val platforms: List<ExportPlatform>
)

/** Item do plano: como exportar para uma plataforma. */
data class PlatformExportPlan(
    val profile: PlatformProfile,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateBps: Long,
    /** Verdadeiro quando a saída ficou menor que o perfil porque a origem não permite ampliar. */
    val reducedBySource: Boolean,
    /** Verdadeiro quando o corte excede a duração máxima do perfil. */
    val exceedsMaxDuration: Boolean,
    /** Plataformas com a mesma chave compartilham um único arquivo. */
    val fileKey: String
) {
    val platform: ExportPlatform get() = profile.platform
    val resolutionLabel: String get() = "${width}x$height"
}

/** Grupo de plataformas que usam o mesmo arquivo de saída. */
data class ExportFileGroup(val fileKey: String, val width: Int, val height: Int, val fps: Int, val videoBitrateBps: Long, val platforms: List<ExportPlatform>)

data class ExportPlan(val items: List<PlatformExportPlan>) {
    val fileGroups: List<ExportFileGroup>
        get() = items.groupBy { it.fileKey }.map { (key, list) ->
            val first = list.first()
            ExportFileGroup(key, first.width, first.height, first.fps, first.videoBitrateBps, list.map { it.platform })
        }
    val durationWarnings: List<ExportPlatform> get() = items.filter { it.exceedsMaxDuration }.map { it.platform }
}

/** Planejador puro (sem Android, sem IO): decide parâmetros finais por plataforma. */
object ExportPlanner {
    private const val ASPECT_W = 9
    private const val ASPECT_H = 16

    fun plan(request: ExportPlanRequest, profiles: (ExportPlatform) -> PlatformProfile = PlatformProfiles::forPlatform): ExportPlan {
        require(request.sourceWidth > 0 && request.sourceHeight > 0) { "Origem inválida." }
        require(request.clipDurationSec > 0.0) { "Duração do corte inválida." }
        val cropHeight = minOf(request.sourceHeight, request.sourceWidth * ASPECT_H / ASPECT_W)
        val items = request.platforms.distinct().map { platform ->
            val profile = profiles(platform)
            val height = evenFloor(minOf(profile.height, cropHeight)).coerceAtLeast(2)
            val width = evenFloor(height * ASPECT_W / ASPECT_H).coerceAtLeast(2)
            val fps = request.sourceFps?.takeIf { it > 0 }?.let { minOf(it, profile.fps) } ?: profile.fps
            val reduced = height < profile.height
            PlatformExportPlan(
                profile = profile,
                width = width,
                height = height,
                fps = fps,
                videoBitrateBps = profile.videoBitrateBps,
                reducedBySource = reduced,
                exceedsMaxDuration = request.clipDurationSec > profile.maxDurationSec,
                fileKey = "${width}x$height|$fps|${profile.videoBitrateBps}"
            )
        }
        return ExportPlan(items)
    }

    private fun evenFloor(v: Int): Int = v - (v % 2)
}
