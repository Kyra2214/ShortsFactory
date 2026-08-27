package com.shortsfactory.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.domain.model.TrendRegion
import com.shortsfactory.domain.trends.TrendAnalyzer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projectRepository: ProjectRepository,
    analyzer: TrendAnalyzer,
    onNewProject: (uri: String?) -> Unit,
    onOpenRadar: () -> Unit,
    onOpenHunter: () -> Unit,
    onOpenProject: (id: Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val projects by projectRepository.observeAll().collectAsState(initial = emptyList())
    var urlText by remember { mutableStateOf("") }
    var whatsHot by remember { mutableStateOf<String?>(null) }
    var whatsHotLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) onNewProject(uri.toString())
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shorts Factory") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Meu Vídeo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Selecione um vídeo no seu dispositivo para transformar em Shorts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            pickVideo.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Selecionar arquivo")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Ou cole uma URL de vídeo (pública)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val url = urlText.trim()
                            if (url.isNotEmpty()) onNewProject(url)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Criar a partir da URL")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔥 O que está bom agora", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Consulta rápida de tendências por região. A IA analisa temas em crescimento, formatos e hashtags públicas — sem garantia de viralização.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            TrendRegion.BRAZIL, TrendRegion.USA, TrendRegion.CHINA, TrendRegion.GLOBAL
                        ).forEach { region ->
                            OutlinedButton(
                                onClick = {
                                    whatsHotLoading = true
                                    whatsHot = null
                                    scope.launch {
                                        try {
                                            whatsHot = analyzer.analyze("tendências", region)
                                        } finally {
                                            whatsHotLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(2.dp),
                                enabled = !whatsHotLoading
                            ) {
                                Text("${region.flag} ${region.label}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (whatsHotLoading) {
                        Spacer(Modifier.height(8.dp))
                        Text("Consultando IA...", style = MaterialTheme.typography.bodySmall)
                    }
                    whatsHot?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onOpenRadar,
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        ) {
                            Text("Radar completo", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = onOpenHunter,
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        ) {
                            Text("Caçador de tendências", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRadar),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Radar de Conteúdo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Descubra o que está bom agora no Brasil, EUA, China e no mundo — apenas tendências e páginas públicas, sem simulações.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TrendRegion.entries.take(4).forEach { region ->
                            Text(
                                "${region.flag} ${region.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Projetos recentes", style = MaterialTheme.typography.titleMedium)
            if (projects.isEmpty()) {
                Text(
                    text = "Nenhum projeto ainda. Selecione um vídeo para começar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                projects.forEach { project ->
                    ProjectRow(project, onOpenProject)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectEntity, onOpen: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(project.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${project.videoName} • ${project.videoDurationMs / 1000}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
