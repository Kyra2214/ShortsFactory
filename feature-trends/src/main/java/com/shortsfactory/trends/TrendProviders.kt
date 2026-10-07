package com.shortsfactory.trends

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.ProviderCapability
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendProvider
import com.shortsfactory.domain.trends.TrendSearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class TrendSearchRepositoryImpl @Inject constructor(
    private val aiProvider: AIProvider
) : TrendSearchRepository {

    private val allProviders: List<TrendProvider> = listOf(
        YouTubeTrendProvider,
        InstagramTrendProvider,
        TikTokTrendProvider,
        FacebookTrendProvider,
        RedditTrendProvider,
        DouyinTrendProvider,
        BilibiliTrendProvider,
        KuaishouTrendProvider,
        XiaohongshuTrendProvider
    )

    override fun providers(): List<TrendProvider> = allProviders

    override suspend fun search(
        query: String,
        region: TrendRegion,
        platform: String,
        niche: String
    ): List<TrendCard> = coroutineScope {
        val aiResults = async {
            aiProvider.searchTrends(
                query = query,
                region = region.key,
                platform = platform,
                niche = niche
            )
        }
        val platformCards = async {
            // "grok" já é coberto por aiResults; chamar o provider repetiria a chamada de IA.
            allProviders
                .firstOrNull { it.platformKey == platform && it.platformKey != GROK_KEY }
                ?.search(query, region, niche)
                ?: emptyList()
        }
        aiResults.await() + platformCards.await()
    }

    override suspend fun hunterSearch(
        region: TrendRegion,
        platforms: List<String>,
        niche: String,
        period: String
    ): List<TrendCard> = coroutineScope {
        // Pesquisa híbrida: Google Trends + IA gratuita em paralelo.
        // O RSS ter resultados não desliga a IA.
        val googleCards = try {
            GoogleTrendsSource.fetch(region, niche)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val aiResults = async {
            val query = niche.ifEmpty { "tendências gerais" } + " " + period
            runCatching {
                aiProvider.searchTrends(
                    query = query.trim(),
                    region = region.key,
                    platform = platforms.joinToString(",").ifEmpty { "qualquer" },
                    niche = niche.ifEmpty { "geral" }
                )
            }.getOrElse { emptyList() }
        }
        // Plataformas sem API pública integrada são links explícitos, não dados coletados.
        val platformCards = async {
            platforms.filter { it != GROK_KEY }.mapNotNull { key ->
                allProviders.firstOrNull { it.platformKey == key }
                    ?.search("", region, niche.ifEmpty { "trending" })
            }.flatten()
        }
        val all = googleCards + aiResults.await() + platformCards.await()
        all.mapIndexed { index, card -> card.copy(order = index + 1) }
    }

private const val GROK_KEY = "grok"

/** Períodos disponíveis para o Caçador de Tendências. */
object HunterPeriods {
    val ALL = listOf(
        "Últimas 24 horas",
        "Últimos 7 dias",
        "Últimos 30 dias",
        "Últimos 90 dias"
    )
}

/** Provedor alimentado pela IA (Grok) com conhecimento público sobre tendências. */
class GrokTrendProvider(private val aiProvider: AIProvider) : TrendProvider {
    override val platformKey: String = GROK_KEY
    override val platformLabel: String = "Análise IA (temas em alta)"

    override val capability: ProviderCapability = ProviderCapability.AI_INFERENCE

    override fun officialUrl(region: TrendRegion, niche: String): String = ""

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> {
        return aiProvider.searchTrends(
            query = query,
            region = region.key,
            platform = "qualquer",
            niche = niche
        )
    }
}

/** YouTube: página pública de busca, sempre acessível via navegador. */
object YouTubeTrendProvider : TrendProvider {
    override val platformKey: String = "youtube"
    override val platformLabel: String = "YouTube Shorts"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY

    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(niche, "UTF-8")}&sp=EgIYAQ%253D%253D"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Explorar Shorts em alta no YouTube ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object InstagramTrendProvider : TrendProvider {
    override val platformKey: String = "instagram"
    override val platformLabel: String = "Instagram Reels"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.instagram.com/explore/tags/${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Hashtags em alta no Instagram ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object TikTokTrendProvider : TrendProvider {
    override val platformKey: String = "tiktok"
    override val platformLabel: String = "TikTok"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.tiktok.com/search?q=${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Busca no TikTok ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object FacebookTrendProvider : TrendProvider {
    override val platformKey: String = "facebook"
    override val platformLabel: String = "Facebook Reels"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.facebook.com/search/videos?q=${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Reels em destaque no Facebook ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object RedditTrendProvider : TrendProvider {
    override val platformKey: String = "reddit"
    override val platformLabel: String = "Reddit"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.reddit.com/search/?q=${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Tópicos quentes no Reddit ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object DouyinTrendProvider : TrendProvider {
    override val platformKey: String = "douyin"
    override val platformLabel: String = "Douyin"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.douyin.com/search/${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Explorar no Douyin ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object BilibiliTrendProvider : TrendProvider {
    override val platformKey: String = "bilibili"
    override val platformLabel: String = "Bilibili"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://search.bilibili.com/all?keyword=${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Busca na Bilibili ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object KuaishouTrendProvider : TrendProvider {
    override val platformKey: String = "kuaishou"
    override val platformLabel: String = "Kuaishou"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.kuaishou.com/search/video?searchKey=${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Explorar no Kuaishou ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}

object XiaohongshuTrendProvider : TrendProvider {
    override val platformKey: String = "xiaohongshu"
    override val platformLabel: String = "Xiaohongshu"

    override val capability: ProviderCapability = ProviderCapability.LINK_ONLY
    override fun officialUrl(region: TrendRegion, niche: String): String =
        "https://www.xiaohongshu.com/search_result?keyword=${java.net.URLEncoder.encode(niche, "UTF-8")}"

    override suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard> =
        listOf(
            TrendCard(
                title = "Explorar no Xiaohongshu ($niche)",
                platform = platformLabel,
                region = region.label,
                views = null,
                engagement = null,
                sourceUrl = officialUrl(region, niche),
                openable = true,
                origin = capability.toOrigin()
            )
        )
}
