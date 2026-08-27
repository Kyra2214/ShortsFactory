package com.shortsfactory.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.domain.model.DurationPreset
import com.shortsfactory.domain.model.SubtitleStyle
import com.shortsfactory.domain.pipeline.PipelineProgress
import com.shortsfactory.domain.pipeline.StageState

/** Dados de UI de um candidato a Short. */
data class CandidateUi(
    val id: Long,
    val score: Float,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val hook: String,
    val topic: String,
    val reason: String
)

/** Fluxo de um projeto recém-criado a partir de uma URL/arquivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    isNew: Boolean = false,
    sourceUri: String = "",
    onRunAnalysis: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Short") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Vídeo importado.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRunAnalysis) {
                    Text("Analisar vídeo com IA")
                }
            }
        }
    }
}

/** Fluxo de um projeto existente: progresso da pipeline e candidatos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    projectId: Long,
    candidates: List<CandidateUi>,
    progress: PipelineProgress?,
    preset: String,
    onRunAnalysis: () -> Unit,
    onChangePreset: (key: String) -> Unit,
    onCancel: () -> Unit,
    onEdit: (shortId: Long) -> Unit,
    onExport: (projectId: Long) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projeto") },
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
            if (progress != null && !progress.isFinished) {
                PipelineStatusCard(progress)
                Spacer(Modifier.height(12.dp))
            }

            Text("Duração alvo do Short", style = MaterialTheme.typography.titleSmall)
            val presets = listOf(
                DurationPreset.FifteenSeconds to "15s",
                DurationPreset.ThirtySeconds to "30s",
                DurationPreset.FortyFiveSeconds to "45s",
                DurationPreset.SixtySeconds to "60s",
                DurationPreset.NinetySeconds to "90s",
                DurationPreset.AIDecided to "ai"
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                presets.forEach { (presetObj, key) ->
                    OutlinedButton(
                        onClick = { onChangePreset(key) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(presetObj.label.take(8), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRunAnalysis,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analisar vídeo com IA")
            }

            Spacer(Modifier.height(16.dp))
            Text("Candidatos a Shorts (${candidates.size})", style = MaterialTheme.typography.titleMedium)
            candidates.forEach { candidate ->
                CandidateCard(candidate, onEdit)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onExport(projectId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = candidates.isNotEmpty()
            ) {
                Text("Exportar em lote")
            }
        }
    }
}

@Composable
fun PipelineStatusCard(progress: PipelineProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Processando...", style = MaterialTheme.typography.titleSmall)
            progress.stages.forEach { stage ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    when (stage.state) {
                        StageState.PENDING -> Text("○", style = MaterialTheme.typography.bodySmall)
                        StageState.PROCESSING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.width(14.dp).height(14.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        StageState.COMPLETED -> Text("✓", style = MaterialTheme.typography.bodySmall)
                        StageState.FAILED -> Text("✗", style = MaterialTheme.typography.bodySmall)
                        StageState.CANCELLED -> Text("⊘", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stage.stage.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun CandidateCard(candidate: CandidateUi, onEdit: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    text = "${candidate.score.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(candidate.hook, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${formatDuration(candidate.startMs)} → ${formatDuration(candidate.endMs)}  •  ${candidate.topic}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onEdit(candidate.id) }) {
                Text("Editar")
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
