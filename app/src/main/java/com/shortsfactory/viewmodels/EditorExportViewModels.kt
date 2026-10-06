package com.shortsfactory.viewmodels

import com.shortsfactory.core.SecureKeyStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.shortsfactory.app.work.ShortsWorkScheduler
import com.shortsfactory.app.work.WorkKeys
import com.shortsfactory.data.repository.ExportBatchRepository
import com.shortsfactory.data.repository.ExportRepository
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ProjectStore
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.ShortUpdateResult
import com.shortsfactory.domain.export.ExportBatchState
import com.shortsfactory.domain.model.BatchExportProgress
import com.shortsfactory.editor.PreviewUiState
import com.shortsfactory.export.ClipPreviewManager
import com.shortsfactory.export.ShortsProcessingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val shortRepository: ShortRepository,
    private val projectRepository: ProjectRepository,
    private val projectStore: ProjectStore,
    private val clipPreviewManager: ClipPreviewManager,
    private val keyStore: SecureKeyStore
) : ViewModel() {

    private val _preview = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    private var previewJob: Job? = null
    val previewState: StateFlow<PreviewUiState> = _preview.asStateFlow()

    /** Renderiza (ou reaproveita do cache) a prévia fiel do Short salvo; cancelável. */
    fun renderPreview() {
        if (previewJob?.isActive == true) return
        _preview.value = PreviewUiState.Rendering(0f)
        previewJob = viewModelScope.launch {
            try {
                val path = clipPreviewManager.render(shortId, keyStore.subtitleStyle()) { p ->
                    _preview.value = PreviewUiState.Rendering(p)
                }
                _preview.value = PreviewUiState.Ready(path)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                _preview.value = PreviewUiState.Idle
                throw ce
            } catch (e: Exception) {
                _preview.value = PreviewUiState.Failed(e.message ?: "Não foi possível gerar a prévia.")
            }
        }
    }

    fun cancelPreview() { previewJob?.cancel() }

    fun dismissPreview() { _preview.value = PreviewUiState.Idle }

    override fun onCleared() {
        previewJob?.cancel()
        super.onCleared()
    }

    private val _error = MutableStateFlow<String?>(null)
    private val _saved = MutableStateFlow(false)
    private val _title = MutableStateFlow("")
    private val _hook = MutableStateFlow("")
    private val _description = MutableStateFlow("")
    private val _hashtags = MutableStateFlow("")
    private val _cta = MutableStateFlow("")
    private val _startMs = MutableStateFlow(0L)
    private val _endMs = MutableStateFlow(0L)
    private val _videoDurationMs = MutableStateFlow(0L)
    private val _videoPath = MutableStateFlow<String?>(null)
    private var shortId: Long = 0L
    private var projectId: Long = 0L

    val title: StateFlow<String> = _title.asStateFlow()
    val hook: StateFlow<String> = _hook.asStateFlow()
    val description: StateFlow<String> = _description.asStateFlow()
    val hashtags: StateFlow<String> = _hashtags.asStateFlow()
    val cta: StateFlow<String> = _cta.asStateFlow()
    val startMs: StateFlow<Long> = _startMs.asStateFlow()
    val endMs: StateFlow<Long> = _endMs.asStateFlow()
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()
    /** Caminho local do vídeo-fonte para a prévia rápida (`null` até carregar). */
    val videoPath: StateFlow<String?> = _videoPath.asStateFlow()
    /** Mensagem de validação/erro da última tentativa de salvar (`null` = nenhuma). */
    val error: StateFlow<String?> = _error.asStateFlow()
    /** Fica verdadeiro depois que o Short foi salvo; a tela navega de volta só então. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun clearError() { _error.value = null }

    fun load(id: Long) {
        shortId = id
        viewModelScope.launch {
            val entity = shortRepository.getById(id) ?: return@launch
            projectId = entity.projectId
            _title.value = entity.title
            _hook.value = entity.hook
            _description.value = entity.description
            _hashtags.value = entity.hashtags
            _cta.value = entity.cta
            _startMs.value = entity.startMs
            _endMs.value = entity.endMs
            val project = projectRepository.getById(entity.projectId)
            _videoDurationMs.value = project?.videoDurationMs ?: entity.endMs
            _videoPath.value = project?.videoUri
        }
    }

    fun saveMetadata(
        title: String,
        hook: String,
        description: String,
        hashtags: String,
        cta: String,
        start: Long,
        end: Long
    ) {
        viewModelScope.launch {
            _error.value = null
            try {
                // Validação (0 <= início < fim <= vídeo, mínimo/teto, sem sobreposição) e gravação
                // atômica ficam no ProjectStore; intervalo inválido é recusado, não "corrigido".
                when (val result = projectStore.updateShort(shortId, title, hook, description, hashtags, cta, start, end)) {
                    is ShortUpdateResult.Saved -> {
                        _title.value = title.trim()
                        _hook.value = hook.trim()
                        _description.value = description.trim()
                        _hashtags.value = hashtags.trim()
                        _cta.value = cta.trim()
                        _startMs.value = start
                        _endMs.value = end
                        _saved.value = true
                    }
                    is ShortUpdateResult.Rejected -> _error.value = result.message
                    ShortUpdateResult.NotFound -> _error.value = "Short não encontrado."
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                _error.value = e.message ?: "Não foi possível salvar o Short."
            }
        }
    }

    fun getProjectId(): Long = projectId
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val shortRepository: ShortRepository,
    private val workScheduler: ShortsWorkScheduler,
    private val keyStore: SecureKeyStore,
    private val exportRepository: ExportRepository,
    private val exportBatchRepository: ExportBatchRepository,
    private val aiProvider: com.shortsfactory.domain.ai.AIProvider,
    private val metadataRepository: com.shortsfactory.data.repository.PlatformMetadataRepository
) : ViewModel() {

    private val _metadata = MutableStateFlow<List<com.shortsfactory.export.ShortMetadataUi>>(emptyList())
    private val _metadataBusy = MutableStateFlow(false)
    private var metadataJob: Job? = null
    private val _suggestions = MutableStateFlow<List<com.shortsfactory.export.ShortSuggestionUi>>(emptyList())
    private val _suggestionsBusy = MutableStateFlow(false)
    private var suggestionsJob: Job? = null

    val suggestions: StateFlow<List<com.shortsfactory.export.ShortSuggestionUi>> = _suggestions.asStateFlow()
    val suggestionsBusy: StateFlow<Boolean> = _suggestionsBusy.asStateFlow()

    val metadata: StateFlow<List<com.shortsfactory.export.ShortMetadataUi>> = _metadata.asStateFlow()
    val metadataBusy: StateFlow<Boolean> = _metadataBusy.asStateFlow()

    private val _shortsCount = MutableStateFlow(0)
    private val _progress = MutableStateFlow<BatchExportProgress?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private var projectId: Long = 0L
    private var exportObservationJob: Job? = null

    val shortsCount: StateFlow<Int> = _shortsCount.asStateFlow()
    val progress: StateFlow<BatchExportProgress?> = _progress.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(id: Long) {
        projectId = id
        viewModelScope.launch {
            val shorts = shortRepository.getByProject(id)
            _shortsCount.value = shorts.size
            _metadata.value = shorts.mapNotNull { s ->
                metadataRepository.get(s.id).takeIf { it.isNotEmpty() }
                    ?.let { com.shortsfactory.export.ShortMetadataUi(s.id, s.title, it, fromAi = true) }
            }
            restoreExportProgress(id)
            exportObservationJob?.cancel()
            exportObservationJob = launch {
                workScheduler.observeExport(id).collectLatest { infos ->
                    val info = infos.firstOrNull() ?: return@collectLatest
                    updateProgress(info)
                    if (info.state == WorkInfo.State.FAILED) {
                        _error.value = info.outputData.getString(WorkKeys.ERROR) ?: "Falha ao exportar os Shorts."
                    } else if (info.state == WorkInfo.State.SUCCEEDED) {
                        val failed = info.outputData.getInt(WorkKeys.FAILED_COUNT, 0)
                        val total = info.outputData.getInt(WorkKeys.TOTAL, 0)
                        _error.value = if (failed > 0) "$failed de $total exportações falharam." else null
                    }
                }
            }
        }
    }

    /**
     * Reabrir o app: mostra o progresso do último lote persistido e, se o lote ficou aberto sem trabalho
     * ativo no WorkManager, retoma com os parâmetros dos exports pendentes (`getResumableByProject`).
     */
    private suspend fun restoreExportProgress(id: Long) {
        val batch = exportBatchRepository.getLatest(id) ?: return
        val open = ExportBatchState.isOpen(batch.state)
        if (_progress.value == null) {
            _progress.value = BatchExportProgress(
                total = batch.total,
                current = (batch.completed + batch.failed).coerceIn(0, batch.total.coerceAtLeast(0)),
                currentProgress = 0f,
                isRunning = false
            )
        }
        if (!open) return
        val hasActiveWork = workScheduler.observeExport(id).first().any { !it.state.isFinished }
        if (hasActiveWork) return
        val resumable = exportRepository.getResumableByProject(id)
        val pending = resumable.firstOrNull() ?: return
        val perPlatform = pending.quality == PROFILE_QUALITY
        // Modo automático: um export por grupo de arquivo; retoma com as plataformas de todos os pendentes.
        val platforms = if (perPlatform) {
            resumable.flatMap { it.platform.split(',') }.filter { it.isNotBlank() }.distinct()
        } else {
            pending.platform.split(',').filter { it.isNotBlank() }
        }
        workScheduler.enqueueExport(
            id, platforms, pending.quality, pending.resolution, pending.fps, keyStore.subtitleStyle(), perPlatform
        )
    }

    fun startExport(platforms: List<String>, quality: String, resolution: String, fps: Int, automatic: Boolean = false) {
        if (_progress.value?.isRunning == true) return
        _error.value = null
        workScheduler.enqueueExport(projectId, platforms, quality, resolution, fps, keyStore.subtitleStyle(), automatic)
    }

    /** A IA escolhe plataformas por Short com justificativa; sem IA/falha não há sugestão (nada é inventado). */
    fun suggestPlatforms() {
        if (_suggestionsBusy.value) return
        suggestionsJob = viewModelScope.launch {
            _suggestionsBusy.value = true
            try {
                val selector = com.shortsfactory.domain.export.PlatformSelector(aiProvider)
                val candidates = com.shortsfactory.domain.export.PlatformProfiles.all()
                val result = mutableListOf<com.shortsfactory.export.ShortSuggestionUi>()
                var failed = 0
                for (s in shortRepository.getByProject(projectId)) {
                    try {
                        val items = selector.suggest(
                            com.shortsfactory.domain.export.PlatformSuggestionRequest(
                                s.title, s.hook, s.topic, s.description, (s.endMs - s.startMs) / 1000.0, candidates
                            )
                        )
                        result += com.shortsfactory.export.ShortSuggestionUi(s.id, s.title, items)
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        failed++
                    }
                }
                _suggestions.value = result
                if (failed > 0) {
                    _error.value = "$failed Short(s) sem sugestão da IA (sem chave ou falha): escolha as plataformas manualmente."
                }
            } finally {
                _suggestionsBusy.value = false
            }
        }
    }

    /** Gera título/descrição/hashtags por plataforma para cada Short; sem IA usa só título e gancho (não persiste). */
    fun generateMetadata(platformKeys: List<String>) {
        if (_metadataBusy.value) return
        val profiles = platformKeys.mapNotNull { com.shortsfactory.domain.export.PlatformProfiles.forKey(it) }
        if (profiles.isEmpty()) {
            _error.value = "Selecione ao menos uma plataforma."
            return
        }
        metadataJob = viewModelScope.launch {
            _metadataBusy.value = true
            try {
                val generator = com.shortsfactory.domain.export.PlatformMetadataGenerator(aiProvider)
                val result = mutableListOf<com.shortsfactory.export.ShortMetadataUi>()
                var withoutAi = 0
                for (s in shortRepository.getByProject(projectId)) {
                    try {
                        val items = generator.generate(
                            com.shortsfactory.domain.export.PlatformMetadataRequest(s.title, s.hook, s.topic, s.description, profiles)
                        )
                        metadataRepository.save(s.id, items)
                        result += com.shortsfactory.export.ShortMetadataUi(s.id, s.title, items, fromAi = true)
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        withoutAi++
                        val items = profiles.mapNotNull {
                            com.shortsfactory.domain.export.PlatformMetadataValidator.fallback(it, s.title, s.hook)
                        }
                        result += com.shortsfactory.export.ShortMetadataUi(s.id, s.title, items, fromAi = false)
                    }
                }
                _metadata.value = result
                if (withoutAi > 0) {
                    _error.value = "$withoutAi Short(s) sem texto da IA (sem chave ou falha): exibindo só título e gancho."
                }
            } finally {
                _metadataBusy.value = false
            }
        }
    }

    fun cancel() {
        workScheduler.cancelExport(projectId)
        viewModelScope.launch { exportBatchRepository.cancelOpen(projectId) }
    }

    override fun onCleared() {
        exportObservationJob?.cancel()
        metadataJob?.cancel()
        suggestionsJob?.cancel()
        super.onCleared()
    }

    private fun updateProgress(info: WorkInfo) {
        val total = info.progress.getInt(WorkKeys.TOTAL, _shortsCount.value)
        val current = info.progress.getInt(WorkKeys.CURRENT, if (info.state == WorkInfo.State.SUCCEEDED) total else 0)
        val currentProgress = info.progress.getFloat(WorkKeys.PROGRESS, 0f)
        _progress.value = BatchExportProgress(
            total = total,
            current = current.coerceIn(0, total.coerceAtLeast(0)),
            currentProgress = currentProgress.coerceIn(0f, 1f),
            isRunning = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
        )
    }

    private companion object {
        const val PROFILE_QUALITY = "Perfil"
    }
}
