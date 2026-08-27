package com.shortsfactory.trends

import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import javax.inject.Inject

/** Analisador de tendências via IA (Grok). Implementa a interface TrendAnalyzer. */
class TrendAnalyzerImpl @Inject constructor(
    private val aiProvider: AIProvider
) : TrendAnalyzer {

    override suspend fun analyzeBriefing(query: String, region: TrendRegion): String =
        aiProvider.analyzeTrends(query, region.key)

    override suspend fun analyze(query: String, region: TrendRegion): String =
        aiProvider.analyzeTrends(query, region.key)

    override suspend fun briefFromTrend(trendTitle: String, platform: String, region: TrendRegion): String =
        aiProvider.briefFromTrend(trendTitle, platform, region.key)
}
