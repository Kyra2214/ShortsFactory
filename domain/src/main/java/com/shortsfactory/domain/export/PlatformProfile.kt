package com.shortsfactory.domain.export

import com.shortsfactory.domain.model.ExportPlatform

/** Limites de texto de publicação de uma plataforma. */
data class PlatformTextLimits(
    val titleMaxChars: Int,
    val descriptionMaxChars: Int,
    val maxHashtags: Int
) {
    init {
        require(titleMaxChars >= 0 && descriptionMaxChars > 0 && maxHashtags >= 0) { "Limites de texto inválidos." }
    }
}

/**
 * Perfil de codificação e publicação de uma plataforma.
 * `sourceNote` registra a origem dos valores; `verified` só é true quando conferido na documentação oficial.
 */
data class PlatformProfile(
    val platform: ExportPlatform,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateBps: Long,
    val maxDurationSec: Int,
    /** Fração da altura reservada na base, livre de legenda (interface da plataforma). */
    val subtitleBottomMarginFraction: Float,
    val textLimits: PlatformTextLimits,
    val sourceNote: String,
    val verified: Boolean
) {
    init {
        require(width > 0 && height > 0 && fps > 0 && videoBitrateBps > 0 && maxDurationSec > 0) { "Perfil inválido." }
        require(subtitleBottomMarginFraction in 0f..0.5f) { "Margem de legenda fora de 0..0.5." }
    }

    val resolutionLabel: String get() = "${width}x$height"

    /** Perfis com a mesma codificação compartilham o mesmo arquivo. */
    val encodingKey: String get() = "${width}x$height|$fps|$videoBitrateBps"
}

object PlatformProfiles {
    private const val UNVERIFIED =
        "ASSUMINDO: valores de referência não conferidos na documentação oficial (ambiente sem rede); conferir antes de marcar como verificado."

    private val table: Map<ExportPlatform, PlatformProfile> = mapOf(
        ExportPlatform.YOUTUBE to PlatformProfile(
            ExportPlatform.YOUTUBE, 1080, 1920, 30, 8_000_000L, 180, 0.20f,
            PlatformTextLimits(titleMaxChars = 100, descriptionMaxChars = 5000, maxHashtags = 15), UNVERIFIED, false
        ),
        ExportPlatform.INSTAGRAM to PlatformProfile(
            ExportPlatform.INSTAGRAM, 1080, 1920, 30, 8_000_000L, 90, 0.22f,
            PlatformTextLimits(titleMaxChars = 0, descriptionMaxChars = 2200, maxHashtags = 30), UNVERIFIED, false
        ),
        ExportPlatform.TIKTOK to PlatformProfile(
            ExportPlatform.TIKTOK, 1080, 1920, 30, 8_000_000L, 180, 0.25f,
            PlatformTextLimits(titleMaxChars = 0, descriptionMaxChars = 2200, maxHashtags = 30), UNVERIFIED, false
        ),
        ExportPlatform.FACEBOOK to PlatformProfile(
            ExportPlatform.FACEBOOK, 1080, 1920, 30, 8_000_000L, 90, 0.20f,
            PlatformTextLimits(titleMaxChars = 0, descriptionMaxChars = 2200, maxHashtags = 30), UNVERIFIED, false
        )
    )

    fun forPlatform(platform: ExportPlatform): PlatformProfile = table.getValue(platform)

    fun forKey(key: String): PlatformProfile? =
        ExportPlatform.entries.firstOrNull { it.key == key }?.let(::forPlatform)

    fun all(): List<PlatformProfile> = ExportPlatform.entries.map(::forPlatform)
}
