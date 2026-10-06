package com.shortsfactory.domain.editor

import com.shortsfactory.domain.pipeline.SelectionRules

/** Intervalo `[startMs, endMs)` de um Short dentro do vídeo-fonte. */
data class TimeRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
    fun overlaps(other: TimeRange): Boolean = endMs > other.startMs && startMs < other.endMs
}

enum class RangeError { START_NEGATIVE, END_NOT_AFTER_START, BEYOND_VIDEO, TOO_SHORT, TOO_LONG, OVERLAPS_OTHER }

sealed class RangeValidation {
    data class Valid(val range: TimeRange) : RangeValidation()
    data class Invalid(val error: RangeError, val message: String) : RangeValidation()
}

/**
 * Regras do intervalo editado de um Short. Mesmos limites da seleção ([SelectionRules]), para que o
 * editor nunca aceite o que o selector rejeitaria: `0 <= início < fim <= duração do vídeo`,
 * duração entre o mínimo e o teto global, sem sobreposição com os outros Shorts do projeto.
 * Nada é "corrigido" em silêncio: valor inválido vira [RangeValidation.Invalid] com mensagem para a UI.
 */
object ShortRangeValidator {

    fun validate(
        startMs: Long,
        endMs: Long,
        videoDurationMs: Long?,
        others: List<TimeRange> = emptyList(),
        rules: SelectionRules = SelectionRules()
    ): RangeValidation {
        if (startMs < 0L) return invalid(RangeError.START_NEGATIVE, "O início não pode ser negativo.")
        if (endMs <= startMs) return invalid(RangeError.END_NOT_AFTER_START, "O fim precisa ser maior que o início.")
        val knownVideoMs = videoDurationMs?.takeIf { it > 0L }
        if (knownVideoMs != null && endMs > knownVideoMs) {
            return invalid(RangeError.BEYOND_VIDEO, "O fim ultrapassa a duração do vídeo (" + seconds(knownVideoMs) + ").")
        }
        val range = TimeRange(startMs, endMs)
        if (range.durationMs < rules.minDurationMs) {
            return invalid(RangeError.TOO_SHORT, "O Short precisa ter pelo menos " + seconds(rules.minDurationMs) + ".")
        }
        if (range.durationMs > rules.aiMaxDurationMs) {
            return invalid(RangeError.TOO_LONG, "O Short não pode passar de " + seconds(rules.aiMaxDurationMs) + ".")
        }
        if (others.any { range.overlaps(it) }) {
            return invalid(RangeError.OVERLAPS_OTHER, "O trecho se sobrepõe a outro Short do projeto.")
        }
        return RangeValidation.Valid(range)
    }

    private fun invalid(error: RangeError, message: String) = RangeValidation.Invalid(error, message)

    private fun seconds(ms: Long): String =
        if (ms % 1000L == 0L) (ms / 1000L).toString() + " s" else (ms / 1000.0).toString() + " s"
}
