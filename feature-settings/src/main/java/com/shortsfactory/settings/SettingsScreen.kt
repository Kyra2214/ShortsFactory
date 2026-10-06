package com.shortsfactory.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.model.SubtitleStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onUpdate: (resolution: String, quality: String, fps: Int, subtitleStyle: String, duration: String) -> Unit,
    onOpenGrokSettings: () -> Unit,
    onOpenFreeApis: () -> Unit,
    onBack: () -> Unit
) {
    val context: Context = LocalContext.current
    val keyStore = remember { SecureKeyStore(context) }

    var resolution by remember { mutableStateOf(keyStore.resolution()) }
    var quality by remember { mutableStateOf(keyStore.quality()) }
    var fps by remember { mutableIntStateOf(keyStore.fps()) }
    var subtitleStyle by remember { mutableStateOf(keyStore.subtitleStyle()) }
    var duration by remember { mutableStateOf(keyStore.durationPreset()) }
    var savedSnapshot by remember {
        mutableStateOf(listOf<Any>(resolution, quality, fps, subtitleStyle, duration))
    }
    val dirty = listOf<Any>(resolution, quality, fps, subtitleStyle, duration) != savedSnapshot

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = {
                        keyStore.saveSettings(resolution, quality, fps, subtitleStyle, duration)
                        onUpdate(resolution, quality, fps, subtitleStyle, duration)
                        savedSnapshot = listOf(resolution, quality, fps, subtitleStyle, duration)
                    },
                    enabled = dirty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (dirty) "Salvar alterações" else "Tudo salvo")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(24.dp))
            SfSectionTitle("Exportação padrão")
            SettingsLabel("Resolução")
            ChipRow {
                ResolutionPreset.ALL.forEach { preset ->
                    FilterChip(
                        selected = resolution == preset.label,
                        onClick = { resolution = preset.label },
                        label = { Text(preset.label) }
                    )
                }
            }
            SettingsLabel("Qualidade")
            ChipRow {
                ExportQuality.entries.forEach { q ->
                    FilterChip(
                        selected = quality == q.label,
                        onClick = { quality = q.label },
                        label = { Text(q.label) }
                    )
                }
            }
            SettingsLabel("Quadros por segundo")
            ChipRow {
                listOf(24, 30, 60).forEach { option ->
                    FilterChip(
                        selected = fps == option,
                        onClick = { fps = option },
                        label = { Text("$option fps") }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            SfSectionTitle("Duração padrão")
            Spacer(Modifier.height(8.dp))
            ChipRow {
                DurationPreset.ALL.forEach { preset ->
                    FilterChip(
                        selected = duration == preset.key,
                        onClick = { duration = preset.key },
                        label = { Text(preset.label) }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            SfSectionTitle("Estilo de legenda")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleStyle.entries.forEach { style ->
                    val selected = subtitleStyle == style.key
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { subtitleStyle = style.key },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(style.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(style.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SfSectionTitle("Inteligência artificial")
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGrokSettings),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("IA e chaves", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Chaves xAI e OpenAI usadas na análise e na transcrição",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFreeApis),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("APIs gratuitas", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Provedores com plano gratuito: cadastro, chaves e teste",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}
