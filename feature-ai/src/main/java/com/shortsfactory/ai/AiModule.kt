package com.shortsfactory.ai

import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.ai.AIProvider
import dagger.Module
import dagger.Provides
import android.content.Context
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAIProvider(
        keyStore: SecureKeyStore,
        @ApplicationContext context: Context
    ): AIProvider = MultiAIProvider(
        providers = listOf(
            OpenAiProvider(keyStore),
            GrokProvider(keyStore),
            FreeApisAIProvider(context, keyStore)
        )
    )
}
