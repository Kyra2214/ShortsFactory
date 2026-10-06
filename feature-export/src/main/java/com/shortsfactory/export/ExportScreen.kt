package com.shortsfactory.export

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
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.model.ExportPlatform
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: Long,
    shortsCount: Int,
    progress: com.shortsfactory.domain.model.BatchExportProgress?,
    error: String? = null,
    onExport: (platforms: List<String>, quality: String, resolution: String, fps: Int) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
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
                            onClick = { onExport(platforms.toList(), quality, resolution, fps) },
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
private fun ChipRow(content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}
