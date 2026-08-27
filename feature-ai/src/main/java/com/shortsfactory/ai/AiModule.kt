package com.shortsfactory.ai

import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.ai.AIProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAIProvider(keyStore: SecureKeyStore): AIProvider = GrokProvider(keyStore)
}
