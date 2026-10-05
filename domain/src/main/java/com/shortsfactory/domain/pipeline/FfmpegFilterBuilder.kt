package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.SubtitleSegment
import java.util.Locale

/** Monta as cadeias de filtros do FFmpeg em Kotlin puro (testável na JVM). */
object FfmpegFilterBuilder {

    /**
     * Cadeia `-vf` do clipe: `crop` (com foco, se houver trilha) → `scale` → `fps` → [extraFilters].
     * Tempos da trilha são absolutos; a conversão para tempo relativo ao clipe é feita por [FocusCropExpression].
     */
    fun videoFilter(spec: ClipSpec, extraFilters: List<String> = emptyList()): String {
        require(spec.targetWidth > 0 && spec.targetHeight > 0) { "A resolução alvo deve ser positiva." }
        require(spec.fps in 1..120) { "O FPS deve estar entre 1 e 120." }
        val durationMs = spec.endMs - spec.startMs
        require(durationMs > 0L) { "O intervalo do clipe deve ser positivo." }

        val crop = FocusCropExpression.cropFilter(
            targetWidth = spec.targetWidth,
            targetHeight = spec.targetHeight,
            track = spec.focusTrack,
            clipStartMs = spec.startMs,
            clipDurationMs = durationMs
        )
        val parts = mutableListOf(crop, "scale=${spec.targetWidth}:${spec.targetHeight}", "fps=${spec.fps}")
        parts += extraFilters.filter { it.isNotBlank() }
        return parts.joinToString(",")
    }

    /**
     * Grafo `-filter_complex` para legendas por imagem (o binário não tem `drawtext`/libass; `overlay` existe).
     * Entrada 0 é o vídeo; a entrada `i+1` é o PNG da legenda `subtitles[i]` (tempos relativos ao clipe).
     * Cada PNG é centralizado na horizontal, com o centro vertical em `positionPercent` da altura, limitado ao quadro.
     * Retorna null sem legendas utilizáveis (usar [videoFilter] com `-vf`).
     */
    fun filterGraph(spec: ClipSpec): FilterGraph? {
        val relative = SubtitleTiming.toClipRelative(spec.subtitles, spec.startMs, spec.endMs)
        if (relative.isEmpty()) return null
        val position = spec.subtitleStyle.positionPercent
        val graph = StringBuilder("[0:v]").append(videoFilter(spec)).append("[b0]")
        relative.forEachIndexed { i, seg ->
            graph.append(";[b$i][${i + 1}:v]overlay=x=(W-w)/2:y=min(H-h\\,H*$position/100-h/2)")
                .append(":enable='between(t\\,${seconds(seg.startMs)}\\,${seconds(seg.endMs)})'")
                .append("[b${i + 1}]")
        }
        return FilterGraph(graph.toString(), "b${relative.size}", relative)
    }

    /** [subtitles] em tempo relativo ao clipe, na ordem das entradas de imagem (1..n). */
    data class FilterGraph(val filterComplex: String, val outputLabel: String, val subtitles: List<SubtitleSegment>)

    private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms / 1000.0)
}
