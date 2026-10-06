package com.shortsfactory.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfScoreBadge
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.pipeline.PipelineProgress
import com.shortsfactory.domain.pipeline.StageState
import com.shortsfactory.player.SfVideoPlayer

/** Dados de UI de um candidato a Short. */
data class CandidateUi(
    val id: Long,
    val score: Float,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val hook: String,
    val topic: String,
    val reason: String,
    /** Arquivo final exportado (status `done` e arquivo existente); `null` se não houver. */
    val exportedPath: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
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

/** Fluxo de um projeto recém-criado a partir de uma URL/arquivo. */
@Composable
fun ProjectScreen(
    isNew: Boolean = false,
    sourceUri: String = "",
    onRunAnalysis: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ProjectTopBar("Novo Short", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Vídeo importado", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A IA vai transcrever e sugerir os melhores cortes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRunAnalysis,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Analisar vídeo com IA")
            }
        }
    }
}

/** Fluxo de um projeto existente: progresso da pipeline e candidatos. */
@Composable
fun ProjectScreen(
    projectId: Long,
    candidates: List<CandidateUi>,
    progress: PipelineProgress?,
    preset: String,
    error: String? = null,
    onRunAnalysis: () -> Unit,
    onChangePreset: (key: String) -> Unit,
    onCancel: () -> Unit,
    onEdit: (shortId: Long) -> Unit,
    onExport: (projectId: Long) -> Unit,
    onBack: () -> Unit
) {
    val isRunning = progress?.stages?.any { it.state == StageState.PROCESSING } == true
    val ranked = candidates.sortedByDescending { it.score }
    var watching by remember { mutableStateOf<CandidateUi?>(null) }
    // Se o export deixar de existir (intervalo editado, arquivo removido), o diálogo fecha sozinho.
    val watched = watching?.let { w -> candidates.firstOrNull { it.id == w.id && it.exportedPath != null } }
    if (watched?.exportedPath != null) {
        AlertDialog(
            onDismissRequest = { watching = null },
            title = { Text(watched.title, maxLines = 2) },
            text = {
                SfVideoPlayer(
                    filePath = watched.exportedPath,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    aspectRatio = 9f / 16f,
                    autoPlay = true
                )
            },
            confirmButton = { TextButton(onClick = { watching = null }) { Text("Fechar") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { ProjectTopBar("Projeto", onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onExport(projectId) },
                    enabled = candidates.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Exportar em lote")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (progress != null && (!progress.isFinished || error != null)) {
                item { PipelineStatusCard(progress) }
            }
            error?.let { message ->
                item {
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
            }
            item {
                SfSectionTitle("Duração do Short")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DurationPreset.ALL.forEach { option ->
                        FilterChip(
                            selected = option.key == preset,
                            onClick = { onChangePreset(option.key) },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRunAnalysis,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (isRunning) "Análise em andamento..." else "Analisar vídeo com IA")
                }
                if (isRunning) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar análise")
                    }
                }
                Spacer(Modifier.height(16.dp))
                SfSectionTitle("Cortes sugeridos (${candidates.size})")
            }
            if (ranked.isEmpty()) {
                item {
                    Text(
                        text = "Os cortes aparecem aqui depois da análise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ranked, key = { it.id }) { candidate ->
                CandidateCard(candidate, onEdit, onWatch = { watching = candidate })
            }
        }
    }
}

@Composable
fun PipelineStatusCard(progress: PipelineProgress) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val aggregateProgress = progress.stages.map { it.progress }.average().toFloat().coerceIn(0f, 1f)
            Text(
                if (progress.isFinished) "Análise finalizada" else "Processando",
                style = MaterialTheme.typography.titleSmall
            )
            LinearProgressIndicator(
                progress = { aggregateProgress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
            progress.stages.forEach { stage ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    val iconModifier = Modifier.size(18.dp)
                    when (stage.state) {
                        StageState.PENDING -> Icon(
                            Icons.Outlined.RadioButtonUnchecked, null, iconModifier,
                            tint = MaterialTheme.colorScheme.outline
                        )
                        StageState.PROCESSING -> CircularProgressIndicator(
                            modifier = iconModifier,
                            strokeWidth = 2.dp
                        )
                        StageState.COMPLETED -> Icon(
                            Icons.Filled.CheckCircle, null, iconModifier,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        StageState.FAILED -> Icon(
                            Icons.Filled.Error, null, iconModifier,
                            tint = MaterialTheme.colorScheme.error
                        )
                        StageState.CANCELLED -> Icon(
                            Icons.Filled.Cancel, null, iconModifier,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stage.stage.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun CandidateCard(candidate: CandidateUi, onEdit: (Long) -> Unit, onWatch: (Long) -> Unit = {}) {
    val lengthSeconds = ((candidate.endMs - candidate.startMs) / 1000).coerceAtLeast(0)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(candidate.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SfScoreBadge("${candidate.score.toInt()}%")
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${formatDuration(candidate.startMs)} a ${formatDuration(candidate.endMs)}, ${lengthSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(candidate.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                if (candidate.hook.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        candidate.hook,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (candidate.topic.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        candidate.topic,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (candidate.exportedPath != null) {
                IconButton(onClick = { onWatch(candidate.id) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Assistir exportado")
                }
            }
            IconButton(onClick = { onEdit(candidate.id) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar corte")
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
