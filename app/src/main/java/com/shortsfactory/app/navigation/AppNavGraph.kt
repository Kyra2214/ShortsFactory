package com.shortsfactory.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shortsfactory.ai.GrokSettingsScreen
import com.shortsfactory.editor.ShortEditorScreen
import com.shortsfactory.export.ExportScreen
import com.shortsfactory.home.HomeScreen
import com.shortsfactory.projects.ProjectScreen
import com.shortsfactory.settings.SettingsScreen
import com.shortsfactory.trends.ContentRadarScreen
import com.shortsfactory.trends.TrendHunterScreen
import com.shortsfactory.viewmodels.EditorViewModel
import com.shortsfactory.viewmodels.ExportViewModel
import com.shortsfactory.viewmodels.HomeViewModel
import com.shortsfactory.viewmodels.ProjectViewModel

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

object Routes {
    const val HOME = "home"
    const val RADAR = "radar"
    const val HUNTER = "hunter"
    const val SETTINGS = "settings"
    const val GROK_SETTINGS = "grok_settings"
    const val PROJECT = "project/{projectId}"
    const val EDITOR = "editor/{shortId}"
    const val EXPORT = "export/{projectId}"
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        TabItem(Routes.HOME, "Início", Icons.Filled.Home),
        TabItem(Routes.RADAR, "Tendências", Icons.AutoMirrored.Filled.TrendingUp),
        TabItem(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings)
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.padding(inner).consumeWindowInsets(inner)
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                projectRepository = viewModel.projectRepository,
                analyzer = viewModel.trendAnalyzer,
                onNewProject = { uri ->
                    val encodedUri = android.net.Uri.encode(uri ?: "")
                    navController.navigate("project/new?uri=$encodedUri")
                },
                onOpenHunter = { navController.navigate(Routes.HUNTER) },
                onOpenProject = { projectId ->
                    navController.navigate("project/$projectId")
                }
            )
        }

        composable(
            route = "project/new?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
            val viewModel: ProjectViewModel = hiltViewModel()
            val createdProjectId by viewModel.createdProjectId.collectAsState()

            LaunchedEffect(uriStr) {
                viewModel.setSource(uriStr)
            }
            LaunchedEffect(createdProjectId) {
                createdProjectId?.let { projectId ->
                    navController.navigate("project/$projectId") {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            }

            ProjectScreen(
                isNew = true,
                sourceUri = uriStr,
                onRunAnalysis = { viewModel.runAnalysis() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            val viewModel: ProjectViewModel = hiltViewModel(key = "project_$projectId")
            val candidates by viewModel.candidates.collectAsState()
            val progress by viewModel.progress.collectAsState()
            val preset by viewModel.selectedPreset.collectAsState()
            val error by viewModel.error.collectAsState()
            LaunchedEffect(projectId) { viewModel.load(projectId) }
            ProjectScreen(
                projectId = projectId,
                candidates = candidates,
                progress = progress,
                preset = preset,
                error = error,
                onRunAnalysis = { viewModel.runAnalysis() },
                onChangePreset = { key -> viewModel.changePreset(key) },
                onCancel = { viewModel.cancelAnalysis() },
                onEdit = { shortId -> navController.navigate("editor/$shortId") },
                onExport = { pid -> navController.navigate("export/$pid") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("shortId") { type = NavType.LongType })
        ) { backStackEntry ->
            val shortId = backStackEntry.arguments?.getLong("shortId") ?: return@composable
            val viewModel: EditorViewModel = hiltViewModel(key = "editor_$shortId")
            LaunchedEffect(shortId) { viewModel.load(shortId) }
            val title by viewModel.title.collectAsState()
            val hook by viewModel.hook.collectAsState()
            val description by viewModel.description.collectAsState()
            val hashtags by viewModel.hashtags.collectAsState()
            val cta by viewModel.cta.collectAsState()
            val startMs by viewModel.startMs.collectAsState()
            val endMs by viewModel.endMs.collectAsState()
            val videoDurationMs by viewModel.videoDurationMs.collectAsState()
            val videoPath by viewModel.videoPath.collectAsState()
            val previewState by viewModel.previewState.collectAsState()
            val editorError by viewModel.error.collectAsState()
            val saved by viewModel.saved.collectAsState()
            // Só volta depois de gravar com sucesso; intervalo inválido fica na tela com a mensagem.
            LaunchedEffect(saved) { if (saved) navController.popBackStack() }
            ShortEditorScreen(
                title = title,
                hook = hook,
                description = description,
                hashtags = hashtags,
                cta = cta,
                startMs = startMs,
                endMs = endMs,
                videoDurationMs = videoDurationMs,
                videoPath = videoPath,
                error = editorError,
                previewState = previewState,
                onSave = { title, hook, description, hashtags, cta, start, end ->
                    viewModel.saveMetadata(title, hook, description, hashtags, cta, start, end)
                },
                onPreview = { viewModel.renderPreview() },
                onCancelPreview = { viewModel.cancelPreview() },
                onDismissPreview = { viewModel.dismissPreview() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            val viewModel: ExportViewModel = hiltViewModel(key = "export_$projectId")
            LaunchedEffect(projectId) { viewModel.load(projectId) }
            val shortsCount by viewModel.shortsCount.collectAsState()
            val exportProgress by viewModel.progress.collectAsState()
            val exportError by viewModel.error.collectAsState()
            ExportScreen(
                projectId = projectId,
                shortsCount = shortsCount,
                progress = exportProgress,
                error = exportError,
                onExport = { platforms, quality, resolution, fps ->
                    viewModel.startExport(platforms, quality, resolution, fps)
                },
                onCancel = { viewModel.cancel() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RADAR) {
            val viewModel: HomeViewModel = hiltViewModel()
            ContentRadarScreen(
                repository = viewModel.trendSearchRepository,
                analyzer = viewModel.trendAnalyzer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HUNTER) {
            val viewModel: HomeViewModel = hiltViewModel()
            TrendHunterScreen(
                repository = viewModel.trendSearchRepository,
                analyzer = viewModel.trendAnalyzer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onUpdate = { _, _, _, _, _ -> /* preferences persistidas automaticamente */ },
                onOpenGrokSettings = { navController.navigate(Routes.GROK_SETTINGS) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GROK_SETTINGS) {
            GrokSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
    }
}
