package com.shortsfactory.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.core.ui.SfSectionTitle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

private const val XAI_API_KEYS_URL = "https://console.x.ai/team/default/api-keys"
private const val OPENAI_API_KEYS_URL = "https://platform.openai.com/api-keys"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrokSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyStore = remember(context) { secureKeyStore(context) }
    val scope = rememberCoroutineScope()
    var apiKeys by remember { mutableStateOf(keyStore.getApiKeys().ifEmpty { listOf("") }) }
    var transcriptionKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var transcriptionStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("IA e chaves", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SfSectionTitle("Análise de conteúdo (xAI)")
            Text(
                text = "Adicione uma ou mais chaves da xAI. O app consulta os modelos de texto liberados para cada chave e tenta automaticamente a próxima opção quando uma chave, modelo ou limite estiver indisponível.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Como criar e configurar", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "1. Entre ou crie sua conta na xAI Console.\n2. Abra a página de API Keys e crie uma chave.\n3. Copie a chave e cole abaixo.\n4. Se quiser mais alternativas, adicione outras chaves em ordem de preferência.\n5. Toque em Testar e salvar chaves.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = "A disponibilidade de modelos, limites e eventuais créditos depende da conta e da própria xAI. O app não grava chaves no código nem nos logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(
                        onClick = { openExternalUrl(context, XAI_API_KEYS_URL) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir página para criar chaves xAI")
                    }
                }
            }

            apiKeys.forEachIndexed { index, key ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { value ->
                            apiKeys = apiKeys.mapIndexed { itemIndex, item ->
                                if (itemIndex == index) value else item
                            }
                        },
                        label = { Text("Chave xAI ${index + 1} (xai-...)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (apiKeys.size > 1) {
                        IconButton(
                            onClick = { apiKeys = apiKeys.filterIndexed { itemIndex, _ -> itemIndex != index } },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover chave ${index + 1}")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { apiKeys = apiKeys + "" },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar outra chave")
            }

            Button(
                onClick = {
                    val configuredKeys = apiKeys.map(String::trim).filter(String::isNotEmpty).distinct()
                    if (configuredKeys.isEmpty()) {
                        status = "Informe pelo menos uma chave xAI."
                        return@Button
                    }
                    status = null
                    testing = true
                    scope.launch {
                        try {
                            val provider = GrokProvider(keyStore).also { it.overrideKeys(configuredKeys) }
                            provider.analyzeVideo(
                                com.shortsfactory.domain.model.Transcript(
                                    listOf(
                                        com.shortsfactory.domain.model.TranscriptSegment(0, 5000, "teste de conexão")
                                    )
                                ),
                                com.shortsfactory.domain.pipeline.GenerationSummaryHint("30s")
                            )
                            provider.persistApiKeys(configuredKeys)
                            val model = provider.lastSuccessfulModel ?: "modelo disponível"
                            val keyNumber = (provider.lastSuccessfulKeyIndex ?: 0) + 1
                            status = "Chaves salvas. Conexão validada com $model usando a chave $keyNumber."
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            status = "Não foi possível validar as chaves. Verifique a rede, a validade e os limites da conta xAI."
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (testing) "Procurando uma IA disponível..." else "Testar e salvar chaves")
            }
            if (testing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            status?.let {
                StatusBlock(it)
            }

            Spacer(Modifier.height(12.dp))
            SfSectionTitle("Transcrição de áudio (OpenAI)")
            Text(
                text = "A chave OpenAI pode ser usada para transcrição e análise de conteúdo. Ela será tentada primeiro; se estiver sem acesso ou limite, o app tentará a xAI automaticamente. O valor não é exibido novamente nem incluído nos logs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = transcriptionKey,
                onValueChange = { transcriptionKey = it },
                label = { Text("Chave OpenAI (sk-...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            TextButton(
                onClick = { openExternalUrl(context, OPENAI_API_KEYS_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir página para criar chave OpenAI")
            }
            Button(
                onClick = {
                    if (transcriptionKey.isBlank()) {
                        transcriptionStatus = "Informe a chave OpenAI primeiro."
                        return@Button
                    }
                    testing = true
                    scope.launch {
                        try {
                            val provider = OpenAiProvider(keyStore).also { it.overrideKey(transcriptionKey) }
                            provider.analyzeVideo(
                                com.shortsfactory.domain.model.Transcript(
                                    listOf(
                                        com.shortsfactory.domain.model.TranscriptSegment(0, 5000, "teste de conexão")
                                    )
                                ),
                                com.shortsfactory.domain.pipeline.GenerationSummaryHint("30s")
                            )
                            keyStore.saveTranscriptionApiKey(transcriptionKey)
                            transcriptionKey = ""
                            transcriptionStatus = "OpenAI validada e salva. Ela será a primeira opção para análise e transcrição."
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            transcriptionStatus = "OpenAI não está disponível para esta chave. Verifique a conta e os limites."
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (testing) "Testando OpenAI..." else "Testar e salvar OpenAI")
            }
            transcriptionStatus?.let {
                StatusBlock(it)
            }
            Text(
                text = "As chaves ficam armazenadas criptograficamente no dispositivo. A ordem automática é OpenAI primeiro e xAI depois; o app passa para a próxima quando uma opção não está disponível.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun StatusBlock(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun openExternalUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
