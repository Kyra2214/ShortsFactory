package com.shortsfactory.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.core.ui.SfSectionTitle
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    projectRepository: ProjectRepository,
    analyzer: TrendAnalyzer,
    onNewProject: (uri: String?) -> Unit,
    onOpenHunter: () -> Unit,
    onOpenProject: (id: Long) -> Unit
) {
    val projects by projectRepository.observeAll().collectAsState(initial = emptyList())
    var urlText by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<TrendRegion?>(null) }
    var whatsHot by remember { mutableStateOf<String?>(null) }
    var whatsHotLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) onNewProject(uri.toString())
        }
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp
            )
        ) {
            item {
                Text("Shorts Factory", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Novo Short", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Escolha um vídeo do aparelho ou cole um link público.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                pickVideo.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Escolher vídeo")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                placeholder = { Text("https://...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = {
                                    val url = urlText.trim()
                                    if (url.isNotEmpty()) onNewProject(url)
                                },
                                enabled = urlText.isNotBlank(),
                                modifier = Modifier.size(52.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Criar a partir da URL"
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                SfSectionTitle(
                    text = "Tendências agora",
                    trailing = { TextButton(onClick = onOpenHunter) { Text("Caçador") } }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TrendRegion.BRAZIL, TrendRegion.USA, TrendRegion.CHINA, TrendRegion.GLOBAL
                    ).forEach { r ->
                        FilterChip(
                            selected = region == r,
                            onClick = {
                                region = r
                                whatsHotLoading = true
                                whatsHot = null
                                scope.launch {
                                    try {
                                        whatsHot = analyzer.analyze("tendências", r)
                                    } finally {
                                        whatsHotLoading = false
                                    }
                                }
                            },
                            enabled = !whatsHotLoading,
                            label = { Text(r.label) }
                        )
                    }
                }
                if (whatsHotLoading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                whatsHot?.let {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Leitura por IA de temas públicos; não garante viralização.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(28.dp))
                SfSectionTitle(text = "Projetos")
                Spacer(Modifier.height(4.dp))
                if (projects.isEmpty()) {
                    Text(
                        text = "Nenhum projeto ainda. Escolha um vídeo para começar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(projects, key = { it.id }) { project ->
                ProjectRow(project, onOpenProject)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, onOpen: (Long) -> Unit) {
    val totalSeconds = project.videoDurationMs / 1000
    val duration = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(project.id) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.extraSmall
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                project.name.firstOrNull()?.uppercaseChar()?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "${project.videoName}, $duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
