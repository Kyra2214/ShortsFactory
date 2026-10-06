package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.ShortCandidate

/** Limites globais da seleção, iguais para todos os presets. */
data class SelectionRules(
    /** Duração mínima de um Short (candidatos menores são descartados). */
    val minDurationMs: Long = 3_000L,
    /** Teto do preset "ai" quando a IA não sugere duração. Também é o teto máximo da sugestão. */
    val aiMaxDurationMs: Long = 90_000L
) {
    init {
        require(minDurationMs > 0L) { "minDurationMs deve ser positivo." }
        require(aiMaxDurationMs >= minDurationMs) { "aiMaxDurationMs deve ser >= minDurationMs." }
    }
}

/** Motivo pelo qual um candidato não foi selecionado. */
enum class RejectionReason(val label: String) {
    INVALID_RANGE("intervalo inválido"),
    TOO_SHORT("curto demais"),
    TOO_LONG("acima da duração máxima"),
    BEYOND_VIDEO("fora da duração do vídeo"),
    OVERLAP("sobreposto a candidato de maior score"),
    OVER_LIMIT("acima do limite de candidatos")
}

data class RejectedCandidate(val candidate: ShortCandidate, val reason: RejectionReason)

/** Resultado da seleção: escolhidos (por score) e rejeitados com motivo. */
data class SelectionResult(
    val selected: List<ShortCandidate>,
    val rejected: List<RejectedCandidate>
) {
    fun summary(): String {
        if (rejected.isEmpty()) return selected.size.toString() + " selecionados"
        val byReason = rejected.groupingBy { it.reason }.eachCount()
            .entries.sortedBy { it.key.ordinal }
            .joinToString(", ") { it.value.toString() + " " + it.key.label }
        return selected.size.toString() + " selecionados, " + rejected.size + " rejeitados (" + byReason + ")"
    }
}

/**
 * Seleção determinística. As MESMAS regras valem para todos os presets, inclusive "ai":
 * faixa válida, dentro do vídeo, duração entre o mínimo e o teto, sem sobreposição
 * (vence o maior score) e no máximo `maxCandidates`.
 *
 * Ordem: score desc, depois `startMs` asc, depois `endMs` asc — mesmo conjunto de entrada
 * produz sempre a mesma saída, independentemente da ordem em que chegou.
 */
class CandidateSelector(private val rules: SelectionRules = SelectionRules()) {

    /** Preset desconhecido lança [IllegalArgumentException] (ver [DurationPreset.fromKey]). */
    fun select(
        candidates: List<ShortCandidate>,
        config: GenerationConfig,
        videoDurationMs: Long? = null,
        suggestedDurationMs: Long? = null
    ): List<ShortCandidate> =
        selectWithReport(candidates, config, videoDurationMs, suggestedDurationMs).selected

    /**
     * @param videoDurationMs duração real do vídeo; `null` ou `<= 0` = desconhecida (não limita o fim).
     * @param suggestedDurationMs sugestão da IA, usada como teto só no preset "ai"
     *   (limitada a `[minDurationMs, aiMaxDurationMs]`); sem sugestão, vale `aiMaxDurationMs`.
     */
    fun selectWithReport(
        candidates: List<ShortCandidate>,
        config: GenerationConfig,
        videoDurationMs: Long? = null,
        suggestedDurationMs: Long? = null
    ): SelectionResult {
        val preset = DurationPreset.fromKey(config.preset)
        val ceilingMs = ceilingFor(preset, suggestedDurationMs)
        val limit = config.maxCandidates.coerceAtLeast(0)
        val knownVideoMs = videoDurationMs?.takeIf { it > 0L }

        val rejected = mutableListOf<RejectedCandidate>()
        val eligible = ArrayList<ShortCandidate>()
        for (candidate in candidates) {
            val reason = ineligibility(candidate, ceilingMs, knownVideoMs)
            if (reason != null) rejected += RejectedCandidate(candidate, reason) else eligible += candidate
        }

        val ordered = eligible.sortedWith(
            compareByDescending<ShortCandidate> { it.score }
                .thenBy { it.startMs }
                .thenBy { it.endMs }
        )
        val picked = mutableListOf<ShortCandidate>()
        for (candidate in ordered) {
            when {
                picked.size >= limit -> rejected += RejectedCandidate(candidate, RejectionReason.OVER_LIMIT)
                picked.any { overlaps(it, candidate) } ->
                    rejected += RejectedCandidate(candidate, RejectionReason.OVERLAP)
                else -> picked += candidate
            }
        }
        return SelectionResult(picked, rejected)
    }

    private fun ceilingFor(preset: DurationPreset, suggestedMs: Long?): Long {
        preset.maxMs?.let { return it }
        val suggestion = suggestedMs?.takeIf { it > 0L } ?: return rules.aiMaxDurationMs
        return suggestion.coerceIn(rules.minDurationMs, rules.aiMaxDurationMs)
    }

    private fun ineligibility(candidate: ShortCandidate, ceilingMs: Long, videoMs: Long?): RejectionReason? {
        if (candidate.startMs < 0L || candidate.endMs <= candidate.startMs) return RejectionReason.INVALID_RANGE
        if (videoMs != null && candidate.endMs > videoMs) return RejectionReason.BEYOND_VIDEO
        val duration = candidate.endMs - candidate.startMs
        if (duration < rules.minDurationMs) return RejectionReason.TOO_SHORT
        if (duration > ceilingMs) return RejectionReason.TOO_LONG
        return null
    }

    /** Intervalos encostados (fim == início) não se sobrepõem. */
    private fun overlaps(a: ShortCandidate, b: ShortCandidate): Boolean =
        a.endMs > b.startMs && a.startMs < b.endMs
}
