package com.shortsfactory.domain.editor

/** Trecho enviado ao leitor na prévia rápida: sempre dentro do vídeo e com duração mínima. */
object PreviewClipRange {
    const val MIN_DURATION_MS = 1_000L

    /** @return trecho ajustado ao vídeo, ou `null` se o vídeo não comporta o mínimo (ou a duração é desconhecida). */
    fun resolve(startMs: Long, endMs: Long, videoDurationMs: Long): TimeRange? {
        if (videoDurationMs < MIN_DURATION_MS) return null
        val start = startMs.coerceIn(0L, videoDurationMs - MIN_DURATION_MS)
        val end = endMs.coerceIn(start + MIN_DURATION_MS, videoDurationMs)
        return TimeRange(start, end)
    }
}
