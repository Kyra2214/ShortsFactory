package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.Transcript

enum class DiscardReason(val label: String) {
    BLANK_TITLE("sem título"),
    MISSING_INTERVAL("início/fim ausentes"),
    INVALID_INTERVAL("intervalo inválido"),
    OUTSIDE_VIDEO("fora da duração do vídeo"),
    OUTSIDE_TRANSCRIPT("fora da região transcrita")
}

data class DiscardedCandidate(val title: String, val startMs: Long, val endMs: Long, val reason: DiscardReason)

data class SanitizedCandidates(val kept: List<ShortCandidate>, val discarded: List<DiscardedCandidate>) {
    /** Contagem por motivo, para a mensagem da etapa e para o log. */
    fun summary(): String {
        val head = "${kept.size} válidos, ${discarded.size} descartados"
        if (discarded.isEmpty()) return head
        val byReason = discarded.groupingBy { it.reason }.eachCount()
            .entries.joinToString(", ") { (reason, count) -> "${reason.label}: $count" }
        return "$head ($byReason)"
    }
}

/** A saída da IA é entrada não confiável: só candidatos reais e dentro do que foi transcrito passam. */
object AiResponseSanitizer {
    /** Folga entre o fim da fala e o candidato (a IA arredonda). */
    const val TRANSCRIPT_TOLERANCE_MS = 1_000L

    /** @param videoDurationMs 0 quando desconhecida (então a regra de duração do vídeo não se aplica). */
    fun sanitize(candidates: List<ShortCandidate>, videoDurationMs: Long, transcript: Transcript): SanitizedCandidates {
        val coverageStart = transcript.segments.minOfOrNull { it.startMs }
        val coverageEnd = transcript.segments.maxOfOrNull { it.endMs }
        val kept = mutableListOf<ShortCandidate>()
        val discarded = mutableListOf<DiscardedCandidate>()

        for (candidate in candidates) {
            val reason = when {
                candidate.title.isBlank() -> DiscardReason.BLANK_TITLE
                candidate.startMs == ShortCandidate.MISSING_MS || candidate.endMs == ShortCandidate.MISSING_MS ->
                    DiscardReason.MISSING_INTERVAL
                candidate.startMs < 0L || candidate.endMs <= candidate.startMs -> DiscardReason.INVALID_INTERVAL
                videoDurationMs > 0L && candidate.endMs > videoDurationMs -> DiscardReason.OUTSIDE_VIDEO
                coverageStart != null && coverageEnd != null &&
                    (candidate.startMs < coverageStart - TRANSCRIPT_TOLERANCE_MS ||
                        candidate.endMs > coverageEnd + TRANSCRIPT_TOLERANCE_MS) -> DiscardReason.OUTSIDE_TRANSCRIPT
                else -> null
            }
            if (reason == null) kept += candidate
            else discarded += DiscardedCandidate(candidate.title, candidate.startMs, candidate.endMs, reason)
        }
        return SanitizedCandidates(kept, discarded)
    }
}
