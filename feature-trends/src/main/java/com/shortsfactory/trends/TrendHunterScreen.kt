package com.shortsfactory.trends

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import com.shortsfactory.domain.trends.TrendSearchRepository
import kotlinx.coroutines.launch

/**
 * Item 6 do Radar — Modo "Caçador de Tendências".
 * O usuário escolhe região, nicho, plataformas (múltiplas) e período; o sistema
 * ordena os resultados por tendência/relevância/engajamento e permite criar
 * conteúdo original a partir da tendência selecionada (item 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendHunterScreen(
    repository: TrendSearchRepository,
    analyzer: TrendAnalyzer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var region by remember { mutableStateOf(TrendRegion.GLOBAL) }
    var niche by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf<List<String>>(listOf("youtube", "tiktok")) }
    var period by remember { mutableStateOf(HunterPeriods.ALL.first()) }
    var periodExpanded by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf<List<TrendCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caçador de Tendências") },
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
                text = "Configure a caçada: o app consulta a IA e as plataformas selecionadas, organiza por relevância, crescimento e engajamento. Trate o resultado como análise de tendências, não como garantia de viralização.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            Text("Região", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrendRegion.entries.take(4).forEach { r ->
                    OutlinedButton(
                        onClick = { region = r },
                        modifier = Modifier.weight(1f).padding(2.dp)
                    ) {
                        Text("${r.flag} ${r.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = niche,
                onValueChange = { niche = it },
                label = { Text("Nicho (ex.: tecnologia, humor, finanças)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Plataformas", style = MaterialTheme.typography.titleSmall)
            repository.providers().filter { it.platformKey != "grok" }.forEach { provider ->
                val checked = provider.platformKey in selectedPlatforms
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            selectedPlatforms = if (it) selectedPlatforms + provider.platformKey
                            else selectedPlatforms - provider.platformKey
                        }
                    )
                    Text(
                        provider.platformLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Período", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(
                expanded = periodExpanded,
                onExpandedChange = { periodExpanded = it }
            ) {
                OutlinedTextField(
                    value = period,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Período") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = periodExpanded,
                    onDismissRequest = { periodExpanded = false }
                ) {
                    HunterPeriods.ALL.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p) },
                            onClick = {
                                period = p
                                periodExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            cards = repository.hunterSearch(
                                region = region,
                                platforms = selectedPlatforms,
                                niche = niche,
                                period = period
                            )
                        } catch (e: Exception) {
                            error = "Falha na caçada: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                Text(if (loading) "Caçando tendências..." else "Caçar tendências")
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

            if (cards.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Resultados por relevância (${cards.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            cards.forEach { card ->
                Spacer(Modifier.height(8.dp))
                HunterCardItem(
                    card = card,
                    analyzer = analyzer,
                    onOpenUrl = { url -> openUrl(context, url) },
                    onBrief = { card -> /* briefing em card dedicado */ }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HunterCardItem(
    card: TrendCard,
    analyzer: TrendAnalyzer,
    onOpenUrl: (String) -> Unit,
    onBrief: (TrendCard) -> Unit
) {
    val scope = rememberCoroutineScope()
    var briefing by remember(card.sourceUrl) { mutableStateOf<String?>(null) }
    var briefingLoading by remember(card.sourceUrl) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text("🔥 ${card.title}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "Relevância: ${card.relevanceScore}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "${card.platform} • ${card.region}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            card.views?.let { Text("Visualizações: $it", style = MaterialTheme.typography.bodySmall) }
            card.engagement?.let { Text("Engajamento: $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Row {
                if (card.openable && card.sourceUrl.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onOpenUrl(card.sourceUrl) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ver origem", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        briefingLoading = true
                        briefing = null
                        scope.launch {
                            try {
                                briefing = analyzer.briefFromTrend(
                                    trendTitle = card.title,
                                    platform = card.platform,
                                    region = TrendRegion.entries.firstOrNull { it.label == card.region } ?: TrendRegion.GLOBAL
                                )
                            } finally {
                                briefingLoading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !briefingLoading
                ) {
                    Text(if (briefingLoading) "Gerando..." else "Criar baseado na tendência", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (briefingLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            briefing?.let {
                Spacer(Modifier.height(8.dp))
                Text("Briefing:", style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
