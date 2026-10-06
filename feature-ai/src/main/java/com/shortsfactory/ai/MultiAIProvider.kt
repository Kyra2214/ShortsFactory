package com.shortsfactory.ai

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.ai.AiException
import com.shortsfactory.domain.ai.isTransientFailure
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
    ): List<TrendCard> = withFallback { it.searchTrends(query, region, platform, niche) }

    override suspend fun analyzeTrends(query: String, region: String): String =
        withFallback { it.analyzeTrends(query, region) }

    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String =
        withFallback { it.briefFromTrend(trendTitle, platform, region) }

    private suspend fun <T> withFallback(operation: suspend (AIProvider) -> T): T {
        require(providers.isNotEmpty()) { "Nenhum provedor de IA foi configurado." }
        var lastError: Throwable? = null
        var anyTransient = false
        providers.forEach { provider ->
            try {
                return operation(provider)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
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
