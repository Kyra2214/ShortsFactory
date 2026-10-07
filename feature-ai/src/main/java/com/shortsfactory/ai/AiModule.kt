package com.shortsfactory.ai

import android.content.Context
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.ai.AIProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    /** Somente APIs gratuitas configuradas pelo usuário. */
    @Provides
    @Singleton
    fun provideAIProvider(
        keyStore: SecureKeyStore,
        @ApplicationContext context: Context
    ): AIProvider = FreeApisAIProvider(context, keyStore)
}
