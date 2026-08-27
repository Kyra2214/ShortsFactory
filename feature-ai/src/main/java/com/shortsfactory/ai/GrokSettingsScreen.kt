package com.shortsfactory.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.SecureKeyStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrokSettingsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grok (xAI)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Insira sua chave de API do Grok para habilitar a análise por IA.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Chave da API (xai-...)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true
            )
            val context = LocalContext.current
            Button(
                onClick = {
                    if (apiKey.isBlank()) {
                        status = "Informe a chave primeiro."
                        return@Button
                    }
                    status = null
                    testing = true
                    scope.launch {
                        try {
                            val provider = grokProvider(context, apiKey)
                            provider.analyzeVideo(
                                com.shortsfactory.domain.model.Transcript(
                                    listOf(
                                        com.shortsfactory.domain.model.TranscriptSegment(0, 5000, "teste de conexão")
                                    )
                                ),
                                com.shortsfactory.domain.pipeline.GenerationSummaryHint("30s")
                            )
                            provider.persistApiKey(apiKey)
                            status = "Chave validada e salva com sucesso."
                        } catch (e: Exception) {
                            status = "Falha: ${e.message}"
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (testing) "Testando..." else "Testar e salvar")
            }
            if (testing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            status?.let {
                Card(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "A chave é armazenada criptografamente no dispositivo e nunca sai dele além das chamadas oficiais à API da xAI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private interface GrokSettingsEntryPoint {
    fun keyStore(): SecureKeyStore
}

private fun grokProvider(context: Context, apiKey: String): GrokProvider {
    // Entrada manual ao gráfico Hilt (tela sem @AndroidEntryPoint).
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext, GrokSettingsEntryPoint::class.java
    )
    return GrokProvider(entryPoint.keyStore()).also { it.overrideKey(apiKey) }
}

