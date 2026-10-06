package com.shortsfactory.domain.importing

/** Escolhe a extensão do arquivo importado priorizando o tipo declarado pela fonte, não o texto da URL. */
object ImportExtension {
    const val DEFAULT = "mp4"
    private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "mkv", "avi", "m4v")

    fun fromMime(mime: String?): String? {
        val m = mime?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return when (m) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/x-matroska" -> "mkv"
            "video/x-msvideo" -> "avi"
            "video/x-m4v" -> "m4v"
            else -> null
        }
    }

    fun fromName(name: String?): String? {
        val clean = name?.substringBefore('?')?.substringBefore('#') ?: return null
        val ext = clean.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in VIDEO_EXTENSIONS }
    }

    /** Ordem: MIME (`Content-Type`/`ContentResolver.getType`) → nome/URL → `mp4`. */
    fun resolve(mime: String?, name: String?): String = fromMime(mime) ?: fromName(name) ?: DEFAULT
}
