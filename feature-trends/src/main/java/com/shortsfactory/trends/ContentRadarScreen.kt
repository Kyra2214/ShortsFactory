package com.shortsfactory.trends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import com.shortsfactory.domain.trends.TrendSearchRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentRadarScreen(
    repository: TrendSearchRepository,
    analyzer: TrendAnalyzer,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var niche by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(TrendRegion.BRAZIL) }
    var platformKey by remember { mutableStateOf("grok") }
    var cards by remember { mutableStateOf<List<TrendCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var whatsHot by remember { mutableStateOf<String?>(null) }
    var whatsHotLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar de Conteúdo") },
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
                text = "Explore o que está em alta no Brasil, EUA, China e no mundo. O app mostra apenas tendências e páginas públicas acessíveis por qualquer pessoa, sem simular métricas ou dados de acesso.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("O que você quer explorar?") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = niche,
                onValueChange = { niche = it },
                label = { Text("Nicho (ex.: finanças, receitas, fitness)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Spacer(Modifier.height(8.dp))
            Text("Região", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrendRegion.entries.forEach { r ->
                    OutlinedButton(
                        onClick = { region = r },
                        modifier = Modifier.weight(1f).padding(2.dp)
                    ) {
                        Text("${r.flag} ${r.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Plataforma", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                repository.providers().forEach { provider ->
                    OutlinedButton(
                        onClick = { platformKey = provider.platformKey },
                        modifier = Modifier.weight(1f).padding(2.dp)
                    ) {
                        Text(provider.platformLabel, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("🔥 O que está bom agora?", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    TrendRegion.BRAZIL, TrendRegion.USA, TrendRegion.CHINA, TrendRegion.GLOBAL
                ).forEach { r ->
                    OutlinedButton(
                        onClick = {
                            whatsHotLoading = true
                            whatsHot = null
                            scope.launch {
                                try {
                                    whatsHot = analyzer.analyze(
                                        query = niche.ifEmpty { "tendências gerais" },
                                        region = r
                                    )
                                } finally {
                                    whatsHotLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).padding(2.dp),
                        enabled = !whatsHotLoading
                    ) {
                        Text("${r.flag} ${r.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (whatsHotLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            whatsHot?.let {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Análise de tendências (${region.flag} ${region.label})", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Análise de tendências, não garantia de viralização.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            cards = repository.search(
                                query = query,
                                region = region,
                                platform = platformKey,
                                niche = niche
                            )
                        } catch (e: Exception) {
                            error = "Falha ao consultar: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                Text(if (loading) "Consultando..." else "Buscar tendências")
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            cards.forEach { card ->
                TrendCardItem(card)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TrendCardItem(card: TrendCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${card.platform} • ${card.region}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            card.views?.let {
                Text("Visualizações estimadas: $it", style = MaterialTheme.typography.bodySmall)
            }
            card.engagement?.let {
                Text("Engajamento: $it", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = card.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
