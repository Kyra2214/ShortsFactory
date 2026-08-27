package com.shortsfactory.export

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.domain.model.ExportPlatform
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: Long,
    shortsCount: Int,
    progress: com.shortsfactory.domain.model.BatchExportProgress?,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar em lote") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isRunning) {
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
                text = "$shortsCount Shorts serão exportados em lote.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            Text("Plataformas de destino", style = MaterialTheme.typography.titleSmall)
            ExportPlatform.entries.forEach { platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = platform.key in platforms,
                        onCheckedChange = { checked ->
                            platforms = if (checked) platforms + platform.key else platforms - platform.key
                        },
                        enabled = !isRunning
                    )
                    Text(platform.label)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Qualidade", style = MaterialTheme.typography.titleSmall)
            ExportQuality.entries.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = quality == q.label,
                        onClick = { quality = q.label },
                        enabled = !isRunning
                    )
                    Text(q.label)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Resolução", style = MaterialTheme.typography.titleSmall)
            ResolutionPreset.ALL.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = resolution == preset.label,
                        onClick = { resolution = preset.label },
                        enabled = !isRunning
                    )
                    Text(preset.label)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("FPS", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(24, 30, 60).forEachIndexed { index, option ->
                    OutlinedButton(
                        onClick = { if (!isRunning) fps = option },
                        modifier = Modifier.weight(1f),
                    ) {

                        Text("$option")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Processando ${progress?.current ?: 0} de ${progress?.total ?: 0}...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Cancelar")
                }
            } else {
                Button(
                    onClick = { onExport(platforms.toList(), quality, resolution, fps) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Iniciar exportação em lote")
                }
            }

            Spacer(Modifier.height(8.dp))
            Card {
                Text(
                    text = "A exportação ocorre 100% no dispositivo, sem envio a servidores externos. Cada Short recebe legendas embutidas e é salvo em Arquivos internos.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
