package com.shortsfactory.domain.importing

/** Regras puras de limite e espaço livre para importação de vídeo. */
object ImportSpacePolicy {
    const val MAX_IMPORT_BYTES: Long = 8L * 1024L * 1024L * 1024L
    const val FREE_SPACE_MARGIN_BYTES: Long = 100L * 1024L * 1024L

    /**
     * @param requiredBytes bytes ainda a gravar (tamanho conhecido menos o já baixado); `<0` = desconhecido
     * @param totalBytes tamanho total do arquivo final; `<0` = desconhecido
     * @return mensagem de erro, ou `null` se pode prosseguir
     */
    fun check(requiredBytes: Long, totalBytes: Long, freeBytes: Long): String? {
        if (totalBytes > MAX_IMPORT_BYTES) return "O vídeo excede o limite local."
        if (requiredBytes >= 0L && requiredBytes + FREE_SPACE_MARGIN_BYTES > freeBytes) {
            return "Espaço de armazenamento insuficiente para importar o vídeo."
        }
        return null
    }
}
