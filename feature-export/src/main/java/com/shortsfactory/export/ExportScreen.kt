package com.shortsfactory.export

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.export.PlatformMetadata
import com.shortsfactory.domain.export.PlatformSuggestion
import com.shortsfactory.domain.export.PlatformProfiles
import com.shortsfactory.domain.model.ExportPlatform
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset

/** Textos de publicação de um Short; `fromAi = false` = só título e gancho (sem IA). */
data class ShortMetadataUi(
    val shortId: Long,
    val title: String,
    val items: List<PlatformMetadata>,
    val fromAi: Boolean
)

/** Plataformas sugeridas pela IA para um Short, cada uma com justificativa. */
data class ShortSuggestionUi(
    val shortId: Long,
    val title: String,
    val items: List<PlatformSuggestion>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: Long,
    shortsCount: Int,
    progress: com.shortsfactory.domain.model.BatchExportProgress?,
    error: String? = null,
    metadata: List<ShortMetadataUi> = emptyList(),
    metadataBusy: Boolean = false,
    onGenerateMetadata: (platforms: List<String>) -> Unit = {},
    suggestions: List<ShortSuggestionUi> = emptyList(),
    suggestionsBusy: Boolean = false,
    onSuggestPlatforms: () -> Unit = {},
    onExport: (platforms: List<String>, quality: String, resolution: String, fps: Int, automatic: Boolean) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    var automatic by remember { mutableStateOf(true) }
    var platforms by remember { mutableStateOf(ExportPlatform.entries.map { it.key }.toSet()) }
    var quality by remember { mutableStateOf("Normal") }
    var resolution by remember { mutableStateOf(ResolutionPreset.FULL_HD.label) }
    var fps by remember { mutableIntStateOf(30) }

    val isRunning = progress?.isRunning == true
    val currentProgress = progress?.let { if (it.total > 0) (it.current + it.currentProgress) / it.total else 0f } ?: 0f
    val isDone = progress != null && progress.current == progress.total && progress.total > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Exportar em lote", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isRunning) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (isRunning) {
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Processando ${progress?.current ?: 0} de ${progress?.total ?: 0}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = onCancel) { Text("Cancelar") }
                        }
                    } else {
                        if (isDone) {
                            Text(
                                text = "Exportação concluída.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = { onExport(platforms.toList(), quality, resolution, fps, automatic) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = shortsCount > 0
                        ) {
                            Text(if (isDone) "Exportar novamente" else "Iniciar exportação")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("$shortsCount", style = MaterialTheme.typography.displaySmall)
                    Text(
                        text = if (shortsCount == 1) "Short será exportado" else "Shorts serão exportados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SfSectionTitle("Modo")
            ChipRow {
                FilterChip(
                    selected = automatic,
                    onClick = { automatic = true },
                    enabled = !isRunning,
                    label = { Text("Automático") }
                )
                FilterChip(
                    selected = !automatic,
                    onClick = { automatic = false },
                    enabled = !isRunning,
                    label = { Text("Manual") }
                )
            }
            Text(
                text = if (automatic) {
                    "Um arquivo por perfil de plataforma; plataformas com a mesma codificação compartilham o arquivo."
                } else {
                    "Um único arquivo com a qualidade, resolução e fps escolhidos abaixo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))
            SfSectionTitle("Plataformas")
            ChipRow {
                ExportPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = platform.key in platforms,
                        onClick = {
                            platforms = if (platform.key in platforms) platforms - platform.key else platforms + platform.key
                        },
                        enabled = !isRunning,
                        label = { Text(platform.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onSuggestPlatforms,
                enabled = !isRunning && !suggestionsBusy && shortsCount > 0
            ) {
                Text(if (suggestionsBusy) "Consultando a IA..." else "Sugerir plataformas com IA")
            }
            suggestions.forEach { SuggestionCard(it) }
            if (suggestions.any { it.items.isNotEmpty() }) {
                TextButton(
                    onClick = {
                        platforms = suggestions.flatMap { sug -> sug.items.map { it.platform.key } }.toSet()
                    },
                    enabled = !isRunning
                ) { Text("Aplicar sugestão às plataformas") }
            }

            if (automatic) {
                Spacer(Modifier.height(20.dp))
                SfSectionTitle("Perfis")
                ProfileSummary(platforms)
            } else {
            Spacer(Modifier.height(20.dp))
            SfSectionTitle("Qualidade")
            ChipRow {
                ExportQuality.entries.forEach { q ->
                    FilterChip(
                        selected = quality == q.label,
                        onClick = { quality = q.label },
                        enabled = !isRunning,
                        label = { Text(q.label) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SfSectionTitle("Resolução")
            ChipRow {
                ResolutionPreset.ALL.forEach { preset ->
                    FilterChip(
                        selected = resolution == preset.label,
                        onClick = { resolution = preset.label },
                        enabled = !isRunning,
                        label = { Text(preset.label) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SfSectionTitle("Quadros por segundo")
            ChipRow {
                listOf(24, 30, 60).forEach { option ->
                    FilterChip(
                        selected = fps == option,
                        onClick = { fps = option },
                        enabled = !isRunning,
                        label = { Text("$option fps") }
                    )
                }
            }

            }

            Spacer(Modifier.height(24.dp))
            SfSectionTitle("Textos por plataforma")
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onGenerateMetadata(platforms.toList()) },
                enabled = !isRunning && !metadataBusy && platforms.isNotEmpty() && shortsCount > 0
            ) {
                Text(if (metadataBusy) "Gerando textos..." else "Gerar títulos, descrições e hashtags")
            }
            metadata.forEach { MetadataCard(it) }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "A exportação ocorre 100% no dispositivo, sem envio a servidores externos. Cada Short recebe legendas embutidas e é salvo em Arquivos internos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileSummary(selected: Set<String>) {
    PlatformProfiles.all().filter { it.platform.key in selected }.forEach { p ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(p.platform.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${p.resolutionLabel} · ${p.fps} fps · ${p.videoBitrateBps / 1_000_000} Mbps · até ${p.maxDurationSec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!p.verified) {
                    Text(
                        text = "Valores de referência, ainda não conferidos na documentação oficial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(item: ShortSuggestionUi) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            item.items.forEach { s ->
                Text(
                    text = "${s.platform.label}: ${s.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MetadataCard(item: ShortMetadataUi) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            if (!item.fromAi) {
                Text(
                    text = "Sem texto da IA: apenas título e gancho do corte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.items.forEach { m ->
                val text = metadataText(m)
                Spacer(Modifier.height(12.dp))
                Text(m.platform.label, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copiar") }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartilhar texto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Compartilhar") }
                }
            }
        }
    }
}

private fun metadataText(m: PlatformMetadata): String =
    listOf(m.title, m.description, m.hashtags.joinToString(" ")).filter { it.isNotBlank() }.joinToString("\n\n")

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}
