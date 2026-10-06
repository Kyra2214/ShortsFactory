package com.shortsfactory.ai

import android.content.Context
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.ai.catalog.ApiProviderEntry
import com.shortsfactory.domain.ai.routing.ProviderStatsTracker
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.pipeline.GenerationSummaryHint

/** Provedor OpenAI-compatível do catálogo gratuito; reaproveita a camada de chat do [GrokProvider]. */
internal class FreeApiProvider(
    keyStore: SecureKeyStore,
    entry: ApiProviderEntry
) : GrokProvider(
    keyStore = keyStore,
    config = ChatProviderConfig.free(entry),
    keyProvider = { keyStore.getProviderKeys(entry.id) }
) {
    /** Provedores gratuitos não têm busca ao vivo: o `MultiAIProvider` segue para o próximo. */
    override suspend fun searchTrends(
        query: String,
        region: String,
        platform: String,
        niche: String
    ): List<TrendCard> = throw AiException("$providerName não oferece busca de tendências ao vivo.")
}

/**
 * Reúne, a cada chamada, os provedores gratuitos que têm chave salva (desligado = sem chave),
 * na ordem do catálogo, com fallback entre eles.
 */
internal class FreeApisAIProvider(
    private val context: Context,
    private val keyStore: SecureKeyStore
) : AIProvider {
    override val providerName: String = "APIs gratuitas"

    private val statsPrefs = context.applicationContext
        .getSharedPreferences("free_api_routing_stats", Context.MODE_PRIVATE)

    private val tracker = ProviderStatsTracker(
        initialJson = statsPrefs.getString("stats", null),
        onChange = { json -> statsPrefs.edit().putString("stats", json).apply() }
    )

    private fun delegate(): AIProvider {
        val catalog = runCatching { ApiCatalogLoader.load(context) }.getOrNull()
        val active = catalog?.providers.orEmpty()
            .filter { keyStore.hasProviderKey(it.id) }
            .map { FreeApiProvider(keyStore, it) }
        if (active.isEmpty()) throw AiException("Nenhuma API gratuita configurada.")
        return MultiAIProvider(active, routing = tracker)
    }

    override suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult =
        delegate().analyzeVideo(transcript, hint)

    override suspend fun searchTrends(query: String, region: String, platform: String, niche: String): List<TrendCard> =
        delegate().searchTrends(query, region, platform, niche)

    override suspend fun analyzeTrends(query: String, region: String): String =
        delegate().analyzeTrends(query, region)

    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String =
        delegate().briefFromTrend(trendTitle, platform, region)

    override suspend fun generateText(prompt: String): String =
        delegate().generateText(prompt)
}
