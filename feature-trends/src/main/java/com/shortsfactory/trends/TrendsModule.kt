package com.shortsfactory.trends

import com.shortsfactory.domain.trends.TrendAnalyzer
import com.shortsfactory.domain.trends.TrendSearchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrendsModule {

    @Provides
    @Singleton
    fun provideTrendAnalyzer(
        aiProvider: com.shortsfactory.domain.ai.AIProvider
    ): TrendAnalyzer = TrendAnalyzerImpl(aiProvider)

    @Provides
    @Singleton
    fun provideTrendSearchRepository(
        aiProvider: com.shortsfactory.domain.ai.AIProvider
    ): TrendSearchRepository = TrendSearchRepositoryImpl(aiProvider)
}
