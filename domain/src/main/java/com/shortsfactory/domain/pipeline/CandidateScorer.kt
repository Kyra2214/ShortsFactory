package com.shortsfactory.domain.pipeline

import com.shortsfactory.domain.model.ShortCandidate
import com.shortsfactory.domain.model.Transcript

/**
 * Reclassifica candidatos usando sinais determinísticos disponíveis localmente.
 * A IA fornece a hipótese; este componente transforma os sinais observáveis em um score estável.
 */
class CandidateScorer {

    fun score(candidate: ShortCandidate, transcript: Transcript, maxDurationMs: Long?): ShortCandidate {
        val duration = (candidate.endMs - candidate.startMs).coerceAtLeast(1L)
        val ai = candidate.score.coerceIn(0f, 1f)
        val hook = hookScore(candidate)
        val density = speechDensity(candidate, transcript)
        val durationFit = durationFit(duration, maxDurationMs)
        val finalScore = (ai * 0.45f + hook * 0.20f + density * 0.20f + durationFit * 0.15f)
            .coerceIn(0f, 1f)
        return candidate.copy(score = finalScore)
    }

    private fun hookScore(candidate: ShortCandidate): Float {
        val hook = candidate.hook.trim()
        if (hook.isEmpty()) return 0f
        val words = hook.split(Regex("\\s+")).filter(String::isNotBlank)
        val hasQuestionOrExclamation = hook.any { it == '?' || it == '!' }
        val lengthScore = (words.size / 12f).coerceIn(0f, 1f)
        return (lengthScore * 0.65f + if (hasQuestionOrExclamation) 0.35f else 0f).coerceIn(0f, 1f)
    }

    private fun speechDensity(candidate: ShortCandidate, transcript: Transcript): Float {
        val overlapMs = transcript.segments
            .filter { it.endMs > candidate.startMs && it.startMs < candidate.endMs }
            .sumOf { minOf(it.endMs, candidate.endMs) - maxOf(it.startMs, candidate.startMs) }
        return (overlapMs.toDouble() / (candidate.endMs - candidate.startMs).coerceAtLeast(1L))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    private fun durationFit(durationMs: Long, maxDurationMs: Long?): Float {
        if (maxDurationMs == null || maxDurationMs <= 0L) return 1f
        val ratio = durationMs.toDouble() / maxDurationMs.toDouble()
        return when {
            ratio <= 0.55 -> 0.75f
            ratio <= 1.0 -> 1f
            else -> 0f
        }
    }
}
