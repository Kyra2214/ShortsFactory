package com.shortsfactory.domain.trends

import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendRegion

/** Provedor de tendências de uma plataforma (YouTube, TikTok, etc.). */
interface TrendProvider {
    val platformKey: String
    val platformLabel: String

    /** Indica se a integração está disponível via API oficial. */
    fun isAvailable(): Boolean

    /** URL oficial da plataforma para abrir externamente. */
    fun officialUrl(region: TrendRegion, niche: String): String

    /** Busca tendências reais quando a API oficial permitir. */
    suspend fun search(query: String, region: TrendRegion, niche: String): List<TrendCard>
}

/** Repositório agregador de tendências de todas as plataformas. */
interface TrendSearchRepository {
    suspend fun search(
        query: String,
        region: TrendRegion,
        platform: String,
        niche: String
    ): List<TrendCard>

    /** Caçador de Tendências: busca em várias plataformas e ordena por relevância. */
    suspend fun hunterSearch(
        region: TrendRegion,
        platforms: List<String>,
        niche: String,
        period: String
    ): List<TrendCard>

    fun providers(): List<TrendProvider>
}

/** Analisador de tendências via IA (identifica padrões em metadados públicos). */
interface TrendAnalyzer {
    suspend fun analyzeBriefing(query: String, region: TrendRegion): String

    /** Análise de mercado: assuntos em crescimento, formatos, duração típica, hashtags (item 7). */
    suspend fun analyze(query: String, region: TrendRegion): String

    /** Briefing de conteúdo original inspirado em uma tendência (item 8). */
    suspend fun briefFromTrend(trendTitle: String, platform: String, region: TrendRegion): String
}
