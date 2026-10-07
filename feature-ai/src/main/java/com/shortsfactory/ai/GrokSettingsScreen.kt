package com.shortsfactory.ai

import androidx.compose.runtime.Composable

/**
 * Compatibilidade de navegação: a antiga tela xAI/OpenAI agora aponta para
 * a tela única de APIs gratuitas. Nenhuma chave paga é solicitada.
 */
@Composable
fun GrokSettingsScreen(onBack: () -> Unit) {
    FreeApiSettingsScreen(onBack = onBack)
}
