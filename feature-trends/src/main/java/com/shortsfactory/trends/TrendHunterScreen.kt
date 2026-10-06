package com.shortsfactory.trends

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import com.shortsfactory.domain.trends.TrendSearchRepository
import kotlinx.coroutines.launch

/**
 * Modo "Caçador de Tendências" (item 6 do Radar).
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Caçador de Tendências", style = MaterialTheme.typography.titleLarge) },
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            HunterContent(repository, analyzer)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun HunterContent(repository: TrendSearchRepository, analyzer: TrendAnalyzer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var region by remember { mutableStateOf(TrendRegion.GLOBAL) }
    var niche by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf<List<String>>(listOf("youtube", "tiktok")) }
    var period by remember { mutableStateOf(HunterPeriods.ALL.first()) }
    var cards by remember { mutableStateOf<List<TrendCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Consulta a IA e as plataformas selecionadas e organiza por relevância, crescimento e engajamento. É análise de tendências, não garantia de viralização.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TrendChipSection("Região") {
        TrendRegion.entries.take(4).forEach { r ->
            FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r.label) })
        }
    }

    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = niche,
        onValueChange = { niche = it },
        label = { Text("Nicho (ex.: tecnologia, humor, finanças)") },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )

    TrendChipSection("Plataformas") {
        repository.providers().filter { it.platformKey != "grok" }.forEach { provider ->
            val checked = provider.platformKey in selectedPlatforms
            FilterChip(
                selected = checked,
                onClick = {
                    selectedPlatforms = if (checked) selectedPlatforms - provider.platformKey
                    else selectedPlatforms + provider.platformKey
                },
                label = { Text(provider.platformLabel) }
            )
        }
    }

    TrendChipSection("Período") {
        HunterPeriods.ALL.forEach { p ->
            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
        }
    }

    Spacer(Modifier.height(20.dp))
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "Falha na caçada: ${e.message}"
                } finally {
                    loading = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
        enabled = !loading
    ) {
        Text(if (loading) "Caçando tendências..." else "Caçar tendências")
    }
    if (loading) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    error?.let { TrendErrorBlock(it) }

    if (cards.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SfSectionTitle("Resultados por relevância (${cards.size})")
    }
    cards.forEach { card ->
        Spacer(Modifier.height(10.dp))
        HunterCardItem(
            card = card,
            analyzer = analyzer,
            onOpenUrl = { url -> openUrl(context, url) }
        )
    }
}

@Composable
private fun HunterCardItem(
    card: TrendCard,
    analyzer: TrendAnalyzer,
    onOpenUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var briefing by remember(card.sourceUrl) { mutableStateOf<String?>(null) }
    var briefingLoading by remember(card.sourceUrl) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (card.order > 0) {
                    Text(
                        "#${card.order}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Text(
                "${card.platform} · ${card.region}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TrendCardInfo(card)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (card.openable && card.sourceUrl.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onOpenUrl(card.sourceUrl) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ver origem", style = MaterialTheme.typography.labelMedium)
                    }
                }
                FilledTonalButton(
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
                    Text(
                        if (briefingLoading) "Gerando..." else "Criar com a tendência",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (briefingLoading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            briefing?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Briefing", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
