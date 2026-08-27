package com.shortsfactory.domain.model

/** Candidato a Short identificado pela análise de IA. */
data class ShortCandidate(
    val score: Float,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val hook: String,
    val topic: String,
    val reason: String,
    val focusTrack: com.shortsfactory.domain.pipeline.FocusTrack? = null,
    val subtitles: List<SubtitleSegment> = emptyList()
)

/** Segmento de legenda com timing e palavras. */
data class SubtitleSegment(
    val startMs: Long,
    val endMs: Long,
    val words: List<String>
)

/** Configuração visual de legenda aplicada via drawtext do FFmpeg. */
data class SubtitleStyleConfig(
    val styleKey: String,
    val fontSizePx: Int,
    val positionPercent: Double
)

/** Transcrição completa do áudio do vídeo. */
data class Transcript(val segments: List<TranscriptSegment>)

/** Segmento de transcrição com texto. */
data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)

/** Preset de duração configurável do Short. */
sealed class DurationPreset(val label: String, val maxMs: Long?) {
    object FifteenSeconds : DurationPreset("15 segundos", 15_000L)
    object ThirtySeconds : DurationPreset("30 segundos", 30_000L)
    object FortyFiveSeconds : DurationPreset("45 segundos", 45_000L)
    object SixtySeconds : DurationPreset("60 segundos", 60_000L)
    object NinetySeconds : DurationPreset("90 segundos", 90_000L)
    object AIDecided : DurationPreset("IA decide a duração", null)
}

/** Qualidade de exportação. */
enum class ExportQuality(val label: String, val videoBitrateBps: Long) {
    Economica("Econômica", 3_000_000L),
    Normal("Normal", 8_000_000L),
    Alta("Alta", 12_000_000L),
    Maxima("Máxima", 20_000_000L)
}

/** Estilo de legenda pré-configurado. */
enum class SubtitleStyle(val key: String, val displayName: String, val description: String) {
    Classico("classico", "Clássico", "Legenda branca simples."),
    Impacto("impacto", "Impacto", "Palavras importantes destacadas."),
    Creator("creator", "Creator", "Legenda grande para conteúdo vertical."),
    Minimal("minimal", "Minimal", "Legenda discreta.")
}

/** Preset de resolução vertical. */
data class ResolutionPreset(val label: String, val width: Int, val height: Int) {
    companion object {
        val FULL_HD = ResolutionPreset("1080 × 1920", 1080, 1920)
        val HD = ResolutionPreset("720 × 1280", 720, 1280)
        val ORIGINAL = ResolutionPreset("Original adaptada", 0, 0)
        val ALL = listOf(FULL_HD, HD, ORIGINAL)
    }
}

/** Resultado consolidado do modo "Gerar Tudo". */
data class BatchGenerationResult(
    val originalDurationMs: Long,
    val candidatesFound: Int,
    val shortsGenerated: Int,
    val shortsDiscarded: Int,
    val duplicateOverlapMs: Long
)

/** Status de progresso de exportação em lote. */
data class BatchExportProgress(
    val total: Int,
    val current: Int,
    val currentProgress: Float,
    val isRunning: Boolean
)

/** Plataforma de exportação alvo. */
enum class ExportPlatform(val key: String, val label: String) {
    YOUTUBE("yt", "YouTube Shorts"),
    INSTAGRAM("ig", "Instagram Reels"),
    TIKTOK("tt", "TikTok"),
    FACEBOOK("fb", "Facebook Reels")
}

/** Região de tendência para o Radar de Conteúdo. */
enum class TrendRegion(val key: String, val label: String, val flag: String) {
    BRAZIL("br", "Brasil", "🇧🇷"),
    USA("us", "EUA", "🇺🇸"),
    CHINA("cn", "China", "🇨🇳"),
    JAPAN("jp", "Japão", "🇯🇵"),
    KOREA("kr", "Coreia", "🇰🇷"),
    GLOBAL("global", "Global", "🌎")
}

/** Resultado da análise de IA de um vídeo. */
data class AIAnalysisResult(
    val title: String,
    val summary: String,
    val candidates: List<ShortCandidate>,
    val suggestedDurationMs: Long? = null
)

/** Card de tendência no Radar de Conteúdo. */
data class TrendCard(
    val title: String,
    val platform: String,
    val region: String,
    val views: String?,
    val engagement: String?,
    val sourceUrl: String,
    val openable: Boolean,
    /** Ordem de relevância calculada pelo modo Caçador (0-100). */
    val relevanceScore: Int = 0
)

/** Resultado de uma rodada do Caçador de Tendências. */
data class HunterResult(
    val region: TrendRegion,
    val niche: String,
    val platforms: List<String>,
    val period: String,
    val cards: List<TrendCard>,
    val analysis: String?
)
