package com.shortsfactory.ai

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.pipeline.GenerationSummaryHint

/**
 * Orquestra provedores de análise e usa o próximo quando o anterior está sem acesso,
 * limitado, indisponível ou retorna uma resposta inválida.
 */
class MultiAIProvider(
    private val providers: List<AIProvider>
) : AIProvider {
    override val providerName: String = "IA automática"

    override suspend fun analyzeVideo(
        transcript: Transcript,
        hint: GenerationSummaryHint
    ): AIAnalysisResult = withFallback { it.analyzeVideo(transcript, hint) }

    override suspend fun searchTrends(
        query: String,
        region: String,
        platform: String,
        niche: String
    ): List<TrendCard> = withFallback { it.searchTrends(query, region, platform, niche) }

    override suspend fun analyzeTrends(query: String, region: String): String =
        withFallback { it.analyzeTrends(query, region) }

    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String =
        withFallback { it.briefFromTrend(trendTitle, platform, region) }

    private suspend fun <T> withFallback(operation: suspend (AIProvider) -> T): T {
        require(providers.isNotEmpty()) { "Nenhum provedor de IA foi configurado." }
        var lastError: Throwable? = null
        providers.forEach { provider ->
            try {
                return operation(provider)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                lastError = error
            }
        }
        throw IllegalStateException(
            "Nenhum provedor de IA está disponível no momento" +
                (lastError?.message?.let { ": ${it.take(180)}" } ?: ".")
        )
    }
}
