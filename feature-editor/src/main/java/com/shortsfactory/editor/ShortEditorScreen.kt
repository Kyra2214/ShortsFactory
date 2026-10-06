package com.shortsfactory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.editor.PreviewClipRange
import com.shortsfactory.player.SfVideoPlayer

/** Estado da prévia fiel (render 540x960 com o mesmo pipeline da exportação). */
sealed interface PreviewUiState {
    data object Idle : PreviewUiState
    data class Rendering(val progress: Float) : PreviewUiState
    data class Ready(val path: String) : PreviewUiState
    data class Failed(val message: String) : PreviewUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortEditorScreen(
    title: String,
    hook: String,
    description: String,
    hashtags: String,
    cta: String,
    startMs: Long,
    endMs: Long,
    videoDurationMs: Long,
    videoPath: String?,
    error: String?,
    previewState: PreviewUiState,
    onSave: (title: String, hook: String, description: String, hashtags: String, cta: String, startMs: Long, endMs: Long) -> Unit,
    onPreview: () -> Unit,
    onCancelPreview: () -> Unit,
    onDismissPreview: () -> Unit,
    onBack: () -> Unit
) {
    var titleText by remember(title) { mutableStateOf(title) }
    var hookText by remember(hook) { mutableStateOf(hook) }
    var descriptionText by remember(description) { mutableStateOf(description) }
    var hashtagsText by remember(hashtags) { mutableStateOf(hashtags) }
    var ctaText by remember(cta) { mutableStateOf(cta) }
    var startText by remember(startMs) { mutableFloatStateOf(startMs.toFloat()) }
    var endText by remember(endMs) { mutableFloatStateOf(endMs.toFloat()) }
    var previewStart by remember(startMs) { mutableFloatStateOf(startMs.toFloat()) }
    var previewEnd by remember(endMs) { mutableFloatStateOf(endMs.toFloat()) }
    val previewClip = PreviewClipRange.resolve(previewStart.toLong(), previewEnd.toLong(), videoDurationMs)
    val rangeDirty = startText.toLong() != startMs || endText.toLong() != endMs
    val maxMs = videoDurationMs.coerceAtLeast(1000L).toFloat()

    if (previewState !is PreviewUiState.Idle) {
        FaithfulPreviewDialog(previewState, onCancelPreview, onDismissPreview)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Editar Short", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreview,
                        enabled = !rangeDirty && previewState !is PreviewUiState.Rendering,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Prévia fiel")
                    }
                    Button(
                        onClick = {
                            onSave(
                                titleText, hookText, descriptionText, hashtagsText, ctaText,
                                startText.toLong(), endText.toLong()
                            )
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Salvar")
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
            if (videoPath != null && previewState is PreviewUiState.Idle) {
                SfVideoPlayer(
                    filePath = videoPath,
                    modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally),
                    aspectRatio = 9f / 16f,
                    autoPlay = true,
                    loop = true,
                    clipStartMs = previewClip?.startMs ?: 0L,
                    clipEndMs = previewClip?.endMs,
                    fillFrame = true
                )
                Text(
                    "Prévia rápida do trecho, sem legenda e sem foco automático.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(20.dp))
            }
            SfSectionTitle("Trecho")
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TimeReadout("Início", formatDuration(startText.toLong()), Alignment.Start)
                        TimeReadout(
                            "Duração",
                            "${((endText - startText) / 1000f).toLong()} s",
                            Alignment.CenterHorizontally
                        )
                        TimeReadout("Fim", formatDuration(endText.toLong()), Alignment.End)
                    }
                    RangeSlider(
                        value = startText..endText,
                        onValueChange = { range ->
                            if (range.start != startText) {
                                startText = range.start.coerceAtMost(endText - 1000f).coerceAtLeast(0f)
                            } else {
                                endText = range.endInclusive.coerceAtLeast(startText + 1000f)
                            }
                        },
                        onValueChangeFinished = {
                            previewStart = startText
                            previewEnd = endText
                        },
                        valueRange = 0f..maxMs,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "0:00",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            formatDuration(maxMs.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (rangeDirty) {
                Text(
                    "Salve o trecho para gerar a prévia fiel (ela usa o intervalo salvo).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            SfSectionTitle("Publicação")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = hookText,
                    onValueChange = { hookText = it },
                    label = { Text("Gancho") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Descrição") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = hashtagsText,
                    onValueChange = { hashtagsText = it },
                    label = { Text("Hashtags") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = ctaText,
                    onValueChange = { ctaText = it },
                    label = { Text("Chamada para ação (CTA)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FaithfulPreviewDialog(
    state: PreviewUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (state !is PreviewUiState.Rendering) onDismiss() },
        title = { Text("Prévia fiel") },
        text = {
            when (state) {
                is PreviewUiState.Rendering -> Column {
                    Text("Renderizando com legenda e foco, como na exportação.")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is PreviewUiState.Ready -> SfVideoPlayer(
                    filePath = state.path,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    aspectRatio = 9f / 16f,
                    autoPlay = true,
                    loop = true
                )
                is PreviewUiState.Failed -> Text(state.message)
                PreviewUiState.Idle -> Unit
            }
        },
        confirmButton = {
            if (state is PreviewUiState.Rendering) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
            } else {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    )
}

@Composable
private fun TimeReadout(label: String, value: String, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
