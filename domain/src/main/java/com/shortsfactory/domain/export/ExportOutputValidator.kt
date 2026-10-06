package com.shortsfactory.domain.export

import com.shortsfactory.domain.pipeline.InputVideoInfo

/** Valida o arquivo exportado (novo ou a reutilizar). Retorna a mensagem de erro, ou `null` se válido. */
object ExportOutputValidator {
    fun validate(info: InputVideoInfo, targetWidth: Int, targetHeight: Int, clipDurationMs: Long): String? = when {
        info.width != targetWidth || info.height != targetHeight ->
            "A resolução exportada não corresponde ao preset selecionado."
        info.durationMs <= 0L || info.durationMs > clipDurationMs + DURATION_TOLERANCE_MS ->
            "A duração do arquivo exportado é inválida."
        !info.hasAudio -> "O arquivo exportado não contém áudio."
        else -> null
    }

    private const val DURATION_TOLERANCE_MS = 1_000L
}
