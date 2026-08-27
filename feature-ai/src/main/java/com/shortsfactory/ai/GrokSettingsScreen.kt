package com.shortsfactory.ai

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.SecureKeyStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrokSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyStore = remember(context) { secureKeyStore(context) }
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var transcriptionKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var transcriptionStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IA e transcrição") },
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
            Text("Análise de conteúdo", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Insira sua chave de API do Grok para habilitar a análise por IA.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Chave da API (xai-...)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
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
                            val provider = GrokProvider(keyStore).also { it.overrideKey(apiKey) }
                            provider.analyzeVideo(
                                com.shortsfactory.domain.model.Transcript(
                                    listOf(
                                        com.shortsfactory.domain.model.TranscriptSegment(0, 5000, "teste de conexão")
                                    )
                                ),
                                com.shortsfactory.domain.pipeline.GenerationSummaryHint("30s")
                            )
                            provider.persistApiKey(apiKey)
                            status = "Chave Grok validada e salva com sucesso."
                        } catch (e: Exception) {
                            status = "Falha ao validar a chave Grok."
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (testing) "Testando Grok..." else "Testar e salvar Grok")
            }
            if (testing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            status?.let {
                Card(modifier = Modifier.padding(top = 8.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                "Transcrição de áudio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = "A chave OpenAI é usada somente para enviar o áudio à transcrição. O valor não é exibido novamente nem incluído nos logs.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = transcriptionKey,
                onValueChange = { transcriptionKey = it },
                label = { Text("Chave OpenAI (sk-...)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = {
                    if (transcriptionKey.isBlank()) {
                        transcriptionStatus = "Informe a chave de transcrição primeiro."
                    } else {
                        keyStore.saveTranscriptionApiKey(transcriptionKey)
                        transcriptionKey = ""
                        transcriptionStatus = "Chave de transcrição salva com segurança no dispositivo."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar chave de transcrição")
            }
            transcriptionStatus?.let {
                Card(modifier = Modifier.padding(top = 8.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = "As chaves ficam armazenadas criptograficamente no dispositivo. O áudio e as chaves só são enviados às APIs oficiais durante as operações configuradas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface GrokSettingsEntryPoint {
    fun keyStore(): SecureKeyStore
}

private fun secureKeyStore(context: Context): SecureKeyStore =
    EntryPointAccessors.fromApplication(
        context.applicationContext, GrokSettingsEntryPoint::class.java
    ).keyStore()
