package com.shortsfactory.trends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import com.shortsfactory.domain.trends.TrendSearchRepository
import kotlinx.coroutines.launch

/** Tela de Tendências: modos Explorar (Radar) e Caçador no mesmo lugar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentRadarScreen(
    repository: TrendSearchRepository,
    analyzer: TrendAnalyzer,
    onBack: () -> Unit
) {
    var mode by remember { mutableIntStateOf(0) }
    val modes = listOf("Explorar", "Caçador")

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Tendências", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = mode == index,
                        onClick = { mode = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                    ) {
                        Text(label)
                    }
                }
            }
            if (mode == 0) {
                RadarContent(repository, analyzer)
            } else {
                HunterContent(repository, analyzer)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun RadarContent(repository: TrendSearchRepository, analyzer: TrendAnalyzer) {
    var query by remember { mutableStateOf("") }
    var niche by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(TrendRegion.BRAZIL) }
    var platformKey by remember { mutableStateOf("grok") }
    var cards by remember { mutableStateOf<List<TrendCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var whatsHot by remember { mutableStateOf<String?>(null) }
    var whatsHotRegion by remember { mutableStateOf<TrendRegion?>(null) }
    var whatsHotLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Mostra apenas tendências e páginas públicas, sem simular métricas ou dados de acesso.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("O que você quer explorar?") },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = niche,
        onValueChange = { niche = it },
        label = { Text("Nicho (ex.: finanças, receitas, fitness)") },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )

    TrendChipSection("Região") {
        TrendRegion.entries.forEach { r ->
            FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r.label) })
        }
    }
    TrendChipSection("Plataforma") {
        repository.providers().forEach { provider ->
            FilterChip(
                selected = platformKey == provider.platformKey,
                onClick = { platformKey = provider.platformKey },
                label = { Text(provider.platformLabel) }
            )
        }
    }

    Spacer(Modifier.height(20.dp))
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "Falha ao consultar: ${e.message}"
                } finally {
                    loading = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
        enabled = !loading
    ) {
        Text(if (loading) "Consultando..." else "Buscar tendências")
    }
    if (loading) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    error?.let { TrendErrorBlock(it) }

    cards.forEach { card ->
        Spacer(Modifier.height(10.dp))
        TrendCardItem(card)
    }

    TrendChipSection("Análise rápida por região") {
        listOf(TrendRegion.BRAZIL, TrendRegion.USA, TrendRegion.CHINA, TrendRegion.GLOBAL).forEach { r ->
            FilterChip(
                selected = whatsHotRegion == r,
                onClick = {
                    whatsHotRegion = r
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
                enabled = !whatsHotLoading,
                label = { Text(r.label) }
            )
        }
    }
    if (whatsHotLoading) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    whatsHot?.let {
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SfSectionTitle("Análise de tendências (${whatsHotRegion?.label ?: region.label})")
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Análise de tendências, não garantia de viralização.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrendCardItem(card: TrendCard) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${card.platform} · ${card.region}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TrendCardInfo(card)
            Spacer(Modifier.height(8.dp))
            Text(
                text = card.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}
