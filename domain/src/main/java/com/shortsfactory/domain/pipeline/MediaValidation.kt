package com.shortsfactory.domain.pipeline

/** Metadados físicos de uma mídia já localizada no dispositivo. */
data class MediaMetadata(
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean
)

enum class MediaValidationCode {
    VALID,
    FILE_MISSING,
    FILE_EMPTY,
    DURATION_UNKNOWN,
    DIMENSIONS_UNKNOWN,
    AUDIO_MISSING
}

data class MediaValidationResult(
    val valid: Boolean,
    val code: MediaValidationCode,
    val message: String
)

/** Regras puras de validação, reutilizáveis por ViewModel, Worker e testes JVM. */
object MediaValidator {
    fun validate(metadata: MediaMetadata, requireAudio: Boolean = true): MediaValidationResult {
        if (metadata.path.isBlank()) {
            return invalid(MediaValidationCode.FILE_MISSING, "O caminho do vídeo não foi informado.")
        }
        if (metadata.sizeBytes <= 0L) {
            return invalid(MediaValidationCode.FILE_EMPTY, "O arquivo de vídeo está vazio ou indisponível.")
        }
        if (metadata.durationMs <= 0L) {
            return invalid(MediaValidationCode.DURATION_UNKNOWN, "Não foi possível identificar a duração do vídeo.")
        }
        if (metadata.width <= 0 || metadata.height <= 0) {
            return invalid(MediaValidationCode.DIMENSIONS_UNKNOWN, "Não foi possível identificar a resolução do vídeo.")
        }
        if (requireAudio && !metadata.hasAudio) {
            return invalid(MediaValidationCode.AUDIO_MISSING, "O vídeo não possui uma faixa de áudio detectável.")
        }
        return MediaValidationResult(true, MediaValidationCode.VALID, "Mídia válida.")
    }

    private fun invalid(code: MediaValidationCode, message: String) =
        MediaValidationResult(false, code, message)
}
