package com.shortsfactory.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.ai.catalog.ApiProviderEntry
import com.shortsfactory.domain.ai.catalog.ApiRegion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeApiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val keyStore = remember(context) { freeApiKeyStore(context) }
    val catalog = remember(context) { runCatching { ApiCatalogLoader.load(context) }.getOrNull() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("APIs gratuitas", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            Text(
                text = "Provedores com plano gratuito para a análise de conteúdo. Cada um fica desligado até você salvar uma chave. A transcrição do vídeo é enviada ao provedor escolhido e sai do aparelho.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (catalog == null) {
                StatusText("Não foi possível carregar o catálogo de provedores.")
            } else {
                catalog.providers.forEach { provider ->
                    ProviderCard(context, provider, keyStore)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderCard(context: Context, provider: ApiProviderEntry, keyStore: SecureKeyStore) {
    val scope = rememberCoroutineScope()
    var configured by remember(provider.id) { mutableStateOf(keyStore.hasProviderKey(provider.id)) }
    var keys by remember(provider.id) {
        mutableStateOf(keyStore.getProviderKeys(provider.id).size.coerceAtLeast(0))
    }
    val input = remember(provider.id) { mutableStateMapOf<String, String>() }
    var testing by remember(provider.id) { mutableStateOf(false) }
    var status by remember(provider.id) { mutableStateOf<String?>(null) }
    val china = provider.region == ApiRegion.CHINA

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SfSectionTitle(provider.name)
            Text(
                text = if (configured) "Ligado ($keys chave${if (keys == 1) "" else "s"} salva${if (keys == 1) "" else "s"})" else "Desligado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (china) {
                Text(
                    text = "Provedor com servidores na China: a transcrição enviada fica sujeita às regras e à jurisdição do provedor. Use só com conteúdo que você aceita enviar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row {
                TextButton(onClick = { openUrl(context, provider.officialUrl) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cadastrar-se")
                }
                TextButton(onClick = { openUrl(context, provider.documentationUrl) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Documentação")
                }
            }
            OutlinedTextField(
                value = input[provider.id].orEmpty(),
                onValueChange = { input[provider.id] = it },
                label = { Text(if (configured) "Nova chave (substitui as salvas)" else "Chave de API") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = {
                    val key = input[provider.id].orEmpty().trim()
                    if (key.isEmpty()) {
                        status = "Informe a chave primeiro."
                        return@Button
                    }
                    status = null
                    testing = true
                    scope.launch {
                        try {
                            when (val result = FreeApiKeyTester.test(provider, key)) {
                                FreeApiKeyTester.Result.Valid -> {
                                    keyStore.saveProviderKeys(provider.id, listOf(key))
                                    input[provider.id] = ""
                                    configured = true
                                    keys = 1
                                    status = "Chave validada e salva. Provedor ligado."
                                }
                                FreeApiKeyTester.Result.Rejected ->
                                    status = "O provedor recusou a chave. Verifique se ela está correta."
                                is FreeApiKeyTester.Result.Failed -> status = result.message
                            }
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (testing) "Testando..." else "Testar e salvar")
            }
            if (testing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (configured) {
                OutlinedButton(
                    onClick = {
                        keyStore.clearProviderKeys(provider.id)
                        configured = false
                        keys = 0
                        status = "Chaves removidas. Provedor desligado."
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Remover chaves") }
            }
            status?.let { StatusText(it) }
        }
    }
}

@Composable
private fun StatusText(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface FreeApiSettingsEntryPoint {
    fun keyStore(): SecureKeyStore
}

private fun freeApiKeyStore(context: Context): SecureKeyStore =
    EntryPointAccessors.fromApplication(
        context.applicationContext, FreeApiSettingsEntryPoint::class.java
    ).keyStore()
