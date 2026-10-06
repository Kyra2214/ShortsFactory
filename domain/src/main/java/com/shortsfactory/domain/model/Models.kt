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
) {
    companion object {
        /** Marca início/fim ausentes na resposta da IA (o saneador descarta o candidato). */
        const val MISSING_MS = -1L
    }
}

/** Segmento de legenda com timing e palavras. */
data class SubtitleSegment(
    val startMs: Long,
    val endMs: Long,
    val words: List<String>
)

/** Configuração visual da legenda (renderizada em PNG e aplicada com overlay do FFmpeg). */
data class SubtitleStyleConfig(
    val styleKey: String,
    val fontSizePx: Int,
    val positionPercent: Double
)

/** Transcrição completa do áudio do vídeo. */
data class Transcript(val segments: List<TranscriptSegment>)

/** Segmento de transcrição com texto. */
data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)

/**
 * Preset de duração configurável do Short. É a ÚNICA fonte da relação chave ↔ duração máxima:
 * pipeline, selector, scorer e telas usam [fromKey]/[key], sem mapas paralelos.
 */
sealed class DurationPreset(val key: String, val label: String, val maxMs: Long?) {
    object FifteenSeconds : DurationPreset("15s", "15 segundos", 15_000L)
    object ThirtySeconds : DurationPreset("30s", "30 segundos", 30_000L)
    object FortyFiveSeconds : DurationPreset("45s", "45 segundos", 45_000L)
    object SixtySeconds : DurationPreset("60s", "60 segundos", 60_000L)
    object NinetySeconds : DurationPreset("90s", "90 segundos", 90_000L)
    object AIDecided : DurationPreset("ai", "IA decide a duração", null)

    companion object {
        val ALL: List<DurationPreset> by lazy {
            listOf(FifteenSeconds, ThirtySeconds, FortyFiveSeconds, SixtySeconds, NinetySeconds, AIDecided)
        }

        /** Preset da chave, ou `null` se desconhecida. */
        fun fromKeyOrNull(key: String): DurationPreset? = ALL.firstOrNull { it.key == key }

        /** Preset da chave; chave desconhecida é erro explícito (nunca um default silencioso). */
        fun fromKey(key: String): DurationPreset = fromKeyOrNull(key)
            ?: throw IllegalArgumentException(
                "Preset de duração desconhecido: \"" + key + "\" (válidos: " + ALL.joinToString { it.key } + ")."
            )
    }
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
        val ALL = listOf(FULL_HD, HD)
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
    val suggestedDurationMs: Long? = null,
    /** Provedor e modelo que de fato responderam (não o "configurado"). */
    val provider: String? = null,
    val model: String? = null
)

/** Origem do dado de um card de tendência. */
enum class TrendOrigin {
    /** Dado vindo de API oficial da plataforma. Único caso em que `views`/`engagement` são permitidos. */
    OFFICIAL_API,

    /** Inferência de IA; nunca é métrica oficial. */
    AI_INFERENCE,

    /** Apenas link de busca/exploração na plataforma, sem dados. */
    LINK_ONLY
}

/** Capacidade real de um provedor de tendências. */
enum class ProviderCapability {
    OFFICIAL_API,
    AI_INFERENCE,
    LINK_ONLY;

    fun toOrigin(): TrendOrigin = when (this) {
        OFFICIAL_API -> TrendOrigin.OFFICIAL_API
        AI_INFERENCE -> TrendOrigin.AI_INFERENCE
        LINK_ONLY -> TrendOrigin.LINK_ONLY
    }
}

/** Card de tendência no Radar de Conteúdo. */
data class TrendCard(
    val title: String,
    val platform: String,
    val region: String,
    /** Métrica oficial; só pode existir com `origin == OFFICIAL_API`. */
    val views: String?,
    /** Métrica oficial; só pode existir com `origin == OFFICIAL_API`. */
    val engagement: String?,
    val sourceUrl: String,
    val openable: Boolean,
    /** Posição (1-based) na ordem de chegada do modo Caçador; 0 = sem ordem. Não é relevância medida. */
    val order: Int = 0,
    val origin: TrendOrigin = TrendOrigin.LINK_ONLY,
    /** Verdadeiro somente quando `views`/`engagement` vêm de API oficial. */
    val metricsVerified: Boolean = false,
    /** Estimativa gerada por IA (rótulo "estimativa da IA"); nunca é métrica. */
    val aiEstimate: String? = null
) {
    init {
        require(origin == TrendOrigin.OFFICIAL_API || (views == null && engagement == null)) {
            "views/engagement só são permitidos com origin OFFICIAL_API"
        }
        require(!metricsVerified || origin == TrendOrigin.OFFICIAL_API) {
            "metricsVerified exige origin OFFICIAL_API"
        }
        require(aiEstimate == null || origin == TrendOrigin.AI_INFERENCE) {
            "aiEstimate exige origin AI_INFERENCE"
        }
    }
}

/** Resultado de uma rodada do Caçador de Tendências. */
data class HunterResult(
    val region: TrendRegion,
    val niche: String,
    val platforms: List<String>,
    val period: String,
    val cards: List<TrendCard>,
    val analysis: String?
)
