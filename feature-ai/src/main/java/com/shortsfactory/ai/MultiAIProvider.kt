package com.shortsfactory.ai

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.ai.isTransientFailure
import com.shortsfactory.domain.ai.routing.ProviderRouting
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.pipeline.GenerationSummaryHint

/**
 * Orquestra provedores de análise e usa o próximo quando o anterior está sem acesso,
 * limitado, indisponível ou retorna uma resposta inválida.
 */
class MultiAIProvider(
    private val providers: List<AIProvider>,
    /** Quando presente, reordena os provedores por estatística e registra cada tentativa. */
    private val routing: ProviderRouting? = null
) : AIProvider {
    override val providerName: String = "IA automática"

    override suspend fun analyzeVideo(
        transcript: Transcript,
        hint: GenerationSummaryHint
    ): AIAnalysisResult = withFallback { provider ->
        val result = provider.analyzeVideo(transcript, hint)
        // Quem respondeu de fato; o provedor interno já preenche o modelo.
        result.copy(provider = result.provider ?: provider.providerName)
    }

    override suspend fun searchTrends(
        query: String,
        region: String,
        platform: String,
        niche: String
    ): List<TrendCard> = withFallback(record = false) { it.searchTrends(query, region, platform, niche) }

    override suspend fun analyzeTrends(query: String, region: String): String =
        withFallback { it.analyzeTrends(query, region) }

    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String =
        withFallback { it.briefFromTrend(trendTitle, platform, region) }

    override suspend fun generateText(prompt: String): String =
        withFallback { it.generateText(prompt) }

    private suspend fun <T> withFallback(record: Boolean = true, operation: suspend (AIProvider) -> T): T {
        require(providers.isNotEmpty()) { "Nenhum provedor de IA foi configurado." }
        var lastError: Throwable? = null
        var anyTransient = false
        val ordered = routing?.let { r ->
            val rank = r.order(providers.map { it.providerName }).withIndex().associate { it.value to it.index }
            providers.sortedBy { rank[it.providerName] ?: Int.MAX_VALUE }
        } ?: providers
        ordered.forEach { provider ->
            val startedNs = System.nanoTime()
            try {
                val result = operation(provider)
                if (record) routing?.record(provider.providerName, true, (System.nanoTime() - startedNs) / 1_000_000)
                return result
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (record) routing?.record(provider.providerName, false, (System.nanoTime() - startedNs) / 1_000_000)
                lastError = error
                if (error.isTransientFailure()) anyTransient = true
            }
        }
        throw AiException(
            "Nenhum provedor de IA está disponível no momento" +
                (lastError?.message?.let { ": ${it.take(180)}" } ?: "."),
            transient = anyTransient,
            cause = lastError
        )
    }
}
