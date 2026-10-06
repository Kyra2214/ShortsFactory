package com.shortsfactory.domain.importing

/** Intervalo de um `Content-Range: bytes start-end/total` (`total` nulo quando `*`). */
data class ContentRange(val start: Long, val end: Long, val total: Long?)

/** Regras puras para retomada de download via HTTP Range. */
object HttpResume {
    private val RANGE = Regex("""^\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$""", RegexOption.IGNORE_CASE)

    fun parseContentRange(header: String?): ContentRange? {
        val m = RANGE.matchEntire(header ?: return null) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        val end = m.groupValues[2].toLongOrNull() ?: return null
        val total = m.groupValues[3].let { if (it == "*") null else it.toLongOrNull() ?: return null }
        if (end < start || (total != null && end >= total)) return null
        return ContentRange(start, end, total)
    }

    /** Validador para `If-Range`: ETag forte, senão `Last-Modified`; ETag fraco (`W/`) não serve. */
    fun ifRangeValidator(etag: String?, lastModified: String?): String? =
        etag?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("W/") }
            ?: lastModified?.trim()?.takeIf { it.isNotEmpty() }

    /** @return mensagem de erro se a resposta 206 não continua o parcial em [offset]. */
    fun validateResume(range: ContentRange?, offset: Long): String? {
        if (range == null) return "A fonte retornou um Content-Range inválido para a retomada."
        if (range.start != offset) return "A fonte retornou um intervalo HTTP incompatível com o download parcial."
        if (range.total != null && range.total > ImportSpacePolicy.MAX_IMPORT_BYTES) return "O vídeo excede o limite local."
        return null
    }
}
