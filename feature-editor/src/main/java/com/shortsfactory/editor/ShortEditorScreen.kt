package com.shortsfactory.editor

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortEditorScreen(
    shortId: Long,
    title: String,
    hook: String,
    description: String,
    hashtags: String,
    cta: String,
    startMs: Long,
    endMs: Long,
    videoDurationMs: Long,
    onSave: (title: String, description: String, hashtags: String, cta: String, startMs: Long, endMs: Long) -> Unit,
    onPreview: (shortId: Long, localPath: String?) -> Unit,
    onBack: () -> Unit
) {
    var titleText by remember { mutableStateOf(title) }
    var descriptionText by remember { mutableStateOf(description) }
    var hashtagsText by remember { mutableStateOf(hashtags) }
    var ctaText by remember { mutableStateOf(cta) }
    var startText by remember { mutableFloatStateOf(startMs.toFloat()) }
    var endText by remember { mutableFloatStateOf(endMs.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Short") },
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
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Descrição / gancho") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = hashtagsText,
                onValueChange = { hashtagsText = it },
                label = { Text("Hashtags") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ctaText,
                onValueChange = { ctaText = it },
                label = { Text("Chamada para ação (CTA)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text("Intervalo do trecho", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = startText,
                onValueChange = { startText = it.coerceAtMost(endText - 1000f) },
                valueRange = 0f..(videoDurationMs.coerceAtLeast(1000L)).toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = endText,
                onValueChange = { endText = it.coerceAtLeast(startText + 1000f) },
                valueRange = 0f..(videoDurationMs.coerceAtLeast(1000L)).toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(startText.toLong()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(endText.toLong()), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onPreview(shortId, null) },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text("Visualizar")
                }
                Button(
                    onClick = { onSave(titleText, descriptionText, hashtagsText, ctaText, startText.toLong(), endText.toLong()) },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text("Salvar")
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
