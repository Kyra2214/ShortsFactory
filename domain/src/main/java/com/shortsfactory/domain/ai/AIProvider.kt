package com.shortsfactory.domain.ai

import com.shortsfactory.domain.model.AIAnalysisResult
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.pipeline.GenerationSummaryHint

/** Provedor de análise via IA (Grok/xAI). */
interface AIProvider {
    val providerName: String

    /** Analisa a transcrição de um vídeo e devolve título, resumo e candidatos a Shorts. */
    suspend fun analyzeVideo(transcript: Transcript, hint: GenerationSummaryHint): AIAnalysisResult

    /** Pesquisa tendências públicas via ferramenta de busca do Grok. */
    suspend fun searchTrends(
        query: String,
        region: String,
        platform: String,
        niche: String
    ): List<TrendCard>

    /** Analisa tendências públicas (títulos, descrições e metadados) e identifica padrões. */
    suspend fun analyzeTrends(query: String, region: String): String

    /** Cria um briefing de conteúdo original inspirado em uma tendência (item 8 do Radar). */
    suspend fun briefFromTrend(trendTitle: String, platform: String, region: String): String

    /** Geração de texto livre (ex.: metadados por plataforma). Provedores sem suporte lançam [AiException]. */
    suspend fun generateText(prompt: String): String =
        throw AiException("$providerName não oferece geração de texto livre.")
}
