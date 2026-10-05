package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.SubtitleSegment
import com.shortsfactory.domain.model.TranscriptSegment

/**
 * Convenção: [SubtitleSegment] e [TranscriptSegment] usam tempo **absoluto** do vídeo de origem.
 * O FFmpeg roda o filtro com `t` relativo ao clipe (`-ss` antes de `-i`); [toClipRelative] faz a conversão.
 */
object SubtitleTiming {

    /** Segmentos de transcrição que cruzam [startMs, endMs], recortados ao intervalo (tempo absoluto). */
    fun fromTranscript(segments: List<TranscriptSegment>, startMs: Long, endMs: Long): List<SubtitleSegment> =
        segments.asSequence()
            .filter { it.endMs > startMs && it.startMs < endMs }
            .map {
                SubtitleSegment(
                    startMs = maxOf(it.startMs, startMs),
                    endMs = minOf(it.endMs, endMs),
                    words = it.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                )
            }
            .filter { it.endMs > it.startMs && it.words.isNotEmpty() }
            .toList()

    /** Recorta ao clipe, subtrai [clipStartMs] e descarta segmentos vazios, sem palavras ou fora do clipe. */
    fun toClipRelative(segments: List<SubtitleSegment>, clipStartMs: Long, clipEndMs: Long): List<SubtitleSegment> =
        segments.asSequence()
            .map {
                SubtitleSegment(
                    startMs = maxOf(it.startMs, clipStartMs) - clipStartMs,
                    endMs = minOf(it.endMs, clipEndMs) - clipStartMs,
                    words = it.words
                )
            }
            .filter { it.endMs > it.startMs && it.words.isNotEmpty() }
            .sortedBy { it.startMs }
            .toList()
}
