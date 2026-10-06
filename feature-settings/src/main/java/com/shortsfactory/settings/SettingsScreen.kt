package com.shortsfactory.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.ExportQuality
import com.shortsfactory.domain.model.ResolutionPreset
import com.shortsfactory.domain.model.SubtitleStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onUpdate: (resolution: String, quality: String, fps: Int, subtitleStyle: String, duration: String) -> Unit,
    onOpenGrokSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context: Context = LocalContext.current
    val keyStore = remember { SecureKeyStore(context) }

    var resolution by remember { mutableStateOf(keyStore.resolution()) }
    var quality by remember { mutableStateOf(keyStore.quality()) }
    var fps by remember { mutableIntStateOf(keyStore.fps()) }
    var subtitleStyle by remember { mutableStateOf(keyStore.subtitleStyle()) }
    var duration by remember { mutableStateOf(keyStore.durationPreset()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
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
            Text("Resolução de exportação", style = MaterialTheme.typography.titleSmall)
            ResolutionPreset.ALL.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = resolution == preset.label, onClick = { resolution = preset.label })
                    Text(preset.label)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Qualidade de vídeo", style = MaterialTheme.typography.titleSmall)
            ExportQuality.entries.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = quality == q.label, onClick = { quality = q.label })
                    Text(q.label)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("FPS", style = MaterialTheme.typography.titleSmall)
            listOf(24, 30, 60).forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = fps == option, onClick = { fps = option })
                    Text("$option fps")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Estilo de legenda", style = MaterialTheme.typography.titleSmall)
            SubtitleStyle.entries.forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = subtitleStyle == style.key,
                        onClick = { subtitleStyle = style.key }
                    )
                    Text("${style.displayName} — ${style.description}")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Duração padrão", style = MaterialTheme.typography.titleSmall)
            DurationPreset.ALL.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = duration == preset.key,
                        onClick = { duration = preset.key }
                    )
                    Text(preset.label)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    keyStore.saveSettings(resolution, quality, fps, subtitleStyle, duration)
                    onUpdate(resolution, quality, fps, subtitleStyle, duration)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar configurações")
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenGrokSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configurar IA e chaves")
            }
        }
    }
}
