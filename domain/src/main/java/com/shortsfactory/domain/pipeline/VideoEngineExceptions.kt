package com.shortsfactory.domain.pipeline

import kotlinx.coroutines.CancellationException

/**
 * O processo externo (ffmpeg/ffprobe) terminou com erro, estourou o tempo limite ou não pôde ser iniciado.
 * Nunca representa cancelamento: cancelamento é sempre [CancellationException]/[FfmpegCancelledException].
 *
 * @param exitCode código de saída do processo; -1 quando o processo foi encerrado por tempo limite.
 * @param stderrTail últimas linhas da saída do processo (limitadas), para diagnóstico.
 */
class FfmpegFailedException(
    val exitCode: Int,
    val stderrTail: String,
    val timedOut: Boolean = false
) : RuntimeException(buildMessage(exitCode, stderrTail, timedOut)) {

    companion object {
        /** Tamanho máximo da saída guardada na exceção (≈ 2 KB). */
        const val MAX_TAIL_CHARS = 2_048

        private fun buildMessage(exitCode: Int, stderrTail: String, timedOut: Boolean): String {
            val head = if (timedOut) "FFmpeg excedeu o tempo limite." else "FFmpeg falhou (exit $exitCode)."
            val tail = stderrTail.trim()
            return if (tail.isEmpty()) head else "$head $tail"
        }
    }
}

/**
 * O processo foi encerrado porque o coroutine dono foi cancelado. É uma [CancellationException]
 * para que `catch (e: Exception)` genérico não a confunda com falha quando relançada, e para que o
 * pipeline/worker gravem o estado "cancelado" (e nunca "falhou").
 */
class FfmpegCancelledException(message: String = "Processamento cancelado.") : CancellationException(message)
